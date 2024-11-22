package xyz.xenondevs.nova.addon.logistics

import xyz.xenondevs.nova.addon.Addon
import xyz.xenondevs.nova.update.ProjectDistributor

object Logistics : Addon() {
    
    override val projectDistributors = listOf(ProjectDistributor.hangar("xenondevs/Logistics"))
    
}