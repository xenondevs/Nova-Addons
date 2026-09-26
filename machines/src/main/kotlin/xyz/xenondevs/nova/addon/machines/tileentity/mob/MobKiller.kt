package xyz.xenondevs.nova.addon.machines.tileentity.mob

import kotlinx.coroutines.runBlocking
import net.minecraft.core.registries.Registries
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import org.bukkit.entity.Mob
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.nova.addon.machines.gui.idleBar
import xyz.xenondevs.nova.addon.machines.registry.Blocks.MOB_KILLER
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.util.energyConsumption
import xyz.xenondevs.nova.addon.machines.util.markAsHurtByPlayer
import xyz.xenondevs.nova.addon.machines.util.maxIdleTime
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedRegion
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.nova.util.BlockSideSet
import xyz.xenondevs.nova.util.getOrThrow
import xyz.xenondevs.nova.util.nmsEntity
import xyz.xenondevs.nova.util.serverLevel
import xyz.xenondevs.nova.util.toVec3
import org.bukkit.block.Block
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.region.Region
import kotlin.math.min

private val BLOCKED_SIDES = BlockSideSet(front = true)

private val MAX_ENERGY = MOB_KILLER.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = MOB_KILLER.config.entry<Long>("energy_per_tick")
private val ENERGY_PER_DAMAGE = MOB_KILLER.config.entry<Long>("energy_per_damage")
private val IDLE_TIME = MOB_KILLER.config.entry<Int>("idle_time")
private val KILL_LIMIT by MOB_KILLER.config.entry<Int>("kill_limit")
private val DAMAGE by MOB_KILLER.config.entry<Float>("damage")
private val MIN_RANGE = MOB_KILLER.config.entry<Int>("range", "min")
private val MAX_RANGE = MOB_KILLER.config.entry<Int>("range", "max")
private val DEFAULT_RANGE by MOB_KILLER.config.entry<Int>("range", "default")

class MobKiller(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.RANGE)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT, BLOCKED_SIDES)
    
    private val region = storedRegion("region.default", MIN_RANGE, MAX_RANGE, DEFAULT_RANGE, upgradeHolder) {
        val size = 1 + it * 2
        Region.inFrontOf(this, size, size, 4, -1)
    }
    
    private val energyPerTick by energyConsumption(ENERGY_PER_TICK, upgradeHolder)
    private val energyPerDamage by energyConsumption(ENERGY_PER_DAMAGE, upgradeHolder)
    private val maxIdleTimeProvider = maxIdleTime(IDLE_TIME, upgradeHolder)
    private val mxIdleTime by maxIdleTimeProvider
    
    private val timePassedProvider = mutableProvider(0)
    private var timePassed by timePassedProvider
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.MOB_KILLER) {
        upperGui by gui(
            "s . . i . e . . p",
            "r . . i . e . . n",
            "u . . i . e . . m",
        ) {
            's' by openSideConfigItem()
            'u' by openUpgradesItem(upgradeHolder)
            'r' by region.visualizeRegionItem
            'p' by region.increaseSizeItem
            'm' by region.decreaseSizeItem
            'n' by region.displaySizeItem
            'e' by energyBar(energyHolder)
            'i' by idleBar("menu.machines.mob_killer.idle", timePassedProvider, maxIdleTimeProvider)
        }
    }
    
    override fun handleTick() {
        if (energyHolder.energy >= energyPerTick) {
            energyHolder.energy -= energyPerTick
            
            if (timePassed++ >= mxIdleTime) {
                timePassed = 0
                
                val killLimit = min((energyHolder.energy / energyPerDamage).toInt(), KILL_LIMIT)
                
                block.world.getNearbyEntities(region.toBoundingBox()).asSequence()
                    .filterIsInstance<Mob>()
                    .filter { runBlocking { ProtectionManager.canHurtEntity(this@MobKiller, it, null) } } // TODO non-blocking
                    .take(killLimit)
                    .forEach { entity ->
                        // TODO: custom damage type
                        val damageType = Registries.DAMAGE_TYPE.getOrThrow(DamageTypes.MOB_ATTACK)
                        entity.markAsHurtByPlayer()
                        entity.nmsEntity.hurtServer(entity.world.serverLevel, DamageSource(damageType, block.location.toVec3()), DAMAGE)
                    }
            }
        }
    }
    
}
