package xyz.xenondevs.nova.addon.jetpacks.registry

import org.bukkit.Material
import org.bukkit.inventory.EquipmentSlot
import xyz.xenondevs.nova.addon.jetpacks.JetpackTier
import xyz.xenondevs.nova.addon.jetpacks.Jetpacks
import xyz.xenondevs.nova.addon.jetpacks.item.JetpackBehavior
import xyz.xenondevs.nova.addon.registry.ItemRegistry
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.world.item.behavior.Chargeable
import xyz.xenondevs.nova.world.item.behavior.Wearable

@Init(stage = InitStage.PRE_PACK)
object Items : ItemRegistry by Jetpacks.registry {
    
    val BASIC_JETPACK = item("basic_jetpack") {
        behaviors(Chargeable, Wearable(null, EquipmentSlot.CHEST), JetpackBehavior(JetpackTier.BASIC))
        models { selectModel { getModel("attachment/basic_jetpack") } }
    }
    val ADVANCED_JETPACK = item("advanced_jetpack") {
        behaviors(Chargeable, Wearable(null, EquipmentSlot.CHEST), JetpackBehavior(JetpackTier.ADVANCED))
        models { selectModel { getModel("attachment/advanced_jetpack") } }
    }
    val ELITE_JETPACK = item("elite_jetpack") {
        behaviors(Chargeable, Wearable(null, EquipmentSlot.CHEST), JetpackBehavior(JetpackTier.ELITE))
        models { selectModel { getModel("attachment/elite_jetpack") } }
    }
    val ULTIMATE_JETPACK = item("ultimate_jetpack") {
        behaviors(Chargeable, Wearable(null, EquipmentSlot.CHEST), JetpackBehavior(JetpackTier.ULTIMATE))
        models { selectModel { getModel("attachment/ultimate_jetpack") } }
    }
    val ARMORED_BASIC_JETPACK = item("armored_basic_jetpack") {
        behaviors(Chargeable, Wearable(null, EquipmentSlot.CHEST), JetpackBehavior(JetpackTier.BASIC))
        models { itemType(Material.LEATHER_CHESTPLATE) }
    }
    val ARMORED_ADVANCED_JETPACK = item("armored_advanced_jetpack") {
        behaviors(Chargeable, Wearable(null, EquipmentSlot.CHEST), JetpackBehavior(JetpackTier.ADVANCED))
        models { itemType(Material.IRON_CHESTPLATE) }
    }
    val ARMORED_ELITE_JETPACK = item("armored_elite_jetpack") {
        behaviors(Chargeable, Wearable(null, EquipmentSlot.CHEST), JetpackBehavior(JetpackTier.ELITE))
        models { itemType(Material.DIAMOND_CHESTPLATE) }
    }
    val ARMORED_ULTIMATE_JETPACK = item("armored_ultimate_jetpack") {
        behaviors(Chargeable, Wearable(null, EquipmentSlot.CHEST), JetpackBehavior(JetpackTier.ULTIMATE))
        models { itemType(Material.NETHERITE_CHESTPLATE) }
    }
    
}