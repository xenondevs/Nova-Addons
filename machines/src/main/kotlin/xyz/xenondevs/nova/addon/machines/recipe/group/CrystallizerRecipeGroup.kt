package xyz.xenondevs.nova.addon.machines.recipe.group

import xyz.xenondevs.nova.addon.machines.recipe.CrystallizerRecipe
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.ConversionRecipeGroup
import xyz.xenondevs.nova.ui.overlay.guitexture.DefaultGuiTextures

object CrystallizerRecipeGroup : ConversionRecipeGroup<CrystallizerRecipe>() {
    override val icon = Items.CRYSTALLIZER.clientsideProvider
    override val priority = 10
    override val texture = DefaultGuiTextures.RECIPE_CONVERSION
}