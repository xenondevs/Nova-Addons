package xyz.xenondevs.nova.addon.logistics.gui.itemfilter

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.gui.ScrollGui
import xyz.xenondevs.invui.inventory.VirtualInventory
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.inventory.event.UpdateReason
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.invui.item.builder.setDisplayName
import xyz.xenondevs.invui.item.impl.AbstractItem
import xyz.xenondevs.invui.window.Window
import xyz.xenondevs.invui.window.type.context.setTitle
import xyz.xenondevs.nova.addon.logistics.item.isItemFilter
import xyz.xenondevs.nova.addon.logistics.registry.GuiMaterials
import xyz.xenondevs.nova.item.NovaItem
import xyz.xenondevs.nova.tileentity.network.item.getOrCreateFilterConfig
import xyz.xenondevs.nova.tileentity.network.item.saveFilterConfig
import xyz.xenondevs.nova.util.item.novaItem
import kotlin.math.ceil

class ItemFilterWindow(player: Player, item: NovaItem, size: Int, private val itemStack: ItemStack) {
    
    private val itemFilter = itemStack.getOrCreateFilterConfig(size)
    private val filterInventory = object : VirtualInventory(null, itemFilter.items.size, itemFilter.items, IntArray(itemFilter.items.size) { 1 }) {
        
        override fun addItem(updateReason: UpdateReason?, itemStack: ItemStack): Int {
            items.withIndex()
                .firstOrNull { it.value == null }
                ?.index
                ?.also { putItem(updateReason, it, itemStack) }
            
            return itemStack.amount
        }
        
        override fun setItem(updateReason: UpdateReason?, slot: Int, itemStack: ItemStack?): Boolean {
            return super.forceSetItem(updateReason, slot, itemStack)
        }
        
    }
    
    private val gui: Gui
    private val window: Window
    
    init {
        val rows = ceil(itemFilter.items.size / 7.0).toInt()
        
        if (rows > 3) {
            gui = ScrollGui.inventories()
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
            gui = Gui.normal()
                .setStructure(9, 3 + rows,
                    "1 - - - - - - - 2" +
                        "| # # m # n # # |" +
                        ("| # # # # # # # |").repeat(rows) +
                        "3 - - - - - - - 4")
                .addIngredient('m', SwitchModeItem())
                .addIngredient('n', SwitchNBTItem())
                .build()
            gui.fillRectangle(1, 2, 7, filterInventory, true)
        }
        
        filterInventory.setPreUpdateHandler(::handleInventoryUpdate)
        
        window = Window.single {
            it.setGui(gui)
            it.setViewer(player)
            it.setTitle(item.name)
            it.addCloseHandler(::saveFilterConfig)
        }.apply { open() }
    }
    
    private fun saveFilterConfig() {
        itemFilter.items = filterInventory.items
        itemStack.saveFilterConfig(itemFilter)
    }
    
    private fun handleInventoryUpdate(event: ItemPreUpdateEvent) {
        if (event.updateReason == null) return
        
        event.isCancelled = true
        if (event.newItem?.novaItem.isItemFilter()) return
        filterInventory.setItem(null, event.slot, event.newItem?.clone()?.apply { amount = 1 })
    }
    
    private inner class SwitchModeItem : AbstractItem() {
        
        override fun getItemProvider(): ItemProvider =
            if (itemFilter.whitelist) GuiMaterials.WHITELIST_BTN.createClientsideItemBuilder().setDisplayName(Component.translatable("menu.logistics.item_filter.whitelist"))
            else GuiMaterials.BLACKLIST_BTN.createClientsideItemBuilder().setDisplayName(Component.translatable("menu.logistics.item_filter.blacklist"))
        
        override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) {
            itemFilter.whitelist = !itemFilter.whitelist
            notifyWindows()
        }
        
    }
    
    private inner class SwitchNBTItem : AbstractItem() {
        
        override fun getItemProvider(): ItemProvider {
            return (if (itemFilter.nbt) GuiMaterials.NBT_BTN_ON else GuiMaterials.NBT_BTN_OFF)
                .createClientsideItemBuilder().setDisplayName(Component.translatable("menu.logistics.item_filter.nbt." + if (itemFilter.nbt) "on" else "off"))
        }
        
        override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) {
            itemFilter.nbt = !itemFilter.nbt
            notifyWindows()
        }
        
    }
    
}