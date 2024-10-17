package xyz.xenondevs.nova.addon.machines.tileentity.world

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bukkit.Material
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.enumSetOf
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.registry.Blocks.BLOCK_BREAKER
import xyz.xenondevs.nova.addon.machines.util.efficiencyDividedValue
import xyz.xenondevs.nova.addon.machines.util.speedMultipliedValue
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.api.NovaEventFactory
import xyz.xenondevs.nova.config.GlobalValues
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.DefaultContextIntentions.BlockBreak
import xyz.xenondevs.nova.context.param.DefaultContextParamTypes
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.BlockUtils
import xyz.xenondevs.nova.util.hardness
import xyz.xenondevs.nova.util.item.ToolUtils
import xyz.xenondevs.nova.util.item.isTraversable
import xyz.xenondevs.nova.util.setBreakStage
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.state.property.DefaultBlockStateProperties
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import kotlin.math.min
import kotlin.math.roundToInt

private val BLOCKED_SIDES = enumSetOf(BlockSide.FRONT)

private val MAX_ENERGY = BLOCK_BREAKER.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = BLOCK_BREAKER.config.entry<Long>("energy_per_tick")
private val BREAK_SPEED_MULTIPLIER = BLOCK_BREAKER.config.entry<Double>("break_speed_multiplier")
private val BLOCK_DAMAGE_CLAMP by BLOCK_BREAKER.config.entry<Double>("break_speed_clamp")

class BlockBreaker(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val inventory = storedInventory("inventory", 9, ::handleInventoryUpdate)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(inventory to EXTRACT, blockedSides = BLOCKED_SIDES)
    
    private val energyPerTick by efficiencyDividedValue(ENERGY_PER_TICK, upgradeHolder)
    private val breakSpeed by speedMultipliedValue(BREAK_SPEED_MULTIPLIER, upgradeHolder)
    
    private val entityId = uuid.hashCode()
    private val targetPos = pos.advance(blockState.getOrThrow(DefaultBlockStateProperties.FACING))
    private var lastType: Material? = null
    private var breakProgress by storedValue("breakProgress") { 0.0 }
    
    @Volatile
    private var hasBreakPermission = false
    
    override fun handleDisable() {
        super.handleDisable()
        targetPos.block.setBreakStage(entityId, -1)
    }
    
    private fun handleInventoryUpdate(event: ItemPreUpdateEvent) {
        if (event.isAdd && event.updateReason != SELF_UPDATE_REASON)
            event.isCancelled = true
    }
    
    
    override fun handleEnableTicking() {
        CoroutineScope(coroutineSupervisor).launch {
            while (true) {
                hasBreakPermission = ProtectionManager.canBreak(this@BlockBreaker, null, targetPos)
                delay(50)
            }
        }
    }
    
    override fun handleTick() {
        val type = targetPos.block.type
        if (energyHolder.energy >= energyPerTick
            && !type.isTraversable()
            && targetPos.block.hardness >= 0
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
                targetPos.block.hardness,
                correctForDrops = true,
                speed = breakSpeed
            ).coerceAtMost(BLOCK_DAMAGE_CLAMP)
            breakProgress = min(1.0, breakProgress + damage)
            
            if (breakProgress >= 1.0) {
                val ctx = Context.intention(BlockBreak)
                    .param(DefaultContextParamTypes.BLOCK_POS, targetPos)
                    .param(DefaultContextParamTypes.BLOCK_DROPS, true)
                    .param(DefaultContextParamTypes.SOURCE_TILE_ENTITY, this)
                    .build()
                val drops = BlockUtils.getDrops(ctx).toMutableList()
                NovaEventFactory.callTileEntityBlockBreakEvent(this, targetPos.block, drops)
                
                if (!GlobalValues.DROP_EXCESS_ON_GROUND && !inventory.canHold(drops))
                    return
                
                // reset break progress
                breakProgress = 0.0
                targetPos.block.setBreakStage(entityId, -1)
                
                // break block, add items to inventory / drop them if full
                BlockUtils.breakBlock(ctx)
                drops.forEach { drop ->
                    val amountLeft = inventory.addItem(SELF_UPDATE_REASON, drop)
                    if (GlobalValues.DROP_EXCESS_ON_GROUND && amountLeft != 0) {
                        drop.amount = amountLeft
                        pos.world.dropItemNaturally(targetPos.location.add(0.5, 0.0, 0.5), drop)
                    }
                }
            } else {
                // send break state
                targetPos.block.setBreakStage(entityId, (breakProgress * 9).roundToInt())
            }
        }
    }
    
    @TileEntityMenuClass
    inner class BlockBreakerMenu : GlobalTileEntityMenu() {
        
        private val sideConfigGui = SideConfigMenu(
            this@BlockBreaker,
            mapOf(Pair(itemHolder.getNetworkedInventory(inventory), "inventory.nova.default")),
            ::openWindow
        )
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s # i i i # e |",
                "| u # i i i # e |",
                "| # # i i i # e |",
                "3 - - - - - - - 4")
            .addIngredient('i', inventory)
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('e', EnergyBar(3, energyHolder))
            .build()
        
    }
    
}