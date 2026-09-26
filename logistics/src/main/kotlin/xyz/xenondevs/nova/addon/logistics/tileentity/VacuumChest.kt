package xyz.xenondevs.nova.addon.logistics.tileentity

import kotlinx.coroutines.runBlocking
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Item
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.combinedProvider
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.dsl.with
import xyz.xenondevs.invui.inventory.VirtualInventory
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.logistics.registry.Blocks.VACUUM_CHEST
import xyz.xenondevs.nova.addon.logistics.registry.GuiItems
import xyz.xenondevs.nova.addon.logistics.registry.GuiTextures
import xyz.xenondevs.nova.addon.logistics.util.getItemFilter
import xyz.xenondevs.nova.addon.logistics.util.isItemFilter
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.nova.util.CubeFaceMap
import xyz.xenondevs.nova.util.serverTick
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.ItemFilter
import xyz.xenondevs.nova.world.region.Region

private val MIN_RANGE = VACUUM_CHEST.config.entry<Int>("range", "min")
private val MAX_RANGE = VACUUM_CHEST.config.entry<Int>("range", "max")
private val DEFAULT_RANGE by VACUUM_CHEST.config.entry<Int>("range", "default")

private val EXTRACT_SIDE_CONFIG = CubeFaceMap(NetworkConnectionType.EXTRACT)

class VacuumChest(pos: Block, state: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, state, data) {
    
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
        
        filterInventory.addPreUpdateHandler(::handleFilterInventoryUpdate)
        filterInventory.setGuiPriority(1)
    }
    
    override fun requestsLocalNetwork(face: BlockFace): Boolean =
        itemHolder.connectionConfig[face] != NetworkConnectionType.BUFFER
    
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
            block.world.getNearbyEntities(region.toBoundingBox()).forEach {
                if (it is Item
                    && filter?.allows(it.itemStack) != false
                    && inventory.canHold(it.itemStack.clone().apply { amount = 1 })
                    && runBlocking { ProtectionManager.canInteractWithEntity(this@VacuumChest, it, null) } // TODO: non-blocking
                ) {
                    items += it
                    it.velocity = block.location.subtract(it.location).toVector()
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
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.VACUUM_CHEST) {
        upperGui by gui(
            "s u . i i i . . p",
            "r . . i i i . . d",
            "f . . i i i . . m",
        ) {
            'i' by inventory
            'f' by (filterInventory with GuiItems.ITEM_FILTER_PLACEHOLDER)
            's' by openSideConfigItem(
                mapOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default"),
            )
            'u' by openUpgradesItem(upgradeHolder)
            'r' by region.visualizeRegionItem
            'p' by region.increaseSizeItem
            'm' by region.decreaseSizeItem
            'd' by region.displaySizeItem
        }
    }
    
}