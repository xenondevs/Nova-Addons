package xyz.xenondevs.nova.addon.machines.tileentity.mob

import net.minecraft.world.entity.Mob
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.combinedProvider
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.dsl.item
import xyz.xenondevs.invui.inventory.event.ItemPostUpdateEvent
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.gui.idleBar
import xyz.xenondevs.nova.addon.machines.item.DISALLOWED_ENTITY_TYPES
import xyz.xenondevs.nova.addon.machines.item.MobCatcherBehavior
import xyz.xenondevs.nova.addon.machines.registry.Blocks.MOB_DUPLICATOR
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.util.energyConsumption
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.invui.dsl.with
import xyz.xenondevs.nova.util.EntityUtils
import xyz.xenondevs.nova.util.data.NBTUtils
import xyz.xenondevs.nova.util.isBetweenXZ
import xyz.xenondevs.nova.util.nmsEntity
import xyz.xenondevs.nova.util.playClickSound
import org.bukkit.block.Block
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.BUFFER
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.item.guiItemProvider
import xyz.xenondevs.nova.world.item.getBehaviorOrNull
import xyz.xenondevs.nova.world.item.hasBehavior
import xyz.xenondevs.nova.world.item.itemType
import kotlin.math.roundToInt

private val MAX_ENERGY = MOB_DUPLICATOR.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = MOB_DUPLICATOR.config.entry<Long>("energy_per_tick")
private val ENERGY_PER_TICK_NBT = MOB_DUPLICATOR.config.entry<Long>("energy_per_tick_nbt")
private val IDLE_TIME = MOB_DUPLICATOR.config.entry<Int>("idle_time")
private val IDLE_TIME_NBT = MOB_DUPLICATOR.config.entry<Int>("idle_time_nbt")
private val ENTITY_LIMIT by MOB_DUPLICATOR.config.entry<Int>("entity_limit")
private val NERF_MOBS by MOB_DUPLICATOR.config.entry<Boolean>("nerf_mobs")

class MobDuplicator(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val inventory = storedInventory("inventory", size = 1, persistent = false, maxStackSizes = intArrayOf(1), ::handlePreUpdate, ::handlePostUpdate)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT)
    private val itemHolder = storedItemHolder(inventory to BUFFER)
    
    private val keepNbtProvider = storedValue("keepNbt") { false }
    private var keepNbt by keepNbtProvider
    private val maxIdleTimeNbt = combinedProvider(
        IDLE_TIME_NBT, upgradeHolder.getValueProvider(UpgradeTypes.SPEED)
    ) { idleTimeNBT, speed -> (idleTimeNBT / speed).roundToInt() }
    private val maxIdleTimeBasic = combinedProvider(
        IDLE_TIME, upgradeHolder.getValueProvider(UpgradeTypes.SPEED)
    ) { idleTime, speed -> (idleTime / speed).roundToInt() }
    private val maxIdleTimeProvider = combinedProvider(
        keepNbtProvider, maxIdleTimeNbt, maxIdleTimeBasic
    ) { keepNbtProvider, maxIdleTimeNbt, maxIdleTimeBasic ->
        if (keepNbtProvider) maxIdleTimeNbt else maxIdleTimeBasic
    }
    private val maxIdleTime by maxIdleTimeProvider
    
    private val energyPerTickNbt by energyConsumption(ENERGY_PER_TICK_NBT, upgradeHolder)
    private val energyPerTickBasic by energyConsumption(ENERGY_PER_TICK, upgradeHolder)
    private val energyPerTick: Long
        get() = if (keepNbt) energyPerTickNbt else energyPerTickBasic
    
    private val timePassedProvider = mutableProvider(0)
    private var timePassed by timePassedProvider
    private var entityType: EntityType? = null
    private var entityData: ByteArray? = null
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.MOB_DUPLICATOR) {
        upperGui by gui(
            "s . . . . . . p e",
            "n . . . i . . p e",
            "u . . . . . . p e",
        ) {
            's' by openSideConfigItem(mapOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default"))
            'i' by (inventory with GuiItems.MOB_CATCHER_PLACEHOLDER)
            'n' by item {
                itemProvider by keepNbtProvider.flatMap {
                    if (it) GuiItems.NBT_BTN_ON.guiItemProvider else GuiItems.NBT_BTN_OFF.guiItemProvider
                }
                onClick {
                    keepNbt = !keepNbt
                    timePassed = 0
                    player.playClickSound()
                }
            }
            'u' by openUpgradesItem(upgradeHolder)
            'e' by energyBar(energyHolder)
            'p' by idleBar("menu.machines.mob_duplicator.idle", timePassedProvider, maxIdleTimeProvider)
        }
    }
    
    init {
        updateEntityData(inventory.getItem(0))
    }
    
    override fun handleTick() {
        if (entityData != null && entityType != null && energyHolder.energy >= energyPerTick) {
            energyHolder.energy -= energyPerTick
            
            if (timePassed++ >= maxIdleTime) {
                timePassed = 0
                
                spawnEntity()
            }
        }
    }
    
    private fun handlePreUpdate(event: ItemPreUpdateEvent) {
        if (!event.isRemove) {
            event.isCancelled = event.newItem?.itemType?.hasBehavior<MobCatcherBehavior>() != true
        }
    }
    
    private fun handlePostUpdate(event: ItemPostUpdateEvent) {
        updateEntityData(event.newItem)
    }
    
    private fun updateEntityData(itemStack: ItemStack?) {
        val catcher = itemStack?.itemType?.getBehaviorOrNull<MobCatcherBehavior>()
        
        entityData = catcher?.getEntityData(itemStack)
        entityType = catcher?.getEntityType(itemStack)
        timePassed = 0
    }
    
    private fun spawnEntity() {
        if (ENTITY_LIMIT != -1 && countSurroundingEntities() > ENTITY_LIMIT)
            return
        
        val spawnLocation = block.location.add(0.5, 1.0, 0.5)
        
        val entityType = entityType
        val entityData = entityData
        
        var entity: Entity? = null
        if (entityType != null && entityData != null && entityType !in DISALLOWED_ENTITY_TYPES) {
            if (keepNbt) {
                entity = EntityUtils.deserializeAndSpawn(
                    entityData,
                    spawnLocation,
                    disallowedEntityTypes = DISALLOWED_ENTITY_TYPES,
                    nbtModifier = NBTUtils::removeItemData
                )?.bukkitEntity
            } else {
                entity = spawnLocation.world?.spawnEntity(spawnLocation, entityType)
            }
        }
        
        if (entity == null)
            return
        
        val nmsEntity = entity.nmsEntity
        if (NERF_MOBS && nmsEntity is Mob)
            nmsEntity.aware = false
    }
    
    private fun countSurroundingEntities(): Int {
        val from = block.location.subtract(16.0, 0.0, 16.0)
        val to = block.location.add(16.0, 0.0, 16.0)
        return block.world.livingEntities.count { it.location.isBetweenXZ(from, to) }
    }
    
}
