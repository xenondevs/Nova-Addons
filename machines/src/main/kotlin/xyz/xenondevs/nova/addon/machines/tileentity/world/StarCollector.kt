package xyz.xenondevs.nova.addon.machines.tileentity.world

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.EquipmentSlot
import org.bukkit.Bukkit
import org.bukkit.util.Vector
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.item.builder.ItemBuilder
import xyz.xenondevs.invui.item.builder.setDisplayName
import xyz.xenondevs.nmsutils.particle.color
import xyz.xenondevs.nmsutils.particle.dustTransition
import xyz.xenondevs.nmsutils.particle.particle
import xyz.xenondevs.nova.data.config.GlobalValues
import xyz.xenondevs.nova.data.config.NovaConfig
import xyz.xenondevs.nova.data.config.configReloadable
import xyz.xenondevs.nova.data.resources.model.data.DisplayEntityBlockModelData
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.item.DefaultGuiItems
import xyz.xenondevs.nova.addon.machines.registry.Blocks.STAR_COLLECTOR
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.item.holder.NovaItemHolder
import xyz.xenondevs.nova.tileentity.upgrade.Upgradable
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.OpenUpgradesItem
import xyz.xenondevs.nova.ui.VerticalBar
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.Vector
import xyz.xenondevs.nova.util.calculateYaw
import xyz.xenondevs.nova.util.center
import xyz.xenondevs.nova.util.dropItem
import xyz.xenondevs.nova.util.sendTo
import xyz.xenondevs.nova.world.fakeentity.impl.FakeArmorStand
import xyz.xenondevs.nova.addon.simpleupgrades.ConsumerEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import java.awt.Color

private val MAX_ENERGY = configReloadable { NovaConfig[STAR_COLLECTOR].getLong("capacity") }
private val IDLE_ENERGY_PER_TICK = configReloadable { NovaConfig[STAR_COLLECTOR].getLong("energy_per_tick_idle") }
private val COLLECTING_ENERGY_PER_TICK = configReloadable { NovaConfig[STAR_COLLECTOR].getLong("energy_per_tick_collecting") }
private val IDLE_TIME by configReloadable { NovaConfig[STAR_COLLECTOR].getInt("idle_time") }
private val COLLECTION_TIME by configReloadable { NovaConfig[STAR_COLLECTOR].getInt("collection_time") }

private const val STAR_PARTICLE_DISTANCE_PER_TICK = 0.75

class StarCollector(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState), Upgradable {
    
    private val inventory = getInventory("inventory", 1, ::handleInventoryUpdate)
    override val upgradeHolder = getUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    override val itemHolder = NovaItemHolder(this, inventory to NetworkConnectionType.EXTRACT) {
        createExclusiveSideConfig(NetworkConnectionType.EXTRACT, BlockSide.BOTTOM)
    }
    override val energyHolder = ConsumerEnergyHolder(this, MAX_ENERGY, IDLE_ENERGY_PER_TICK, COLLECTING_ENERGY_PER_TICK, upgradeHolder) {
        createExclusiveSideConfig(NetworkConnectionType.INSERT, BlockSide.BOTTOM)
    }
    
    private var maxIdleTime = 0
    private var maxCollectionTime = 0
    private var timeSpentIdle = 0
    private var timeSpentCollecting = -1
    private lateinit var particleVector: Vector
    
    private val rodLocation = location.clone().center().apply { y += 0.7 }
    private val rod = FakeArmorStand(location.clone().center().apply { y -= 1 }, true) { ast, data ->
        data.isMarker = true
        data.isInvisible = true
        ast.setEquipment(EquipmentSlot.HEAD, (block.model as DisplayEntityBlockModelData)[1].get(), false)
    }
    
    private val particleTask = createPacketTask(listOf(
        particle(ParticleTypes.DUST_COLOR_TRANSITION) {
            location(location.clone().center().apply { y += 0.2 })
            dustTransition(Color(132, 0, 245), Color(196, 128, 217), 1f)
            offset(0.25, 0.1, 0.25)
            amount(3)
        }
    ), 1)
    
    init {
        reload()
    }
    
