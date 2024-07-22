package xyz.xenondevs.nova.addon.logistics.item.itemfilter

import org.bukkit.inventory.ItemStack
import xyz.xenondevs.nova.addon.logistics.item.ItemFilterBehavior
import xyz.xenondevs.nova.addon.logistics.registry.Items
import xyz.xenondevs.nova.addon.logistics.util.setItemFilter
import xyz.xenondevs.nova.item.NovaItem
import xyz.xenondevs.nova.tileentity.network.type.item.ItemFilter

private val FILTER_TYPES: Map<Int, NovaItem> = setOf(
    Items.BASIC_ITEM_FILTER,
    Items.ADVANCED_ITEM_FILTER,
    Items.ELITE_ITEM_FILTER,
    Items.ULTIMATE_ITEM_FILTER
).associateBy { it.getBehavior<ItemFilterBehavior>().size }

abstract class LogisticsItemFilter : ItemFilter<LogisticsItemFilter> {
    
    abstract val items: List<ItemStack>
    abstract val whitelist: Boolean
    
    override fun toItemStack(): ItemStack {
        val itemStack = FILTER_TYPES[items.size]!!.createItemStack()
        itemStack.setItemFilter(this)
        return itemStack
    }
    
}