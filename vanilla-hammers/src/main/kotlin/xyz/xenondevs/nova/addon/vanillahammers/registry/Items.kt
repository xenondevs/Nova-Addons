package xyz.xenondevs.nova.addon.vanillahammers.registry

import xyz.xenondevs.nova.addon.vanillahammers.VanillaHammers.item
import xyz.xenondevs.nova.addon.vanillahammers.item.Hammer
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.world.item.behavior.Damageable
import xyz.xenondevs.nova.world.item.behavior.Enchantable
import xyz.xenondevs.nova.world.item.behavior.FireResistant
import xyz.xenondevs.nova.world.item.behavior.Fuel
import xyz.xenondevs.nova.world.item.behavior.Tool

@Init(stage = InitStage.PRE_PACK)
object Items {
    
    val WOODEN_HAMMER = item("wooden_hammer") {
        behaviors(Tool(), Hammer, Damageable(), Enchantable(), Fuel())
        maxStackSize(1)
    }
    val STONE_HAMMER = item("stone_hammer") {
        behaviors(Tool(), Hammer, Damageable(), Enchantable())
        maxStackSize(1)
    }
    val IRON_HAMMER = item("iron_hammer") {
        behaviors(Tool(), Hammer, Damageable(), Enchantable())
        maxStackSize(1)
    }
    val GOLDEN_HAMMER = item("golden_hammer") {
        behaviors(Tool(), Hammer, Damageable(), Enchantable())
        maxStackSize(1)
    }
    val DIAMOND_HAMMER = item("diamond_hammer") {
        behaviors(Tool(), Hammer, Damageable(), Enchantable())
        maxStackSize(1)
    }
    val NETHERITE_HAMMER = item("netherite_hammer") {
        behaviors(Tool(), Hammer, Damageable(), Enchantable(), FireResistant)
        maxStackSize(1)
    }
    val EMERALD_HAMMER = item("emerald_hammer") {
        behaviors(Tool(), Hammer, Damageable(), Enchantable())
        maxStackSize(1)
    }
    val LAPIS_HAMMER = item("lapis_hammer") {
        behaviors(Tool(), Hammer, Damageable(), Enchantable())
        maxStackSize(1)
    }
    val QUARTZ_HAMMER = item("quartz_hammer") {
        behaviors(Tool(), Hammer, Damageable(), Enchantable())
        maxStackSize(1)
    }
    val OBSIDIAN_HAMMER = item("obsidian_hammer") {
        behaviors(Tool(), Hammer, Damageable(), Enchantable())
        maxStackSize(1)
    }
    val PRISMARINE_HAMMER = item("prismarine_hammer") {
        behaviors(Tool(), Hammer, Damageable(), Enchantable())
        maxStackSize(1)
    }
    val FIERY_HAMMER = item("fiery_hammer") {
        behaviors(Tool(), Hammer, Damageable(), Enchantable(), FireResistant)
        maxStackSize(1)
    }
    val SLIME_HAMMER = item("slime_hammer") {
        behaviors(Tool(), Hammer, Damageable(), Enchantable())
        maxStackSize(1)
    }
    val ENDER_HAMMER = item("ender_hammer") {
        behaviors(Tool(), Hammer, Damageable(), Enchantable())
        maxStackSize(1)
    }
    
}