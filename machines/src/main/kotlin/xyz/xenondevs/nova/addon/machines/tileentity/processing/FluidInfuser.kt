package xyz.xenondevs.nova.addon.machines.tileentity.processing

import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.invui.item.impl.AbstractItem
import xyz.xenondevs.invui.item.impl.CycleItem
import xyz.xenondevs.invui.item.impl.SimpleItem
import xyz.xenondevs.nova.data.config.NovaConfig
import xyz.xenondevs.nova.data.config.configReloadable
import xyz.xenondevs.nova.data.recipe.RecipeManager
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.addon.machines.recipe.FluidInfuserRecipe
import xyz.xenondevs.nova.addon.machines.registry.Blocks.FLUID_INFUSER
import xyz.xenondevs.nova.addon.machines.registry.GuiMaterials
import xyz.xenondevs.nova.addon.machines.registry.RecipeTypes
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.fluid.FluidType
import xyz.xenondevs.nova.tileentity.network.fluid.holder.NovaFluidHolder
import xyz.xenondevs.nova.tileentity.network.item.holder.NovaItemHolder
import xyz.xenondevs.nova.tileentity.upgrade.Upgradable
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.FluidBar
import xyz.xenondevs.nova.ui.OpenUpgradesItem
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.addon.simpleupgrades.ConsumerEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.getFluidContainer
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import kotlin.math.roundToInt

fun getFluidInfuserInsertRecipeFor(fluidType: FluidType, input: ItemStack): FluidInfuserRecipe? {
    return RecipeManager.novaRecipes[RecipeTypes.FLUID_INFUSER]?.values?.asSequence()
        ?.map { it as FluidInfuserRecipe }
        ?.firstOrNull { recipe ->
            recipe.mode == FluidInfuserRecipe.InfuserMode.INSERT
                && recipe.fluidType == fluidType
                && recipe.input.test(input)
        }
}

fun getFluidInfuserExtractRecipeFor(input: ItemStack): FluidInfuserRecipe? {
    return RecipeManager.novaRecipes[RecipeTypes.FLUID_INFUSER]?.values?.asSequence()
        ?.map { it as FluidInfuserRecipe }
        ?.firstOrNull { recipe ->
            recipe.mode == FluidInfuserRecipe.InfuserMode.EXTRACT
                && recipe.input.test(input)
        }
}

private val ENERGY_PER_TICK = configReloadable { NovaConfig[FLUID_INFUSER].getLong("energy_per_tick") }
private val ENERGY_CAPACITY = configReloadable { NovaConfig[FLUID_INFUSER].getLong("energy_capacity") }
private val FLUID_CAPACITY = configReloadable { NovaConfig[FLUID_INFUSER].getLong("fluid_capacity") }

class FluidInfuser(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState), Upgradable {
    
