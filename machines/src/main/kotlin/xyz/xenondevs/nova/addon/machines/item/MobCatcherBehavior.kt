@file:Suppress("DEPRECATION")

package xyz.xenondevs.nova.addon.machines.item

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.nova.addon.machines.Machines
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.data.config.NovaConfig
import xyz.xenondevs.nova.data.config.configReloadable
import xyz.xenondevs.nova.data.serialization.cbf.NamespacedCompound
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.item.behavior.ItemBehavior
import xyz.xenondevs.nova.item.logic.PacketItemData
import xyz.xenondevs.nova.util.EntityUtils
import xyz.xenondevs.nova.util.addPrioritized
import xyz.xenondevs.nova.util.data.NamespacedKey
import xyz.xenondevs.nova.util.getTargetLocation
import xyz.xenondevs.nova.util.item.retrieveData
import xyz.xenondevs.nova.util.item.storeData

private val DATA_KEY = NamespacedKey(Machines, "entitydata")
private val TYPE_KEY = NamespacedKey(Machines, "entitytype")
private val TIME_KEY = NamespacedKey(Machines, "filltime")

private val BLACKLISTED_ENTITY_TYPES by configReloadable {
    NovaConfig[Items.MOB_CATCHER]
        .getStringList("entity_blacklist")
        .mapTo(HashSet(), EntityType::valueOf)
}

object MobCatcherBehavior : ItemBehavior() {
    
    override fun handleEntityInteract(player: Player, itemStack: ItemStack, clicked: Entity, event: PlayerInteractAtEntityEvent) {
        if (clicked is Mob
            && clicked.type !in BLACKLISTED_ENTITY_TYPES
            && ProtectionManager.canInteractWithEntity(player, clicked, itemStack).get()
            && getEntityData(itemStack) == null
        ) {
            val fakeDamageEvent = EntityDamageByEntityEvent(player, clicked, EntityDamageEvent.DamageCause.ENTITY_ATTACK, Double.MAX_VALUE)
            Bukkit.getPluginManager().callEvent(fakeDamageEvent)
            
            if (!fakeDamageEvent.isCancelled && fakeDamageEvent.damage != 0.0) {
                val newCatcher = Items.MOB_CATCHER.createItemStack()
                absorbEntity(newCatcher, clicked)
                
                player.inventory.getItem(event.hand)!!.amount -= 1
                player.inventory.addPrioritized(event.hand, newCatcher)
                
                if (event.hand == EquipmentSlot.HAND) player.swingMainHand() else player.swingOffHand()
                
                event.isCancelled = true
            }
            
        }
    }
    
    override fun handleInteract(player: Player, itemStack: ItemStack, action: Action, event: PlayerInteractEvent) {
        if (action == Action.RIGHT_CLICK_BLOCK) {
            // Adds a small delay to prevent players from spamming the item
            if (System.currentTimeMillis() - (itemStack.retrieveData<Long>(TIME_KEY) ?: -1 ) < 50) return
            
            val data = getEntityData(itemStack)
            if (data != null) {
                val location = player.eyeLocation.getTargetLocation(0.25, 8.0)
                
                if (ProtectionManager.canUseItem(player, itemStack, location).get()) {
                    player.inventory.getItem(event.hand!!)!!.amount -= 1
                    player.inventory.addPrioritized(event.hand!!, Items.MOB_CATCHER.createItemStack())
                    
                    
                    EntityUtils.deserializeAndSpawn(data, location)
                    if (event.hand == EquipmentSlot.HAND) player.swingMainHand() else player.swingOffHand()
                    
                    event.isCancelled = true
                }
            }
        }
    }
    
    fun getEntityData(itemStack: ItemStack): ByteArray? = itemStack.retrieveData(DATA_KEY)
    
    fun getEntityData(compound: NamespacedCompound): ByteArray? = compound[DATA_KEY]
    
    fun getEntityType(itemStack: ItemStack): EntityType? = itemStack.retrieveData(TYPE_KEY)
    
    fun getEntityType(compound: NamespacedCompound): EntityType? = compound[TYPE_KEY]
    
    private fun setEntityData(itemStack: ItemStack, type: EntityType, data: ByteArray) {
        itemStack.storeData(DATA_KEY, data)
        itemStack.storeData(TYPE_KEY, type)
        itemStack.storeData(TIME_KEY, System.currentTimeMillis())
    }
    
    private fun absorbEntity(itemStack: ItemStack, entity: Entity) {
        val data = EntityUtils.serialize(entity, true)
        setEntityData(itemStack, entity.type, data)
    }
    
    override fun updatePacketItemData(data: NamespacedCompound, itemData: PacketItemData) {
        val type = getEntityType(data) ?: return
        val nmsType = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation("minecraft", type.key.key))
        
        itemData.addLore(Component.translatable(
            "item.machines.mob_catcher.type",
            NamedTextColor.DARK_GRAY,
            Component.translatable(nmsType.descriptionId, NamedTextColor.YELLOW)
        ))
    }
    
}