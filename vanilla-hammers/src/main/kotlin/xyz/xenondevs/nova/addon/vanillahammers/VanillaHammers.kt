package xyz.xenondevs.nova.addon.vanillahammers

import xyz.xenondevs.nova.addon.Addon
import xyz.xenondevs.nova.update.ProjectDistributor

object VanillaHammers : Addon() {
    
    override val projectDistributors = listOf(ProjectDistributor.hangar("xenondevs/Vanilla-Hammers"))
    
}