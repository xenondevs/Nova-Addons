package xyz.xenondevs.nova.addon.logistics.tileentity

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.MutableProvider
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.VirtualInventory
import xyz.xenondevs.invui.inventory.event.ItemPostUpdateEvent
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.item.AbstractItem
import xyz.xenondevs.invui.item.Click
import xyz.xenondevs.invui.item.ItemBuilder
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.nova.addon.logistics.registry.Blocks.STORAGE_UNIT
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.item.takeUnlessEmpty
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.inventory.NetworkedInventory
import kotlin.math.min

private val MAX_ITEMS by STORAGE_UNIT.config.entry<Int>("max_items")

class StorageUnit(pos: BlockPos, state: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, state, data) {
    
    private val inventory = StorageUnitInventory(storedValue("type", true, ItemStack::empty), storedValue("amount", true) { 0 })
    private val inputInventory = VirtualInventory(null, 1).apply { setPreUpdateHandler(::handleInputInventoryUpdate) }
    private val outputInventory = VirtualInventory(null, 1).apply { setPreUpdateHandler(::handlePreOutputInventoryUpdate); setPostUpdateHandler(::handlePostOutputInventoryUpdate) }
    
    init {
        storedItemHolder(inventory to NetworkConnectionType.BUFFER)
    }
    
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
                inventory.type.clone().apply { amount = min(type.maxStackSize, inventory.amount) }
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
    
    @TileEntityMenuClass
    inner class StorageUnitMenu : GlobalTileEntityMenu() {
        
        private val sideConfigMenu = SideConfigMenu(
            this@StorageUnit,
            mapOf(inventory to "inventory.nova.default"),
            ::openWindow
        )
        
        private val storageUnitDisplay = StorageUnitDisplay()
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| # i # c # o s |",
                "3 - - - - - - - 4")
            .addIngredient('c', storageUnitDisplay)
            .addIngredient('i', inputInventory)
            .addIngredient('o', outputInventory)
            .addIngredient('s', OpenSideConfigItem(sideConfigMenu))
            .build()
        
        init {
            update()
        }
        
        fun update() {
            storageUnitDisplay.notifyWindows()
            updateOutputSlot()
        }
        
        private inner class StorageUnitDisplay : AbstractItem() {
            
            override fun getItemProvider(player: Player): ItemProvider {
                val type = inventory.type.takeUnlessEmpty() ?: return ItemBuilder(Material.BARRIER).hideTooltip(true)
                val amount = inventory.amount
                val component = Component.translatable(
                    "menu.logistics.storage_unit.item_display_" + if (amount > 1) "plural" else "singular",
                    NamedTextColor.GRAY,
                    Component.text(amount, NamedTextColor.GREEN)
                )
                return ItemBuilder(type).setName(component).setAmount(1)
            }
            
            override fun handleClick(clickType: ClickType, player: Player, click: Click) = Unit
            
        }
        
    }
    
    inner class StorageUnitInventory(
        type: MutableProvider<ItemStack>,
        amount: MutableProvider<Int>
    ) : NetworkedInventory {
        
        override val uuid = this@StorageUnit.uuid
        override val size = 1
        
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
            
            menuContainer.forEachMenu(StorageUnitMenu::update)
            
            return amount - transferred
        }
        
        override fun canTake(slot: Int, amount: Int): Boolean {
            return this.amount >= amount
        }
        
        override fun take(slot: Int, amount: Int) {
            this.amount -= amount
            if (this.amount == 0)
                type = ItemStack.empty()
            
            menuContainer.forEachMenu(StorageUnitMenu::update)
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