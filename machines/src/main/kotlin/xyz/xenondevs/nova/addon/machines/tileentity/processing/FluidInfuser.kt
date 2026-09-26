package xyz.xenondevs.nova.addon.machines.tileentity.processing

import org.bukkit.inventory.ItemStack
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.dsl.item
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.recipe.FluidInfuserRecipe
import xyz.xenondevs.nova.addon.machines.recipe.FluidInfuserRecipe.InfuserMode
import xyz.xenondevs.nova.addon.machines.registry.Blocks.FLUID_INFUSER
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.RecipeTypes
import xyz.xenondevs.nova.addon.machines.util.energyConsumption
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedFluidContainer
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.ui.menu.fluidBar
import xyz.xenondevs.nova.ui.menu.item.progressItem
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.nova.util.BlockSideSet
import xyz.xenondevs.nova.util.playClickSound
import org.bukkit.block.Block
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
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

private val BLOCKED_SIDES = BlockSideSet(front = true)

private val ENERGY_PER_TICK = FLUID_INFUSER.config.entry<Long>("energy_per_tick")
private val ENERGY_CAPACITY = FLUID_INFUSER.config.entry<Long>("energy_capacity")
private val FLUID_CAPACITY = FLUID_INFUSER.config.entry<Long>("fluid_capacity")

class FluidInfuser(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.FLUID)
    private val input = storedInventory("input", 1, ::handleInputInventoryUpdate)
    private val output = storedInventory("output", 1, ::handleOutputInventoryUpdate)
    private val tank = storedFluidContainer("tank", setOf(FluidType.WATER, FluidType.LAVA), FLUID_CAPACITY, upgradeHolder)
    private val energyHolder = storedEnergyHolder(ENERGY_CAPACITY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val fluidHolder = storedFluidHolder(tank to BUFFER, blockedSides = BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(input to INSERT, output to EXTRACT, blockedSides = BLOCKED_SIDES)
    
    private val energyPerTick by energyConsumption(ENERGY_PER_TICK, upgradeHolder)
    
    private val _mode = storedValue("mode") { InfuserMode.INSERT }
    private var mode by _mode
    
    private var recipe: FluidInfuserRecipe? = null
    private val recipeTime: Int
        get() = (recipe!!.time.toDouble() / upgradeHolder.getValue(UpgradeTypes.SPEED)).roundToInt()
    private var timePassed = 0
    private val progress = mutableProvider(0.0)
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.FLUID_INFUSER) {
        upperGui by gui(
            "s f . . . . . . e",
            "u f p i > o . . e",
            "m f . . . . . . e",
        ) {
            'i' by input
            'o' by output
            'p' by progressItem(_mode.flatMap(InfuserMode::progressItemProvider), progress)
            'm' by item {
                itemProvider by _mode.flatMap(InfuserMode::btnItemProvider)
                onClick {
                    mode = InfuserMode.entries[(mode.ordinal + 1) % InfuserMode.entries.size]
                    reset()
                    player.playClickSound()
                }
            }
            '>' by GuiItems.ARROW_PROGRESS
            's' by openSideConfigItem(
                mapOf(
                    itemHolder.getNetworkedInventory(input) to "inventory.nova.input",
                    itemHolder.getNetworkedInventory(output) to "inventory.nova.output"
                ),
                mapOf(tank to "container.nova.fluid_tank")
            )
            'u' by openUpgradesItem(upgradeHolder)
            'f' by fluidBar(fluidHolder, tank)
            'e' by energyBar(energyHolder)
        }
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
        updateProgress()
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
                    } else updateProgress()
                } else timePassed = 0
            }
        }
    }
    
    private fun updateProgress() {
        progress.set(if (recipe != null) timePassed.toDouble() / recipeTime.toDouble() else 0.0)
    }
    
}
