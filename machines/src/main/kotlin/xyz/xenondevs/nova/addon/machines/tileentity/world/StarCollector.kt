package xyz.xenondevs.nova.addon.machines.tileentity.world

import net.minecraft.core.particles.ParticleTypes
import org.bukkit.Bukkit
import org.bukkit.util.Vector
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.gui.idleBar
import xyz.xenondevs.nova.addon.machines.registry.Blocks.STAR_COLLECTOR
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.addon.machines.registry.Models
import xyz.xenondevs.nova.addon.machines.util.energyConsumption
import xyz.xenondevs.nova.addon.machines.util.maxIdleTime
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.GlobalValues
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.network.sendTo
import xyz.xenondevs.nova.packetentity.isInvisible
import xyz.xenondevs.nova.packetentity.isMarker
import xyz.xenondevs.nova.packetentity.packetArmorStand
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.nova.util.CubeFaceSet
import xyz.xenondevs.nova.util.PacketTask
import xyz.xenondevs.nova.util.Vector
import xyz.xenondevs.nova.util.calculateYaw
import xyz.xenondevs.nova.util.dropItem
import xyz.xenondevs.nova.util.particle.color
import xyz.xenondevs.nova.util.particle.dustTransition
import xyz.xenondevs.nova.util.particle.particle
import org.bukkit.block.Block
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.item.guiItemProvider
import java.awt.Color

private val BLOCKED_FACES = CubeFaceSet(north = true, east = true, south = true, west = true, up = true)

private val MAX_ENERGY = STAR_COLLECTOR.config.entry<Long>("capacity")
private val IDLE_ENERGY_PER_TICK = STAR_COLLECTOR.config.entry<Long>("energy_per_tick_idle")
private val COLLECTING_ENERGY_PER_TICK = STAR_COLLECTOR.config.entry<Long>("energy_per_tick_collecting")
private val IDLE_TIME = STAR_COLLECTOR.config.entry<Int>("idle_time")
private val COLLECTION_TIME = STAR_COLLECTOR.config.entry<Int>("collection_time")

private const val STAR_PARTICLE_DISTANCE_PER_TICK = 0.75

