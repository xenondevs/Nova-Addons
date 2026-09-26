package xyz.xenondevs.nova.addon.simpleupgrades.registry

import xyz.xenondevs.cbf.Cbf
import xyz.xenondevs.nova.addon.simpleupgrades.SimpleUpgrades
import xyz.xenondevs.nova.addon.simpleupgrades.UpgradeType
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.serialization.cbf.registerRegistrySerializers

/**
 * Contains the default upgrade types.
 */
@Init(stage = InitStage.PRE_PACK)
object UpgradeTypes {
    
    internal val upgradeTypeRegistry = SimpleUpgrades.registry<UpgradeType<*>>("upgrade_type")
    
    /**
     * The speed upgrade type, making things faster (`simple_upgrades:speed`).
     * Multiplicative.
     */
    val SPEED = SimpleUpgrades.upgradeType<Double>("speed") {
        item(Items.SPEED_UPGRADE)
        placeholder(GuiItems.SPEED_UPGRADE_PLACEHOLDER)
        defaultValue(1.0)
    }
    
    /**
     * The efficiency upgrade type, making things use less energy (`simple_upgrades:efficiency`).
     * Multiplicative.
     */
    val EFFICIENCY = SimpleUpgrades.upgradeType<Double>("efficiency") {
        item(Items.EFFICIENCY_UPGRADE)
        placeholder(GuiItems.EFFICIENCY_UPGRADE_PLACEHOLDER)
        defaultValue(1.0)
    }
    
    /**
     * The energy upgrade type, making things able to store more energy (`simple_upgrades:energy`).
     * Multiplicative.
     */
    val ENERGY = SimpleUpgrades.upgradeType<Double>("energy") {
        item(Items.ENERGY_UPGRADE)
        placeholder(GuiItems.ENERGY_UPGRADE_PLACEHOLDER)
        defaultValue(1.0)
    }
    
    /**
     * The fluid upgrade type, making things able to store more fluid (`simple_upgrades:fluid`).
     * Multiplicative.
     */
    val FLUID = SimpleUpgrades.upgradeType<Double>("fluid") {
        item(Items.FLUID_UPGRADE)
        placeholder(GuiItems.FLUID_UPGRADE_PLACEHOLDER)
        defaultValue(1.0)
    }
    
    /**
     * The range upgrade type, making things able to reach further (`simple_upgrades:range`).
     * Additive.
     */
    val RANGE = SimpleUpgrades.upgradeType<Int>("range") {
        item(Items.RANGE_UPGRADE)
        placeholder(GuiItems.RANGE_UPGRADE_PLACEHOLDER)
        defaultValue(0)
    }
    
    init {
        Cbf.registerRegistrySerializers(upgradeTypeRegistry)
    }
    
}