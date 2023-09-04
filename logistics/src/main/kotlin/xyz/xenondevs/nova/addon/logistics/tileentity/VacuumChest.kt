package xyz.xenondevs.nova.addon.logistics.tileentity

import org.bukkit.entity.Item
import org.bukkit.entity.Player
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.VirtualInventory
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.logistics.item.getItemFilterConfig
import xyz.xenondevs.nova.addon.logistics.item.isItemFilter
import xyz.xenondevs.nova.addon.logistics.registry.Blocks.VACUUM_CHEST
import xyz.xenondevs.nova.addon.logistics.registry.GuiMaterials
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.data.config.entry
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.item.ItemFilter
import xyz.xenondevs.nova.tileentity.network.item.holder.NovaItemHolder
import xyz.xenondevs.nova.tileentity.upgrade.Upgradable
import xyz.xenondevs.nova.ui.OpenUpgradesItem
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu
import xyz.xenondevs.nova.util.item.novaItem
import xyz.xenondevs.nova.util.serverTick

private val MIN_RANGE = VACUUM_CHEST.config.entry<Int>("range", "min")
private val MAX_RANGE = VACUUM_CHEST.config.entry<Int>("range", "max")
private val DEFAULT_RANGE by VACUUM_CHEST.config.entry<Int>("range", "default")

class VacuumChest(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState), Upgradable {
    
    private val inventory: VirtualInventory = getInventory("inventory", 9)
    private val filterInventory: VirtualInventory = VirtualInventory(null, 1, arrayOfNulls(1), intArrayOf(1))
    override val itemHolder: NovaItemHolder = NovaItemHolder(
        this,
        inventory to NetworkConnectionType.BUFFER
    ) { createSideConfig(NetworkConnectionType.EXTRACT) }
    override val upgradeHolder = getUpgradeHolder(UpgradeTypes.RANGE)
    
    private var filter: ItemFilter? by storedValue("itemFilter")
    private val region = getUpgradableRegion(UpgradeTypes.RANGE, MIN_RANGE, MAX_RANGE, DEFAULT_RANGE) { getSurroundingRegion(it) }
    
    private val items = ArrayList<Item>()
    
    init {
        filter?.let { filterInventory.setItem(SELF_UPDATE_REASON, 0, it.createFilterItem()) }
        
        filterInventory.setPreUpdateHandler(::handleFilterInventoryUpdate)
        filterInventory.guiPriority = 1
    }
    
    override fun handleTick() {
        items
            .forEach {
                if (it.isValid) {
                    val itemStack = it.itemStack
                    val remaining = inventory.addItem(null, itemStack)
                    if (remaining != 0) it.itemStack = itemStack.apply { amount = remaining }
                    else it.remove()
                }
            }
        
        items.clear()
        
        if (serverTick % 10 == 0) {
            world.entities.forEach {
                if (it is Item
                    && it.location in region
                    && filter?.allowsItem(it.itemStack) != false
                    && ProtectionManager.canInteractWithEntity(this, it, null).get()
                ) {
                    items += it
                    it.velocity = location.clone().subtract(it.location).toVector()
                }
            }
        }
    }
    
    private fun handleFilterInventoryUpdate(event: ItemPreUpdateEvent) {
        val newStack = event.newItem
        if (newStack != null) {
            if (newStack.novaItem.isItemFilter()) {
                filter = newStack.getItemFilterConfig()
            } else event.isCancelled = true
        }
    }
    
    @TileEntityMenuClass
    inner class VacuumChestGui(player: Player) : IndividualTileEntityMenu(player) {
        
        private val SideConfigMenu = SideConfigMenu(
            this@VacuumChest,
            listOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default"),
            ::openWindow
        )
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s u # i i i p |",
                "| r # # i i i d |",
                "| f # # i i i m |",
                "3 - - - - - - - 4")
            .addIngredient('i', inventory)
            .addIngredient('f', filterInventory, GuiMaterials.ITEM_FILTER_PLACEHOLDER.clientsideProvider)
            .addIngredient('s', OpenSideConfigItem(SideConfigMenu))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('r', region.createVisualizeRegionItem(player))
            .addIngredient('p', region.increaseSizeItem)
            .addIngredient('m', region.decreaseSizeItem)
            .addIngredient('d', region.displaySizeItem)
            .build()
        
    }
    
}