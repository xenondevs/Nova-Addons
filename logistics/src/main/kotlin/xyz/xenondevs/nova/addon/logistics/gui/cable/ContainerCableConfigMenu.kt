package xyz.xenondevs.nova.addon.logistics.gui.cable

import net.kyori.adventure.text.Component
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import xyz.xenondevs.invui.Click
import xyz.xenondevs.invui.item.AbstractItem
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.ItemProvider
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
    
    @Volatile
    protected var allowsExtract = false
    
    @Volatile
    protected var allowsInsert = false
    
    @Volatile
    protected var insertPriority = -1
    
    @Volatile
    protected var extractPriority = -1
    
    @Volatile
    protected var insertState = false
    
    @Volatile
    protected var extractState = false
    
    @Volatile
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
    open fun updateGui() {
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
        
        override fun getItemProvider(player: Player): ItemProvider {
            val item = if (insertState) DefaultGuiItems.GREEN_BTN else DefaultGuiItems.RED_BTN
            return item.createClientsideItemBuilder()
                .setName(Component.translatable("menu.logistics.cable_config.insert"))
        }
        
        override fun handleClick(clickType: ClickType, player: Player, click: Click) {
            if (!allowsInsert)
                return
            
            insertState = !insertState
            notifyWindows()
            player.playClickSound()
        }
        
    }
    
    protected inner class ExtractItem : AbstractItem() {
        
        override fun getItemProvider(player: Player): ItemProvider {
            val item = if (extractState) DefaultGuiItems.GREEN_BTN else DefaultGuiItems.RED_BTN
            return item.createClientsideItemBuilder()
                .setName(Component.translatable("menu.logistics.cable_config.extract"))
        }
        
        override fun handleClick(clickType: ClickType, player: Player, click: Click) {
            if (!allowsExtract) return
            
            extractState = !extractState
            notifyWindows()
            player.playClickSound()
        }
        
    }
    
    protected inner class SwitchChannelItem : AbstractItem() {
        
        override fun getItemProvider(player: Player): ItemProvider {
            return BUTTON_COLORS[channel].createClientsideItemBuilder()
                .setName(Component.translatable("menu.logistics.cable_config.channel", Component.text(channel + 1)))
        }
        
        override fun handleClick(clickType: ClickType, player: Player, click: Click) {
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