package xyz.xenondevs.nova.addon.machines.tileentity.mob

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.world.entity.Mob
import org.bukkit.Sound
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.gson.isString
import xyz.xenondevs.commons.provider.immutable.combinedProvider
import xyz.xenondevs.commons.provider.immutable.map
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.invui.item.builder.ItemBuilder
import xyz.xenondevs.invui.item.builder.SkullBuilder
import xyz.xenondevs.invui.item.builder.SkullBuilder.HeadTexture
import xyz.xenondevs.invui.item.builder.setDisplayName
import xyz.xenondevs.invui.item.impl.AbstractItem
import xyz.xenondevs.nova.addon.machines.item.MobCatcherBehavior
import xyz.xenondevs.nova.addon.machines.registry.Blocks.MOB_DUPLICATOR
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.util.efficiencyDividedValue
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.item.DefaultGuiItems
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.type.NetworkConnectionType.BUFFER
import xyz.xenondevs.nova.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.VerticalBar
import xyz.xenondevs.nova.ui.menu.addIngredient
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.EntityUtils
import xyz.xenondevs.nova.util.data.NBTUtils
import xyz.xenondevs.nova.util.isBetweenXZ
import xyz.xenondevs.nova.util.item.novaItem
import xyz.xenondevs.nova.util.nmsEntity
import xyz.xenondevs.nova.util.runAsyncTask
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import java.net.URL
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
    
    private val maxIdleTimeNbt by combinedProvider(IDLE_TIME_NBT, upgradeHolder.getValueProvider(UpgradeTypes.SPEED))
        .map { (idleTimeNBT, speed) -> (idleTimeNBT / speed).roundToInt() }
    private val maxIdleTimeBasic by combinedProvider(IDLE_TIME, upgradeHolder.getValueProvider(UpgradeTypes.SPEED))
        .map { (idleTime, speed) -> (idleTime / speed).roundToInt() }
    private val energyPerTickNbt by efficiencyDividedValue(ENERGY_PER_TICK_NBT, upgradeHolder)
    private val energyPerTickBasic by efficiencyDividedValue(ENERGY_PER_TICK, upgradeHolder)
    
    private val energyPerTick: Long
        get() = if (keepNbt) energyPerTickNbt else energyPerTickBasic
    private val maxIdleTime: Int
        get() = if (keepNbt) maxIdleTimeNbt else maxIdleTimeBasic
    
    private var timePassed = 0
    private var entityType: EntityType? = null
    private var entityData: ByteArray? = null
    private var keepNbt by storedValue("keepNbt") { false }
    
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
            
            menuContainer.forEachMenu(MobDuplicatorMenu::updateIdleBar)
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
        menuContainer.forEachMenu(MobDuplicatorMenu::updateIdleBar)
    }
    
    private fun spawnEntity() {
        if (ENTITY_LIMIT != -1 && countSurroundingEntities() > ENTITY_LIMIT)
            return
        
        val spawnLocation = pos.location.add(0.5, 1.0, 0.5)
        val entity = if (keepNbt) {
            EntityUtils.deserializeAndSpawn(entityData!!, spawnLocation, NBTUtils::removeItemData).bukkitEntity
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
        
        private val idleBar = object : VerticalBar(3) {
            override val barItem = DefaultGuiItems.BAR_GREEN
            override fun modifyItemBuilder(itemBuilder: ItemBuilder) =
                itemBuilder.setDisplayName(Component.translatable(
                    "menu.machines.mob_duplicator.idle",
                    NamedTextColor.GRAY,
                    Component.text(maxIdleTime - timePassed)
                ))
        }
        
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
            .addIngredient('p', idleBar)
            .build()
        
        fun updateIdleBar() {
            idleBar.percentage = timePassed.toDouble() / maxIdleTime.toDouble()
        }
        
        private inner class ToggleNBTModeItem : AbstractItem() {
            
            override fun getItemProvider(): ItemProvider {
                return (if (keepNbt) GuiItems.NBT_BTN_ON else GuiItems.NBT_BTN_OFF).model.clientsideProvider
            }
            
            override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) {
                keepNbt = !keepNbt
                notifyWindows()
                
                timePassed = 0
                updateIdleBar()
                
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
            }
            
        }
        
    }
    
    private companion object PatronSkulls {
        
        private const val PATRON_SKULLS_URL = "https://xenondevs.xyz/nova/patron_skulls.json"
        val patreonSkulls = ArrayList<SkullBuilder>()
        
        init {
            runAsyncTask {
                val url = URL(PATRON_SKULLS_URL)
                val array = url.openConnection().getInputStream().bufferedReader().use(JsonParser::parseReader)
                if (array is JsonArray) {
                    array.asSequence()
                        .filter(JsonElement::isString)
                        .forEach {
                            patreonSkulls += SkullBuilder(HeadTexture(it.asString))
                        }
                }
            }
        }
        
    }
    
}