package xyz.xenondevs.nova.addon.machines.recipe.group

import net.kyori.adventure.text.Component
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.builder.ItemBuilder
import xyz.xenondevs.invui.item.builder.setDisplayName
import xyz.xenondevs.nova.addon.machines.recipe.FluidInfuserRecipe
import xyz.xenondevs.nova.addon.machines.registry.Blocks
import xyz.xenondevs.nova.addon.machines.registry.GuiMaterials
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.data.config.entry
import xyz.xenondevs.nova.item.DefaultGuiItems
import xyz.xenondevs.nova.ui.StaticFluidBar
import xyz.xenondevs.nova.ui.menu.item.recipes.createRecipeChoiceItem
import xyz.xenondevs.nova.ui.menu.item.recipes.group.RecipeGroup

private val FLUID_CAPACITY by Blocks.FLUID_INFUSER.config.entry<Long>("fluid_capacity")

object FluidInfuserRecipeGroup : RecipeGroup<FluidInfuserRecipe>() {
    
    override val texture = GuiTextures.RECIPE_FLUID_INFUSER
    override val icon = Items.FLUID_INFUSER.basicClientsideProvider
    override val priority = 6
    
    override fun createGui(recipe: FluidInfuserRecipe): Gui {
        val progressItem: ItemBuilder
        val translate: String
        if (recipe.mode == FluidInfuserRecipe.InfuserMode.INSERT) {
            progressItem = GuiMaterials.TP_FLUID_PROGRESS_LEFT_RIGHT.createItemBuilder()
            translate = "menu.machines.recipe.insert_fluid"
        } else {
            progressItem = GuiMaterials.TP_FLUID_PROGRESS_RIGHT_LEFT.createItemBuilder()
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
            .addIngredient('f', StaticFluidBar(recipe.fluidType, recipe.fluidAmount, FLUID_CAPACITY, 3))
            .addIngredient('t', DefaultGuiItems.TP_STOPWATCH
                .createClientsideItemBuilder()
                .setDisplayName(Component.translatable("menu.nova.recipe.time", Component.text(recipe.time / 20.0)))
            ).build()
    }
    
}