package xyz.xenondevs.nova.addon.machines.recipe.group

import net.kyori.adventure.text.Component
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.ItemBuilder
import xyz.xenondevs.nova.addon.machines.recipe.FluidInfuserRecipe
import xyz.xenondevs.nova.addon.machines.registry.Blocks
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.StaticFluidBar
import xyz.xenondevs.nova.ui.menu.explorer.recipes.createRecipeChoiceItem
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.RecipeGroup
import xyz.xenondevs.nova.world.item.DefaultGuiItems

private val FLUID_CAPACITY by Blocks.FLUID_INFUSER.config.entry<Long>("fluid_capacity")

object FluidInfuserRecipeGroup : RecipeGroup<FluidInfuserRecipe>() {
    
    override val texture = GuiTextures.RECIPE_FLUID_INFUSER
    override val icon = Items.FLUID_INFUSER.clientsideProvider
    override val priority = 6
    
    override fun createGui(recipe: FluidInfuserRecipe): Gui {
        val progressItem: ItemBuilder
        val translate: String
        if (recipe.mode == FluidInfuserRecipe.InfuserMode.INSERT) {
            progressItem = GuiItems.TP_FLUID_PROGRESS_LEFT_RIGHT.createClientsideItemBuilder()
            translate = "menu.machines.recipe.insert_fluid"
        } else {
            progressItem = GuiItems.TP_FLUID_PROGRESS_RIGHT_LEFT.createClientsideItemBuilder()
            translate = "menu.machines.recipe.extract_fluid"
        }
        
        progressItem.setName(Component.translatable(
            translate,
            Component.text(recipe.fluidAmount),
            Component.translatable(recipe.fluidType.localizedName)
        ))
        
        return Gui.builder()
            .setStructure(
                ". f . . . . . . .",
                ". f p i . t . r .",
                ". f . . . . . . .")
            .addIngredient('i', createRecipeChoiceItem(recipe.input))
            .addIngredient('r', createRecipeChoiceItem(listOf(recipe.result)))
            .addIngredient('p', progressItem)
            .addIngredient('f', StaticFluidBar(3, FLUID_CAPACITY, recipe.fluidType, recipe.fluidAmount, GuiItems.TP_FLUID_BAR_ITEMS))
            .addIngredient('t', DefaultGuiItems.INVISIBLE_ITEM
                .createClientsideItemBuilder()
                .setName(Component.translatable("menu.nova.recipe.time", Component.text(recipe.time / 20.0)))
            ).build()
    }
    
}