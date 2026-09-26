package xyz.xenondevs.nova.addon.simpleupgrades.registry

import net.kyori.adventure.text.Component
import org.joml.Vector2i
import xyz.xenondevs.nova.addon.simpleupgrades.SimpleUpgrades.guiTexture
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.ui.overlay.guitexture.GuiTexture.TitlePosition.Alignment

@Init(stage = InitStage.PRE_PACK)
object GuiTextures {
    
    val UPGRADES = guiTexture("upgrades") {
        title {
            staticLine(Component.translatable("menu.simple_upgrades.upgrades"), Alignment.LEFT, Vector2i(21, 18))
        }
        inventoryLabel(true)
        texture { path("gui/upgrades") }
    }
    
}