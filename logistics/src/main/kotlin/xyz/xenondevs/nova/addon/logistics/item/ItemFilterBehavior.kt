package xyz.xenondevs.nova.addon.logistics.item

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.commons.collections.mapToArray
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.nova.addon.logistics.Logistics
import xyz.xenondevs.nova.addon.logistics.gui.itemfilter.ItemFilterMenu
import xyz.xenondevs.nova.addon.logistics.item.itemfilter.LogisticsItemFilter
import xyz.xenondevs.nova.addon.logistics.item.itemfilter.NbtItemFilter
import xyz.xenondevs.nova.addon.logistics.item.itemfilter.TypeItemFilter
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.ItemUse
import xyz.xenondevs.nova.serialization.cbf.NamespacedCompound
import xyz.xenondevs.nova.util.Key
import xyz.xenondevs.nova.util.component.adventure.withoutPreFormatting
import xyz.xenondevs.nova.util.item.ItemUtils
import xyz.xenondevs.nova.util.item.novaItem
import xyz.xenondevs.nova.util.item.retrieveData
import xyz.xenondevs.nova.util.item.storeData
import xyz.xenondevs.nova.util.item.takeUnlessEmpty
import xyz.xenondevs.nova.world.InteractionResult
import xyz.xenondevs.nova.world.item.ItemAction
import xyz.xenondevs.nova.world.item.NovaItem
import xyz.xenondevs.nova.world.item.behavior.ItemBehavior
import xyz.xenondevs.nova.world.item.behavior.ItemBehaviorFactory
import xyz.xenondevs.nova.world.item.behavior.ItemFilterContainer

private val ITEM_FILTER_KEY = Key(Logistics, "item_filter")

class ItemFilterBehavior(size: Provider<Int>) : ItemBehavior, ItemFilterContainer<LogisticsItemFilter> {
    
    val size: Int by size
    
    private val defaultFilter: Provider<LogisticsItemFilter> = 
        size.map { size -> TypeItemFilter(Array(size) { ItemStack.empty() }.asList(), true) }
    
    override val defaultCompound: Provider<NamespacedCompound> =
        defaultFilter.map { defaultFilter -> NamespacedCompound().apply { set(ITEM_FILTER_KEY, defaultFilter) } }
    
    override fun getFilter(itemStack: ItemStack): LogisticsItemFilter =
        itemStack.retrieveData(ITEM_FILTER_KEY) ?: defaultFilter.get()
    
    override fun setFilter(itemStack: ItemStack, filter: LogisticsItemFilter?) {
        if (filter != null) {
            itemStack.storeData(ITEM_FILTER_KEY, filter)
        } else {
            itemStack.storeData(ITEM_FILTER_KEY, defaultFilter.get())
        }
    }
    
    override fun use(itemStack: ItemStack, ctx: Context<ItemUse>): InteractionResult {
        val player = ctx[ItemUse.SOURCE_PLAYER]
            ?: return InteractionResult.Pass
        
        val filter = getFilter(itemStack)
        val filterItems = filter.items.mapToArray { it.takeUnlessEmpty() }
        val whitelist = filter.whitelist
        val nbt = filter is NbtItemFilter
        ItemFilterMenu(player, itemStack.novaItem!!.name!!, itemStack, filterItems, whitelist, nbt).open()
        
        return InteractionResult.Success(swing = false, action = ItemAction.None)
    }
    
    override fun modifyClientSideStack(player: Player?, server: ItemStack, client: ItemStack): ItemStack {
        val itemFilter = getFilter(server)
        val whitelist = itemFilter.whitelist
        
        val lore = ArrayList<Component>()
        
        lore += Component.translatable(
            "item.logistics.item_filter.lore.type",
            NamedTextColor.GRAY,
            Component.translatable(
                "item.logistics.item_filter.lore.type.${if (whitelist) "whitelist" else "blacklist"}",
                if (whitelist) NamedTextColor.GREEN else NamedTextColor.RED
            )
        )
        
        val nbt = itemFilter is NbtItemFilter
        lore += Component.translatable(
            "item.logistics.item_filter.lore.nbt",
            NamedTextColor.GRAY,
            Component.translatable(
                "item.logistics.item_filter.lore.nbt.${if (nbt) "on" else "off"}",
                if (nbt) NamedTextColor.GREEN else NamedTextColor.RED
            )
        )
        
        lore += Component.empty()
        
        lore += Component.translatable(
            "item.logistics.item_filter.lore.contents",
            NamedTextColor.GRAY,
            Component.text(itemFilter.items.count { !it.isEmpty })
        )
        
        itemFilter.items
            .filter { !it.isEmpty }
            .forEach { lore += Component.text("- ", NamedTextColor.GRAY).append(ItemUtils.getName(it)) }
        
        client.lore((client.lore() ?: emptyList()) + lore.map(Component::withoutPreFormatting))
        
        return client
    }
    
    companion object : ItemBehaviorFactory<ItemFilterBehavior> {
        override fun create(item: NovaItem) = ItemFilterBehavior(item.config.entry("size"))
    }
    
}