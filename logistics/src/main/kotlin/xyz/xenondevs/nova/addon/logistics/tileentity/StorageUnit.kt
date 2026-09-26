package xyz.xenondevs.nova.addon.logistics.tileentity

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.MutableProvider
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.inventory.VirtualInventory
import xyz.xenondevs.invui.inventory.event.ItemPostUpdateEvent
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.item.ItemWrapper
import xyz.xenondevs.nova.addon.logistics.registry.Blocks.STORAGE_UNIT
import xyz.xenondevs.nova.addon.logistics.registry.GuiTextures
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.itemProvider
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.nova.util.item.takeUnlessEmpty
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.inventory.NetworkedInventory
import xyz.xenondevs.nova.world.item.itemType
import kotlin.math.min

private val MAX_ITEMS by STORAGE_UNIT.config.entry<Int>("max_items")

class StorageUnit(pos: Block, state: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, state, data) {
    
    private val inventory = StorageUnitInventory(storedValue("type", true, ItemStack::empty), storedValue("amount", true) { 0 })
    private val inputInventory = VirtualInventory(null, 1).apply { addPreUpdateHandler(::handleInputInventoryUpdate) }
    private val outputInventory = VirtualInventory(null, 1).apply { addPreUpdateHandler(::handlePreOutputInventoryUpdate); addPostUpdateHandler(::handlePostOutputInventoryUpdate) }
    private val itemHolder = storedItemHolder(inventory to NetworkConnectionType.BUFFER, mergedInventory = inventory)
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.STORAGE_UNIT) {
        upperGui by gui(
            "s . i . c . o . .",
        ) {
            's' by openSideConfigItem(mapOf(inventory to "inventory.nova.default"))
            'i' by inputInventory
            'o' by outputInventory
            
            'c' by itemProvider(
                inventory.typeProvider.map {
                    it.takeUnlessEmpty()?.let(::ItemWrapper)
                        ?: ItemWrapper(ItemType.BARRIER.createItemStack())
                }
            ) {
                name by inventory.amountProvider.map { amount ->
                    Component.translatable(
                        "menu.logistics.storage_unit.item_display_" + if (amount > 1) "plural" else "singular",
                        NamedTextColor.GRAY,
                        Component.text(amount, NamedTextColor.GREEN)
                    )
                }
            }
        }
    }
    
    init {
        inventory.typeProvider.subscribe { updateOutputSlot() }
        inventory.amountProvider.subscribe { updateOutputSlot() }
        updateOutputSlot()
    }
    
    override fun requestsLocalNetwork(face: BlockFace): Boolean =
        itemHolder.connectionConfig[face] != NetworkConnectionType.BUFFER
    
    private fun handleInputInventoryUpdate(event: ItemPreUpdateEvent) {
        if (event.isAdd && !inventory.type.isEmpty && !inventory.type.isSimilar(event.newItem))
            event.isCancelled = true
    }
    
    private fun handlePreOutputInventoryUpdate(event: ItemPreUpdateEvent) {
        if (event.updateReason == SELF_UPDATE_REASON)
            return
        
        if (!event.isRemove) {
            event.isCancelled = true
        }
    }
    
    private fun handlePostOutputInventoryUpdate(event: ItemPostUpdateEvent) {
        if (event.updateReason == SELF_UPDATE_REASON)
            return
        
        // preUpdateHandler enforces that only remove is possible
        inventory.take(0, event.removedAmount)
    }
    
    private fun updateOutputSlot() {
        if (inventory.type.isEmpty) {
            outputInventory.setItem(SELF_UPDATE_REASON, 0, null)
        } else {
            outputInventory.setItem(
                SELF_UPDATE_REASON,
                0,
                inventory.type.clone().apply { amount = min(itemType.maxStackSize, inventory.amount) }
            )
        }
    }
    
    override fun handleTick() {
        val item = inputInventory.getItem(0)
        if (item != null) {
            val remaining = inventory.add(item, item.amount)
            inputInventory.setItem(null, 0, item.apply { amount = remaining }.takeUnless { it.amount <= 0 })
        }
    }
    
    inner class StorageUnitInventory(
        type: MutableProvider<ItemStack>,
        amount: MutableProvider<Int>
    ) : NetworkedInventory {
        
        override val uuid = this@StorageUnit.uuid
        override val size = 1
        
        val typeProvider: Provider<ItemStack> = type
        val amountProvider: Provider<Int> = amount
        
        var type by type
            private set
        var amount by amount
            private set
        
        override fun add(itemStack: ItemStack, amount: Int): Int {
            if (type.isEmpty) {
                type = itemStack.clone().also { it.amount = amount }
            } else if (!type.isSimilar(itemStack)) {
                return amount
            }
            
            val transferred = min(amount, MAX_ITEMS - this.amount)
            this.amount += transferred
            
            return amount - transferred
        }
        
        override fun canTake(slot: Int, amount: Int): Boolean {
            return this.amount >= amount
        }
        
        override fun take(slot: Int, amount: Int) {
            this.amount -= amount
            if (this.amount == 0)
                type = ItemStack.empty()
        }
        
        override fun isFull(): Boolean {
            return amount >= MAX_ITEMS
        }
        
        override fun isEmpty(): Boolean {
            return amount == 0
        }
        
        override fun copyContents(destination: Array<ItemStack>) {
            destination[0] = type.clone().also { it.amount = amount }
        }
        
    }
    
}