class StarCollector(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val inventory = storedInventory("inventory", 1, ::handleInventoryUpdate)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    private val itemHolder = storedItemHolder(inventory to EXTRACT, blockedFaces = BLOCKED_FACES)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT, BLOCKED_FACES)
    
    private val idleEnergyPerTick by energyConsumption(IDLE_ENERGY_PER_TICK, upgradeHolder)
    private val collectingEnergyPerTick by energyConsumption(COLLECTING_ENERGY_PER_TICK, upgradeHolder)
    private val _maxIdleTime = maxIdleTime(IDLE_TIME, upgradeHolder)
    private val maxIdleTime: Int by _maxIdleTime
    private val _maxCollectionTime = maxIdleTime(COLLECTION_TIME, upgradeHolder)
    private val maxCollectionTime by _maxCollectionTime
    private val _timeSpentIdle = mutableProvider(0)
    private var timeSpentIdle by _timeSpentIdle
    private val _timeSpentCollecting = mutableProvider(-1)
    private var timeSpentCollecting by _timeSpentCollecting
    private val _isActive = mutableProvider(false)
    private var isActive by _isActive
    private lateinit var particleVector: Vector
    
    private val rodLocation = block.location.add(0.5, 0.7, 0.5)
    
    private val rod = packetArmorStand {
        location by block.location.add(0.5, -1.0, 0.5)
        metadata {
            isMarker by true
            isInvisible by true
        }
        equipment {
            head by _isActive
                .flatMap { active ->
                    if (active) Models.STAR_COLLECTOR_ROD_ON.guiItemProvider
                    else Models.STAR_COLLECTOR_ROD_OFF.guiItemProvider
                }
                .map { it.get() }
        }
    }
    
    private val particleTask = PacketTask(
        listOf(
            particle(ParticleTypes.DUST_COLOR_TRANSITION) {
                location(block.location.add(0.5, 0.2, 0.5))
                dustTransition(Color(132, 0, 245), Color(196, 128, 217), 1f)
                offset(0.25, 0.1, 0.25)
                amount(3)
            }
        ),
        1,
        ::getViewers
    )
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.STAR_COLLECTOR) {
        upperGui by gui(
            "s . . . . . c p e",
            "u . . i . . c p e",
            ". . . . . . c p e",
        ) {
            's' by openSideConfigItem(mapOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.output"))
            'u' by openUpgradesItem(upgradeHolder)
            'i' by inventory
            'c' by idleBar("menu.machines.star_collector.collection", _timeSpentCollecting, _maxCollectionTime)
            'p' by idleBar("menu.machines.star_collector.idle", _timeSpentIdle, _maxIdleTime)
            'e' by energyBar(energyHolder)
        }
    }
    
    override fun handleEnable() {
        super.handleEnable()
        rod.spawn()
        particleTask.start()
    }
    
    override fun handleDisable() {
        super.handleDisable()
        rod.despawn()
        particleTask.stop()
    }
    
    override fun handleTick() {
        if (block.world.time in 13_000..23_000 || timeSpentCollecting != -1) {
            handleNightTick()
        } else handleDayTick()
    }
    
    private fun handleNightTick() {
        if (timeSpentCollecting != -1) {
            if (!GlobalValues.DROP_EXCESS_ON_GROUND && inventory.isFull)
                return
            
            if (energyHolder.energy >= collectingEnergyPerTick) {
                energyHolder.energy -= collectingEnergyPerTick
                handleCollectionTick()
            }
        } else if (energyHolder.energy >= idleEnergyPerTick) {
            energyHolder.energy -= idleEnergyPerTick
            handleIdleTick()
        }
    }
    
    private fun handleCollectionTick() {
        timeSpentCollecting++
        if (timeSpentCollecting >= maxCollectionTime) {
            timeSpentIdle = 0
            timeSpentCollecting = -1
            
            val item = Items.STAR_DUST.get().createItemStack()
            val leftOver = inventory.addItem(SELF_UPDATE_REASON, item)
            if (GlobalValues.DROP_EXCESS_ON_GROUND && leftOver != 0)
                block.location.dropItem(item)
            
            particleTask.stop()
            isActive = false
        } else {
            val percentageCollected = (maxCollectionTime - timeSpentCollecting) / maxCollectionTime.toDouble()
            val particleDistance = percentageCollected * (STAR_PARTICLE_DISTANCE_PER_TICK * maxCollectionTime)
            val particleLocation = rodLocation.clone().add(particleVector.clone().multiply(particleDistance))
            
            particle(ParticleTypes.DUST) {
                location(particleLocation)
                color(Color(255, 255, 255))
            }.sendTo(getViewers())
        }
    }
    
    private fun handleIdleTick() {
        timeSpentIdle++
        if (timeSpentIdle >= maxIdleTime) {
            timeSpentCollecting = 0
            
            particleTask.start()
            isActive = true
            
            rodLocation.yaw = rod.location.yaw
            particleVector = Vector(rod.location.yaw, -65F)
        } else rod.teleport { this.yaw += 2F }
    }
    
    private fun handleDayTick() {
        val player = Bukkit.getOnlinePlayers()
            .asSequence()
            .filter { it.location.world == block.world }
            .minByOrNull { it.location.distanceSquared(rodLocation) }
        
        if (player != null) {
            val distance = rodLocation.distance(player.location)
            
            if (distance <= 5) {
                val vector = player.location.subtract(rodLocation).toVector()
                val yaw = vector.calculateYaw()
                
                rod.teleport { this.yaw = yaw }
            }
        }
    }
    
    private fun handleInventoryUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = event.updateReason != SELF_UPDATE_REASON && !event.isRemove
    }
    
}
