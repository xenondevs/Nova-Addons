package xyz.xenondevs.nova.addon.simpleupgrades.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.invui.Click
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.gui.Markers
import xyz.xenondevs.invui.gui.ScrollGui
import xyz.xenondevs.invui.inventory.VirtualInventory
import xyz.xenondevs.invui.inventory.event.ItemPostUpdateEvent
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.item.AbstractItem
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.invui.window.Window
import xyz.xenondevs.nova.addon.simpleupgrades.UpgradeHolder
import xyz.xenondevs.nova.addon.simpleupgrades.UpgradeType
import xyz.xenondevs.nova.addon.simpleupgrades.registry.GuiItems
import xyz.xenondevs.nova.ui.menu.item.BackItem
import xyz.xenondevs.nova.ui.menu.item.ScrollLeftItem
import xyz.xenondevs.nova.ui.menu.item.ScrollRightItem
import xyz.xenondevs.nova.util.addItemCorrectly
import xyz.xenondevs.nova.util.item.novaItem
import xyz.xenondevs.nova.util.playClickSound
import xyz.xenondevs.nova.util.playItemPickupSound
import xyz.xenondevs.nova.world.block.tileentity.TileEntity
import xyz.xenondevs.nova.world.item.DefaultGuiItems

private fun ItemStack.getUpgradeType(): UpgradeType<*>? =
    novaItem?.let { UpgradeType.of<UpgradeType<*>>(it) }

internal class UpgradesGui(val upgradeHolder: UpgradeHolder, openPrevious: (Player) -> Unit) {
    
    private val input = VirtualInventory(null, 1).apply { addPreUpdateHandler(::handlePreInvUpdate); addPostUpdateHandler(::handlePostInvUpdate) }
    
    private val upgradeItems = ArrayList<Item>()
    
    private val upgradeScrollGui = ScrollGui.itemsBuilder()
        .setStructure(
            "x x x x x",
            "x x x x x",
            "< - - - >"
        )
        .addIngredient('<', ScrollLeftItem())
        .addIngredient('>', ScrollRightItem())
        .addIngredient('x', Markers.CONTENT_LIST_SLOT_VERTICAL)
        .setBackground(DefaultGuiItems.INVENTORY_PART.clientsideProvider)
        .setContent(createUpgradeItemList())
        .build()
    
    val gui: Gui = Gui.builder()
        .setStructure(
            "b - - - - - - - 2",
            "| i # . . . . . |",
            "| # # . . . . . |",
            "3 - - . . . . . 4"
        )
        .addIngredient('i', input)
        .addIngredient('b', BackItem(openPrevious = openPrevious))
        .build()
        .apply { fillRectangle(3, 1, upgradeScrollGui, true) }
    
    private fun createUpgradeItemList(): List<Item> {
        val list = ArrayList<Item>()
        upgradeHolder.allowed.forEach {
            list += UpgradeDisplay(it)
            list += UpgradeCounter(it)
        }
        return list
    }
    
    fun openWindow(player: Player) {
        val window = Window.builder()
            .setViewer(player)
            .setTitle(Component.translatable("menu.simple_upgrades.upgrades"))
            .setUpperGui(gui)
            .build()
        
        upgradeHolder.tileEntity.menuContainer.registerWindow(window)
        window.open()
    }
    
    fun updateUpgrades() {
        upgradeItems.forEach(Item::notifyWindows)
    }
    
    private fun handlePreInvUpdate(event: ItemPreUpdateEvent) {
        if (event.updateReason == TileEntity.SELF_UPDATE_REASON || event.isRemove || event.newItem == null)
            return
        
        val upgradeType = event.newItem!!.getUpgradeType()
        if (upgradeType == null || upgradeType !in upgradeHolder.allowed) {
            event.isCancelled = true
            return
        }
        
        val limit = upgradeHolder.getLimit(upgradeType)
        
        val currentAmount = upgradeHolder.getLevel(upgradeType)
        if (currentAmount == limit) {
            event.isCancelled = true
            return
        }
        
        var addedAmount = event.addedAmount
        if (addedAmount + currentAmount > limit) {
            addedAmount = limit - currentAmount
            event.newItem!!.amount = addedAmount
        }
    }
    
    private fun handlePostInvUpdate(event: ItemPostUpdateEvent) {
        var item = event.newItem?.clone()
        if (item != null) {
            val upgradeType = item.getUpgradeType()!!
            
            val amountLeft = upgradeHolder.addUpgrade(upgradeType, item.amount)
            if (amountLeft == 0) item = null
            else item.amount = amountLeft
            input.setItem(TileEntity.SELF_UPDATE_REASON, 0, item)
        }
    }
    
    private inner class UpgradeDisplay(private val type: UpgradeType<*>) : AbstractItem() {
        
        init {
            upgradeItems += this
        }
        
        override fun getItemProvider(player: Player): ItemProvider {
            val builder = type.icon.createClientsideItemBuilder()
            val typeId = type.id
            builder.setName(Component.translatable(
                "menu.${typeId.namespace}.upgrades.type.${typeId.path}",
                NamedTextColor.GRAY,
                Component.text(upgradeHolder.getLevel(type)),
                Component.text(upgradeHolder.getLimit(type))
            ))
            
            return builder
        }
        
        override fun handleClick(clickType: ClickType, player: Player, click: Click) {
            val item = upgradeHolder.removeUpgrade(type, clickType.isShiftClick) ?: return
            val location = player.location
            val leftover = player.inventory.addItemCorrectly(item)
            if (leftover != 0) location.world!!.dropItemNaturally(location, item.apply { amount = leftover })
            else player.playItemPickupSound()
        }
        
    }
    
    private inner class UpgradeCounter(private val type: UpgradeType<*>) : AbstractItem() {
        
        init {
            upgradeItems += this
        }
        
        override fun getItemProvider(player: Player): ItemProvider =
            DefaultGuiItems.NUMBER.createClientsideItemBuilder().addCustomModelData(upgradeHolder.getLevel(type))
        
        override fun handleClick(clickType: ClickType, player: Player, click: Click) = Unit
        
    }
    
}

class OpenUpgradesItem(private val upgradeHolder: UpgradeHolder) : AbstractItem() {
    
    override fun getItemProvider(player: Player) = GuiItems.UPGRADES_BTN.clientsideProvider
    
    override fun handleClick(clickType: ClickType, player: Player, click: Click) {
        player.playClickSound()
        upgradeHolder.gui.value.openWindow(player)
    }
    
}