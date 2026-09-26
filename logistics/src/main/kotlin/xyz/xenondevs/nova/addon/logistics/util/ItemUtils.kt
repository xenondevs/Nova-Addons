package xyz.xenondevs.nova.addon.logistics.util

import org.bukkit.inventory.ItemStack
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.ItemFilter
import xyz.xenondevs.nova.world.item.behavior.ItemFilterContainer
import xyz.xenondevs.nova.world.item.getBehaviorOrThrow
import xyz.xenondevs.nova.world.item.hasBehavior
import xyz.xenondevs.nova.world.item.itemType

fun <T : ItemFilter<T>> ItemStack.setItemFilter(itemFilter: T): Unit =
    itemType.getBehaviorOrThrow<ItemFilterContainer<T>>().setFilter(this, itemFilter)

fun <T : ItemFilter<T>> ItemStack.getItemFilter(): T? =
    itemType.getBehaviorOrThrow<ItemFilterContainer<T>>().getFilter(this)

fun ItemStack.isItemFilter(): Boolean = 
    itemType.hasBehavior<ItemFilterContainer<*>>()