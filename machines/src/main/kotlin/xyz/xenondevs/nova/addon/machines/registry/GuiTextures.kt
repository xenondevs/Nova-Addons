package xyz.xenondevs.nova.addon.machines.registry

import xyz.xenondevs.nova.addon.machines.Machines
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.ui.overlay.character.gui.GuiTexture

@Init
object GuiTextures {
    
    val CONFIGURE_POTION = GuiTexture.of(Machines, "configure_potion")
    val ELECTRIC_BREWING_STAND = GuiTexture.of(Machines, "electric_brewing_stand")
    val RECIPE_PULVERIZER = GuiTexture.of(Machines, "recipe_pulverizer")
    val RECIPE_PRESS = GuiTexture.of(Machines, "recipe_press")
    val RECIPE_FLUID_INFUSER = GuiTexture.of(Machines, "recipe_fluid_infuser")
    val RECIPE_FREEZER = GuiTexture.of(Machines, "recipe_freezer")
    val RECIPE_STAR_COLLECTOR = GuiTexture.of(Machines, "recipe_star_collector")
    val RECIPE_COBBLESTONE_GENERATOR = GuiTexture.of(Machines, "recipe_cobblestone_generator")
    val RECIPE_ELECTRIC_BREWING_STAND = GuiTexture.of(Machines, "recipe_electric_brewing_stand")
    
}