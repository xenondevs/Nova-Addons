package xyz.xenondevs.nova.addon.logistics.gui.itemfilter

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.gui.ScrollGui
import xyz.xenondevs.invui.inventory.VirtualInventory
import xyz.xenondevs.invui.inventory.event.UpdateReason
import xyz.xenondevs.invui.item.AbstractItem
import xyz.xenondevs.invui.item.Click
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.invui.window.Window
import xyz.xenondevs.nova.addon.logistics.item.itemfilter.LogisticsItemFilter
import xyz.xenondevs.nova.addon.logistics.item.itemfilter.NbtItemFilter
import xyz.xenondevs.nova.addon.logistics.item.itemfilter.TypeItemFilter
import xyz.xenondevs.nova.addon.logistics.registry.GuiItems
import xyz.xenondevs.nova.addon.logistics.util.isItemFilter
import xyz.xenondevs.nova.addon.logistics.util.setItemFilter
import xyz.xenondevs.nova.util.playClickSound
import kotlin.math.ceil

class ItemFilterMenu(
    player: Player,
    title: Component,
    private val itemStack: ItemStack,
    items: Array<ItemStack?>,
    private var whitelist: Boolean,
    private var nbt: Boolean,
) {
    
    private val filterInventory = VirtualInventory(null, items.size, items, IntArray(items.size) {1}).apply { 
        addPreUpdateHandler { event ->
            event.isCancelled = true
            
            // disallow item filters in item filters
            if (event.newItem?.isItemFilter() == true)
                return@addPreUpdateHandler
                
            if (event.isAdd || event.isSwap) {
                putItem(UpdateReason.SUPPRESSED, event.slot, event.newItem!!.clone().apply { amount = 1} )
            } else if (event.isRemove) {
                setItem(UpdateReason.SUPPRESSED, event.slot, null)
            }
        }
    }
    
    private val window: Window =
        Window.single {
            it.setGui {
                val rows = ceil(items.size / 7.0).toInt()
                if (rows > 3) {
                    return@setGui ScrollGui.inventories()
                        .setStructure(
                            "1 - - - - - - - 2",
                            "| # # m # n # # |",
                            "| x x x x x x x u",
                            "| x x x x x x x |",
                            "| x x x x x x x d",
                            "3 - - - - - - - 4"
                        )
                        .addIngredient('m', SwitchModeItem())
                        .addIngredient('n', SwitchNBTItem())
                        .addContent(filterInventory)
                        .build()
                } else {
                    val gui = Gui.normal()
                        .setStructure(
                            9, 3 + rows,
                            "1 - - - - - - - 2" +
                                "| # # m # n # # |" +
                                ("| # # # # # # # |").repeat(rows) +
                                "3 - - - - - - - 4")
                        .addIngredient('m', SwitchModeItem())
                        .addIngredient('n', SwitchNBTItem())
                        .build()
                    gui.fillRectangle(1, 2, 7, filterInventory, true)
                    return@setGui gui
                }
            }
            
            it.setViewer(player)
            it.setTitle(title)
            it.addCloseHandler { itemStack.setItemFilter(createItemFilter()) }
        }
    
    fun open() {
        window.open()
    }
    
    private fun createItemFilter(): LogisticsItemFilter {
        if (nbt) {
            return NbtItemFilter(filterInventory.items.map { it ?: ItemStack.empty() }, whitelist)
        } else {
            return TypeItemFilter(filterInventory.items.map { it ?: ItemStack.empty() }, whitelist)
        }
    }
    
    private inner class SwitchModeItem : AbstractItem() {
        
        override fun getItemProvider(player: Player): ItemProvider =
            (if (whitelist) GuiItems.WHITELIST_BTN else GuiItems.BLACKLIST_BTN).clientsideProvider
        
        override fun handleClick(clickType: ClickType, player: Player, click: Click) {
            if (clickType == ClickType.LEFT) {
                whitelist = !whitelist
                notifyWindows()
                player.playClickSound()
            }
        }
        
    }
    
    private inner class SwitchNBTItem : AbstractItem() {
        
        override fun getItemProvider(player: Player): ItemProvider =
            (if (nbt) GuiItems.NBT_BTN_ON else GuiItems.NBT_BTN_OFF).clientsideProvider
        
        override fun handleClick(clickType: ClickType, player: Player, click: Click) {
            if (clickType == ClickType.LEFT) {
                nbt = !nbt
                notifyWindows()
                player.playClickSound()
            }
        }
        
    }
    
}