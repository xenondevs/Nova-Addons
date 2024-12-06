package xyz.xenondevs.nova.addon.machines.recipe.group.hardcoded

import net.kyori.adventure.text.Component
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.ui.menu.StaticFluidBar
import xyz.xenondevs.nova.ui.menu.addIngredient
import xyz.xenondevs.nova.ui.menu.explorer.recipes.createRecipeChoiceItem
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.RecipeGroup
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType

object CobblestoneGeneratorRecipeGroup : RecipeGroup<CobblestoneGeneratorRecipe>() {
    
    override val priority = 7
    override val texture = GuiTextures.RECIPE_COBBLESTONE_GENERATOR
    override val icon = Items.COBBLESTONE_GENERATOR.clientsideProvider
    
    override fun createGui(recipe: CobblestoneGeneratorRecipe): Gui {
        val progressItem = GuiItems.TP_FLUID_PROGRESS_LEFT_RIGHT
            .createClientsideItemBuilder()
            .setName(Component.translatable("menu.machines.recipe.cobblestone_generator.${recipe.mode.name.lowercase()}"))
        
        return Gui.normal()
            .setStructure(
                ". w l . . . . . .",
                ". w l . > . r . .",
                ". w l . m . . . .")
            .addIngredient('r', createRecipeChoiceItem(listOf(recipe.result)))
            .addIngredient('m', recipe.mode.uiItem)
            .addIngredient('>', progressItem)
            .addIngredient('w', StaticFluidBar(3, 1000, FluidType.WATER, 1000))
            .addIngredient('l', StaticFluidBar(3, 1000, FluidType.LAVA, 1000))
            .build()
        
    }
    
}