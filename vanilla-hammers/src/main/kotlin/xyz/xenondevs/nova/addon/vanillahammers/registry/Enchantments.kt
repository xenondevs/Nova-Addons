package xyz.xenondevs.nova.addon.vanillahammers.registry

import xyz.xenondevs.nova.addon.registry.EnchantmentCategoryRegistry
import xyz.xenondevs.nova.addon.registry.EnchantmentRegistry
import xyz.xenondevs.nova.addon.vanillahammers.VanillaHammers
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage

@Init(stage = InitStage.PRE_PACK)
object Enchantments : EnchantmentRegistry, EnchantmentCategoryRegistry by VanillaHammers.registry {
    
    val HAMMER_CATEGORY = registerEnchantmentCategory("hammer")
    
    val CURSE_OF_GIGANTISM = enchantment("curse_of_gigantism")
        .categories(HAMMER_CATEGORY)
        .tableDiscoverable(true)
        .tableLevelRequirement(0..30)
        .rarity(2)
        .curse(false)
        .register()
    
}