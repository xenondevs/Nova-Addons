package xyz.xenondevs.nova.addon.simpleupgrades.registry

import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypeRegistry.registerUpgradeType
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage

@Init(stage = InitStage.POST_PACK_PRE_WORLD)
object UpgradeTypes {
    
    val SPEED = registerUpgradeType<Double>("speed", Items.SPEED_UPGRADE, GuiItems.SPEED_UPGRADE)
    val EFFICIENCY = registerUpgradeType<Double>("efficiency", Items.EFFICIENCY_UPGRADE, GuiItems.EFFICIENCY_UPGRADE)
    val ENERGY = registerUpgradeType<Double>("energy", Items.ENERGY_UPGRADE, GuiItems.ENERGY_UPGRADE)
    val FLUID = registerUpgradeType<Double>("fluid", Items.FLUID_UPGRADE, GuiItems.FLUID_UPGRADE)
    val RANGE = registerUpgradeType<Int>("range", Items.RANGE_UPGRADE, GuiItems.RANGE_UPGRADE)
    
}