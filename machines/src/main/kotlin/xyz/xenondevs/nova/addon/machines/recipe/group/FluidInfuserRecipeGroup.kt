package xyz.xenondevs.nova.addon.machines.recipe.group

import net.kyori.adventure.text.Component
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.builder.ItemBuilder
import xyz.xenondevs.invui.item.builder.setDisplayName
import xyz.xenondevs.nova.addon.machines.recipe.FluidInfuserRecipe
import xyz.xenondevs.nova.addon.machines.registry.Blocks
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.item.DefaultGuiItems
import xyz.xenondevs.nova.ui.menu.StaticFluidBar
import xyz.xenondevs.nova.ui.menu.explorer.recipes.createRecipeChoiceItem
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.RecipeGroup

private val FLUID_CAPACITY by Blocks.FLUID_INFUSER.config.entry<Long>("fluid_capacity")

object FluidInfuserRecipeGroup : RecipeGroup<FluidInfuserRecipe>() {
    
    override val texture = GuiTextures.RECIPE_FLUID_INFUSER
    override val icon = Items.FLUID_INFUSER.model.clientsideProvider
    override val priority = 6
    
    override fun createGui(recipe: FluidInfuserRecipe): Gui {
        val progressItem: ItemBuilder
        val translate: String
        if (recipe.mode == FluidInfuserRecipe.InfuserMode.INSERT) {
            progressItem = GuiItems.TP_FLUID_PROGRESS_LEFT_RIGHT.model.createClientsideItemBuilder()
            translate = "menu.machines.recipe.insert_fluid"
        } else {
            progressItem = GuiItems.TP_FLUID_PROGRESS_RIGHT_LEFT.model.createClientsideItemBuilder()
            translate = "menu.machines.recipe.extract_fluid"
        }
        
        progressItem.setDisplayName(Component.translatable(
            translate,
            Component.text(recipe.fluidAmount),
            Component.translatable(recipe.fluidType.localizedName)
        ))
        
        return Gui.normal()
            .setStructure(
                ". f . t . . . . .",
                ". f p i . . . r .",
                ". f . . . . . . .")
            .addIngredient('i', createRecipeChoiceItem(recipe.input))
            .addIngredient('r', createRecipeChoiceItem(listOf(recipe.result)))
            .addIngredient('p', progressItem)
            .addIngredient('f', StaticFluidBar(3, FLUID_CAPACITY, recipe.fluidType, recipe.fluidAmount))
            .addIngredient('t', DefaultGuiItems.TP_STOPWATCH.model
                .createClientsideItemBuilder()
                .setDisplayName(Component.translatable("menu.nova.recipe.time", Component.text(recipe.time / 20.0)))
            ).build()
    }
    
}