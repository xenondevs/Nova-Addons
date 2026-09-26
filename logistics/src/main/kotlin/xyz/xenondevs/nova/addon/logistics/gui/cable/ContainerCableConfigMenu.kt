package xyz.xenondevs.nova.addon.logistics.gui.cable

import net.kyori.adventure.text.Component
import org.bukkit.block.BlockFace
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.invui.dsl.item
import xyz.xenondevs.invui.dsl.itemProvider
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.nova.ui.menu.item.TP_BUTTON_COLORS
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
    
    protected val allowsExtract = mutableProvider(false)
    protected val allowsInsert = mutableProvider(false)
    protected val insertPriority = mutableProvider(-1)
    protected val extractPriority = mutableProvider(-1)
    protected val insertState = mutableProvider(false)
    protected val extractState = mutableProvider(false)
    protected val channel = mutableProvider(-1)
    
    /**
     * Loads the values relevant for this menu from the [holder].
     *
     * Should only be called from the network configurator context.
     */
    open fun updateValues() {
        val allowedConnection = holder.containers[holder.containerConfig[face]]!!
        allowsExtract.set(allowedConnection.extract)
        allowsInsert.set(allowedConnection.insert)
        insertPriority.set(holder.insertPriorities[face])
        extractPriority.set(holder.extractPriorities[face])
        insertState.set(holder.connectionConfig[face].insert)
        extractState.set(holder.connectionConfig[face].extract)
        channel.set(holder.channels[face])
    }
    
    /**
     * Updates the UI elements of this menu.
     *
     * Should only be called from the main thread.
     */
    open fun updateGui() = Unit
    
    /**
     * Writes the values of this menu back to the [holder].
     * Should only be called from the network configurator context.
     *
     * @return `true` if anything changed
     */
    open fun writeChanges(): Boolean {
        var changed = false
        
        val newConnectionType = NetworkConnectionType.of(insertState.get(), extractState.get())
        
        if (holder.connectionConfig[face] != newConnectionType) {
            changed = true
            holder.connectionConfig = holder.connectionConfig.with(face, newConnectionType)
        }
        
        if (holder.insertPriorities[face] != insertPriority.get()) {
            changed = true
            holder.insertPriorities = holder.insertPriorities.with(face, insertPriority.get())
        }
        
        if (holder.extractPriorities[face] != extractPriority.get()) {
            changed = true
            holder.extractPriorities = holder.extractPriorities.with(face, extractPriority.get())
        }
        
        if (holder.channels[face] != channel.get()) {
            changed = true
            holder.channels = holder.channels.with(face, channel.get())
        }
        
        return changed
    }
    
    protected fun insertItem(): Item = item {
        itemProvider by itemProvider(insertState.flatMap {
            if (it) DefaultGuiItems.TP_GREEN_BTN else DefaultGuiItems.TP_RED_BTN
        }) {
            name by Component.translatable("menu.logistics.cable_config.insert")
        }
        onClick {
            if (allowsInsert.get()) {
                insertState.set(!insertState.get())
                player.playClickSound()
            }
        }
    }
    
    protected fun extractItem(): Item = item {
        itemProvider by itemProvider(extractState.flatMap {
            if (it) DefaultGuiItems.TP_GREEN_BTN else DefaultGuiItems.TP_RED_BTN
        }) {
            name by Component.translatable("menu.logistics.cable_config.extract")
        }
        onClick {
            if (allowsExtract.get()) {
                extractState.set(!extractState.get())
                player.playClickSound()
            }
        }
    }
    
    protected fun switchChannelItem(): Item = item {
        itemProvider by itemProvider(channel.flatMap { TP_BUTTON_COLORS[it] }) {
            name by channel.map { Component.translatable("menu.logistics.cable_config.channel", Component.text(it + 1)) }
        }
        onClick {
            if (clickType.isLeftClick || clickType.isRightClick) {
                val move = if (clickType.isLeftClick) 1 else -1
                channel.set((channel.get() + move).mod(channelAmount))
                player.playClickSound()
            }
        }
    }
    
}
