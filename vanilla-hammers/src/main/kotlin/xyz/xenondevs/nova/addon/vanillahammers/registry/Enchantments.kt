package xyz.xenondevs.nova.addon.vanillahammers.registry

import xyz.xenondevs.nova.addon.vanillahammers.VanillaHammers.enchantment
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage

@Init(stage = InitStage.PRE_PACK)
object Enchantments {
    
    val CURSE_OF_GIGANTISM by enchantment("curse_of_gigantism") {
        tableLevelRequirement(0..30)
        rarity(2)
        tableDiscoverable(true)
        treasure(true)
        tradeable(true)
        curse(true)
    }
    
}