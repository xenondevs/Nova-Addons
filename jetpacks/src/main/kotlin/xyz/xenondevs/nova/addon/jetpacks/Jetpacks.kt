package xyz.xenondevs.nova.addon.jetpacks

import org.bukkit.plugin.java.JavaPlugin
import xyz.xenondevs.nova.addon.Addon
import xyz.xenondevs.nova.update.ProjectDistributor

object Jetpacks : JavaPlugin(), Addon {
    
    override val projectDistributors = listOf(ProjectDistributor.hangar("xenondevs/Jetpacks"))
    
}