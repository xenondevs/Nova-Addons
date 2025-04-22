package xyz.xenondevs.nova.addon.machines.tileentity.processing.brewing

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.PotionContents.potionContents
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.ItemBuilder
import xyz.xenondevs.invui.item.setItemProvider
import xyz.xenondevs.invui.window.Window
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.ui.menu.item.AioNumberItem
import xyz.xenondevs.nova.ui.menu.item.BackItem
import xyz.xenondevs.nova.util.playClickSound
import xyz.xenondevs.nova.world.block.tileentity.menu.MenuContainer
import java.awt.Color

class ColorPickerMenu(
    private val menuContainer: MenuContainer,
    color: Color,
    openPrevious: (Player) -> Unit
) {
    
    val openItem = Item.builder()
        .setItemProvider(GuiItems.TP_COLOR_PICKER.clientsideProvider)
        .addClickHandler { _, click ->
            if (click.clickType == ClickType.LEFT) {
                openWindow(click.player)
                click.player.playClickSound()
            }
        }
        .build()
    
    val color: Color
        get() = Color(red.get(), green.get(), blue.get())
    
    private val red = mutableProvider(color.red)
    private val green = mutableProvider(color.green)
    private val blue = mutableProvider(color.blue)
    
    private val gui = Gui.builder()
        .setStructure(
            "< . . . p . . . .",
            ". . . . . . . . .",
            ". . r . g . b . ."
        )
        .addIngredient('p', Item.builder()
            .setItemProvider(red, green, blue) { red, green, blue ->
                ItemBuilder(Material.POTION)
                    .setCustomName(Component.translatable("menu.machines.color_picker.current_color"))
                    .set(
                        DataComponentTypes.POTION_CONTENTS,
                        potionContents().customColor(org.bukkit.Color.fromRGB(red, green, blue))
                    )
            }
        )
        .addIngredient('r', ChangeColorItem(red::get, red::set, "menu.nova.color_picker.red", ItemBuilder(Material.RED_DYE)))
        .addIngredient('g', ChangeColorItem(green::get, green::set, "menu.nova.color_picker.green", ItemBuilder(Material.LIME_DYE)))
        .addIngredient('b', ChangeColorItem(blue::get, blue::set, "menu.nova.color_picker.blue", ItemBuilder(Material.BLUE_DYE)))
        .addIngredient('<', BackItem(openPrevious = openPrevious))
        .build()
    
    fun openWindow(player: Player) {
        val window = Window.builder()
            .setViewer(player)
            .setTitle(GuiTextures.COLOR_PICKER.getTitle("menu.nova.color_picker"))
            .setUpperGui(gui)
            .build()
        
        menuContainer.registerWindow(window)
        
        window.open()
    }
    
}

private class ChangeColorItem(
    getNumber: () -> Int,
    setNumber: (Int) -> Unit,
    localizedName: String,
    builder: ItemBuilder
) : AioNumberItem(
    1, 10,
    { 0..255 },
    getNumber, setNumber,
    localizedName, builder
)