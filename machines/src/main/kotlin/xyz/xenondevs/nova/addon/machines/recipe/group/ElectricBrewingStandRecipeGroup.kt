package xyz.xenondevs.nova.addon.machines.recipe.group

import org.bukkit.Material
import org.bukkit.potion.PotionEffect
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.gui.ScrollGui
import xyz.xenondevs.invui.item.builder.ItemBuilder
import xyz.xenondevs.invui.item.builder.PotionBuilder
import xyz.xenondevs.nova.addon.machines.recipe.ElectricBrewingStandRecipe
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.ui.menu.explorer.recipes.createRecipeChoiceItem
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.RecipeGroup
import xyz.xenondevs.nova.ui.menu.item.ScrollLeftItem
import xyz.xenondevs.nova.ui.menu.item.ScrollRightItem
import xyz.xenondevs.nova.world.item.DefaultGuiItems

object ElectricBrewingStandRecipeGroup : RecipeGroup<ElectricBrewingStandRecipe>() {
    
    override val icon = Items.ELECTRIC_BREWING_STAND.model.clientsideProvider
    override val priority = 0
    override val texture = GuiTextures.RECIPE_ELECTRIC_BREWING_STAND
    
    override fun createGui(recipe: ElectricBrewingStandRecipe): Gui {
        val result = PotionBuilder(PotionBuilder.PotionType.NORMAL)
            .addEffect(PotionEffect(recipe.result, -1, -1))
            .get()
        
        val timeItem = DefaultGuiItems.INVISIBLE_ITEM.model.createClientsideItemBuilder()
            .setDisplayName("Time: ${recipe.defaultTime} ticks")
        val durationItem = ItemBuilder(Material.REDSTONE)
            .setDisplayName("Max duration level: ${recipe.maxDurationLevel}\nDuration multiplier: ${recipe.redstoneMultiplier}")
        val amplifierItem = ItemBuilder(Material.GLOWSTONE_DUST)
            .setDisplayName("Max amplifier level: ${recipe.maxAmplifierLevel}\nAmplifier multiplier: ${recipe.glowstoneMultiplier}")
        
        return ScrollGui.items()
            .setStructure(
                "< x x x x x x x >",
                ". . . . t . . . .",
                ". . d . r . a . .")
            .addIngredient('r', createRecipeChoiceItem(listOf(result)))
            .addIngredient('<', ::ScrollLeftItem)
            .addIngredient('>', ::ScrollRightItem)
            .addIngredient('t', timeItem)
            .addIngredient('d', durationItem)
            .addIngredient('a', amplifierItem)
            .setContent(recipe.inputs.map(::createRecipeChoiceItem))
            .build()
    }
    
}