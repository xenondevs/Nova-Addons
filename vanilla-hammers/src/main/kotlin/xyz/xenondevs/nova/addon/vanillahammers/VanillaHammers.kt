package xyz.xenondevs.nova.addon.vanillahammers

import org.bukkit.plugin.java.JavaPlugin
import xyz.xenondevs.nova.addon.Addon
import xyz.xenondevs.nova.update.ProjectDistributor

object VanillaHammers : JavaPlugin(), Addon {
    
    override val projectDistributors = listOf(ProjectDistributor.hangar("xenondevs/Vanilla-Hammers"))
    
}