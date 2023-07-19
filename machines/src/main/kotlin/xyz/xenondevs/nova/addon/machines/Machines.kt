package xyz.xenondevs.nova.addon.machines

import xyz.xenondevs.nova.addon.Addon
import xyz.xenondevs.nova.update.ProjectDistributor
import java.util.logging.Logger

lateinit var LOGGER: Logger

object Machines : Addon() {
    
    override val projectDistributors = listOf(ProjectDistributor.hangar("xenondevs/Machines"))
    
    override fun init() {
        LOGGER = logger
    }
    
}