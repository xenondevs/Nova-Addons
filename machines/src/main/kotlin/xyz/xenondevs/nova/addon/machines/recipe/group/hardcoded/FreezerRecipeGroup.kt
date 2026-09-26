package xyz.xenondevs.nova.addon.machines.recipe.group.hardcoded

import xyz.xenondevs.commons.provider.provider
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.ui.menu.explorer.recipes.createRecipeChoiceItem
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.RecipeGroup
import xyz.xenondevs.nova.ui.menu.fluidBar
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.world.item.guiItemProvider

object FreezerRecipeGroup : RecipeGroup<FreezerRecipe>() {
    
    override val priority = 8
    override val texture = GuiTextures.RECIPE_FREEZER
    override val icon = Items.FREEZER.guiItemProvider
    
    override fun createGui(recipe: FreezerRecipe) = gui(
        ". w . . . . . . .",
        ". w . . . . r . .",
        ". w . . . . . . ."
    ) {
        'r' by createRecipeChoiceItem(listOf(recipe.result))
        'w' by fluidBar(
            provider(FluidType.WATER),
            provider(1000L * recipe.mode.maxCostMultiplier),
            provider(100_000L)
        )
    }
    
}
