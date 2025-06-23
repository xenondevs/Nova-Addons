package xyz.xenondevs.nova.addon.simpleupgrades.registry

import xyz.xenondevs.nova.addon.simpleupgrades.SimpleUpgrades.registerItem
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage

@Init(stage = InitStage.PRE_PACK)
object Items {
    
    val SPEED_UPGRADE = registerItem("speed_upgrade")
    val EFFICIENCY_UPGRADE = registerItem("efficiency_upgrade")
    val ENERGY_UPGRADE = registerItem("energy_upgrade")
    val RANGE_UPGRADE = registerItem("range_upgrade")
    val FLUID_UPGRADE = registerItem("fluid_upgrade")
    
}