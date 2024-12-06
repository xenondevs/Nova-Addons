package xyz.xenondevs.nova.addon.machines.tileentity.mob

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.minecraft.world.entity.Mob
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.gson.isString
import xyz.xenondevs.commons.provider.combinedProvider
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.item.AbstractItem
import xyz.xenondevs.invui.item.Click
import xyz.xenondevs.invui.item.ItemBuilder
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.nova.addon.machines.gui.IdleBar
import xyz.xenondevs.nova.addon.machines.item.MobCatcherBehavior
import xyz.xenondevs.nova.addon.machines.registry.Blocks.MOB_DUPLICATOR
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.util.efficiencyDividedValue
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.addIngredient
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.EntityUtils
import xyz.xenondevs.nova.util.data.NBTUtils
import xyz.xenondevs.nova.util.isBetweenXZ
import xyz.xenondevs.nova.util.item.novaItem
import xyz.xenondevs.nova.util.nmsEntity
import xyz.xenondevs.nova.util.playClickSound
import xyz.xenondevs.nova.util.runAsyncTask
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.BUFFER
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import java.net.URI
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.random.nextInt

private val MAX_ENERGY = MOB_DUPLICATOR.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = MOB_DUPLICATOR.config.entry<Long>("energy_per_tick")
private val ENERGY_PER_TICK_NBT = MOB_DUPLICATOR.config.entry<Long>("energy_per_tick_nbt")
private val IDLE_TIME = MOB_DUPLICATOR.config.entry<Int>("idle_time")
private val IDLE_TIME_NBT = MOB_DUPLICATOR.config.entry<Int>("idle_time_nbt")
private val ENTITY_LIMIT by MOB_DUPLICATOR.config.entry<Int>("entity_limit")
private val NERF_MOBS by MOB_DUPLICATOR.config.entry<Boolean>("nerf_mobs")

class MobDuplicator(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val inventory = storedInventory("inventory", 1, ::handleInventoryUpdate)
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
    
    private val energyPerTickNbt by efficiencyDividedValue(ENERGY_PER_TICK_NBT, upgradeHolder)
    private val energyPerTickBasic by efficiencyDividedValue(ENERGY_PER_TICK, upgradeHolder)
    private val energyPerTick: Long
        get() = if (keepNbt) energyPerTickNbt else energyPerTickBasic
    
    private val timePassedProvider = mutableProvider(0)
    private var timePassed by timePassedProvider
    private var entityType: EntityType? = null
    private var entityData: ByteArray? = null
    
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
    
    private fun handleInventoryUpdate(event: ItemPreUpdateEvent) {
        if (event.newItem != null) {
            event.isCancelled = !updateEntityData(event.newItem)
        } else setEntityData(null, null)
    }
    
    private fun updateEntityData(itemStack: ItemStack?): Boolean {
        val catcher = itemStack?.novaItem?.getBehaviorOrNull<MobCatcherBehavior>()
        if (catcher != null) {
            setEntityData(catcher.getEntityType(itemStack), catcher.getEntityData(itemStack))
            return true
        }
        return false
    }
    
    private fun setEntityData(type: EntityType?, data: ByteArray?) {
        entityData = data
        entityType = type
        timePassed = 0
    }
    
    private fun spawnEntity() {
        if (ENTITY_LIMIT != -1 && countSurroundingEntities() > ENTITY_LIMIT)
            return
        
        val spawnLocation = pos.location.add(0.5, 1.0, 0.5)
        val entity = if (keepNbt) {
            EntityUtils.deserializeAndSpawn(entityData!!, spawnLocation, nbtModifier = NBTUtils::removeItemData).bukkitEntity
        } else spawnLocation.world!!.spawnEntity(spawnLocation, entityType!!)
        
        val nmsEntity = entity.nmsEntity
        if (NERF_MOBS && nmsEntity is Mob)
            nmsEntity.aware = false
        
        if (patreonSkulls.isNotEmpty() && entity is LivingEntity && Random.nextInt(1..1000) == 1) {
            entity.equipment?.setHelmet(patreonSkulls.random().get(), true)
        }
    }
    
    private fun countSurroundingEntities(): Int =
        pos.world.livingEntities.asSequence()
            .filter {
                it.location.isBetweenXZ(
                    pos.location.subtract(16.0, 0.0, 16.0),
                    pos.location.add(16.0, 0.0, 16.0)
                )
            }.count()
    
    @TileEntityMenuClass
    inner class MobDuplicatorMenu : GlobalTileEntityMenu() {
        
        private val sideConfigGui = SideConfigMenu(
            this@MobDuplicator,
            mapOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default"),
            ::openWindow
        )
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s # # # # p e |",
                "| n # # i # p e |",
                "| u # # # # p e |",
                "3 - - - - - - - 4")
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('i', inventory, GuiItems.MOB_CATCHER_PLACEHOLDER)
            .addIngredient('n', ToggleNBTModeItem())
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('e', EnergyBar(3, energyHolder))
            .addIngredient('p', IdleBar(3, "menu.machines.mob_duplicator.idle", timePassedProvider, maxIdleTimeProvider))
            .build()
        
        private inner class ToggleNBTModeItem : AbstractItem() {
            
            override fun getItemProvider(player: Player): ItemProvider {
                return (if (keepNbt) GuiItems.NBT_BTN_ON else GuiItems.NBT_BTN_OFF).clientsideProvider
            }
            
            override fun handleClick(clickType: ClickType, player: Player, click: Click) {
                keepNbt = !keepNbt
                notifyWindows()
                
                timePassed = 0
                
                player.playClickSound()
            }
            
        }
        
    }
    
    private companion object PatronSkulls {
        
        private const val PATRON_SKULLS_URL = "https://xenondevs.xyz/nova/patron_skulls.json"
        val patreonSkulls = ArrayList<ItemBuilder>()
        
        init {
            runAsyncTask {
                val url = URI(PATRON_SKULLS_URL).toURL()
                val array = url.openConnection().getInputStream().bufferedReader().use(JsonParser::parseReader)
                if (array is JsonArray) {
                    array.asSequence()
                        .filter(JsonElement::isString)
                        .forEach {
                            patreonSkulls += ItemBuilder(Material.PLAYER_HEAD).set(
                                DataComponentTypes.PROFILE,
                                ResolvableProfile.resolvableProfile().name(it.asString)
                            )
                        }
                }
            }
        }
        
    }
    
}