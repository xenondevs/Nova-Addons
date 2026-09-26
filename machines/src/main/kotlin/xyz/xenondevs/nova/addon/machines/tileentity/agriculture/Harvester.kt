package xyz.xenondevs.nova.addon.machines.tileentity.agriculture

import org.bukkit.block.Block
import org.bukkit.block.BlockType
import org.bukkit.inventory.ItemType
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.inventory.VirtualInventory
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.registry.Blocks.HARVESTER
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.util.PlantUtils
import xyz.xenondevs.nova.addon.machines.util.blockSequence
import xyz.xenondevs.nova.addon.machines.util.energyConsumption
import xyz.xenondevs.nova.addon.machines.util.isLeaveLike
import xyz.xenondevs.nova.addon.machines.util.maxIdleTime
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedRegion
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.api.NovaEventFactory
import xyz.xenondevs.nova.config.GlobalValues
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.BlockBreak
import xyz.xenondevs.nova.registry.tags.BlockTypeTags
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.invui.dsl.with
import xyz.xenondevs.nova.util.BlockSideSet
import xyz.xenondevs.nova.util.addAll
import xyz.xenondevs.nova.util.dropItemsNaturally
import xyz.xenondevs.nova.util.item.damage
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.blockType
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.registry.tags.ItemTypeTags
import xyz.xenondevs.nova.world.item.itemType
import xyz.xenondevs.nova.world.region.Region
import xyz.xenondevs.nova.world.region.VisualRegion
import java.util.*

private val BLOCKED_SIDES = BlockSideSet(front = true)

private val MAX_ENERGY = HARVESTER.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = HARVESTER.config.entry<Long>("energy_per_tick")
private val ENERGY_PER_BREAK = HARVESTER.config.entry<Long>("energy_per_break")
private val IDLE_TIME = HARVESTER.config.entry<Int>("idle_time")
private val MIN_RANGE = HARVESTER.config.entry<Int>("range", "min")
private val MAX_RANGE = HARVESTER.config.entry<Int>("range", "max")
private val DEFAULT_RANGE by HARVESTER.config.entry<Int>("range", "default")

