package xyz.xenondevs.nova.addon.logistics.gui.itemfilter

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.invui.dsl.item
import xyz.xenondevs.invui.dsl.scrollInventoriesGui
import xyz.xenondevs.invui.dsl.window
import xyz.xenondevs.invui.gui.Markers
import xyz.xenondevs.invui.inventory.VirtualInventory
import xyz.xenondevs.invui.inventory.event.UpdateReason
import xyz.xenondevs.invui.window.Window
import xyz.xenondevs.nova.addon.logistics.item.itemfilter.LogisticsItemFilter
import xyz.xenondevs.nova.addon.logistics.item.itemfilter.NbtItemFilter
import xyz.xenondevs.nova.addon.logistics.item.itemfilter.TypeItemFilter
import xyz.xenondevs.nova.addon.logistics.registry.GuiItems
import xyz.xenondevs.nova.addon.logistics.registry.GuiTextures
import xyz.xenondevs.nova.addon.logistics.util.isItemFilter
import xyz.xenondevs.nova.addon.logistics.util.setItemFilter
import xyz.xenondevs.nova.ui.menu.item.SCROLL_ENABLING_VISUALIZER_EMPTIES
import xyz.xenondevs.nova.ui.menu.item.installBackgroundScrollSupport
import xyz.xenondevs.nova.ui.menu.item.installInventoryScrollSupport
import xyz.xenondevs.nova.ui.menu.item.scrollBar
import xyz.xenondevs.nova.ui.menu.locale
import xyz.xenondevs.nova.ui.overlay.guitexture.getTitle
import xyz.xenondevs.nova.util.playClickSound
import xyz.xenondevs.nova.world.item.DefaultGuiItems
import xyz.xenondevs.nova.world.item.guiItemProvider

class ItemFilterMenu(
    player: Player,
    hand: EquipmentSlot,
    menuTitle: Component,
    private val itemStack: ItemStack,
    items: Array<ItemStack?>,
    whitelist: Boolean,
    nbt: Boolean
) {
    
    private val filterInventory = VirtualInventory(null, items.size, items, IntArray(items.size) { 1 }).apply {
        setVisualizer(SCROLL_ENABLING_VISUALIZER_EMPTIES)
        addPreUpdateHandler { event ->
            event.isCancelled = true
            
            // disallow item filters in item filters
            if (event.newItem?.isItemFilter() == true)
                return@addPreUpdateHandler
            
            if (event.isAdd || event.isSwap) {
                putItem(UpdateReason.SUPPRESSED, event.slot, event.newItem!!.clone().apply { amount = 1 })
            } else if (event.isRemove) {
                setItem(UpdateReason.SUPPRESSED, event.slot, null)
            }
        }
    }
    
    private val whitelistState = mutableProvider(whitelist)
    private val nbtState = mutableProvider(nbt)
    
    private val window: Window = window(player) {
        val modeItem = item {
            itemProvider by whitelistState.flatMap {
                if (it) GuiItems.WHITELIST_BTN.guiItemProvider else GuiItems.BLACKLIST_BTN.guiItemProvider
            }
            onClick {
                if (clickType.isLeftClick) {
                    whitelistState.set(!whitelistState.get())
                    player.playClickSound()
                }
            }
        }
        val nbtItem = item {
            itemProvider by nbtState.flatMap {
                if (it) GuiItems.NBT_BTN_ON.guiItemProvider else GuiItems.NBT_BTN_OFF.guiItemProvider
            }
            onClick {
                if (clickType.isLeftClick) {
                    nbtState.set(!nbtState.get())
                    player.playClickSound()
                }
            }
        }
        title by GuiTextures.ITEM_FILTER.getTitle([menuTitle], locale)
        upperGui by scrollInventoriesGui(
            "m n . . . . . . .",
            "x x x x x x x x |",
            "x x x x x x x x |",
            "x x x x x x x x |"
        ) {
            '.' by ItemStack.empty()
            'm' by modeItem
            'n' by nbtItem
            'x' by Markers.CONTENT_LIST_SLOT_HORIZONTAL
            '|' by scrollBar(offset = 2)
            background by DefaultGuiItems.DISABLED_SLOT.guiItemProvider
            content by listOf(filterInventory)
            installInventoryScrollSupport()
            installBackgroundScrollSupport()
        }
        onClose {
            if (player.inventory.getItem(hand) == itemStack) {
                val newItemStack = itemStack.clone().apply {
                    setItemFilter(createItemFilter())
                }
                player.inventory.setItem(hand, newItemStack)
            }
        }
    }
    
    fun open() {
        window.open()
    }
    
    private fun createItemFilter(): LogisticsItemFilter {
        if (nbtState.get()) {
            return NbtItemFilter(filterInventory.items.map { it ?: ItemStack.empty() }, whitelistState.get())
        } else {
            return TypeItemFilter(filterInventory.items.map { it ?: ItemStack.empty() }, whitelistState.get())
        }
    }
    
}