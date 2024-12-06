package xyz.xenondevs.nova.addon.machines.tileentity.processing

import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.enumSetOf
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.item.AbstractItem
import xyz.xenondevs.invui.item.Click
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.invui.item.setItemProvider
import xyz.xenondevs.nova.addon.machines.recipe.FluidInfuserRecipe
import xyz.xenondevs.nova.addon.machines.recipe.FluidInfuserRecipe.InfuserMode
import xyz.xenondevs.nova.addon.machines.registry.Blocks.FLUID_INFUSER
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.GuiItems.FLUID_LEFT_RIGHT_BTN
import xyz.xenondevs.nova.addon.machines.registry.GuiItems.FLUID_RIGHT_LEFT_BTN
import xyz.xenondevs.nova.addon.machines.registry.RecipeTypes
import xyz.xenondevs.nova.addon.machines.util.efficiencyDividedValue
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedFluidContainer
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.FluidBar
import xyz.xenondevs.nova.ui.menu.addIngredient
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.playClickSound
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.*
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.world.item.recipe.RecipeManager
import kotlin.math.roundToInt

fun getFluidInfuserInsertRecipeFor(fluidType: FluidType, input: ItemStack): FluidInfuserRecipe? {
    return RecipeManager.novaRecipes[RecipeTypes.FLUID_INFUSER]?.values?.asSequence()
        ?.map { it as FluidInfuserRecipe }
        ?.firstOrNull { recipe ->
            recipe.mode == InfuserMode.INSERT
                && recipe.fluidType == fluidType
                && recipe.input.test(input)
        }
}

fun getFluidInfuserExtractRecipeFor(input: ItemStack): FluidInfuserRecipe? {
    return RecipeManager.novaRecipes[RecipeTypes.FLUID_INFUSER]?.values?.asSequence()
        ?.map { it as FluidInfuserRecipe }
        ?.firstOrNull { recipe ->
            recipe.mode == InfuserMode.EXTRACT
                && recipe.input.test(input)
        }
}

private val BLOCKED_SIDES = enumSetOf(BlockSide.FRONT)

private val ENERGY_PER_TICK = FLUID_INFUSER.config.entry<Long>("energy_per_tick")
private val ENERGY_CAPACITY = FLUID_INFUSER.config.entry<Long>("energy_capacity")
private val FLUID_CAPACITY = FLUID_INFUSER.config.entry<Long>("fluid_capacity")

class FluidInfuser(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.FLUID)
    private val input = storedInventory("input", 1, ::handleInputInventoryUpdate)
    private val output = storedInventory("output", 1, ::handleOutputInventoryUpdate)
    private val tank = storedFluidContainer("tank", setOf(FluidType.WATER, FluidType.LAVA), FLUID_CAPACITY, upgradeHolder)
    private val energyHolder = storedEnergyHolder(ENERGY_CAPACITY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val fluidHolder = storedFluidHolder(tank to BUFFER, blockedSides = BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(input to INSERT, output to EXTRACT, blockedSides = BLOCKED_SIDES)
    
    private val energyPerTick by efficiencyDividedValue(ENERGY_PER_TICK, upgradeHolder)
    
    private val _mode = storedValue("mode") { InfuserMode.INSERT }
    private var mode by _mode
    
    private var recipe: FluidInfuserRecipe? = null
    private val recipeTime: Int
        get() = (recipe!!.time.toDouble() / upgradeHolder.getValue(UpgradeTypes.SPEED)).roundToInt()
    private var timePassed = 0
    
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
                
                if (mode == InfuserMode.INSERT && !tank.isEmpty()) {
                    recipe = getFluidInfuserInsertRecipeFor(tank.type!!, item)
                } else if (mode == InfuserMode.EXTRACT) {
                    recipe = getFluidInfuserExtractRecipeFor(item)
                }
            }
            
            val recipe = recipe
            if (recipe != null) {
                if (((mode == InfuserMode.INSERT && tank.amount >= recipe.fluidAmount)
                        || (mode == InfuserMode.EXTRACT && tank.accepts(recipe.fluidType, recipe.fluidAmount)))
                    && output.canHold(recipe.result)) {
                    
                    energyHolder.energy -= energyPerTick
                    if (++timePassed >= recipeTime) {
                        input.addItemAmount(SELF_UPDATE_REASON, 0, -1)
                        output.addItem(SELF_UPDATE_REASON, recipe.result)
                        
                        if (mode == InfuserMode.INSERT) tank.takeFluid(recipe.fluidAmount)
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
        
        private val changeModeItem = Item.builder()
            .setItemProvider(_mode) { mode ->
                when (mode) {
                    InfuserMode.INSERT -> FLUID_LEFT_RIGHT_BTN
                    InfuserMode.EXTRACT -> FLUID_RIGHT_LEFT_BTN
                }.clientsideProvider
            }.addClickHandler { _, click -> 
                mode = InfuserMode.entries[(mode.ordinal + 1) % InfuserMode.entries.size]
                reset()
                click.player.playClickSound()
            }
        
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
        
        private inner class InfuserProgressItem : AbstractItem() {
            
            var percentage: Double = 0.0
                set(value) {
                    field = value.coerceIn(0.0, 1.0)
                    notifyWindows()
                }
            
            override fun getItemProvider(player: Player): ItemProvider {
                val material = if (mode == InfuserMode.INSERT)
                    GuiItems.FLUID_PROGRESS_LEFT_RIGHT
                else GuiItems.FLUID_PROGRESS_RIGHT_LEFT
                
                return material.createClientsideItemBuilder().addCustomModelData(percentage)
            }
            
            override fun handleClick(clickType: ClickType, player: Player, click: Click) = Unit
        }
        
    }
    
}