    override fun reload() {
        super.reload()
        
        maxIdleTime = (IDLE_TIME / upgradeHolder.getValue(UpgradeTypes.SPEED)).toInt()
        maxCollectionTime = (COLLECTION_TIME / upgradeHolder.getValue(UpgradeTypes.SPEED)).toInt()
    }
    
    override fun handleTick() {
        if (world.time in 13_000..23_000 || timeSpentCollecting != -1) handleNightTick()
        else handleDayTick()
    }
    
    private fun handleNightTick() {
        if (timeSpentCollecting != -1) {
            if (!GlobalValues.DROP_EXCESS_ON_GROUND && inventory.isFull) return
            if (energyHolder.energy >= energyHolder.specialEnergyConsumption) {
                energyHolder.energy -= energyHolder.specialEnergyConsumption
                handleCollectionTick() 
            }
        } else if (energyHolder.energy >= energyHolder.energyConsumption) {
            energyHolder.energy -= energyHolder.energyConsumption
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
            if (GlobalValues.DROP_EXCESS_ON_GROUND && leftOver != 0) location.dropItem(item)
            
            particleTask.stop()
            rod.setEquipment(EquipmentSlot.HEAD, (block.model as DisplayEntityBlockModelData)[1].get(), true)
        } else {
            val percentageCollected = (maxCollectionTime - timeSpentCollecting) / maxCollectionTime.toDouble()
            val particleDistance = percentageCollected * (STAR_PARTICLE_DISTANCE_PER_TICK * maxCollectionTime)
            val particleLocation = rodLocation.clone().add(particleVector.clone().multiply(particleDistance))
            
            particle(ParticleTypes.DUST) {
                location(particleLocation)
                color(Color(255, 255, 255))
            }.sendTo(getViewers())
        }
        
        menuContainer.forEachMenu<StarCollectorMenu> { it.collectionBar.percentage = timeSpentCollecting / maxCollectionTime.toDouble() }
    }
    
    private fun handleIdleTick() {
        timeSpentIdle++
        if (timeSpentIdle >= maxIdleTime) {
            timeSpentCollecting = 0
            
            particleTask.start()
            
            rod.setEquipment(EquipmentSlot.HEAD, (block.model as DisplayEntityBlockModelData)[2].get(), true)
            
            rodLocation.yaw = rod.location.yaw
            particleVector = Vector(rod.location.yaw, -65F)
        } else rod.teleport { this.yaw += 2F }
        
        menuContainer.forEachMenu<StarCollectorMenu> { it.idleBar.percentage = timeSpentIdle / maxIdleTime.toDouble() }
    }
    
    private fun handleDayTick() {
        val player = Bukkit.getOnlinePlayers()
            .asSequence()
            .filter { it.location.world == world }
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
    
    override fun handleRemoved(unload: Boolean) {
        super.handleRemoved(unload)
        rod.remove()
    }
    
    @TileEntityMenuClass
    inner class StarCollectorMenu : GlobalTileEntityMenu() {
        
        private val sideConfigGui = SideConfigMenu(
            this@StarCollector,
            listOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.output"),
            ::openWindow
        )
        
        val collectionBar = object : VerticalBar(3) {
            override val barItem = DefaultGuiItems.BAR_GREEN
            override fun modifyItemBuilder(itemBuilder: ItemBuilder): ItemBuilder {
                if (timeSpentCollecting != -1)
                    itemBuilder.setDisplayName(Component.translatable( "menu.machines.star_collector.collection", NamedTextColor.GRAY))
                return itemBuilder
            }
        }
        
        val idleBar = object : VerticalBar(3) {
            override val barItem = DefaultGuiItems.BAR_GREEN
            override fun modifyItemBuilder(itemBuilder: ItemBuilder) =
                itemBuilder.setDisplayName(Component.translatable("menu.machines.star_collector.idle", NamedTextColor.GRAY))
        }
        
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
            .addIngredient('c', collectionBar)
            .addIngredient('p', idleBar)
            .addIngredient('e', EnergyBar(3, energyHolder))
            .build()
        
    }
    
}