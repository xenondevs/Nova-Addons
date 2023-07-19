package xyz.xenondevs.nova.addon.machines.recipe.group.hardcoded

import net.md_5.bungee.api.chat.TranslatableComponent
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.addon.machines.registry.GuiMaterials
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.tileentity.network.fluid.FluidType
import xyz.xenondevs.nova.ui.StaticFluidBar
import xyz.xenondevs.nova.ui.menu.item.recipes.createRecipeChoiceItem
import xyz.xenondevs.nova.ui.menu.item.recipes.group.RecipeGroup

object CobblestoneGeneratorRecipeGroup : RecipeGroup<CobblestoneGeneratorRecipe>() {
    
    override val priority = 7
    override val texture = GuiTextures.RECIPE_COBBLESTONE_GENERATOR
    override val icon = Items.COBBLESTONE_GENERATOR.basicClientsideProvider
    
    override fun createGui(recipe: CobblestoneGeneratorRecipe): Gui {
        val progressItem = GuiMaterials.TP_FLUID_PROGRESS_LEFT_RIGHT
            .createClientsideItemBuilder()
            .setDisplayName(TranslatableComponent("menu.machines.recipe.cobblestone_generator.${recipe.mode.name.lowercase()}"))
        
        return Gui.normal()
            .setStructure(
                ". w l . . . . . .",
                ". w l . > . r . .",
                ". w l . m . . . .")
            .addIngredient('r', createRecipeChoiceItem(listOf(recipe.result)))
            .addIngredient('m', recipe.mode.uiItem.clientsideProvider)
            .addIngredient('>', progressItem)
            .addIngredient('w', StaticFluidBar(FluidType.WATER, 1000, 1000, 3))
            .addIngredient('l', StaticFluidBar(FluidType.LAVA, 1000, 1000, 3))
            .build()
        
    }
    
}