package xyz.xenondevs.nova.addon.logistics.registry

import xyz.xenondevs.nova.addon.logistics.Logistics.registerItemFilterType
import xyz.xenondevs.nova.addon.logistics.item.itemfilter.NbtItemFilter
import xyz.xenondevs.nova.addon.logistics.item.itemfilter.TypeItemFilter
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage

@Init(stage = InitStage.PRE_PACK)
object ItemFilterTypes {
    
    init {
        registerItemFilterType("type_item_filter", TypeItemFilter)
        registerItemFilterType("nbt_item_filter", NbtItemFilter)
    }
    
}