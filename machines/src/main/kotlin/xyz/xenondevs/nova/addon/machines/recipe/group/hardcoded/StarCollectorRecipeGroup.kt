package xyz.xenondevs.nova.addon.machines.recipe.group.hardcoded

import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.ui.menu.item.recipes.createRecipeChoiceItem
import xyz.xenondevs.nova.ui.menu.item.recipes.group.RecipeGroup

object StarCollectorRecipeGroup : RecipeGroup<StarCollectorRecipe>() {
    
    override val priority = 9
    override val texture = GuiTextures.RECIPE_STAR_COLLECTOR
    override val icon = Items.STAR_COLLECTOR.basicClientsideProvider
    
    override fun createGui(recipe: StarCollectorRecipe): Gui {
        return Gui.normal()
            .setStructure(
                ". . . . . . . . .",
                ". . . . . . . r .",
                ". . . . . . . . .")
            .addIngredient('r', createRecipeChoiceItem(listOf(recipe.result)))
            .build()
    }
    
}