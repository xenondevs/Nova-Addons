package xyz.xenondevs.nova.addon.logistics.item.itemfilter

import org.bukkit.inventory.ItemStack
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.ItemFilterSerializer

abstract class LogisticsItemFilterSerializer(
    private val constructor: (List<ItemStack>, Boolean) -> LogisticsItemFilter
) : ItemFilterSerializer<LogisticsItemFilter> {
    
    override fun serialize(filter: LogisticsItemFilter): Compound {
        val compound = Compound()
        compound["items"] = filter.items
        compound["whitelist"] = filter.whitelist
        return compound
    }
    
    override fun deserialize(compound: Compound): LogisticsItemFilter =
        constructor(compound["items"]!!, compound["whitelist"]!!)
    
    override fun copy(filter: LogisticsItemFilter): LogisticsItemFilter {
        return constructor(filter.items, filter.whitelist)
    }
    
}