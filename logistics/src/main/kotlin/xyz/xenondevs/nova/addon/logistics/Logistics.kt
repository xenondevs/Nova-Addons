package xyz.xenondevs.nova.addon.logistics

import xyz.xenondevs.nova.addon.Addon
import xyz.xenondevs.nova.update.ProjectDistributor
import java.util.logging.Logger

lateinit var LOGGER: Logger

object Logistics : Addon() {
    
    override val projectDistributors = listOf(ProjectDistributor.hangar("xenondevs/Logistics"))
    
    override fun init() {
        LOGGER = logger
    }
    
}