package xyz.xenondevs.nova.addon.simpleupgrades.registry

import org.bukkit.Material
import xyz.xenondevs.nova.addon.registry.AddonHolder
import xyz.xenondevs.nova.addon.registry.ItemRegistry
import xyz.xenondevs.nova.addon.simpleupgrades.SimpleUpgrades
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.world.item.NovaItem

@Init(stage = InitStage.PRE_PACK)
object GuiItems : ItemRegistry, AddonHolder by SimpleUpgrades {
    
    val SPEED_UPGRADE = guiItem("speed_upgrade")
    val EFFICIENCY_UPGRADE = guiItem("efficiency_upgrade")
    val ENERGY_UPGRADE = guiItem("energy_upgrade")
    val RANGE_UPGRADE = guiItem("range_upgrade")
    val FLUID_UPGRADE = guiItem("fluid_upgrade")
    
    val UPGRADES_BTN = guiItem("btn/upgrades", "menu.simple_upgrades.upgrades")
    
    private fun guiItem(name: String, localizedName: String = ""): NovaItem = item("gui/$name") {
        localizedName(localizedName)
        hidden(true)
        models {
            itemType(Material.SHULKER_SHELL)
            selectModel { createGuiModel(background = true, stretched = false, "item/$name") }
        }
    }
    
}