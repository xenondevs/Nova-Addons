package xyz.xenondevs.nova.addon.machines.recipe.group

import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.ConversionRecipeGroup
import xyz.xenondevs.nova.world.item.guiItemProvider
import xyz.xenondevs.nova.world.item.recipe.ConversionNovaRecipe

object PressingRecipeGroup : ConversionRecipeGroup<ConversionNovaRecipe>() {
    override val priority = 5
    override val icon = Items.MECHANICAL_PRESS.guiItemProvider
    override val texture = GuiTextures.RECIPE_PRESS
}