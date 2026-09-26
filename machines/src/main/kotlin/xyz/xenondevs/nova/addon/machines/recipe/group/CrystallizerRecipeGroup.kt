package xyz.xenondevs.nova.addon.machines.recipe.group

import xyz.xenondevs.nova.addon.machines.recipe.CrystallizerRecipe
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.ConversionRecipeGroup
import xyz.xenondevs.nova.ui.overlay.guitexture.DefaultGuiTextures
import xyz.xenondevs.nova.world.item.guiItemProvider

object CrystallizerRecipeGroup : ConversionRecipeGroup<CrystallizerRecipe>() {
    override val icon = Items.CRYSTALLIZER.guiItemProvider
    override val priority = 10
    override val texture = DefaultGuiTextures.RECIPE_CONVERSION
}