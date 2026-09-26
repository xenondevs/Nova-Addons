package xyz.xenondevs.nova.addon.logistics.gui.cable

import net.kyori.adventure.text.Component
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.invui.dsl.tabGui
import xyz.xenondevs.invui.dsl.window
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.addon.logistics.registry.GuiTextures
import xyz.xenondevs.nova.ui.menu.item.tabItem
import xyz.xenondevs.nova.ui.menu.locale
import xyz.xenondevs.nova.ui.overlay.guitexture.getTitle
import xyz.xenondevs.nova.world.block.tileentity.network.NetworkManager
import xyz.xenondevs.nova.world.block.tileentity.network.node.ContainerEndPointDataHolder
import xyz.xenondevs.nova.world.block.tileentity.network.node.NetworkBridge
import xyz.xenondevs.nova.world.block.tileentity.network.node.NetworkEndPoint
import xyz.xenondevs.nova.world.block.tileentity.network.type.DefaultNetworkTypes
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkType
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.holder.FluidHolder
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.holder.ItemHolder
import xyz.xenondevs.nova.world.chunkPos
import xyz.xenondevs.nova.world.format.NetworkState
import xyz.xenondevs.nova.world.item.DefaultGuiItems
import xyz.xenondevs.nova.world.item.guiItemProvider

class CableConfigMenu(
    private val bridge: NetworkBridge,
    private val endPoint: NetworkEndPoint,
    val itemHolder: ItemHolder?,
    val fluidHolder: FluidHolder?,
    private val face: BlockFace
) {
    
    private val itemConfigGui = itemHolder?.let { ItemCableConfigMenu(endPoint, it, face) }
    private val fluidConfigGui = fluidHolder?.let { FluidCableConfigMenu(endPoint, it, face) }
    private val selectedTab = mutableProvider(if (itemConfigGui != null) 0 else 1)
    private val gui: Gui
    
    init {
        require(itemConfigGui != null || fluidConfigGui != null)
        
        gui = tabGui(
            "i | x x x x x x x",
            "f | x x x x x x x",
            ". | x x x x x x x"
        ) {
            tabs by listOf(itemConfigGui?.gui, fluidConfigGui?.gui)
            tab by selectedTab
            
            'i' by tabItem(
                0,
                DefaultGuiItems.TP_ITEM_BTN_SELECTED.guiItemProvider,
                DefaultGuiItems.TP_ITEM_BTN_ON.guiItemProvider,
                DefaultGuiItems.TP_ITEM_BTN_OFF.guiItemProvider
            )
            
            'f' by tabItem(
                1,
                DefaultGuiItems.TP_FLUID_BTN_SELECTED.guiItemProvider,
                DefaultGuiItems.TP_FLUID_BTN_ON.guiItemProvider,
                DefaultGuiItems.TP_FLUID_BTN_OFF.guiItemProvider
            )
            
        }
    }
    
    fun openWindow(player: Player) {
        window(player) {
            title by selectedTab.flatMap { tab ->
                val texture = if (tab == 0) GuiTextures.CABLE_CONFIG_ITEM else GuiTextures.CABLE_CONFIG_FLUID
                texture.getTitle([Component.translatable("menu.logistics.cable_config")], locale)
            }
            upperGui by gui
            onClose { queueWriteChanges() }
        }.open()
    }
    
    /**
     * Updates the values of [itemConfigGui] and [fluidConfigGui] by reading
     * from the [itemHolder] and [fluidHolder] respectively.
     *
     * Should only be called from the network configurator context.
     */
    fun updateValues() {
        itemConfigGui?.updateValues()
        fluidConfigGui?.updateValues()
    }
    
    /**
     * Updates the GUI elements of [itemConfigGui] and [fluidConfigGui].
     *
     * Should only be called from the main thread.
     */
    fun updateGui() {
        itemConfigGui?.updateGui()
        fluidConfigGui?.updateGui()
    }
    
    private fun queueWriteChanges() {
        NetworkManager.queue(endPoint.block.chunkPos) { state ->
            var hasChanged = false
            
            if (itemHolder != null && itemConfigGui != null && itemConfigGui.writeChanges()) {
                hasChanged = true
                applyChangesToState(state, DefaultNetworkTypes.ITEM.get(), itemHolder)
            }
            if (fluidHolder != null && fluidConfigGui != null && fluidConfigGui.writeChanges()) {
                hasChanged = true
                applyChangesToState(state, DefaultNetworkTypes.FLUID.get(), fluidHolder)
            }
            
            if (!hasChanged)
                return@queue false
            
            endPoint.handleNetworkUpdate(state)
            bridge.handleNetworkUpdate(state)
            
            return@queue true
        }
    }
    
    private suspend fun <H : ContainerEndPointDataHolder<*>> applyChangesToState(
        state: NetworkState,
        type: NetworkType<*>,
        holder: H,
    ) {
        val network = state.getNetwork(bridge, type)!!
        network.markDirty()
        
        if (face !in holder.allowedFaces) {
            state.removeConnection(endPoint, type, face)
            state.removeConnection(bridge, type, face.oppositeFace)
            state.removeNetwork(endPoint, type, face)
            if (network.removeFace(endPoint, face)) {
                network.cluster?.invalidate()
            }
        } else {
            state.setConnection(endPoint, type, face)
            state.setConnection(bridge, type, face.oppositeFace)
            state.setNetwork(endPoint, face, network)
            if (network.addEndPoint(endPoint, face)) {
                network.cluster?.invalidate()
            }
        }
    }
    
    fun closeForAllViewers() {
        gui.closeForAllViewers()
    }
    
}
