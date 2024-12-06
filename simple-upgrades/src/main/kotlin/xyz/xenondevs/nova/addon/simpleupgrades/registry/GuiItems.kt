package xyz.xenondevs.nova.addon.simpleupgrades.registry

import xyz.xenondevs.nova.addon.registry.ItemRegistry
import xyz.xenondevs.nova.addon.simpleupgrades.SimpleUpgrades
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.world.item.NovaItem

@Init(stage = InitStage.PRE_PACK)
object GuiItems : ItemRegistry by SimpleUpgrades.registry {
    
    val SPEED_UPGRADE = guiItem("speed_upgrade")
    val EFFICIENCY_UPGRADE = guiItem("efficiency_upgrade")
    val ENERGY_UPGRADE = guiItem("energy_upgrade")
    val RANGE_UPGRADE = guiItem("range_upgrade")
    val FLUID_UPGRADE = guiItem("fluid_upgrade")
    
    val UPGRADES_BTN = guiItem("btn/upgrades", "menu.simple_upgrades.upgrades")
    
    private fun guiItem(name: String, localizedName: String = ""): NovaItem = item("gui/$name") {
        localizedName(localizedName)
        hidden(true)
        modelDefinition {
            model = buildModel { createGuiModel(background = true, stretched = false, "item/$name") }
        }
    }
    
}