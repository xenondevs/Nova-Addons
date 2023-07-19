package xyz.xenondevs.nova.addon.vanillahammers.registry

import xyz.xenondevs.nova.addon.registry.ItemRegistry
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.item.behavior.Damageable
import xyz.xenondevs.nova.item.behavior.Enchantable
import xyz.xenondevs.nova.item.behavior.FireResistant
import xyz.xenondevs.nova.item.behavior.Fuel
import xyz.xenondevs.nova.item.behavior.Tool
import xyz.xenondevs.nova.addon.vanillahammers.VanillaHammers
import xyz.xenondevs.nova.addon.vanillahammers.item.Hammer

@Init
object Items : ItemRegistry by VanillaHammers.registry {
    
    val WOODEN_HAMMER = registerItem("wooden_hammer", Tool, Hammer, Damageable, Enchantable, Fuel)
    val STONE_HAMMER = registerItem("stone_hammer", Tool, Hammer, Damageable, Enchantable)
    val IRON_HAMMER = registerItem("iron_hammer", Tool, Hammer, Damageable, Enchantable)
    val GOLDEN_HAMMER = registerItem("golden_hammer", Tool, Hammer, Damageable, Enchantable)
    val DIAMOND_HAMMER = registerItem("diamond_hammer", Tool, Hammer, Damageable, Enchantable)
    val NETHERITE_HAMMER = registerItem("netherite_hammer", Tool, Hammer, Damageable, Enchantable, FireResistant)
    val EMERALD_HAMMER = registerItem("emerald_hammer", Tool, Hammer, Damageable, Enchantable)
    val LAPIS_HAMMER = registerItem("lapis_hammer", Tool, Hammer, Damageable, Enchantable)
    val QUARTZ_HAMMER = registerItem("quartz_hammer", Tool, Hammer, Damageable, Enchantable)
    val OBSIDIAN_HAMMER = registerItem("obsidian_hammer", Tool, Hammer, Damageable, Enchantable)
    val PRISMARINE_HAMMER = registerItem("prismarine_hammer", Tool, Hammer, Damageable, Enchantable)
    val FIERY_HAMMER = registerItem("fiery_hammer", Tool, Hammer, Damageable, Enchantable, FireResistant)
    val SLIME_HAMMER = registerItem("slime_hammer", Tool, Hammer, Damageable, Enchantable)
    val ENDER_HAMMER = registerItem("ender_hammer", Tool, Hammer, Damageable, Enchantable)
    
}