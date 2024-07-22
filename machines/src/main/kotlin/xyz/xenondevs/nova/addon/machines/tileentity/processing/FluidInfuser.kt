package xyz.xenondevs.nova.addon.machines.tileentity.processing

import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.invui.item.impl.AbstractItem
import xyz.xenondevs.invui.item.impl.CycleItem
import xyz.xenondevs.nova.addon.machines.recipe.FluidInfuserRecipe
import xyz.xenondevs.nova.addon.machines.registry.Blocks.FLUID_INFUSER
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.RecipeTypes
import xyz.xenondevs.nova.addon.machines.util.efficiencyDividedValue
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedFluidContainer
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.data.recipe.RecipeManager
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.type.NetworkConnectionType.*
import xyz.xenondevs.nova.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.FluidBar
import xyz.xenondevs.nova.ui.menu.addIngredient
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
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

private val ENERGY_PER_TICK = FLUID_INFUSER.config.entry<Long>("energy_per_tick")
private val ENERGY_CAPACITY = FLUID_INFUSER.config.entry<Long>("energy_capacity")
private val FLUID_CAPACITY = FLUID_INFUSER.config.entry<Long>("fluid_capacity")

class FluidInfuser(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.FLUID)
    private val input = storedInventory("input", 1, ::handleInputInventoryUpdate)
    private val output = storedInventory("output", 1, ::handleOutputInventoryUpdate)
    private val tank = storedFluidContainer("tank", setOf(FluidType.WATER, FluidType.LAVA), FLUID_CAPACITY, upgradeHolder)
    private val energyHolder = storedEnergyHolder(ENERGY_CAPACITY, upgradeHolder, INSERT)
    private val fluidHolder = storedFluidHolder(tank to BUFFER)
    private val itemHolder = storedItemHolder(input to INSERT, output to EXTRACT)
    
    private val energyPerTick by efficiencyDividedValue(ENERGY_PER_TICK, upgradeHolder)
    
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
        if (energyHolder.energy >= energyPerTick) {
            if (recipe == null && !input.isEmpty) {
                val item = input.getItem(0)!!
                
                if (mode == FluidInfuserRecipe.InfuserMode.INSERT && !tank.isEmpty()) {
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
                    
                    energyHolder.energy -= energyPerTick
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
            GuiItems.FLUID_LEFT_RIGHT_BTN.model.clientsideProvider,
            GuiItems.FLUID_RIGHT_LEFT_BTN.model.clientsideProvider
        )
        
        private val sideConfigGui = SideConfigMenu(
            this@FluidInfuser,
            mapOf(
                itemHolder.getNetworkedInventory(input) to "inventory.nova.input",
                itemHolder.getNetworkedInventory(output) to "inventory.nova.output"
            ),
            mapOf(tank to "container.nova.fluid_tank"),
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
            .addIngredient('>', GuiItems.ARROW_PROGRESS)
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('f', FluidBar(3, fluidHolder, tank))
            .addIngredient('e', EnergyBar(3, energyHolder))
            .build()
        
        fun updateProgress() {
            progressItem.percentage = if (recipe != null) timePassed.toDouble() / recipeTime.toDouble() else 0.0
        }
        
        private fun changeMode(player: Player?, modeOrdinal: Int) {
            mode = FluidInfuserRecipe.InfuserMode.entries[modeOrdinal]
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
                    GuiItems.FLUID_PROGRESS_LEFT_RIGHT
                else GuiItems.FLUID_PROGRESS_RIGHT_LEFT
                
                return material.model.unnamedClientsideProviders[(percentage * 16).roundToInt()]
            }
            
            override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) = Unit
        }
        
    }
    
}
