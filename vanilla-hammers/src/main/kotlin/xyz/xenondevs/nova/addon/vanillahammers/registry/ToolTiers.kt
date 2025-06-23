package xyz.xenondevs.nova.addon.vanillahammers.registry

import xyz.xenondevs.nova.addon.vanillahammers.VanillaHammers.registerToolTier
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage

@Init(stage = InitStage.PRE_PACK)
object ToolTiers {
    
    val EMERALD = registerToolTier("emerald")
    val LAPIS = registerToolTier("lapis")
    val QUARTZ = registerToolTier("quartz")
    val OBSIDIAN = registerToolTier("obsidian")
    val PRISMARINE = registerToolTier("prismarine")
    val FIERY = registerToolTier("fiery")
    val SLIME = registerToolTier("slime")
    val ENDER = registerToolTier("ender")
    
}