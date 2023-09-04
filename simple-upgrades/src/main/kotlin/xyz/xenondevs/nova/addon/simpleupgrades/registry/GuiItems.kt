package xyz.xenondevs.nova.addon.simpleupgrades.registry

import xyz.xenondevs.nova.addon.registry.ItemRegistry
import xyz.xenondevs.nova.addon.simpleupgrades.SimpleUpgrades
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage

@Init(stage = InitStage.PRE_PACK)
object GuiItems : ItemRegistry by SimpleUpgrades.registry {
    
    val SPEED_UPGRADE = registerUnnamedHiddenItem("gui_speed_upgrade")
    val EFFICIENCY_UPGRADE = registerUnnamedHiddenItem("gui_efficiency_upgrade")
    val ENERGY_UPGRADE = registerUnnamedHiddenItem("gui_energy_upgrade")
    val RANGE_UPGRADE = registerUnnamedHiddenItem("gui_range_upgrade")
    val FLUID_UPGRADE = registerUnnamedHiddenItem("gui_fluid_upgrade")
    
}