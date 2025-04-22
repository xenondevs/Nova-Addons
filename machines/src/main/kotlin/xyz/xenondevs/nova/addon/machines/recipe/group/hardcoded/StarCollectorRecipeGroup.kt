package xyz.xenondevs.nova.addon.machines.recipe.group.hardcoded

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.ItemWrapper
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.ui.menu.explorer.recipes.createRecipeChoiceItem
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.RecipeGroup

object StarCollectorRecipeGroup : RecipeGroup<StarCollectorRecipe>() {
    
    override val priority = 9
    override val texture = GuiTextures.RECIPE_STAR_COLLECTOR
    override val icon = ItemWrapper(ItemStack(Material.AIR))//Items.STAR_COLLECTOR.model.basicClientsideProvider
    
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