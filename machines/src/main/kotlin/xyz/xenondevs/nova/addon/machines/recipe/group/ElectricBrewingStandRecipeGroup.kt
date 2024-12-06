package xyz.xenondevs.nova.addon.machines.recipe.group

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.PotionContents.potionContents
import org.bukkit.Material
import org.bukkit.potion.PotionEffect
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.gui.ScrollGui
import xyz.xenondevs.invui.item.ItemBuilder
import xyz.xenondevs.nova.addon.machines.recipe.ElectricBrewingStandRecipe
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.ui.menu.explorer.recipes.createRecipeChoiceItem
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.RecipeGroup
import xyz.xenondevs.nova.ui.menu.item.ScrollLeftItem
import xyz.xenondevs.nova.ui.menu.item.ScrollRightItem
import xyz.xenondevs.nova.world.item.DefaultGuiItems

object ElectricBrewingStandRecipeGroup : RecipeGroup<ElectricBrewingStandRecipe>() {
    
    override val icon = Items.ELECTRIC_BREWING_STAND.clientsideProvider
    override val priority = 0
    override val texture = GuiTextures.RECIPE_ELECTRIC_BREWING_STAND
    
    override fun createGui(recipe: ElectricBrewingStandRecipe): Gui {
        val result = ItemBuilder(Material.POTION)
            .set(
                DataComponentTypes.POTION_CONTENTS,
                potionContents().addCustomEffect(PotionEffect(recipe.result, -1, -1))
            ).get()
        
        val timeItem = DefaultGuiItems.INVISIBLE_ITEM.createClientsideItemBuilder()
            .setName("Time: ${recipe.defaultTime} ticks")
        val durationItem = ItemBuilder(Material.REDSTONE)
            .setName("Max duration level: ${recipe.maxDurationLevel}\nDuration multiplier: ${recipe.redstoneMultiplier}")
        val amplifierItem = ItemBuilder(Material.GLOWSTONE_DUST)
            .setName("Max amplifier level: ${recipe.maxAmplifierLevel}\nAmplifier multiplier: ${recipe.glowstoneMultiplier}")
        
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