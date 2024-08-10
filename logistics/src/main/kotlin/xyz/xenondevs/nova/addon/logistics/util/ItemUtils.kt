package xyz.xenondevs.nova.addon.logistics.util

import org.bukkit.inventory.ItemStack
import xyz.xenondevs.nova.world.item.behavior.ItemFilterContainer
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.ItemFilter
import xyz.xenondevs.nova.util.item.novaItem

fun <T : ItemFilter<T>> ItemStack.setItemFilter(itemFilter: T) {
    val container = novaItem?.getBehavior<ItemFilterContainer<T>>()
        ?: throw IllegalArgumentException("ItemStack does not have an ItemFilterContainer behavior")
    
    container.setFilter(this, itemFilter)
}

fun <T : ItemFilter<T>> ItemStack.getItemFilter(): T? {
    val container = novaItem?.getBehavior<ItemFilterContainer<T>>()
        ?: throw IllegalArgumentException("ItemStack does not have an ItemFilterContainer behavior")
    
    return container.getFilter(this)
}

fun ItemStack.isItemFilter(): Boolean {
    return novaItem?.hasBehavior<ItemFilterContainer<*>>() ?: false
}