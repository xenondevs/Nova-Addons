package xyz.xenondevs.nova.addon.machines.tileentity.processing

import org.bukkit.block.BlockType
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.dsl.item
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.gui.leftRightFluidProgressItem
import xyz.xenondevs.nova.addon.machines.registry.Blocks.FREEZER
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.util.energyConsumption
import xyz.xenondevs.nova.addon.machines.util.speedMultipliedValue
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedFluidContainer
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.registry.RegistryEntry
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.ui.menu.fluidBar
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.nova.util.BlockSideSet
import xyz.xenondevs.nova.util.playClickSound
import org.bukkit.block.Block
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.blockType
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.world.item.guiItemProvider
import java.lang.Long.min
import kotlin.math.roundToInt

private val BLOCKED_SIDES = BlockSideSet(front = true)

private val WATER_CAPACITY = FREEZER.config.entry<Long>("water_capacity")
private val ENERGY_CAPACITY = FREEZER.config.entry<Long>("energy_capacity")
private val ENERGY_PER_TICK = FREEZER.config.entry<Long>("energy_per_tick")
private val MB_PER_TICK = FREEZER.config.entry<Long>("mb_per_tick")

class Freezer(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.FLUID)
    private val inventory = storedInventory("inventory", 9, ::handleInventoryUpdate)
    private val waterTank = storedFluidContainer("water", setOf(FluidType.WATER), WATER_CAPACITY, upgradeHolder)
    private val energyHolder = storedEnergyHolder(ENERGY_CAPACITY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(inventory to EXTRACT, blockedSides = BLOCKED_SIDES)
    private val fluidHolder = storedFluidHolder(waterTank to INSERT, blockedSides = BLOCKED_SIDES)
    
    private val energyPerTick by energyConsumption(ENERGY_PER_TICK, upgradeHolder)
    private val mbPerTick by speedMultipliedValue(MB_PER_TICK, upgradeHolder)
    private var mbUsed = 0L
    private val modeProvider = storedValue("mode") { Mode.ICE }
    private var mode by modeProvider
    private val progress = mutableProvider(0.0)
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.FREEZER) {
        updateProgress()
        
        upperGui by gui(
            "s w . i i i . . e",
            "u w > i i i . . e",
            "m w . i i i . . e",
        ) {
            'i' by inventory
            '>' by leftRightFluidProgressItem(progress)
            's' by openSideConfigItem(
                mapOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.output"),
                mapOf(waterTank to "container.nova.water_tank")
            )
            'u' by openUpgradesItem(upgradeHolder)
            'm' by item {
                itemProvider by modeProvider.flatMap { it.uiItem.guiItemProvider }
                onClick {
                    if (clickType == ClickType.LEFT || clickType == ClickType.RIGHT) {
                        val direction = if (clickType == ClickType.LEFT) 1 else -1
                        mode = Mode.entries[(mode.ordinal + direction).mod(Mode.entries.size)]
                        player.playClickSound()
                    }
                }
            }
            'w' by fluidBar(fluidHolder, waterTank)
            'e' by energyBar(energyHolder)
        }
    }
    
    private fun handleInventoryUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = !event.isRemove && event.updateReason != SELF_UPDATE_REASON
    }
    
    override fun handleTick() {
        val mbMaxPerOperation = 1000 * mode.maxCostMultiplier
        
        if (mbUsed > mbMaxPerOperation && inventory.canHold(mode.product)) {
            val compensationCount = (mbUsed / mbMaxPerOperation.toDouble()).roundToInt()
            val compensationItems = ItemType.ICE.createItemStack(compensationCount)
            if (inventory.canHold(compensationItems)) {
                inventory.addItem(SELF_UPDATE_REASON, compensationItems) // Add ice from overflowing water to the inventory
                mbUsed -= compensationCount * mbMaxPerOperation // Take used up mb for the compensatory product
            }
        }
        val mbToTake = min(mbPerTick, mbMaxPerOperation - mbUsed)
        if (waterTank.amount >= mbToTake && energyHolder.energy >= energyPerTick && inventory.canHold(mode.product)) {
            val snowSpawnBlock = block.getRelative(0, 1, 0)
            if (snowSpawnBlock.blockType.isAir)
                snowSpawnBlock.blockType = BlockType.SNOW
            
            energyHolder.energy -= energyPerTick
            mbUsed += mbToTake
            waterTank.takeFluid(mbToTake)
            if (mbUsed >= mbMaxPerOperation) {
                mbUsed = 0
                inventory.addItem(SELF_UPDATE_REASON, mode.product)
            }
            
            updateProgress()
        }
    }
    
    private fun updateProgress() {
        progress.set(mbUsed / (1000 * mode.maxCostMultiplier).toDouble())
    }
    
    enum class Mode(val product: ItemStack, val uiItem: RegistryEntry.Paper<ItemType>, val maxCostMultiplier: Int) {
        ICE(ItemType.ICE.createItemStack(), GuiItems.ICE_MODE_BTN, 1),
        PACKED_ICE(ItemType.PACKED_ICE.createItemStack(), GuiItems.PACKED_ICE_MODE_BTN, 9),
        BLUE_ICE(ItemType.BLUE_ICE.createItemStack(), GuiItems.BLUE_ICE_MODE_BTN, 81)
    }
    
}