class Harvester(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val inventory = storedInventory("harvest", 12, ::handleInventoryUpdate)
    private val shearInventory = storedInventory("shears", 1, ::handleShearInventoryUpdate)
    private val axeInventory = storedInventory("axe", 1, ::handleAxeInventoryUpdate)
    private val hoeInventory = storedInventory("hoe", 1, ::handleHoeInventoryUpdate)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.RANGE)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(
        inventory to EXTRACT, shearInventory to INSERT, axeInventory to INSERT, hoeInventory to INSERT,
        blockedSides = BLOCKED_SIDES
    )
    
    private val energyPerTick by energyConsumption(ENERGY_PER_TICK, upgradeHolder)
    private val energyPerBreak by energyConsumption(ENERGY_PER_BREAK, upgradeHolder)
    private val maxIdleTime by maxIdleTime(IDLE_TIME, upgradeHolder)
    private val region = storedRegion("region.default", MIN_RANGE, MAX_RANGE, DEFAULT_RANGE, upgradeHolder) {
        val size = 1 + it * 2
        Region.inFrontOf(this, size, size, size * 2, 0)
    }
    
    private val queuedBlocks = LinkedList<Pair<Block, BlockType>>()
    private var timePassed = 0
    private var loadCooldown = 0
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.HARVESTER) {
        upperGui by gui(
            "c u v . i i i i e",
            "m n p . i i i i e",
            "s a h . i i i i e",
        ) {
            'i' by inventory
            'c' by openSideConfigItem(
                mapOf(
                    itemHolder.getNetworkedInventory(inventory) to "inventory.nova.output",
                    itemHolder.getNetworkedInventory(shearInventory) to "inventory.machines.shears",
                    itemHolder.getNetworkedInventory(axeInventory) to "inventory.machines.axes",
                    itemHolder.getNetworkedInventory(hoeInventory) to "inventory.machines.hoes",
                )
            )
            's' by (shearInventory with GuiItems.SHEARS_PLACEHOLDER)
            'a' by (axeInventory with GuiItems.AXE_PLACEHOLDER)
            'h' by (hoeInventory with GuiItems.HOE_PLACEHOLDER)
            'v' by region.visualizeRegionItem
            'p' by region.increaseSizeItem
            'm' by region.decreaseSizeItem
            'n' by region.displaySizeItem
            'u' by openUpgradesItem(upgradeHolder)
            'e' by energyBar(energyHolder)
        }
    }
    
    override fun handleTick() {
        if (energyHolder.energy >= energyPerTick) {
            energyHolder.energy -= energyPerTick
            
            if (energyHolder.energy >= energyPerBreak) {
                loadCooldown--
                
                if (timePassed++ >= maxIdleTime) {
                    timePassed = 0
                    
                    if (!GlobalValues.DROP_EXCESS_ON_GROUND && inventory.isFull)
                        return
                    
                    if (queuedBlocks.isEmpty())
                        loadBlocks()
                    
                    harvestNextBlock()
                }
            }
        }
    }
    
    private fun loadBlocks() {
        // TODO: query protection async
        if (loadCooldown <= 0) {
            loadCooldown = 100
            
            queuedBlocks += region
                .blockSequence
                .filter(PlantUtils::isHarvestable)
                .sortedWith(HarvestPriorityComparator)
                .map { it to it.blockType }
        }
    }
    
    private fun harvestNextBlock() {
        do {
            var tryAgain = false
            
            if (queuedBlocks.isNotEmpty()) {
                // get next block
                val (block, expectedType) = queuedBlocks.first()
                queuedBlocks.removeFirst()
                
                // check that the type hasn't changed
                if (block.blockType == expectedType) {
                    
                    val toolInventory: VirtualInventory? = when {
                        expectedType in BlockTypeTags.LEAVES -> if (shearInventory.isEmpty) hoeInventory else shearInventory
                        expectedType in BlockTypeTags.MINEABLE_AXE -> axeInventory
                        expectedType in BlockTypeTags.MINEABLE_HOE -> hoeInventory
                        else -> null
                    }
                    
                    val tool = toolInventory?.getItem(0)
                    
                    // get drops
                    val ctx = Context.intention(BlockBreak)
                        .param(BlockBreak.BLOCK, block)
                        .param(BlockBreak.TOOL_ITEM_STACK, tool)
                        .param(BlockBreak.SOURCE_TILE_ENTITY, this)
                        .build()
                    val drops = PlantUtils.getHarvestDrops(ctx).toMutableList()
                    
                    // check that the drops will fit in the inventory or can be dropped on the ground
                    if (!GlobalValues.DROP_EXCESS_ON_GROUND && !inventory.canHold(drops)) {
                        tryAgain = true
                        continue
                    }
                    
                    // check for tool and damage if present
                    if (toolInventory != null) {
                        if (tool == null) {
                            tryAgain = true
                            continue
                        }
                        
                        toolInventory.setItem(SELF_UPDATE_REASON, 0, tool.damage(1, block.world))
                    }
                    
                    // harvest the plant
                    PlantUtils.harvest(ctx)
                    NovaEventFactory.callTileEntityBlockBreakEvent(this, block, drops)
                    
                    // add the drops to the inventory or drop them in the world if they don't fit
                    if (inventory.canHold(drops)) {
                        inventory.addAll(SELF_UPDATE_REASON, drops)
                    } else if (GlobalValues.DROP_EXCESS_ON_GROUND) {
                        block.world.dropItemsNaturally(block.location, drops)
                    }
                    
                    // take energy
                    energyHolder.energy -= energyPerBreak
                } else tryAgain = true
            }
            
        } while (tryAgain)
    }
    
    private fun handleInventoryUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = event.updateReason != SELF_UPDATE_REASON && event.isAdd
    }
    
    private fun handleShearInventoryUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = event.newItem != null && event.newItem?.itemType != ItemType.SHEARS
    }
    
    private fun handleAxeInventoryUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = event.newItem != null && event.newItem!!.itemType !in ItemTypeTags.AXES
    }
    
    private fun handleHoeInventoryUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = event.newItem != null && event.newItem!!.itemType !in ItemTypeTags.HOES
    }
    
    override fun handleDisable() {
        super.handleDisable()
        VisualRegion.removeRegion(uuid)
    }
    
}

private object HarvestPriorityComparator : Comparator<Block> {
    
    override fun compare(o1: Block, o2: Block): Int {
        val type1 = o1.blockType
        val type2 = o2.blockType
        
        fun compareYPos(): Int =
            o2.location.y.compareTo(o1.location.y)
        
        if (type1 == type2)
            return compareYPos()
        
        if (PlantUtils.isTreeAttachment(type1)) {
            if (PlantUtils.isTreeAttachment(type2)) {
                return compareYPos()
            } else {
                return -1
            }
        } else if (PlantUtils.isTreeAttachment(type2)) {
            return 1
        }
        
        if (type1.isLeaveLike()) {
            if (type2.isLeaveLike()) {
                return compareYPos()
            } else {
                return -1
            }
        } else if (type2.isLeaveLike()) {
            return 1
        }
        
        if (type1 in BlockTypeTags.LOGS) {
            if (type2 in BlockTypeTags.LOGS) {
                return compareYPos()
            } else {
                return -1
            }
        } else if (type2 in BlockTypeTags.LOGS) {
            return 1
        }
        
        return compareYPos()
    }
    
}
