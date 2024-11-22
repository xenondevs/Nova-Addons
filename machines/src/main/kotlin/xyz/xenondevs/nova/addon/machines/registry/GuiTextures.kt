package xyz.xenondevs.nova.addon.machines.registry

import xyz.xenondevs.nova.addon.machines.Machines
import xyz.xenondevs.nova.addon.registry.GuiTextureRegistry
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage

@Init(stage = InitStage.PRE_PACK)
object GuiTextures : GuiTextureRegistry by Machines.registry {
    
    val CONFIGURE_POTION = guiTexture("configure_potion") { texture { path("gui/configure_potion") } }
    val ELECTRIC_BREWING_STAND = guiTexture("electric_brewing_stand") { texture { path("gui/electric_brewing_stand") } }
    val AUTO_CRAFTER_RECIPE = guiTexture("auto_crafter") { texture { path("gui/auto_crafter/recipe") } }
    val AUTO_CRAFTER_INVENTORY = guiTexture("auto_crafter_internal_inventory") { texture { path("gui/auto_crafter/inventory") } }
    val RECIPE_PULVERIZER = guiTexture("recipe_pulverizer") { texture { path("gui/recipe/pulverizer") } }
    val RECIPE_PRESS = guiTexture("recipe_press") { texture { path("gui/recipe/press") } }
    val RECIPE_FLUID_INFUSER = guiTexture("recipe_fluid_infuser") { texture { path("gui/recipe/fluid_infuser") } }
    val RECIPE_FREEZER = guiTexture("recipe_freezer") { texture { path("gui/recipe/freezer") } }
    val RECIPE_STAR_COLLECTOR = guiTexture("recipe_star_collector") { texture { path("gui/recipe/star_collector") } }
    val RECIPE_COBBLESTONE_GENERATOR = guiTexture("recipe_cobblestone_generator") { texture { path("gui/recipe/cobblestone_generator") } }
    val RECIPE_ELECTRIC_BREWING_STAND = guiTexture("recipe_electric_brewing_stand") { texture { path("gui/recipe/electric_brewing_stand") } }
    
}