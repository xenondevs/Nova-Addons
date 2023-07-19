package xyz.xenondevs.nova.addon.logistics.gui.cable

import net.kyori.adventure.text.Component
import net.md_5.bungee.api.chat.TranslatableComponent
import org.bukkit.Sound
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.invui.item.builder.setDisplayName
import xyz.xenondevs.invui.item.impl.AbstractItem
import xyz.xenondevs.invui.item.notifyWindows
import xyz.xenondevs.nova.item.DefaultGuiItems
import xyz.xenondevs.nova.tileentity.network.ContainerEndPointDataHolder
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.NetworkManager
import xyz.xenondevs.nova.ui.item.BUTTON_COLORS

abstract class BaseCableConfigGui<H : ContainerEndPointDataHolder<*>>(
    val holder: H,
    val face: BlockFace,
    private val channelAmount: Int
) {
    
    protected val updatableItems = ArrayList<Item>()
    
    protected var allowsExtract = false
    protected var allowsInsert = false
    
    protected var insertPriority = -1
    protected var extractPriority = -1
    protected var insertState = false
    protected var extractState = false
    protected var channel = -1
    
    protected fun updateButtons() = updatableItems.notifyWindows()
    
    abstract fun updateValues(updateButtons: Boolean)
    
    protected fun updateCoreValues() {
        NetworkManager.execute { // TODO: queueSync / queueAsync ?
            val allowedConnections = holder.allowedConnectionTypes[holder.containerConfig[face]]!!
            allowsExtract = allowedConnections.extract
            allowsInsert = allowedConnections.insert
    
            insertPriority = holder.insertPriorities[face]!!
            extractPriority = holder.extractPriorities[face]!!
            insertState = holder.connectionConfig[face]!!.insert
            extractState = holder.connectionConfig[face]!!.extract
            channel = holder.channels[face]!!
        }
    }
    
    open fun writeChanges() {
        holder.insertPriorities[face] = insertPriority
        holder.extractPriorities[face] = extractPriority
        holder.channels[face] = channel
        holder.connectionConfig[face] = NetworkConnectionType.of(insertState, extractState)
    }
    
    protected inner class InsertItem : AbstractItem() {
        
        override fun getItemProvider(): ItemProvider =
            (if (insertState) DefaultGuiItems.GREEN_BTN else DefaultGuiItems.GRAY_BTN)
                .createClientsideItemBuilder().setDisplayName(Component.translatable("menu.logistics.cable_config.insert"))
        
        override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) {
            if (!allowsInsert) return
            
            insertState = !insertState
            notifyWindows()
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
        }
        
    }
    
    protected inner class ExtractItem : AbstractItem() {
        
        override fun getItemProvider(): ItemProvider =
            (if (extractState) DefaultGuiItems.GREEN_BTN else DefaultGuiItems.GRAY_BTN)
                .createClientsideItemBuilder().setDisplayName(Component.translatable("menu.logistics.cable_config.extract"))
        
        override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) {
            if (!allowsExtract) return
            
            extractState = !extractState
            notifyWindows()
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
        }
        
    }
    
    protected inner class SwitchChannelItem : AbstractItem() {
        
        override fun getItemProvider(): ItemProvider {
            return BUTTON_COLORS[channel].createClientsideItemBuilder()
                .setDisplayName(TranslatableComponent("menu.logistics.cable_config.channel", channel + 1))
        }
        
        override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) {
            if (clickType == ClickType.RIGHT || clickType == ClickType.LEFT) {
                if (clickType == ClickType.LEFT) channel++ else channel--
                if (channel >= channelAmount) channel = 0
                else if (channel < 0) channel = channelAmount - 1
                
                notifyWindows()
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
            }
        }
        
    }
    
}