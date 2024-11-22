package xyz.xenondevs.nova.addon.jetpacks

import xyz.xenondevs.nova.addon.Addon
import xyz.xenondevs.nova.update.ProjectDistributor

object Jetpacks : Addon() {
    
    override val projectDistributors = listOf(ProjectDistributor.hangar("xenondevs/Jetpacks"))
    
}