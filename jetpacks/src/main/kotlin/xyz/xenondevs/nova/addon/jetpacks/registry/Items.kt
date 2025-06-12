package xyz.xenondevs.nova.addon.jetpacks.registry

import org.bukkit.inventory.EquipmentSlot
import xyz.xenondevs.nova.addon.jetpacks.JetpackTier
import xyz.xenondevs.nova.addon.jetpacks.Jetpacks.item
import xyz.xenondevs.nova.addon.jetpacks.item.JetpackBehavior
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.world.item.Equipment
import xyz.xenondevs.nova.world.item.behavior.Chargeable
import xyz.xenondevs.nova.world.item.behavior.Equippable

@Init(stage = InitStage.PRE_PACK)
object Items {
    
    val BASIC_JETPACK = item("basic_jetpack") {
        behaviors(Chargeable(), Equippable(null, EquipmentSlot.CHEST), JetpackBehavior(JetpackTier.BASIC))
        modelDefinition { model = buildModel { getModel("attachment/basic_jetpack") } }
        maxStackSize(1)
    }
    val ADVANCED_JETPACK = item("advanced_jetpack") {
        behaviors(Chargeable(), Equippable(null, EquipmentSlot.CHEST), JetpackBehavior(JetpackTier.ADVANCED))
        modelDefinition { model = buildModel { getModel("attachment/advanced_jetpack") } }
        maxStackSize(1)
    }
    val ELITE_JETPACK = item("elite_jetpack") {
        behaviors(Chargeable(), Equippable(null, EquipmentSlot.CHEST), JetpackBehavior(JetpackTier.ELITE))
        modelDefinition { model = buildModel { getModel("attachment/elite_jetpack") } }
        maxStackSize(1)
    }
    val ULTIMATE_JETPACK = item("ultimate_jetpack") {
        behaviors(Chargeable(), Equippable(null, EquipmentSlot.CHEST), JetpackBehavior(JetpackTier.ULTIMATE))
        modelDefinition { model = buildModel { getModel("attachment/ultimate_jetpack") } }
        maxStackSize(1)
    }
    val ARMORED_BASIC_JETPACK = item("armored_basic_jetpack") {
        behaviors(Chargeable(), Equippable(Equipment.LEATHER, EquipmentSlot.CHEST), JetpackBehavior(JetpackTier.BASIC))
        maxStackSize(1)
    }
    val ARMORED_ADVANCED_JETPACK = item("armored_advanced_jetpack") {
        behaviors(Chargeable(), Equippable(Equipment.IRON, EquipmentSlot.CHEST), JetpackBehavior(JetpackTier.ADVANCED))
        maxStackSize(1)
    }
    val ARMORED_ELITE_JETPACK = item("armored_elite_jetpack") {
        behaviors(Chargeable(), Equippable(Equipment.DIAMOND, EquipmentSlot.CHEST), JetpackBehavior(JetpackTier.ELITE))
        maxStackSize(1)
    }
    val ARMORED_ULTIMATE_JETPACK = item("armored_ultimate_jetpack") {
        behaviors(Chargeable(), Equippable(Equipment.NETHERITE, EquipmentSlot.CHEST), JetpackBehavior(JetpackTier.ULTIMATE))
        maxStackSize(1)
    }
    
}