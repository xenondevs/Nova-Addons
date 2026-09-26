package xyz.xenondevs.nova.addon.machines.recipe.group.hardcoded

import net.kyori.adventure.text.Component
import xyz.xenondevs.commons.provider.provider
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.ui.menu.explorer.recipes.createRecipeChoiceItem
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.RecipeGroup
import xyz.xenondevs.nova.ui.menu.fluidBar
import xyz.xenondevs.nova.ui.menu.itemProvider
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.world.item.guiItemProvider

object CobblestoneGeneratorRecipeGroup : RecipeGroup<CobblestoneGeneratorRecipe>() {
    
    override val priority = 7
    override val texture = GuiTextures.RECIPE_COBBLESTONE_GENERATOR
    override val icon = Items.COBBLESTONE_GENERATOR.guiItemProvider
    
    override fun createGui(recipe: CobblestoneGeneratorRecipe) = gui(
        ". w l . . . . . .",
        ". w l . > . r . .",
        ". w l . m . . . ."
    ) {
        'r' by createRecipeChoiceItem(listOf(recipe.result))
        'm' by recipe.mode.uiItem.guiItemProvider
        '>' by itemProvider(GuiItems.FLUID_PROGRESS_LEFT_RIGHT.guiItemProvider) {
            name by Component.translatable("menu.machines.recipe.cobblestone_generator.${recipe.mode.name.lowercase()}")
        }
        'w' by fluidBar(provider(FluidType.WATER), provider(1000L), provider(1000L))
        'l' by fluidBar(provider(FluidType.LAVA), provider(1000L), provider(1000L))
    }
    
}
