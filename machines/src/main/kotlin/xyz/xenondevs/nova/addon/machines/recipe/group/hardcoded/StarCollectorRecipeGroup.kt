package xyz.xenondevs.nova.addon.machines.recipe.group.hardcoded

import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.ui.menu.explorer.recipes.createRecipeChoiceItem
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.RecipeGroup
import xyz.xenondevs.nova.world.item.guiItemProvider

object StarCollectorRecipeGroup : RecipeGroup<StarCollectorRecipe>() {
    
    override val priority = 9
    override val texture = GuiTextures.RECIPE_STAR_COLLECTOR
    override val icon = Items.STAR_COLLECTOR.guiItemProvider
    
    override fun createGui(recipe: StarCollectorRecipe) = gui(
        ". . . . . . . . .",
        ". . . . . . . r .",
        ". . . . . . . . ."
    ) {
        'r' by createRecipeChoiceItem(listOf(recipe.result))
    }
    
}