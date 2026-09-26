package xyz.xenondevs.nova.addon.machines.tileentity.agriculture

import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.BoneMealItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import org.bukkit.inventory.ItemType
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.registry.Blocks.FERTILIZER
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.util.energyConsumption
import xyz.xenondevs.nova.addon.machines.util.iterator
import xyz.xenondevs.nova.addon.machines.util.maxIdleTime
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedRegion
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.nova.util.BlockSideSet
import xyz.xenondevs.nova.util.EntityUtils
import xyz.xenondevs.nova.util.nmsPos
import xyz.xenondevs.nova.util.serverLevel
import org.bukkit.block.Block
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.item.itemType
import xyz.xenondevs.nova.world.region.Region
import xyz.xenondevs.nova.world.region.VisualRegion

private val BLOCKED_SIDES = BlockSideSet(front = true)

private val MAX_ENERGY = FERTILIZER.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = FERTILIZER.config.entry<Long>("energy_per_tick")
private val ENERGY_PER_FERTILIZE = FERTILIZER.config.entry<Long>("energy_per_fertilize")
private val IDLE_TIME = FERTILIZER.config.entry<Int>("idle_time")
private val MIN_RANGE = FERTILIZER.config.entry<Int>("range", "min")
private val MAX_RANGE = FERTILIZER.config.entry<Int>("range", "max")
private val DEFAULT_RANGE by FERTILIZER.config.entry<Int>("range", "default")

class Fertilizer(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val fertilizerInventory = storedInventory("fertilizer", 12, this::handleInventoryUpdate)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.RANGE)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(fertilizerInventory to INSERT, blockedSides = BLOCKED_SIDES)
    
    private val fakePlayer = EntityUtils.createFakePlayer(block.location)
    private val region = storedRegion("region.default", MIN_RANGE, MAX_RANGE, DEFAULT_RANGE, upgradeHolder) {
        val size = 1 + it * 2
        Region.inFrontOf(this, size, size, 1, 0)
    }
    
    private val energyPerTick by energyConsumption(ENERGY_PER_TICK, upgradeHolder)
    private val energyPerFertilize by energyConsumption(ENERGY_PER_FERTILIZE, upgradeHolder)
    private val maxIdleTime by maxIdleTime(IDLE_TIME, upgradeHolder)
    private var timePassed = 0
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.FERTILIZER) {
        upperGui by gui(
            "s p . i i i i . e",
            "v n . i i i i . e",
            "u m . i i i i . e",
        ) {
            'i' by fertilizerInventory
            's' by openSideConfigItem(mapOf(itemHolder.getNetworkedInventory(fertilizerInventory) to "inventory.machines.fertilizer"))
            'u' by openUpgradesItem(upgradeHolder)
            'e' by energyBar(energyHolder)
            'v' by region.visualizeRegionItem
            'p' by region.increaseSizeItem
            'm' by region.decreaseSizeItem
            'n' by region.displaySizeItem
        }
    }
    
    override fun handleTick() {
        if (energyHolder.energy >= energyPerTick) {
            energyHolder.energy -= energyPerTick
            if (energyHolder.energy >= energyPerFertilize) {
                if (timePassed++ >= maxIdleTime) {
                    timePassed = 0
                    if (!fertilizerInventory.isEmpty)
                        fertilizeNextPlant()
                }
            }
        }
    }
    
    private fun fertilizeNextPlant() {
        for ((index, item) in fertilizerInventory.items.withIndex()) {
            if (item == null)
                continue
            
            for (block in region) {
                val consumed = BoneMealItem.applyBonemeal(
                    UseOnContext(
                        block.world.serverLevel,
                        fakePlayer,
                        InteractionHand.MAIN_HAND,
                        ItemStack(Items.BONE_MEAL),
                        BlockHitResult(Vec3.ZERO, Direction.DOWN, block.nmsPos, false)
                    )
                ).consumesAction()
                if (consumed) {
                    energyHolder.energy -= energyPerFertilize
                    fertilizerInventory.addItemAmount(SELF_UPDATE_REASON, index, -1)
                    return
                }
            }
        }
    }
    
    private fun handleInventoryUpdate(event: ItemPreUpdateEvent) {
        if ((event.isAdd || event.isSwap) && event.newItem?.itemType != ItemType.BONE_MEAL)
            event.isCancelled = true
    }
    
    override fun handleDisable() {
        super.handleDisable()
        VisualRegion.removeRegion(uuid)
    }
    
}
