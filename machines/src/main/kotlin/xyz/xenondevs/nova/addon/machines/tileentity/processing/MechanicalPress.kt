package xyz.xenondevs.nova.addon.machines.tileentity.processing

import net.kyori.adventure.key.Key
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.mapNonNull
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.dsl.item
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.gui.pressProgressItem
import xyz.xenondevs.nova.addon.machines.registry.Blocks.MECHANICAL_PRESS
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.RecipeTypes
import xyz.xenondevs.nova.addon.machines.util.energyConsumption
import xyz.xenondevs.nova.addon.machines.util.speedMultipliedValue
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.nova.util.BlockSideSet
import xyz.xenondevs.nova.util.playClickSound
import org.bukkit.block.Block
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.item.guiItemProvider
import xyz.xenondevs.nova.world.item.recipe.ConversionNovaRecipe
import xyz.xenondevs.nova.world.item.recipe.NovaRecipe
import xyz.xenondevs.nova.world.item.recipe.RecipeManager
import xyz.xenondevs.nova.world.item.recipe.RecipeType
import kotlin.math.max

private val BLOCKED_SIDES = BlockSideSet(front = true)

private val MAX_ENERGY = MECHANICAL_PRESS.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = MECHANICAL_PRESS.config.entry<Long>("energy_per_tick")
private val PRESS_SPEED = MECHANICAL_PRESS.config.entry<Int>("speed")

private enum class PressType(val recipeType: RecipeType<out ConversionNovaRecipe>) {
    PLATE(RecipeTypes.PLATE_PRESS),
    GEAR(RecipeTypes.GEAR_PRESS)
}

class MechanicalPress(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val inputInv = storedInventory("input", 1, ::handleInputUpdate)
    private val outputInv = storedInventory("output", 1, ::handleOutputUpdate)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(inputInv to INSERT, outputInv to EXTRACT, blockedSides = BLOCKED_SIDES)
    private val energyPerTick by energyConsumption(ENERGY_PER_TICK, upgradeHolder)
    private val pressSpeed by speedMultipliedValue(PRESS_SPEED, upgradeHolder)
    
    private val typeProvider = storedValue("pressType") { PressType.PLATE }
    private var type by typeProvider
    private var timeLeft by storedValue("pressTime") { 0 }
    private val progress = mutableProvider(0.0)
    
    private var currentRecipe: ConversionNovaRecipe? by storedValue<Key>("currentRecipe").mapNonNull(
        { RecipeManager.getRecipe(type.recipeType, it) },
        NovaRecipe::id
    )
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.MECHANICAL_PRESS) {
        updateProgress()
        
        upperGui by gui(
            "p g . . i . . . e",
            ". . . . , . . . e",
            "s u . . o . . . e",
        ) {
            'i' by inputInv
            'o' by outputInv
            ',' by pressProgressItem(progress)
            's' by openSideConfigItem(
                mapOf(
                    itemHolder.getNetworkedInventory(inputInv) to "inventory.nova.input",
                    itemHolder.getNetworkedInventory(outputInv) to "inventory.nova.output",
                )
            )
            'p' by item {
                itemProvider by typeProvider.flatMap {
                    if (it == PressType.PLATE) GuiItems.PLATE_BTN_OFF.guiItemProvider
                    else GuiItems.PLATE_BTN_ON.guiItemProvider
                }
                onClick {
                    if (type != PressType.PLATE) {
                        player.playClickSound()
                        type = PressType.PLATE
                    }
                }
            }
            'g' by item {
                itemProvider by typeProvider.flatMap {
                    if (it == PressType.GEAR) GuiItems.GEAR_BTN_OFF.guiItemProvider
                    else GuiItems.GEAR_BTN_ON.guiItemProvider
                }
                onClick {
                    if (type != PressType.GEAR) {
                        player.playClickSound()
                        type = PressType.GEAR
                    }
                }
            }
            'u' by openUpgradesItem(upgradeHolder)
            'e' by energyBar(energyHolder)
        }
    }
    
    override fun handleTick() {
        if (energyHolder.energy >= energyPerTick) {
            if (timeLeft == 0)
                takeItem()
            if (timeLeft != 0) { // is pressing
                timeLeft = max(timeLeft - pressSpeed, 0)
                energyHolder.energy -= energyPerTick
                
                if (timeLeft == 0) {
                    currentRecipe?.let { outputInv.putItem(SELF_UPDATE_REASON, 0, it.result) }
                    currentRecipe = null
                }
                
                updateProgress()
            }
        }
    }
    
    private fun takeItem() {
        val inputItem = inputInv.getItem(0)
        if (inputItem != null) {
            val recipe = RecipeManager.getConversionRecipeFor(type.recipeType, inputItem)
            if (recipe != null && outputInv.canHold(recipe.result)) {
                inputInv.addItemAmount(SELF_UPDATE_REASON, 0, -1)
                timeLeft = recipe.time
                currentRecipe = recipe
            }
        }
    }
    
    private fun handleInputUpdate(event: ItemPreUpdateEvent) {
        if (event.updateReason != SELF_UPDATE_REASON
            && event.newItem != null
            && RecipeManager.getConversionRecipeFor(type.recipeType, event.newItem!!) == null) {
            
            event.isCancelled = true
        }
    }
    
    private fun handleOutputUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = !event.isRemove && event.updateReason != SELF_UPDATE_REASON
    }
    
    private fun updateProgress() {
        val recipeTime = currentRecipe?.time ?: 0
        progress.set(if (timeLeft == 0) 0.0 else (recipeTime - timeLeft).toDouble() / recipeTime.toDouble())
    }
    
}
