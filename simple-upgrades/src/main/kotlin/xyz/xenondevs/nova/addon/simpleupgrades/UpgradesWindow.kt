package xyz.xenondevs.nova.addon.simpleupgrades

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.commons.collections.repeated
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.combinedProvider
import xyz.xenondevs.invui.dsl.WindowDsl
import xyz.xenondevs.invui.dsl.by
import xyz.xenondevs.invui.dsl.item
import xyz.xenondevs.invui.dsl.itemProvider
import xyz.xenondevs.invui.dsl.pagedSlotElementsGui
import xyz.xenondevs.invui.dsl.window
import xyz.xenondevs.invui.gui.InventoryLink
import xyz.xenondevs.invui.gui.SlotElement
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.invui.window.Window
import xyz.xenondevs.nova.addon.simpleupgrades.registry.GuiItems
import xyz.xenondevs.nova.addon.simpleupgrades.registry.GuiTextures
import xyz.xenondevs.nova.registry.RegistryEntry
import xyz.xenondevs.nova.ui.menu.by
import xyz.xenondevs.nova.ui.menu.item.backItem
import xyz.xenondevs.nova.ui.menu.item.pageBackItem
import xyz.xenondevs.nova.ui.menu.item.pageForwardItem
import xyz.xenondevs.nova.util.playClickSound
import xyz.xenondevs.nova.world.item.DefaultGuiItems
import xyz.xenondevs.nova.world.item.guiItemProvider

private fun upgradesWindow(
    upgradeHolder: UpgradeHolder,
    viewer: Player,
    previousWindow: Provider<Window>
) = window(viewer) {
    title by GuiTextures.UPGRADES
    upperGui by pagedSlotElementsGui(
        "b . . . . . . . .",
        "< x . x . x . x >",
    ) {
        'b' by backItem(previousWindow, DefaultGuiItems.TP_SMALL_ARROW_LEFT_ON.guiItemProvider)
        '<' by pageBackItem(page, DefaultGuiItems.TP_ARROW_LEFT_BTN_ON.guiItemProvider, DefaultGuiItems.TP_ARROW_LEFT_BTN_OFF.guiItemProvider)
        '>' by pageForwardItem(page, pageCount, DefaultGuiItems.TP_ARROW_RIGHT_BTN_ON.guiItemProvider, DefaultGuiItems.TP_ARROW_RIGHT_BTN_OFF.guiItemProvider)
        
        content by upgradeHolder.allowed.map { type ->
            InventoryLink(
                upgradeHolder.inventories[type]!!,
                0,
                background(upgradeHolder, type)
            ) { it?.let { visualize(upgradeHolder, type, it) } }
        } + listOf(
            SlotElement.Item(item { itemProvider by DefaultGuiItems.DISABLED_SLOT }),
        ).repeated((4 - upgradeHolder.allowed.size % 4) % 4) // fill remaining columns with disabled slot items
    }
}

// item visualizers are not reactive, but inv is updated on count/limit change anyway, which triggers the visualizer
private fun visualize(holder: UpgradeHolder, type: RegistryEntry.Nova<UpgradeType<*>>, toVisualize: ItemStack): ItemProvider =
    itemProvider(toVisualize) {
        name by Component.translatable(
            "menu.${type.key.namespace()}.upgrades.type.${type.key.value()}",
            NamedTextColor.GRAY,
            Component.text(holder.getLevel(type)),
            Component.text(holder.getLimit(type))
        )
    }.get()

private fun background(holder: UpgradeHolder, type: RegistryEntry.Nova<UpgradeType<*>>): Provider<ItemProvider> =
    itemProvider(type.flatMap { it.placeholder }) {
        name by combinedProvider(
            holder.getLevelProvider(type), holder.getLimitProvider(type)
        ) { level, limit ->
            Component.translatable(
                "menu.${type.key.namespace()}.upgrades.type.${type.key.value()}",
                NamedTextColor.GRAY,
                Component.text(level),
                Component.text(limit)
            )
        }
    }

/**
 * A UI item that creates, memorizes, and opens the upgrades menu when clicked.
 * Uses [the window from the context][windowDsl] as the previous window.
 */
context(windowDsl: WindowDsl)
fun openUpgradesItem(upgradeHolder: UpgradeHolder): Item = item {
    itemProvider by GuiItems.UPGRADES_BTN
    
    val upgradesWindow by lazy {
        val window = upgradesWindow(upgradeHolder, windowDsl.viewer, windowDsl.window)
        upgradeHolder.tileEntity.menu.register(window)
        window
    }
    
    onClick {
        player.playClickSound()
        upgradesWindow.open()
    }
}