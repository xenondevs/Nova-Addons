package xyz.xenondevs.nova.addon.vanillahammers.registry

import xyz.xenondevs.nova.addon.registry.ToolTierRegistry
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.addon.vanillahammers.VanillaHammers

@Init
object ToolTiers : ToolTierRegistry by VanillaHammers.registry {
    
    val EMERALD = registerToolTier("emerald")
    val LAPIS = registerToolTier("lapis")
    val QUARTZ = registerToolTier("quartz")
    val OBSIDIAN = registerToolTier("obsidian")
    val PRISMARINE = registerToolTier("prismarine")
    val FIERY = registerToolTier("fiery")
    val SLIME = registerToolTier("slime")
    val ENDER = registerToolTier("ender")
    
}