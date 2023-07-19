package xyz.xenondevs.nova.addon.logistics.tileentity

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.VirtualInventory
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.invui.item.builder.ItemBuilder
import xyz.xenondevs.invui.item.builder.setDisplayName
import xyz.xenondevs.invui.item.impl.AbstractItem
import xyz.xenondevs.nova.data.config.NovaConfig
import xyz.xenondevs.nova.data.config.configReloadable
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.addon.logistics.registry.Blocks.STORAGE_UNIT
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.item.holder.NovaItemHolder
import xyz.xenondevs.nova.tileentity.network.item.inventory.NetworkedInventory
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu
import xyz.xenondevs.nova.util.item.takeUnlessEmpty
import xyz.xenondevs.nova.util.runTaskLater
import kotlin.math.min

private val MAX_ITEMS by configReloadable { NovaConfig[STORAGE_UNIT].getInt("max_items") }

class StorageUnit(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState) {
    
    private val inventory = StorageUnitInventory(retrieveDataOrNull("type"), retrieveDataOrNull("amount") ?: 0)
    private val inputInventory = VirtualInventory(null, 1).apply { setPreUpdateHandler(::handleInputInventoryUpdate) }
    private val outputInventory = VirtualInventory(null, 1).apply { setPreUpdateHandler(::handleOutputInventoryUpdate) }
    override val itemHolder = NovaItemHolder(
        this,
        uuid to (inventory to NetworkConnectionType.BUFFER)
    ) { createSideConfig(NetworkConnectionType.BUFFER) }
    
    private fun handleInputInventoryUpdate(event: ItemPreUpdateEvent) {
        if (event.isAdd && inventory.type != null && !inventory.type!!.isSimilar(event.newItem))
            event.isCancelled = true
    }
    
    private fun handleOutputInventoryUpdate(event: ItemPreUpdateEvent) {
        if (event.updateReason == SELF_UPDATE_REASON)
            return
        
        if (event.isAdd || event.isSwap) {
            event.isCancelled = true
        } else if (event.isRemove && inventory.type != null) {
            inventory.amount -= event.removedAmount
            if (inventory.amount == 0) inventory.type = null
            
            runTaskLater(1) { menuContainer.forEachMenu(StorageUnitMenu::update) }
        }
    }
    
    private fun updateOutputSlot() {
        if (inventory.type == null)
            outputInventory.setItem(SELF_UPDATE_REASON, 0, null)
        else
            outputInventory.setItem(SELF_UPDATE_REASON, 0, inventory.type!!.apply { amount = min(type.maxStackSize, inventory.amount) })
    }
    
    override fun handleTick() {
        val item = inputInventory.getItem(0)
        if (item != null) {
            val remaining = inventory.addItem(item)
            inputInventory.setItem(null, 0, item.apply { amount = remaining }.takeUnless { it.amount <= 0 })
        }
    }
    
    override fun saveData() {
        super.saveData()
        storeData("type", inventory.type, true)
        storeData("amount", inventory.amount, true)
    }
    
    @TileEntityMenuClass
    inner class StorageUnitMenu : GlobalTileEntityMenu() {
        
        private val SideConfigMenu = SideConfigMenu(
            this@StorageUnit,
            listOf(inventory to "inventory.nova.default"),
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
            .addIngredient('s', OpenSideConfigItem(SideConfigMenu))
            .build()
        
        init {
            update()
        }
        
        fun update() {
            storageUnitDisplay.notifyWindows()
            updateOutputSlot()
        }
        
        private inner class StorageUnitDisplay : AbstractItem() {
            
            override fun getItemProvider(): ItemProvider {
                val type = inventory.type ?: return ItemBuilder(Material.BARRIER).setDisplayName("§r")
                val amount = inventory.amount
                val component = Component.translatable(
                    "menu.logistics.storage_unit.item_display_" + if (amount > 1) "plural" else "singular",
                    NamedTextColor.GRAY,
                    Component.text(amount, NamedTextColor.GREEN)
                )
                return ItemBuilder(type).setDisplayName(component).setAmount(1)
            }
            
            override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) = Unit
            
        }
        
    }
    
    inner class StorageUnitInventory(var type: ItemStack? = null, var amount: Int = 0) : NetworkedInventory {
        
        override val size: Int
            get() = 1
        
        override val items: Array<ItemStack?>
            get() {
                type ?: return emptyArray()
                return arrayOf(type!!.clone().also { it.amount = amount })
            }
        
        init {
            // Fix corrupted inventories
            if (type?.type?.isAir == true) type = null
        }
        
        override fun addItem(item: ItemStack): Int {
            val remaining: Int
            
            if (item.type.isAir) return 0
            
            if (type == null) { // Storage unit is empty
                type = item.clone()
                amount = item.amount
                remaining = 0
            } else if (type!!.isSimilar(item)) { // The item is the same as the one stored in the unit
                val leeway = MAX_ITEMS - amount
                if (leeway >= item.amount) { // The whole stack fits into the storage unit
                    amount += item.amount
                    remaining = 0
                } else remaining = item.amount - leeway  // Not all items fit so a few will remain
            } else remaining = item.amount // The item isn't the same as the one stored in the unit
            
            menuContainer.forEachMenu(StorageUnitMenu::update)
            return remaining
        }
        
        override fun setItem(slot: Int, item: ItemStack?): Boolean {
            amount = item?.takeUnlessEmpty()?.amount ?: 0
            type = if (amount != 0) item else null
            
            menuContainer.forEachMenu(StorageUnitMenu::update)
            return true
        }
        
        override fun decrementByOne(slot: Int) {
            if (amount > 1) {
                amount -= 1
            } else {
                amount = 0
                type = null
            }
            
            menuContainer.forEachMenu(StorageUnitMenu::update)
        }
        
        override fun isFull(): Boolean {
            return amount >= MAX_ITEMS
        }
        
    }
    
}