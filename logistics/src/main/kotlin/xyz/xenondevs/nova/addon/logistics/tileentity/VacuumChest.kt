package xyz.xenondevs.nova.addon.logistics.tileentity

import kotlinx.coroutines.runBlocking
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.enumMap
import xyz.xenondevs.commons.provider.combinedProvider
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.VirtualInventory
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.logistics.registry.Blocks.VACUUM_CHEST
import xyz.xenondevs.nova.addon.logistics.registry.GuiItems
import xyz.xenondevs.nova.addon.logistics.util.getItemFilter
import xyz.xenondevs.nova.addon.logistics.util.isItemFilter
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.ui.menu.addIngredient
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.CUBE_FACES
import xyz.xenondevs.nova.util.serverTick
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.ItemFilter
import xyz.xenondevs.nova.world.region.Region

private val MIN_RANGE = VACUUM_CHEST.config.entry<Int>("range", "min")
private val MAX_RANGE = VACUUM_CHEST.config.entry<Int>("range", "max")
private val DEFAULT_RANGE by VACUUM_CHEST.config.entry<Int>("range", "default")

private val EXTRACT_SIDE_CONFIG = { CUBE_FACES.associateWithTo(enumMap()) { NetworkConnectionType.EXTRACT } }

class VacuumChest(pos: BlockPos, state: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, state, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.RANGE)
    private val inventory = storedInventory("inventory", 9)
    private val filterInventory = VirtualInventory(null, 1, arrayOfNulls(1), intArrayOf(1))
    private var filter: ItemFilter<*>? by storedValue("itemFilter")
    private val region = storedRegion(
        "region.default",
        MIN_RANGE,
        combinedProvider(
            MAX_RANGE,
            upgradeHolder.getValueProvider(UpgradeTypes.RANGE)
        ) { default, extra -> default + extra },
        DEFAULT_RANGE
    ) { Region.surrounding(pos, it) }
    private val itemHolder = storedItemHolder(
        inventory to NetworkConnectionType.BUFFER,
        defaultConnectionConfig = EXTRACT_SIDE_CONFIG
    )
    
    private val items = ArrayList<Item>()
    
    init {
        if (filter != null)
            filterInventory.setItem(SELF_UPDATE_REASON, 0, filter!!.toItemStack())
        
        filterInventory.setPreUpdateHandler(::handleFilterInventoryUpdate)
        filterInventory.guiPriority = 1
    }
    
    override fun handleTick() {
        items.forEach {
            if (it.isValid) {
                val itemStack = it.itemStack
                val remaining = inventory.addItem(null, itemStack)
                if (remaining != 0) it.itemStack = itemStack.apply { amount = remaining }
                else it.remove()
            }
        }
        
        items.clear()
        
        if (serverTick % 10 == 0) {
            pos.world.getNearbyEntities(region.toBoundingBox()).forEach {
                if (it is Item
                    && filter?.allows(it.itemStack) != false
                    && inventory.canHold(it.itemStack.clone().apply { amount = 1 })
                    && runBlocking { ProtectionManager.canInteractWithEntity(this@VacuumChest, it, null) } // TODO: non-blocking
                ) {
                    items += it
                    it.velocity = pos.location.subtract(it.location).toVector()
                }
            }
        }
    }
    
    private fun handleFilterInventoryUpdate(event: ItemPreUpdateEvent) {
        val newStack = event.newItem
        if (newStack != null) {
            if (newStack.isItemFilter()) {
                val f = newStack.getItemFilter() // temp variable required for type inference (?)
                filter = f
            } else event.isCancelled = true
        } else {
            filter = null
        }
    }
    
    @TileEntityMenuClass
    inner class VacuumChestGui(player: Player) : IndividualTileEntityMenu(player) {
        
        private val sideConfigMenu = SideConfigMenu(
            this@VacuumChest,
            mapOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default"),
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
            .addIngredient('f', filterInventory, GuiItems.ITEM_FILTER_PLACEHOLDER)
            .addIngredient('s', OpenSideConfigItem(sideConfigMenu))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('r', region.createVisualizeRegionItem(player))
            .addIngredient('p', region.increaseSizeItem)
            .addIngredient('m', region.decreaseSizeItem)
            .addIngredient('d', region.displaySizeItem)
            .build()
        
    }
    
}