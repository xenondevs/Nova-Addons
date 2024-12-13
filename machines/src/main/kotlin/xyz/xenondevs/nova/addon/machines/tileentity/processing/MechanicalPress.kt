package xyz.xenondevs.nova.addon.machines.tileentity.processing

import net.kyori.adventure.key.Key
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.enumSetOf
import xyz.xenondevs.commons.provider.mapNonNull
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.item.AbstractItem
import xyz.xenondevs.invui.item.Click
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.nova.addon.machines.gui.PressProgressItem
import xyz.xenondevs.nova.addon.machines.registry.Blocks.MECHANICAL_PRESS
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.RecipeTypes
import xyz.xenondevs.nova.addon.machines.util.efficiencyDividedValue
import xyz.xenondevs.nova.addon.machines.util.speedMultipliedValue
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.playClickSound
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.item.recipe.ConversionNovaRecipe
import xyz.xenondevs.nova.world.item.recipe.NovaRecipe
import xyz.xenondevs.nova.world.item.recipe.RecipeManager
import xyz.xenondevs.nova.world.item.recipe.RecipeType
import kotlin.math.max

private val BLOCKED_SIDES = enumSetOf(BlockSide.FRONT)

private val MAX_ENERGY = MECHANICAL_PRESS.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = MECHANICAL_PRESS.config.entry<Long>("energy_per_tick")
private val PRESS_SPEED = MECHANICAL_PRESS.config.entry<Int>("speed")

private enum class PressType(val recipeType: RecipeType<out ConversionNovaRecipe>) {
    PLATE(RecipeTypes.PLATE_PRESS),
    GEAR(RecipeTypes.GEAR_PRESS)
}

class MechanicalPress(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val inputInv = storedInventory("input", 1, ::handleInputUpdate)
    private val outputInv = storedInventory("output", 1, ::handleOutputUpdate)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(inputInv to INSERT, outputInv to EXTRACT, blockedSides = BLOCKED_SIDES)
    private val energyPerTick by efficiencyDividedValue(ENERGY_PER_TICK, upgradeHolder)
    private val pressSpeed by speedMultipliedValue(PRESS_SPEED, upgradeHolder)
    
    private var type by storedValue("pressType") { PressType.PLATE }
    private var timeLeft by storedValue("pressTime") { 0 }
    
    private var currentRecipe: ConversionNovaRecipe? by storedValue<Key>("currentRecipe").mapNonNull(
        { RecipeManager.getRecipe(type.recipeType, it) },
        NovaRecipe::id
    )
    
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
                
                menuContainer.forEachMenu(MechanicalPressMenu::updateProgress)
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
    
    @TileEntityMenuClass
    inner class MechanicalPressMenu : GlobalTileEntityMenu() {
        
        private val pressProgress = PressProgressItem()
        private val pressTypeItems = ArrayList<PressTypeItem>()
        
        private val sideConfigGui = SideConfigMenu(
            this@MechanicalPress,
            mapOf(
                itemHolder.getNetworkedInventory(inputInv) to "inventory.nova.input",
                itemHolder.getNetworkedInventory(outputInv) to "inventory.nova.output",
            ),
            ::openWindow
        )
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| p g # i # # e |",
                "| # # # , # # e |",
                "| s u # o # # e |",
                "3 - - - - - - - 4")
            .addIngredient('i', inputInv)
            .addIngredient('o', outputInv)
            .addIngredient(',', pressProgress)
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('p', PressTypeItem(PressType.PLATE).apply(pressTypeItems::add))
            .addIngredient('g', PressTypeItem(PressType.GEAR).apply(pressTypeItems::add))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('e', EnergyBar(3, energyHolder))
            .build()
        
        init {
            updateProgress()
        }
        
        fun updateProgress() {
            val recipeTime = currentRecipe?.time ?: 0
            pressProgress.percentage = if (timeLeft == 0) 0.0 else (recipeTime - timeLeft).toDouble() / recipeTime.toDouble()
        }
        
        private inner class PressTypeItem(private val type: PressType) : AbstractItem() {
            
            override fun getItemProvider(player: Player): ItemProvider {
                return if (type == PressType.PLATE) {
                    if (this@MechanicalPress.type == PressType.PLATE) GuiItems.PLATE_BTN_OFF.clientsideProvider
                    else GuiItems.PLATE_BTN_ON.clientsideProvider
                } else {
                    if (this@MechanicalPress.type == PressType.GEAR) GuiItems.GEAR_BTN_OFF.clientsideProvider
                    else GuiItems.GEAR_BTN_ON.clientsideProvider
                }
            }
            
            override fun handleClick(clickType: ClickType, player: Player, click: Click) {
                if (this@MechanicalPress.type != type) {
                    player.playClickSound()
                    this@MechanicalPress.type = type
                    pressTypeItems.forEach(Item::notifyWindows)
                }
            }
            
        }
        
    }
    
}