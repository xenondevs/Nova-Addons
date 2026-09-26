package xyz.xenondevs.nova.addon.simpleupgrades.registry

import xyz.xenondevs.nova.addon.simpleupgrades.SimpleUpgrades.item
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage

@Init(stage = InitStage.PRE_PACK)
object GuiItems {
    
    val UPGRADES_BTN = guiItem("btn/upgrades", "menu.simple_upgrades.upgrades")
    
    val SPEED_UPGRADE_PLACEHOLDER = tpGuiItem("placeholder/speed_upgrade")
    val EFFICIENCY_UPGRADE_PLACEHOLDER = tpGuiItem("placeholder/efficiency_upgrade")
    val ENERGY_UPGRADE_PLACEHOLDER = tpGuiItem("placeholder/energy_upgrade")
    val FLUID_UPGRADE_PLACEHOLDER = tpGuiItem("placeholder/fluid_upgrade")
    val RANGE_UPGRADE_PLACEHOLDER = tpGuiItem("placeholder/range_upgrade")
    
    private fun guiItem(name: String, localizedName: String? = null) = item("gui/$name") {
        if (localizedName == null) name(null) else localizedName(localizedName)
        hidden(true)
        modelDefinition {
            model = buildModel { createGuiModel(background = true, stretched = false, "item/$name") }
        }
    }
    
    private fun tpGuiItem(name: String, localizedName: String? = null) = item("gui/$name") {
        if (localizedName == null) name(null) else localizedName(localizedName)
        hidden(true)
        modelDefinition {
            model = buildModel { createGuiModel(background = false, stretched = false, "item/$name") }
        }
    }
    
}