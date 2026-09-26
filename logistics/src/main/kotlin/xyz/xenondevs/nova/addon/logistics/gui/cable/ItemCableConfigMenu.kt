package xyz.xenondevs.nova.addon.logistics.gui.cable

import org.bukkit.block.BlockFace
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.commons.provider.provider
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.dsl.with
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.VirtualInventory
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.inventory.event.UpdateReason
import xyz.xenondevs.nova.addon.logistics.registry.GuiItems
import xyz.xenondevs.nova.addon.logistics.util.getItemFilter
import xyz.xenondevs.nova.addon.logistics.util.isItemFilter
import xyz.xenondevs.nova.ui.menu.item.addNumberItem
import xyz.xenondevs.nova.ui.menu.item.displayNumberItem
import xyz.xenondevs.nova.ui.menu.item.removeNumberItem
import xyz.xenondevs.nova.world.block.tileentity.network.node.NetworkEndPoint
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.ItemNetwork
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.holder.ItemHolder

class ItemCableConfigMenu(
    endPoint: NetworkEndPoint,
    holder: ItemHolder,
    face: BlockFace
) : ContainerCableConfigMenu<ItemHolder>(endPoint, holder, face, ItemNetwork.CHANNEL_AMOUNT) {
    
    @Volatile
    private var insertFilter: ItemStack? = null
    
    @Volatile
    private var extractFilter: ItemStack? = null
    
    private val insertFilterInventory: VirtualInventory
    private val extractFilterInventory: VirtualInventory
    val gui: Gui
    
    init {
        updateValues()
        
        insertFilterInventory = VirtualInventory(null, 1, arrayOf(insertFilter), intArrayOf(1))
        insertFilterInventory.addPreUpdateHandler(::validateIsItemFilter)
        insertFilterInventory.addPostUpdateHandler { insertFilter = it.newItem }
        extractFilterInventory = VirtualInventory(null, 1, arrayOf(extractFilter), intArrayOf(1))
        extractFilterInventory.addPreUpdateHandler(::validateIsItemFilter)
        extractFilterInventory.addPostUpdateHandler { extractFilter = it.newItem }
        
        val priorityRange = provider(0..100)
        gui = gui(
            "p . . c . . P",
            "d . e . i . D",
            "m . E . I . M"
        ) {
            'i' by insertItem()
            'e' by extractItem()
            'I' by (insertFilterInventory with GuiItems.ITEM_FILTER_PLACEHOLDER)
            'E' by (extractFilterInventory with GuiItems.ITEM_FILTER_PLACEHOLDER)
            'P' by addNumberItem(priorityRange, insertPriority)
            'M' by removeNumberItem(priorityRange, insertPriority)
            'D' by displayNumberItem(insertPriority, "menu.logistics.cable_config.insert_priority")
            'p' by addNumberItem(priorityRange, extractPriority)
            'm' by removeNumberItem(priorityRange, extractPriority)
            'd' by displayNumberItem(extractPriority, "menu.logistics.cable_config.extract_priority")
            'c' by switchChannelItem()
        }
    }
    
    override fun updateValues() {
        super.updateValues()
        
        insertFilter = holder.insertFilters[face]?.toItemStack()
        extractFilter = holder.extractFilters[face]?.toItemStack()
    }
    
    override fun updateGui() {
        super.updateGui()
        
        insertFilterInventory.setItem(UpdateReason.SUPPRESSED, 0, insertFilter)
        extractFilterInventory.setItem(UpdateReason.SUPPRESSED, 0, extractFilter)
    }
    
    override fun writeChanges(): Boolean {
        var changed = super.writeChanges()
        
        val insertFilter = insertFilterInventory.getItem(0)?.getItemFilter()
        val extractFilter = extractFilterInventory.getItem(0)?.getItemFilter()
        
        if (holder.insertFilters[face] != insertFilter) {
            changed = true
            holder.insertFilters = holder.insertFilters.with(face, insertFilter)
        }
        
        if (holder.extractFilters[face] != extractFilter) {
            changed = true
            holder.extractFilters = holder.extractFilters.with(face, extractFilter)
        }
        
        return changed
    }
    
    private fun validateIsItemFilter(event: ItemPreUpdateEvent) {
        val newStack = event.newItem
        event.isCancelled = newStack != null && !newStack.isItemFilter()
    }
    
}
