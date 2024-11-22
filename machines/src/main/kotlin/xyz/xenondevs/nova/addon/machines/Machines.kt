package xyz.xenondevs.nova.addon.machines

import xyz.xenondevs.nova.addon.Addon
import xyz.xenondevs.nova.update.ProjectDistributor

object Machines : Addon() {
    
    override val projectDistributors = listOf(ProjectDistributor.hangar("xenondevs/Machines"))
    
}