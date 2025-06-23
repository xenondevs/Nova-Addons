@file:Suppress("DEPRECATION")

package xyz.xenondevs.nova.addon.machines.item

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.nova.addon.machines.Machines
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.util.EntityUtils
import xyz.xenondevs.nova.util.Key
import xyz.xenondevs.nova.util.addPrioritized
import xyz.xenondevs.nova.util.component.adventure.withoutPreFormatting
import xyz.xenondevs.nova.util.getTargetLocation
import xyz.xenondevs.nova.util.item.retrieveData
import xyz.xenondevs.nova.util.item.storeData
import xyz.xenondevs.nova.world.item.behavior.ItemBehavior
import xyz.xenondevs.nova.world.player.WrappedPlayerInteractEvent
import xyz.xenondevs.nova.world.player.swingHandEventless

private val DATA_KEY = Key(Machines, "entitydata")
private val TYPE_KEY = Key(Machines, "entitytype")
private val TIME_KEY = Key(Machines, "filltime")

private val BLACKLISTED_ENTITY_TYPES by Items.MOB_CATCHER.config.entry<Set<EntityType>>("entity_blacklist")

object MobCatcherBehavior : ItemBehavior {
    
    override fun handleEntityInteract(player: Player, itemStack: ItemStack, clicked: Entity, event: PlayerInteractAtEntityEvent) {
        if (clicked is Mob
            && clicked.type !in BLACKLISTED_ENTITY_TYPES
            && ProtectionManager.canInteractWithEntity(player, clicked, itemStack)
            && getEntityData(itemStack) == null
        ) {
            val newCatcher = Items.MOB_CATCHER.createItemStack()
            absorbEntity(newCatcher, clicked)
            
            player.inventory.getItem(event.hand).amount -= 1
            player.inventory.addPrioritized(event.hand, newCatcher)
            
            player.swingHandEventless(event.hand)
            
            event.isCancelled = true
        }
    }
    
    override fun handleInteract(player: Player, itemStack: ItemStack, action: Action, wrappedEvent: WrappedPlayerInteractEvent) {
        if (wrappedEvent.actionPerformed)
            return
        
        val event = wrappedEvent.event
        if (action == Action.RIGHT_CLICK_BLOCK) {
            // Adds a small delay to prevent players from spamming the item
            if (System.currentTimeMillis() - (itemStack.retrieveData<Long>(TIME_KEY) ?: -1) < 50) return
            
            val data = getEntityData(itemStack)
            if (data != null) {
                val location = player.eyeLocation.getTargetLocation(0.25, 8.0)
                
                if (ProtectionManager.canUseItem(player, itemStack, location)) {
                    player.inventory.getItem(event.hand!!).amount -= 1
                    player.inventory.addPrioritized(event.hand!!, Items.MOB_CATCHER.createItemStack())
                    
                    EntityUtils.deserializeAndSpawn(data, location)
                    player.swingHandEventless(event.hand ?: EquipmentSlot.HAND)
                    
                    event.isCancelled = true
                }
            }
        }
    }
    
    fun getEntityData(itemStack: ItemStack): ByteArray? = itemStack.retrieveData(DATA_KEY)
    
    fun getEntityType(itemStack: ItemStack): EntityType? = itemStack.retrieveData(TYPE_KEY)
    
    private fun setEntityData(itemStack: ItemStack, type: EntityType, data: ByteArray) {
        itemStack.storeData(DATA_KEY, data)
        itemStack.storeData(TYPE_KEY, type)
        itemStack.storeData(TIME_KEY, System.currentTimeMillis())
    }
    
    private fun absorbEntity(itemStack: ItemStack, entity: Entity) {
        val data = EntityUtils.serialize(entity, true)
        setEntityData(itemStack, entity.type, data)
    }
    
    override fun modifyClientSideStack(player: Player?, server: ItemStack, client: ItemStack): ItemStack {
        val type = getEntityType(server) ?: return client
        val nmsType = BuiltInRegistries.ENTITY_TYPE.getValue(ResourceLocation.fromNamespaceAndPath("minecraft", type.key.key))
        
        val lore = client.lore() ?: mutableListOf()
        lore += Component.translatable(
            "item.machines.mob_catcher.type",
            NamedTextColor.DARK_GRAY,
            Component.translatable(nmsType.descriptionId, NamedTextColor.YELLOW)
        ).withoutPreFormatting()
        client.lore(lore)
        
        return client
    }
    
}