package xyz.xenondevs.nova.addon.machines.tileentity.world

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bukkit.block.Block
import org.bukkit.block.BlockType
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.registry.Blocks.BLOCK_BREAKER
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.util.energyConsumption
import xyz.xenondevs.nova.addon.machines.util.speedMultipliedValue
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.api.NovaEventFactory
import xyz.xenondevs.nova.config.GlobalValues
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.BlockBreak
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.nova.util.BlockSideSet
import xyz.xenondevs.nova.util.BlockUtils
import xyz.xenondevs.nova.util.item.ToolUtils
import xyz.xenondevs.nova.util.setBreakStage
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.blockType
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.world.block.state.property.DefaultBlockStateProperties
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private val BLOCKED_SIDES = BlockSideSet(front = true)

private val MAX_ENERGY = BLOCK_BREAKER.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = BLOCK_BREAKER.config.entry<Long>("energy_per_tick")
private val BREAK_SPEED_MULTIPLIER = BLOCK_BREAKER.config.entry<Double>("break_speed_multiplier")
private val BLOCK_DAMAGE_CLAMP by BLOCK_BREAKER.config.entry<Double>("break_speed_clamp")

class BlockBreaker(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val inventory = storedInventory("inventory", 9, ::handleInventoryUpdate)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(inventory to EXTRACT, blockedSides = BLOCKED_SIDES)
    
    private val energyPerTick by energyConsumption(ENERGY_PER_TICK, upgradeHolder)
    private val breakSpeed by speedMultipliedValue(BREAK_SPEED_MULTIPLIER, upgradeHolder)
    
    private val entityId = uuid.hashCode()
    private val targetBlock = block.getRelative(blockState.getOrThrow(DefaultBlockStateProperties.FACING_HORIZONTAL))
    private var lastType: BlockType? = null
    private var breakProgress by storedValue("breakProgress") { 0.0 }
    
    @Volatile
    private var hasBreakPermission = false
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.GENERIC_3X3_WITH_BAR) {
        upperGui by gui(
            "s . . i i i . . e",
            "u . . i i i . . e",
            ". . . i i i . . e",
        ) {
            'i' by inventory
            's' by openSideConfigItem(mapOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default"))
            'u' by openUpgradesItem(upgradeHolder)
            'e' by energyBar(energyHolder)
        }
    }
    
    private fun handleInventoryUpdate(event: ItemPreUpdateEvent) {
        if (event.isAdd && event.updateReason != SELF_UPDATE_REASON)
            event.isCancelled = true
    }
    
    override fun handleEnableTicking() {
        coroutineSupervisor?.let(::CoroutineScope)?.launch {
            while (true) {
                hasBreakPermission = ProtectionManager.canBreak(this@BlockBreaker, null, targetBlock)
                delay(50.milliseconds)
            }
        }
    }
    
    override fun handleTick() {
        val type = targetBlock.blockType
        if (energyHolder.energy >= energyPerTick
            && !type.isAir
            && type != BlockType.WATER
            && type != BlockType.BUBBLE_COLUMN
            && type != BlockType.LAVA
            && targetBlock.blockType.hardness >= 0
            && hasBreakPermission
        ) {
            // consume energy
            energyHolder.energy -= energyPerTick
            
            // reset progress when block changed
            if (lastType != null && type != lastType)
                breakProgress = 0.0
            
            // set last known type
            lastType = type
            
            // add progress
            val damage = ToolUtils.calculateDamage(
                targetBlock.blockType.hardness.toDouble(),
                correctForDrops = true,
                speed = breakSpeed
            ).coerceAtMost(BLOCK_DAMAGE_CLAMP)
            breakProgress = min(1.0, breakProgress + damage)
            
            if (breakProgress >= 1.0) {
                val ctx = Context.intention(BlockBreak)
                    .param(BlockBreak.BLOCK, targetBlock)
                    .param(BlockBreak.BLOCK_DROPS, true)
                    .param(BlockBreak.SOURCE_TILE_ENTITY, this)
                    .build()
                val drops = BlockUtils.getDrops(ctx).toMutableList()
                NovaEventFactory.callTileEntityBlockBreakEvent(this, targetBlock, drops)
                
                if (!GlobalValues.DROP_EXCESS_ON_GROUND && !inventory.canHold(drops))
                    return
                
                // reset break progress
                breakProgress = 0.0
                targetBlock.setBreakStage(entityId, -1)
                
                // break block, add items to inventory / drop them if full
                BlockUtils.breakBlock(ctx)
                drops.forEach { drop ->
                    val amountLeft = inventory.addItem(SELF_UPDATE_REASON, drop)
                    if (GlobalValues.DROP_EXCESS_ON_GROUND && amountLeft != 0) {
                        drop.amount = amountLeft
                        block.world.dropItemNaturally(targetBlock.location.add(0.5, 0.0, 0.5), drop)
                    }
                }
            } else {
                // send break state
                targetBlock.setBreakStage(entityId, (breakProgress * 9).roundToInt())
            }
        }
    }
    
}
