package xyz.xenondevs.nova.addon.logistics

import org.bukkit.plugin.java.JavaPlugin
import xyz.xenondevs.nova.addon.Addon
import xyz.xenondevs.nova.update.ProjectDistributor

object Logistics : JavaPlugin(), Addon {
    
    override val projectDistributors = listOf(ProjectDistributor.hangar("xenondevs/Logistics"))
    
}