package xyz.xenondevs.nova.addon.simpleupgrades

import xyz.xenondevs.nova.addon.Addon
import xyz.xenondevs.nova.update.ProjectDistributor

internal object SimpleUpgrades : Addon() {
    
    override val projectDistributors = listOf(
        ProjectDistributor.modrinth("nova-simple-upgrades"),
        ProjectDistributor.hangar("xenondevs/Simple-Upgrades")
    )
    
}