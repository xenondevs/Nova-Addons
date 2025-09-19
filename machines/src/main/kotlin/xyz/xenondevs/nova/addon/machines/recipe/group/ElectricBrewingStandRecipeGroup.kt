package xyz.xenondevs.nova.addon.machines.recipe.group

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.PotionContents.potionContents
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
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
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

object ElectricBrewingStandRecipeGroup : RecipeGroup<ElectricBrewingStandRecipe>() {
    
    private val df = DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.US))
    
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
            .setName(Component.translatable("menu.nova.recipe.time", Component.text(recipe.defaultTime / 20.0)))
        val durationItem = ItemBuilder(Material.REDSTONE)
            .setName(Component.translatable(
                "menu.machines.recipe.electric_brewing_stand.max_duration_level",
                NamedTextColor.GRAY,
                Component.text(recipe.maxDurationLevel, NamedTextColor.AQUA)
            ))
            .addLoreLines(Component.translatable(
                "menu.machines.recipe.electric_brewing_stand.duration_multiplier",
                NamedTextColor.GRAY,
                Component.text(df.format(recipe.redstoneMultiplier), NamedTextColor.AQUA)
            ))
            .build()
        val amplifierItem = ItemBuilder(Material.GLOWSTONE_DUST)
            .setName(Component.translatable(
                "menu.machines.recipe.electric_brewing_stand.max_amplifier_level",
                NamedTextColor.GRAY,
                Component.text(recipe.maxAmplifierLevel, NamedTextColor.AQUA)
            ))
            .addLoreLines(Component.translatable(
                "menu.machines.recipe.electric_brewing_stand.amplifier_multiplier",
                NamedTextColor.GRAY,
                Component.text(df.format(recipe.glowstoneMultiplier), NamedTextColor.AQUA)
            ))
        
        return ScrollGui.itemsBuilder()
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