    override val upgradeHolder = getUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.FLUID)
    private val input = getInventory("input", 1, ::handleInputInventoryUpdate)
    private val output = getInventory("output", 1, ::handleOutputInventoryUpdate)
    private val tank = getFluidContainer("tank", hashSetOf(FluidType.WATER, FluidType.LAVA), FLUID_CAPACITY, upgradeHolder = upgradeHolder)
    override val energyHolder = ConsumerEnergyHolder(this, ENERGY_CAPACITY, ENERGY_PER_TICK, null, upgradeHolder) { createSideConfig(NetworkConnectionType.INSERT, BlockSide.FRONT) }
    override val fluidHolder = NovaFluidHolder(this, tank to NetworkConnectionType.BUFFER) { createSideConfig(NetworkConnectionType.BUFFER, BlockSide.FRONT) }
    override val itemHolder = NovaItemHolder(
        this,
        input to NetworkConnectionType.INSERT,
        output to NetworkConnectionType.EXTRACT
    ) { createSideConfig(NetworkConnectionType.BUFFER, BlockSide.FRONT) }
    
    private var mode = retrieveData("mode") { FluidInfuserRecipe.InfuserMode.INSERT }
    
    private var recipe: FluidInfuserRecipe? = null
    private val recipeTime: Int
        get() = (recipe!!.time.toDouble() / upgradeHolder.getValue(UpgradeTypes.SPEED)).roundToInt()
    private var timePassed = 0
    
    override fun saveData() {
        super.saveData()
        storeData("mode", mode)
    }
    
    private fun handleInputInventoryUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = !event.isRemove && RecipeManager.getConversionRecipeFor(RecipeTypes.FLUID_INFUSER, event.newItem!!) == null
        if (!event.isAdd) reset()
    }
    
    private fun handleOutputInventoryUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = event.updateReason != SELF_UPDATE_REASON && !event.isRemove
    }
    
    private fun reset() {
        this.recipe = null
        this.timePassed = 0
        menuContainer.forEachMenu(FluidInfuserMenu::updateProgress)
    }
    
    override fun handleTick() {
        if (energyHolder.energy >= energyHolder.energyConsumption) {
            if (recipe == null && !input.isEmpty) {
                val item = input.getItem(0)!!
                
                if (mode == FluidInfuserRecipe.InfuserMode.INSERT && tank.hasFluid()) {
                    recipe = getFluidInfuserInsertRecipeFor(tank.type!!, item)
                } else if (mode == FluidInfuserRecipe.InfuserMode.EXTRACT) {
                    recipe = getFluidInfuserExtractRecipeFor(item)
                }
            }
            
            val recipe = recipe
            if (recipe != null) {
                if (((mode == FluidInfuserRecipe.InfuserMode.INSERT && tank.amount >= recipe.fluidAmount)
                        || (mode == FluidInfuserRecipe.InfuserMode.EXTRACT && tank.accepts(recipe.fluidType, recipe.fluidAmount)))
                    && output.canHold(recipe.result)) {
                    
                    energyHolder.energy -= energyHolder.energyConsumption
                    if (++timePassed >= recipeTime) {
                        input.addItemAmount(SELF_UPDATE_REASON, 0, -1)
                        output.addItem(SELF_UPDATE_REASON, recipe.result)
                        
                        if (mode == FluidInfuserRecipe.InfuserMode.INSERT) tank.takeFluid(recipe.fluidAmount)
                        else tank.addFluid(recipe.fluidType, recipe.fluidAmount)
                        
                        reset()
                    } else menuContainer.forEachMenu(FluidInfuserMenu::updateProgress)
                } else timePassed = 0
            }
        }
    }
    
    @TileEntityMenuClass
    inner class FluidInfuserMenu : GlobalTileEntityMenu() {
        
        private val progressItem = InfuserProgressItem()
        private val changeModeItem = CycleItem.withStateChangeHandler(
            ::changeMode,
            mode.ordinal,
            GuiMaterials.FLUID_LEFT_RIGHT_BTN.clientsideProvider,
            GuiMaterials.FLUID_RIGHT_LEFT_BTN.clientsideProvider
        )
        
        private val sideConfigGui = SideConfigMenu(
            this@FluidInfuser,
            listOf(
                itemHolder.getNetworkedInventory(input) to "inventory.nova.input",
                itemHolder.getNetworkedInventory(output) to "inventory.nova.output"
            ),
            listOf(tank to "container.nova.fluid_tank"),
            ::openWindow
        )
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| f # m u s # e |",
                "| f p i > o # e |",
                "| f # # # # # e |",
                "3 - - - - - - - 4")
            .addIngredient('i', input)
            .addIngredient('o', output)
            .addIngredient('p', progressItem)
            .addIngredient('m', changeModeItem)
            .addIngredient('>', SimpleItem(GuiMaterials.ARROW_PROGRESS.clientsideProvider))
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('f', FluidBar(3, fluidHolder, tank))
            .addIngredient('e', EnergyBar(3, energyHolder))
            .build()
        
        fun updateProgress() {
            progressItem.percentage = if (recipe != null) timePassed.toDouble() / recipeTime.toDouble() else 0.0
        }
        
        private fun changeMode(player: Player?, modeOrdinal: Int) {
            mode = FluidInfuserRecipe.InfuserMode.values()[modeOrdinal]
            reset()
            player!!.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
        }
        
        private inner class InfuserProgressItem : AbstractItem() {
            
            var percentage: Double = 0.0
                set(value) {
                    field = value.coerceIn(0.0, 1.0)
                    notifyWindows()
                }
            
            override fun getItemProvider(): ItemProvider {
                val material = if (mode == FluidInfuserRecipe.InfuserMode.INSERT)
                    GuiMaterials.FLUID_PROGRESS_LEFT_RIGHT
                else GuiMaterials.FLUID_PROGRESS_RIGHT_LEFT
                
                return material.model.createItemBuilder((percentage * 16).roundToInt())
            }
            
            override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) = Unit
        }
        
    }
    
}
