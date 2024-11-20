package xyz.xenondevs.nova.addon.logistics.gui.cable

import net.kyori.adventure.text.Component
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.invui.item.builder.setDisplayName
import xyz.xenondevs.invui.item.impl.AbstractItem
import xyz.xenondevs.invui.item.notifyWindows
import xyz.xenondevs.nova.ui.menu.item.BUTTON_COLORS
import xyz.xenondevs.nova.util.playClickSound
import xyz.xenondevs.nova.world.block.tileentity.network.node.ContainerEndPointDataHolder
import xyz.xenondevs.nova.world.block.tileentity.network.node.NetworkEndPoint
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType
import xyz.xenondevs.nova.world.item.DefaultGuiItems

abstract class ContainerCableConfigMenu<H : ContainerEndPointDataHolder<*>>(
    val endPoint: NetworkEndPoint,
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
    
    /**
     * Loads the values relevant for this menu from the [holder].
     *
     * Should only be called from the network configurator context.
     */
    open fun updateValues() {
        val allowedConnection = holder.containers[holder.containerConfig[face]]!!
        allowsExtract = allowedConnection.extract
        allowsInsert = allowedConnection.insert
        insertPriority = holder.insertPriorities[face]!!
        extractPriority = holder.extractPriorities[face]!!
        insertState = holder.connectionConfig[face]!!.insert
        extractState = holder.connectionConfig[face]!!.extract
        channel = holder.channels[face]!!
    }
    
    /**
     * Updates the UI elements of this menu.
     *
     * Should only be called from the main thread.
     */
    fun updateGui() {
        updatableItems.notifyWindows()
    }
    
    /**
     * Writes the values of this menu back to the [holder].
     * Should only be called from the network configurator context.
     *
     * @return `true` if anything changed
     */
    open fun writeChanges(): Boolean {
        var changed = false
        
        val newConnectionType = NetworkConnectionType.of(insertState, extractState)
        
        changed = changed or (holder.connectionConfig.put(face, newConnectionType) != newConnectionType)
        changed = changed or (holder.insertPriorities.put(face, insertPriority) != insertPriority)
        changed = changed or (holder.extractPriorities.put(face, extractPriority) != extractPriority)
        changed = changed or (holder.channels.put(face, channel) != channel)
        
        return changed
    }
    
    protected inner class InsertItem : AbstractItem() {
        
        override fun getItemProvider(): ItemProvider {
            val item = if (insertState) DefaultGuiItems.GREEN_BTN else DefaultGuiItems.RED_BTN
            return item.model.createClientsideItemBuilder()
                .setDisplayName(Component.translatable("menu.logistics.cable_config.insert"))
        }
        
        override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) {
            if (!allowsInsert)
                return
            
            insertState = !insertState
            notifyWindows()
            player.playClickSound()
        }
        
    }
    
    protected inner class ExtractItem : AbstractItem() {
        
        override fun getItemProvider(): ItemProvider {
            val item = if (extractState) DefaultGuiItems.GREEN_BTN else DefaultGuiItems.RED_BTN
            return item.model.createClientsideItemBuilder()
                .setDisplayName(Component.translatable("menu.logistics.cable_config.extract"))
        }
        
        override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) {
            if (!allowsExtract) return
            
            extractState = !extractState
            notifyWindows()
            player.playClickSound()
        }
        
    }
    
    protected inner class SwitchChannelItem : AbstractItem() {
        
        override fun getItemProvider(): ItemProvider {
            return BUTTON_COLORS[channel].model.createClientsideItemBuilder()
                .setDisplayName(Component.translatable("menu.logistics.cable_config.channel", Component.text(channel + 1)))
        }
        
        override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) {
            if (clickType == ClickType.RIGHT || clickType == ClickType.LEFT) {
                if (clickType == ClickType.LEFT) {
                    channel = (channel + 1).mod(channelAmount)
                } else {
                    channel = (channel - 1).mod(channelAmount)
                }
                
                notifyWindows()
                player.playClickSound()
            }
        }
        
    }
    
}