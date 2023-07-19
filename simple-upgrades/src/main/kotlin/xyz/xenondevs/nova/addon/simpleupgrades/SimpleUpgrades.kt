package xyz.xenondevs.nova.addon.simpleupgrades

import xyz.xenondevs.nova.addon.Addon
import xyz.xenondevs.nova.update.ProjectDistributor

object SimpleUpgrades : Addon() {
    
    override val projectDistributors = listOf(ProjectDistributor.hangar("xenondevs/Simple-Upgrades"))
    
}