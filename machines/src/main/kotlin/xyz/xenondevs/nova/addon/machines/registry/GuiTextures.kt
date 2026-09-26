package xyz.xenondevs.nova.addon.machines.registry

import net.kyori.adventure.text.Component
import org.joml.Vector2i
import xyz.xenondevs.nova.addon.machines.Machines.guiTexture
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.ui.overlay.guitexture.GuiTexture.TitlePosition.Alignment

@Init(stage = InitStage.PRE_PACK)
object GuiTextures {
    
    val CENTER_BAR = guiTexture("center_bar_3") {
        title { dynamicLine(Alignment.CENTER) }
    }
    val CHUNK_LOADER = guiTexture("chunk_loader") {
        title { dynamicLine(Alignment.CENTER) }
    }
    val GENERIC_3X3_WITH_BAR = guiTexture("generic_3x3_with_bar") {
        title { dynamicLine(Alignment.CENTER) }
    }
    val GENERIC_1X1_WITH_BAR = guiTexture("generic_1x1_with_bar") {
        title { dynamicLine(Alignment.CENTER) }
    }
    val LAVA_GENERATOR = guiTexture("lava_generator") {
        title { dynamicLine(Alignment.CENTER) }
    }
    val MECHANICAL_PRESS = guiTexture("mechanical_press") {}
    val PULVERIZER = guiTexture("pulverizer") {
        title { dynamicLine(Alignment.CENTER) }
    }
    val ELECTRIC_FURNACE = guiTexture("electric_furnace") {}
    val AUTO_FISHER = guiTexture("auto_fisher") {}
    val BREEDER = guiTexture("breeder") {
        title { dynamicLine(Alignment.CENTER) }
    }
    val MOB_KILLER = guiTexture("mob_killer") {
        title { dynamicLine(Alignment.CENTER) }
    }
    val MOB_DUPLICATOR = guiTexture("mob_duplicator") {
        title { dynamicLine(Alignment.CENTER) }
    }
    val PLANTER = guiTexture("planter") {}
    val HARVESTER = guiTexture("harvester") {}
    val FERTILIZER = guiTexture("fertilizer") {
        title { dynamicLine(Alignment.CENTER, offset = Vector2i(9, 0)) }
    }
    val COBBLESTONE_GENERATOR = guiTexture("cobblestone_generator") {}
    val FLUID_INFUSER = guiTexture("fluid_infuser") {
        title { dynamicLine(Alignment.CENTER) }
    }
    val FREEZER = guiTexture("freezer") {
        title { dynamicLine(Alignment.CENTER) }
    }
    val PUMP = guiTexture("pump") {}
    val STAR_COLLECTOR = guiTexture("star_collector") {
        title { dynamicLine(Alignment.CENTER, offset = Vector2i(-18, 0)) }
    }
    val CRYSTALLIZER = guiTexture("crystallizer") {
        title { dynamicLine(Alignment.CENTER) }
    }
    val TREE_FACTORY = guiTexture("tree_factory") {
        title { dynamicLine(Alignment.CENTER) }
    }
    val CONFIGURE_POTION = guiTexture("configure_potion") {
        title { staticLine(Component.translatable("menu.machines.electric_brewing_stand.configure_potion"), Alignment.LEFT, Vector2i(21, 18)) }
    }
    val POTION_PICKER = guiTexture("potion_picker") {
        title { staticLine(Component.translatable("menu.machines.potion_configurator.pick_effect"), Alignment.LEFT, Vector2i(21, 18)) }
    }
    val ELECTRIC_BREWING_STAND = guiTexture("electric_brewing_stand") {}
    val AUTO_CRAFTER_RECIPE = guiTexture("auto_crafter/recipe") {}
    val AUTO_CRAFTER_INVENTORY = guiTexture("auto_crafter/inventory") {}
    val RECIPE_PULVERIZER = guiTexture("recipe/pulverizer") {
        title { dynamicLine(Alignment.CENTER, Vector2i(0, 34)) }
    }
    val RECIPE_PRESS = guiTexture("recipe/press") {
        title { dynamicLine(Alignment.CENTER, Vector2i(0, 34)) }
    }
    val RECIPE_FLUID_INFUSER = guiTexture("recipe/fluid_infuser") {
        title { dynamicLine(Alignment.CENTER, Vector2i(0, 34)) }
    }
    val RECIPE_FREEZER = guiTexture("recipe/freezer") {
        title { dynamicLine(Alignment.CENTER, Vector2i(0, 34)) }
    }
    val RECIPE_STAR_COLLECTOR = guiTexture("recipe/star_collector") {
        title { dynamicLine(Alignment.CENTER, Vector2i(0, 34)) }
    }
    val RECIPE_COBBLESTONE_GENERATOR = guiTexture("recipe/cobblestone_generator") {
        title { dynamicLine(Alignment.CENTER, Vector2i(0, 34)) }
    }
    val RECIPE_ELECTRIC_BREWING_STAND = guiTexture("recipe/electric_brewing_stand") {
        title { dynamicLine(Alignment.CENTER, Vector2i(0, 34)) }
    }
    
}