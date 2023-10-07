package xyz.xenondevs.nova.addon.machines.recipe.group

import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.data.recipe.ConversionNovaRecipe
import xyz.xenondevs.nova.ui.menu.item.recipes.group.ConversionRecipeGroup

object PressingRecipeGroup : ConversionRecipeGroup<ConversionNovaRecipe>() {
    override val priority = 5
    override val icon = Items.MECHANICAL_PRESS.basicClientsideProvider
    override val texture = GuiTextures.RECIPE_PRESS
}