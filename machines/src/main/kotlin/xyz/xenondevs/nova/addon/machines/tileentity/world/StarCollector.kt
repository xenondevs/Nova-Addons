package xyz.xenondevs.nova.addon.machines.tileentity.world

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.EquipmentSlot
import org.bukkit.Bukkit
import org.bukkit.block.BlockFace
import org.bukkit.util.Vector
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.enumSetOf
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.gui.IdleBar
import xyz.xenondevs.nova.addon.machines.registry.Blocks.STAR_COLLECTOR
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.addon.machines.registry.Models
import xyz.xenondevs.nova.addon.machines.util.efficiencyDividedValue
import xyz.xenondevs.nova.addon.machines.util.maxIdleTime
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.GlobalValues
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.PacketTask
import xyz.xenondevs.nova.util.Vector
import xyz.xenondevs.nova.util.calculateYaw
import xyz.xenondevs.nova.util.dropItem
import xyz.xenondevs.nova.util.particle.color
import xyz.xenondevs.nova.util.particle.dustTransition
import xyz.xenondevs.nova.util.particle.particle
import xyz.xenondevs.nova.util.sendTo
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.fakeentity.impl.FakeArmorStand
import java.awt.Color

private val BLOCKED_FACES = enumSetOf(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP)

private val MAX_ENERGY = STAR_COLLECTOR.config.entry<Long>("capacity")
private val IDLE_ENERGY_PER_TICK = STAR_COLLECTOR.config.entry<Long>("energy_per_tick_idle")
private val COLLECTING_ENERGY_PER_TICK = STAR_COLLECTOR.config.entry<Long>("energy_per_tick_collecting")
private val IDLE_TIME = STAR_COLLECTOR.config.entry<Int>("idle_time")
private val COLLECTION_TIME = STAR_COLLECTOR.config.entry<Int>("collection_time")

private const val STAR_PARTICLE_DISTANCE_PER_TICK = 0.75

class StarCollector(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val inventory = storedInventory("inventory", 1, ::handleInventoryUpdate)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    private val itemHolder = storedItemHolder(inventory to EXTRACT, blockedFaces = BLOCKED_FACES)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT, BLOCKED_FACES)
    
    private val idleEnergyPerTick by efficiencyDividedValue(IDLE_ENERGY_PER_TICK, upgradeHolder)
    private val collectingEnergyPerTick by efficiencyDividedValue(COLLECTING_ENERGY_PER_TICK, upgradeHolder)
    private val maxIdleTimeProvider = maxIdleTime(IDLE_TIME, upgradeHolder)
    private val mxIdleTime by maxIdleTimeProvider
    private val maxCollectionTimeProvider = maxIdleTime(COLLECTION_TIME, upgradeHolder)
    private val maxCollectionTime by maxCollectionTimeProvider
    private val timeSpentIdleProvider = mutableProvider(0)
    private var timeSpentIdle by timeSpentIdleProvider
    private val timeSpentCollectingProvider = mutableProvider(-1)
    private var timeSpentCollecting by timeSpentCollectingProvider
    private lateinit var particleVector: Vector
    
    private val rodLocation = pos.location.add(0.5, 0.7, 0.5)
    private val rod = FakeArmorStand(pos.location.add(0.5, -1.0, 0.5), false) { ast, data ->
        data.isMarker = true
        data.isInvisible = true
        ast.setEquipment(EquipmentSlot.HEAD, Models.STAR_COLLECTOR_ROD_OFF.clientsideProvider.get(), false)
    }
    
    private val particleTask = PacketTask(
        listOf(
            particle(ParticleTypes.DUST_COLOR_TRANSITION) {
                location(pos.location.add(0.5, 0.2, 0.5))
                dustTransition(Color(132, 0, 245), Color(196, 128, 217), 1f)
                offset(0.25, 0.1, 0.25)
                amount(3)
            }
        ),
        1,
        ::getViewers
    )
    
    override fun handleEnable() {
        super.handleEnable()
        rod.register()
        particleTask.start()
    }
    
    override fun handleDisable() {
        super.handleDisable()
        rod.remove()
        particleTask.stop()
    }
    
    override fun handleTick() {
        if (pos.world.time in 13_000..23_000 || timeSpentCollecting != -1) {
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
            
            val item = Items.STAR_DUST.createItemStack()
            val leftOver = inventory.addItem(SELF_UPDATE_REASON, item)
            if (GlobalValues.DROP_EXCESS_ON_GROUND && leftOver != 0)
                pos.location.dropItem(item)
            
            particleTask.stop()
            rod.setEquipment(EquipmentSlot.HEAD, Models.STAR_COLLECTOR_ROD_OFF.clientsideProvider.get(), true)
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
        if (timeSpentIdle >= mxIdleTime) {
            timeSpentCollecting = 0
            
            particleTask.start()
            
            rod.setEquipment(EquipmentSlot.HEAD, Models.STAR_COLLECTOR_ROD_ON.clientsideProvider.get(), true)
            
            rodLocation.yaw = rod.location.yaw
            particleVector = Vector(rod.location.yaw, -65F)
        } else rod.teleport { this.yaw += 2F }
    }
    
    private fun handleDayTick() {
        val player = Bukkit.getOnlinePlayers()
            .asSequence()
            .filter { it.location.world == pos.world }
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
    
    @TileEntityMenuClass
    inner class StarCollectorMenu : GlobalTileEntityMenu() {
        
        private val sideConfigGui = SideConfigMenu(
            this@StarCollector,
            mapOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.output"),
            ::openWindow
        )
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s # # # c p e |",
                "| u # i # c p e |",
                "| # # # # c p e |",
                "3 - - - - - - - 4")
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('i', inventory)
            .addIngredient('c', IdleBar(3, "menu.machines.star_collector.collection", timeSpentCollectingProvider, maxCollectionTimeProvider))
            .addIngredient('p', IdleBar(3, "menu.machines.star_collector.idle", timeSpentIdleProvider, maxIdleTimeProvider))
            .addIngredient('e', EnergyBar(3, energyHolder))
            .build()
        
    }
    
}