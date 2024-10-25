package xyz.xenondevs.nova.addon.machines

import org.bukkit.plugin.java.JavaPlugin
import xyz.xenondevs.nova.addon.Addon
import xyz.xenondevs.nova.update.ProjectDistributor

object Machines : JavaPlugin(), Addon {
    
    override val projectDistributors = listOf(ProjectDistributor.hangar("xenondevs/Machines"))
    
}