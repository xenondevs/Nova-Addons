package xyz.xenondevs.nova.addon.simpleupgrades.registry

import xyz.xenondevs.nova.addon.registry.UpgradeTypeRegistry
import xyz.xenondevs.nova.addon.registry.registerUpgradeType
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.addon.simpleupgrades.SimpleUpgrades

@Init
object UpgradeTypes : UpgradeTypeRegistry by SimpleUpgrades.registry {
    
    val SPEED = registerUpgradeType<Double>("speed", Items.SPEED_UPGRADE, GUIMaterials.SPEED_UPGRADE)
    val EFFICIENCY = registerUpgradeType<Double>("efficiency", Items.EFFICIENCY_UPGRADE, GUIMaterials.EFFICIENCY_UPGRADE)
    val ENERGY = registerUpgradeType<Double>("energy", Items.ENERGY_UPGRADE, GUIMaterials.ENERGY_UPGRADE)
    val FLUID = registerUpgradeType<Double>("fluid", Items.FLUID_UPGRADE, GUIMaterials.FLUID_UPGRADE)
    val RANGE = registerUpgradeType<Int>("range", Items.RANGE_UPGRADE, GUIMaterials.RANGE_UPGRADE)
    
}