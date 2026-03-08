@file:Suppress("DEPRECATION")

package xyz.xenondevs.nova.addon.machines.item

import com.google.common.collect.Sets
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.nova.addon.machines.Machines
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.BlockInteract
import xyz.xenondevs.nova.context.intention.EntityInteract
import xyz.xenondevs.nova.util.EntityUtils
import xyz.xenondevs.nova.util.Key
import xyz.xenondevs.nova.util.advance
import xyz.xenondevs.nova.util.center
import xyz.xenondevs.nova.util.component.adventure.withoutPreFormatting
import xyz.xenondevs.nova.util.item.retrieveData
import xyz.xenondevs.nova.util.item.storeData
import xyz.xenondevs.nova.world.InteractionResult
import xyz.xenondevs.nova.world.item.ItemAction
import xyz.xenondevs.nova.world.item.behavior.ItemBehavior
import kotlin.reflect.full.isSubclassOf

private val DATA_KEY = Key(Machines, "entitydata")
private val TYPE_KEY = Key(Machines, "entitytype")

private val MOB_ENTITY_TYPES: Set<EntityType> = EntityType.entries.filterTo(HashSet()) { it.entityClass?.kotlin?.isSubclassOf(Mob::class) == true }
private val WHITELISTED_ENTITY_TYPES: Provider<Set<EntityType>> = Items.MOB_CATCHER.config.entry<Set<EntityType>>("entity_whitelist")
val DISALLOWED_ENTITY_TYPES: Set<EntityType> by WHITELISTED_ENTITY_TYPES.map { whitelistedEntities ->
    // disallowed are all entities that are not mobs and are not whitelisted
    val whitelistedMobs = Sets.intersection(MOB_ENTITY_TYPES, whitelistedEntities)
    val blacklistedEntities = Sets.difference(EntityType.entries.toSet(), whitelistedMobs)
    return@map blacklistedEntities
}

object MobCatcherBehavior : ItemBehavior {
    
    override fun useOnEntity(itemStack: ItemStack, entity: Entity, ctx: Context<EntityInteract>): InteractionResult {
        if (entity.type in DISALLOWED_ENTITY_TYPES || getEntityData(itemStack) != null)
            return InteractionResult.Pass
        
        val newCatcher = Items.MOB_CATCHER.createItemStack()
        absorbEntity(newCatcher, entity)
        return InteractionResult.Success(swing = true, action = ItemAction.ConvertOne(newCatcher))
    }
    
    override fun useOnBlock(itemStack: ItemStack, block: Block, ctx: Context<BlockInteract>): InteractionResult {
        val data = getEntityData(itemStack)
            ?: return InteractionResult.Pass
        val clickedFace = ctx[BlockInteract.CLICKED_BLOCK_FACE] ?: BlockFace.UP
        val targetLoc = block.center.advance(clickedFace, 1.0)
        
        EntityUtils.deserializeAndSpawn(data, targetLoc, disallowedEntityTypes = DISALLOWED_ENTITY_TYPES)
        
        return InteractionResult.Success(swing = true, action = ItemAction.ConvertOne(Items.MOB_CATCHER.createItemStack()))
    }
    
    fun getEntityData(itemStack: ItemStack): ByteArray? = itemStack.retrieveData(DATA_KEY)
    
    fun getEntityType(itemStack: ItemStack): EntityType? = itemStack.retrieveData(TYPE_KEY)
    
    private fun setEntityData(itemStack: ItemStack, type: EntityType, data: ByteArray) {
        itemStack.storeData(DATA_KEY, data)
        itemStack.storeData(TYPE_KEY, type)
    }
    
    private fun absorbEntity(itemStack: ItemStack, entity: Entity) {
        val data = EntityUtils.serialize(entity, true)
        setEntityData(itemStack, entity.type, data)
    }
    
    override fun modifyClientSideStack(player: Player?, server: ItemStack, client: ItemStack): ItemStack {
        val type = getEntityType(server) ?: return client
        val nmsType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("minecraft", type.key.key))
        
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