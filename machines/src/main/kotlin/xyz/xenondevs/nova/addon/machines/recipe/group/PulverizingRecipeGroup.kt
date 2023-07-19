package xyz.xenondevs.nova.addon.machines.recipe.group

import xyz.xenondevs.nova.addon.machines.recipe.PulverizerRecipe
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.ui.menu.item.recipes.group.ConversionRecipeGroup

object PulverizingRecipeGroup : ConversionRecipeGroup<PulverizerRecipe>() {
    override val priority = 4
    override val icon = Items.PULVERIZER.basicClientsideProvider
    override val texture = GuiTextures.RECIPE_PULVERIZER
}