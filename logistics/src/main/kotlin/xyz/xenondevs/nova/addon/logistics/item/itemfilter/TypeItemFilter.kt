package xyz.xenondevs.nova.addon.logistics.item.itemfilter

import org.bukkit.inventory.ItemStack
import xyz.xenondevs.nova.util.item.novaItem

class TypeItemFilter(
    override val items: List<ItemStack>,
    override val whitelist: Boolean
) : LogisticsItemFilter() {
    
    override val type = TypeItemFilter
    
    private val vanillaTypes = items.mapNotNullTo(HashSet()) { if (it.novaItem == null) it.type else null }
    private val novaTypes = items.mapNotNullTo(HashSet()) { it.novaItem }
    
    override fun allows(itemStack: ItemStack): Boolean {
        val novaItem = itemStack.novaItem
        if (novaItem != null)
            return (novaItem in novaTypes) == whitelist
        return (itemStack.type in vanillaTypes) == whitelist
    }
    
    companion object : LogisticsItemFilterSerializer(::TypeItemFilter)
    
}