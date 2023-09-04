package xyz.xenondevs.nova.addon.jetpacks.registry

import org.bukkit.inventory.EquipmentSlot
import xyz.xenondevs.nova.addon.jetpacks.JetpackTier
import xyz.xenondevs.nova.addon.jetpacks.Jetpacks
import xyz.xenondevs.nova.addon.jetpacks.item.JetpackBehavior
import xyz.xenondevs.nova.addon.registry.ItemRegistry
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.item.behavior.Chargeable
import xyz.xenondevs.nova.item.behavior.Wearable

@Init(stage = InitStage.PRE_PACK)
object Items : ItemRegistry by Jetpacks.registry {
    
    val BASIC_JETPACK = registerItem("basic_jetpack", Chargeable, Wearable(EquipmentSlot.CHEST), JetpackBehavior(JetpackTier.BASIC))
    val ADVANCED_JETPACK = registerItem("advanced_jetpack", Chargeable, Wearable(EquipmentSlot.CHEST), JetpackBehavior(JetpackTier.ADVANCED))
    val ELITE_JETPACK = registerItem("elite_jetpack", Chargeable, Wearable(EquipmentSlot.CHEST), JetpackBehavior(JetpackTier.ELITE))
    val ULTIMATE_JETPACK = registerItem("ultimate_jetpack", Chargeable, Wearable(EquipmentSlot.CHEST), JetpackBehavior(JetpackTier.ULTIMATE))
    
    val ARMORED_BASIC_JETPACK = registerItem("armored_basic_jetpack", Chargeable, Wearable(EquipmentSlot.CHEST), JetpackBehavior(JetpackTier.BASIC))
    val ARMORED_ADVANCED_JETPACK = registerItem("armored_advanced_jetpack", Chargeable, Wearable(EquipmentSlot.CHEST), JetpackBehavior(JetpackTier.ADVANCED))
    val ARMORED_ELITE_JETPACK = registerItem("armored_elite_jetpack", Chargeable, Wearable(EquipmentSlot.CHEST), JetpackBehavior(JetpackTier.ELITE))
    val ARMORED_ULTIMATE_JETPACK = registerItem("armored_ultimate_jetpack", Chargeable, Wearable(EquipmentSlot.CHEST), JetpackBehavior(JetpackTier.ULTIMATE))
    
}