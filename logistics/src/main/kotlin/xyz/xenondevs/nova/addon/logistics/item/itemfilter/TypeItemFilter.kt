package xyz.xenondevs.nova.addon.logistics.item.itemfilter

import org.bukkit.inventory.ItemStack
import xyz.xenondevs.nova.addon.logistics.registry.ItemFilterTypes
import xyz.xenondevs.nova.registry.registryEntrySetOf
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.ItemFilterType
import xyz.xenondevs.nova.world.item.itemTypeEntry

class TypeItemFilter(
    override val items: List<ItemStack>,
    override val whitelist: Boolean
) : LogisticsItemFilter() {
    
    override val type: ItemFilterType<LogisticsItemFilter>
        get() = ItemFilterTypes.TYPE.get()
    
    private val types = registryEntrySetOf(items.map { it.itemTypeEntry })
    
    override fun allows(itemStack: ItemStack): Boolean =
        (itemStack.itemTypeEntry in types) == whitelist
    
    override fun toString(): String {
        return "TypeItemFilter(whitelist=$whitelist, items=$items)"
    }
    
    companion object : LogisticsItemFilterSerializer(::TypeItemFilter)
    
}