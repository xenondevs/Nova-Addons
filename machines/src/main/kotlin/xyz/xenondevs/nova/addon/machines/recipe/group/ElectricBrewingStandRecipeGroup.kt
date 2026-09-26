package xyz.xenondevs.nova.addon.machines.recipe.group

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.PotionContents.potionContents
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.inventory.ItemType
import org.bukkit.potion.PotionEffect
import xyz.xenondevs.invui.dsl.itemProvider
import xyz.xenondevs.invui.dsl.scrollItemsGui
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.ItemBuilder
import xyz.xenondevs.nova.addon.machines.recipe.ElectricBrewingStandRecipe
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.ui.menu.explorer.recipes.createRecipeChoiceItem
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.RecipeGroup
import xyz.xenondevs.nova.ui.menu.item.scrollLeftItem
import xyz.xenondevs.nova.ui.menu.item.scrollRightItem
import xyz.xenondevs.nova.world.item.DefaultGuiItems
import xyz.xenondevs.nova.world.item.guiItemProvider
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

object ElectricBrewingStandRecipeGroup : RecipeGroup<ElectricBrewingStandRecipe>() {
    
    private val df = DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.US))
    
    override val icon = Items.ELECTRIC_BREWING_STAND.guiItemProvider
    override val priority = 0
    override val texture = GuiTextures.RECIPE_ELECTRIC_BREWING_STAND
    
    override fun createGui(recipe: ElectricBrewingStandRecipe): Gui {
        val result = ItemBuilder(ItemType.POTION)
            .set(
                DataComponentTypes.POTION_CONTENTS,
                potionContents().addCustomEffect(PotionEffect(recipe.result, -1, -1))
            ).get()
        
        return scrollItemsGui(
            "< x x x x x x x >",
            ". . . . t . . . .",
            ". . d . r . a . ."
        ) {
            'r' by createRecipeChoiceItem(listOf(result))
            '<' by scrollLeftItem()
            '>' by scrollRightItem()
            't' by itemProvider(DefaultGuiItems.INVISIBLE_ITEM) {
                name by Component.translatable("menu.nova.recipe.time", Component.text(recipe.defaultTime / 20.0))
            }
            'd' by itemProvider(ItemType.REDSTONE) {
                name by Component.translatable(
                    "menu.machines.recipe.electric_brewing_stand.max_duration_level",
                    NamedTextColor.GRAY,
                    Component.text(recipe.maxDurationLevel, NamedTextColor.AQUA)
                )
                lore by listOf(Component.translatable(
                    "menu.machines.recipe.electric_brewing_stand.duration_multiplier",
                    NamedTextColor.GRAY,
                    Component.text(df.format(recipe.redstoneMultiplier), NamedTextColor.AQUA)
                ))
            }
            'a' by itemProvider(ItemType.GLOWSTONE_DUST) {
                name by Component.translatable(
                    "menu.machines.recipe.electric_brewing_stand.max_amplifier_level",
                    NamedTextColor.GRAY,
                    Component.text(recipe.maxAmplifierLevel, NamedTextColor.AQUA)
                )
                lore by listOf(Component.translatable(
                    "menu.machines.recipe.electric_brewing_stand.amplifier_multiplier",
                    NamedTextColor.GRAY,
                    Component.text(df.format(recipe.glowstoneMultiplier), NamedTextColor.AQUA)
                ))
            }
            content by recipe.inputs.map(::createRecipeChoiceItem)
        }
    }
    
}
