package xyz.xenondevs.nova.addon.simpleupgrades

import org.bukkit.plugin.java.JavaPlugin
import xyz.xenondevs.nova.addon.Addon
import xyz.xenondevs.nova.update.ProjectDistributor

object SimpleUpgrades : JavaPlugin(), Addon {
    
    override val projectDistributors = listOf(ProjectDistributor.hangar("xenondevs/Simple-Upgrades"))
    
}