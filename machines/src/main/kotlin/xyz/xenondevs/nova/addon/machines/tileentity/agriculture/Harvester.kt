package xyz.xenondevs.nova.addon.machines.tileentity.agriculture

import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.block.Block
import org.bukkit.entity.Player
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.enumSetOf
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.VirtualInventory
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.registry.Blocks.HARVESTER
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.util.PlantUtils
import xyz.xenondevs.nova.addon.machines.util.blockSequence
import xyz.xenondevs.nova.addon.machines.util.efficiencyDividedValue
import xyz.xenondevs.nova.addon.machines.util.isLeaveLike
import xyz.xenondevs.nova.addon.machines.util.maxIdleTime
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedRegion
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.api.NovaEventFactory
import xyz.xenondevs.nova.config.GlobalValues
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.DefaultContextIntentions.BlockBreak
import xyz.xenondevs.nova.context.param.DefaultContextParamTypes
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.addIngredient
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.addAll
import xyz.xenondevs.nova.util.dropItemsNaturally
import xyz.xenondevs.nova.util.item.damage
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.item.tool.ToolCategory
import xyz.xenondevs.nova.world.item.tool.VanillaToolCategories
import xyz.xenondevs.nova.world.pos
import xyz.xenondevs.nova.world.region.Region
import xyz.xenondevs.nova.world.region.VisualRegion
import java.util.*

private val BLOCKED_SIDES = enumSetOf(BlockSide.FRONT)

private val MAX_ENERGY = HARVESTER.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = HARVESTER.config.entry<Long>("energy_per_tick")
private val ENERGY_PER_BREAK = HARVESTER.config.entry<Long>("energy_per_break")
private val IDLE_TIME = HARVESTER.config.entry<Int>("idle_time")
private val MIN_RANGE = HARVESTER.config.entry<Int>("range", "min")
private val MAX_RANGE = HARVESTER.config.entry<Int>("range", "max")
private val DEFAULT_RANGE by HARVESTER.config.entry<Int>("range", "default")

class Harvester(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
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
    
    private val energyPerTick by efficiencyDividedValue(ENERGY_PER_TICK, upgradeHolder)
    private val energyPerBreak by efficiencyDividedValue(ENERGY_PER_BREAK, upgradeHolder)
    private val maxIdleTime by maxIdleTime(IDLE_TIME, upgradeHolder)
    private val region = storedRegion("region.default", MIN_RANGE, MAX_RANGE, DEFAULT_RANGE, upgradeHolder) {
        val size = 1 + it * 2
        Region.inFrontOf(this, size, size, size * 2, 0)
    }
    
    private val queuedBlocks = LinkedList<Pair<Block, Material>>()
    private var timePassed = 0
    private var loadCooldown = 0
    
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
                .map { it to it.type }
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
                if (block.type == expectedType) {
                    
                    val toolInventory: VirtualInventory? = when {
                        Tag.LEAVES.isTagged(expectedType) -> if (shearInventory.isEmpty) hoeInventory else shearInventory
                        Tag.MINEABLE_AXE.isTagged(expectedType) -> axeInventory
                        Tag.MINEABLE_HOE.isTagged(expectedType) -> hoeInventory
                        else -> null
                    }
                    
                    val tool = toolInventory?.getItem(0)
                    
                    // get drops
                    val ctx = Context.intention(BlockBreak)
                        .param(DefaultContextParamTypes.BLOCK_POS, block.pos)
                        .param(DefaultContextParamTypes.TOOL_ITEM_STACK, tool)
                        .param(DefaultContextParamTypes.SOURCE_TILE_ENTITY, this)
                        .build()
                    val drops = PlantUtils.getHarvestDrops(ctx)!!.toMutableList()
                    
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
                        
                        toolInventory.setItem(SELF_UPDATE_REASON, 0, tool.damage(1, pos.world))
                    }
                    
                    // harvest the plant
                    PlantUtils.harvest(ctx)
                    NovaEventFactory.callTileEntityBlockBreakEvent(this, block, drops)
                    
                    // add the drops to the inventory or drop them in the world if they don't fit
                    if (inventory.canHold(drops)) {
                        inventory.addAll(SELF_UPDATE_REASON, drops)
                    } else if (GlobalValues.DROP_EXCESS_ON_GROUND) {
                        pos.world.dropItemsNaturally(block.location, drops)
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
        event.isCancelled = event.newItem != null && event.newItem?.type != Material.SHEARS
    }
    
    private fun handleAxeInventoryUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = event.newItem != null && VanillaToolCategories.AXE !in ToolCategory.ofItem(event.newItem)
    }
    
    private fun handleHoeInventoryUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = event.newItem != null && VanillaToolCategories.HOE !in ToolCategory.ofItem(event.newItem)
    }
    
    override fun handleDisable() {
        super.handleDisable()
        VisualRegion.removeRegion(uuid)
    }
    
    @TileEntityMenuClass
    inner class HarvesterMenu(player: Player) : IndividualTileEntityMenu(player) {
        
        private val sideConfigGui = SideConfigMenu(
            this@Harvester,
            mapOf(
                itemHolder.getNetworkedInventory(inventory) to "inventory.nova.output",
                itemHolder.getNetworkedInventory(shearInventory) to "inventory.machines.shears",
                itemHolder.getNetworkedInventory(axeInventory) to "inventory.machines.axes",
                itemHolder.getNetworkedInventory(hoeInventory) to "inventory.machines.hoes",
            ),
            ::openWindow
        )
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| c v u s a h e |",
                "| m n p # # # e |",
                "| i i i i i i e |",
                "| i i i i i i e |",
                "3 - - - - - - - 4")
            .addIngredient('i', inventory)
            .addIngredient('c', OpenSideConfigItem(sideConfigGui))
            .addIngredient('s', shearInventory, GuiItems.SHEARS_PLACEHOLDER)
            .addIngredient('a', axeInventory, GuiItems.AXE_PLACEHOLDER)
            .addIngredient('h', hoeInventory, GuiItems.HOE_PLACEHOLDER)
            .addIngredient('v', region.createVisualizeRegionItem(player))
            .addIngredient('p', region.increaseSizeItem)
            .addIngredient('m', region.decreaseSizeItem)
            .addIngredient('n', region.displaySizeItem)
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('e', EnergyBar(4, energyHolder))
            .build()
        
    }
    
}

private object HarvestPriorityComparator : Comparator<Block> {
    
    @Suppress("LiftReturnOrAssignment")
    override fun compare(o1: Block, o2: Block): Int {
        val type1 = o1.type
        val type2 = o2.type
        
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
        
        if (Tag.LOGS.isTagged(type1)) {
            if (Tag.LOGS.isTagged(type2)) {
                return compareYPos()
            } else {
                return -1
            }
        } else if (Tag.LOGS.isTagged(type2)) {
            return 1
        }
        
        return compareYPos()
    }
    
}