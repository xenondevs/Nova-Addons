package xyz.xenondevs.nova.addon.machines.recipe.group.hardcoded

import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.ui.menu.explorer.recipes.createRecipeChoiceItem
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.RecipeGroup

object StarCollectorRecipeGroup : RecipeGroup<StarCollectorRecipe>() {
    
    override val priority = 9
    override val texture = GuiTextures.RECIPE_STAR_COLLECTOR
    override val icon = Items.STAR_COLLECTOR.clientsideProvider
    
    override fun createGui(recipe: StarCollectorRecipe): Gui {
        return Gui.builder()
            .setStructure(
                ". . . . . . . . .",
                ". . . . . . . r .",
                ". . . . . . . . .")
            .addIngredient('r', createRecipeChoiceItem(listOf(recipe.result)))
            .build()
    }
    
}