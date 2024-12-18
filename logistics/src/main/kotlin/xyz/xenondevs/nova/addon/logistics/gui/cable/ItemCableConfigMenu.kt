package xyz.xenondevs.nova.addon.logistics.gui.cable

import org.bukkit.block.BlockFace
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.commons.collections.putOrRemove
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.VirtualInventory
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.logistics.registry.GuiItems
import xyz.xenondevs.nova.addon.logistics.util.getItemFilter
import xyz.xenondevs.nova.addon.logistics.util.isItemFilter
import xyz.xenondevs.nova.ui.menu.addIngredient
import xyz.xenondevs.nova.ui.menu.item.AddNumberItem
import xyz.xenondevs.nova.ui.menu.item.DisplayNumberItem
import xyz.xenondevs.nova.ui.menu.item.RemoveNumberItem
import xyz.xenondevs.nova.util.runTask
import xyz.xenondevs.nova.world.block.tileentity.network.node.NetworkEndPoint
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.ItemNetwork
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.holder.ItemHolder

class ItemCableConfigMenu(
    endPoint: NetworkEndPoint,
    holder: ItemHolder,
    face: BlockFace
) : ContainerCableConfigMenu<ItemHolder>(endPoint, holder, face, ItemNetwork.CHANNEL_AMOUNT) {
    
    val gui: Gui
    
    private var insertFilter: ItemStack? = null
    private var extractFilter: ItemStack? = null
    
    private val insertFilterInventory: VirtualInventory
    private val extractFilterInventory: VirtualInventory
    
    init {
        updateValues()
        
        insertFilterInventory = VirtualInventory(null, 1, arrayOf(insertFilter), intArrayOf(1))
        insertFilterInventory.addPreUpdateHandler(::validateIsItemFilter)
        extractFilterInventory = VirtualInventory(null, 1, arrayOf(extractFilter), intArrayOf(1))
        extractFilterInventory.addPreUpdateHandler(::validateIsItemFilter)
        
        gui = Gui.normal()
            .setStructure(
                "# p # # c # # P #",
                "# d # e # i # D #",
                "# m # E # I # M #")
            .addIngredient('i', InsertItem().also(updatableItems::add))
            .addIngredient('e', ExtractItem().also(updatableItems::add))
            .addIngredient('I', insertFilterInventory, GuiItems.ITEM_FILTER_PLACEHOLDER)
            .addIngredient('E', extractFilterInventory, GuiItems.ITEM_FILTER_PLACEHOLDER)
            .addIngredient('P', AddNumberItem({ 0..100 }, { insertPriority }, { insertPriority = it; updateGui() }).also(updatableItems::add))
            .addIngredient('M', RemoveNumberItem({ 0..100 }, { insertPriority }, { insertPriority = it; updateGui() }).also(updatableItems::add))
            .addIngredient('D', DisplayNumberItem({ insertPriority }, "menu.logistics.cable_config.insert_priority").also(updatableItems::add))
            .addIngredient('p', AddNumberItem({ 0..100 }, { extractPriority }, { extractPriority = it; updateGui() }).also(updatableItems::add))
            .addIngredient('m', RemoveNumberItem({ 0..100 }, { extractPriority }, { extractPriority = it; updateGui() }).also(updatableItems::add))
            .addIngredient('d', DisplayNumberItem({ extractPriority }, "menu.logistics.cable_config.extract_priority").also(updatableItems::add))
            .addIngredient('c', SwitchChannelItem().also(updatableItems::add))
            .build()
    }
    
    override fun updateValues() {
        super.updateValues()
        
        insertFilter = holder.insertFilters[face]?.toItemStack()
        extractFilter = holder.extractFilters[face]?.toItemStack()
        
        runTask {
            insertFilterInventory.setItemSilently(0, insertFilter)
            extractFilterInventory.setItemSilently(0, extractFilter)
        }
    }
    
    override fun writeChanges(): Boolean {
        var changed = super.writeChanges()
        
        val insertFilter = insertFilterInventory.getItem(0)?.getItemFilter()
        val extractFilter = extractFilterInventory.getItem(0)?.getItemFilter()
        
        changed = changed or (holder.insertFilters.putOrRemove(face, insertFilter) != insertFilter)
        changed = changed or (holder.extractFilters.putOrRemove(face, extractFilter) != extractFilter)
        
        return changed
    }
    
    private fun validateIsItemFilter(event: ItemPreUpdateEvent) {
        val newStack = event.newItem
        event.isCancelled = newStack != null && !newStack.isItemFilter()
    }
    
}