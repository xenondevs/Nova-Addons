package xyz.xenondevs.nova.addon.logistics

import xyz.xenondevs.nova.addon.Addon
import xyz.xenondevs.nova.addon.logistics.item.findCorrectFilterItem
import xyz.xenondevs.nova.tileentity.network.item.ItemFilter
import xyz.xenondevs.nova.tileentity.network.item.saveFilterConfig
import xyz.xenondevs.nova.update.ProjectDistributor
import java.util.logging.Logger

lateinit var LOGGER: Logger

object Logistics : Addon() {
    
    override val projectDistributors = listOf(ProjectDistributor.hangar("xenondevs/Logistics"))
    
    override fun init() {
        LOGGER = logger
        ItemFilter.creatorFun = { filter ->
            findCorrectFilterItem(filter).createItemStack().apply { saveFilterConfig(filter) }
        }
    }
    
}