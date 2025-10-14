package xyz.xenondevs.nova.addon.logistics.item.itemfilter

import org.bukkit.inventory.ItemStack

private class HashableItemStack(val itemStack: ItemStack) {
    
    override fun hashCode(): Int = itemStack.hashCode()
    
    override fun equals(other: Any?): Boolean {
        return other is HashableItemStack && itemStack.isSimilar(other.itemStack)
    }
    
}

class NbtItemFilter(
    override val items: List<ItemStack>,
    override val whitelist: Boolean
) : LogisticsItemFilter() {
    
    override val type = NbtItemFilter
    
    private val itemSet = items.mapTo(HashSet(items.size)) { HashableItemStack(it) }
    
    override fun allows(itemStack: ItemStack): Boolean =
        (HashableItemStack(itemStack) in itemSet) == whitelist
    
    companion object : LogisticsItemFilterSerializer(::NbtItemFilter)
    
    override fun toString(): String {
        return "NbtItemFilter(items=$items, whitelist=$whitelist)"
    }
    
}