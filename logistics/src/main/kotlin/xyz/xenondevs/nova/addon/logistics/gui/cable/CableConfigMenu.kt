package xyz.xenondevs.nova.addon.logistics.gui.cable

import net.kyori.adventure.text.Component
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.gui.TabGui
import xyz.xenondevs.invui.window.Window
import xyz.xenondevs.nova.ui.menu.item.ClickyTabItem
import xyz.xenondevs.nova.world.block.tileentity.network.NetworkManager
import xyz.xenondevs.nova.world.block.tileentity.network.ProtoNetwork
import xyz.xenondevs.nova.world.block.tileentity.network.node.ContainerEndPointDataHolder
import xyz.xenondevs.nova.world.block.tileentity.network.node.NetworkBridge
import xyz.xenondevs.nova.world.block.tileentity.network.node.NetworkEndPoint
import xyz.xenondevs.nova.world.block.tileentity.network.type.DefaultNetworkTypes
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkType
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.holder.FluidHolder
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.holder.ItemHolder
import xyz.xenondevs.nova.world.format.NetworkState
import xyz.xenondevs.nova.world.item.DefaultGuiItems

class CableConfigMenu(
    private val bridge: NetworkBridge,
    private val endPoint: NetworkEndPoint,
    val itemHolder: ItemHolder?,
    val fluidHolder: FluidHolder?,
    private val face: BlockFace
) {
    
    private val gui: Gui
    
    private val itemConfigGui = itemHolder?.let { ItemCableConfigMenu(endPoint, it, face) }
    private val fluidConfigGui = fluidHolder?.let { FluidCableConfigMenu(endPoint, it, face) }
    
    init {
        require(itemConfigGui != null || fluidConfigGui != null)
        
        gui = TabGui.builder()
            .setStructure(
                "# # # i # f # # #",
                "- - - - - - - - -",
                "x x x x x x x x x",
                "x x x x x x x x x",
                "x x x x x x x x x"
            )
            .addIngredient('i', ClickyTabItem(0) {
                val item = if (itemConfigGui != null) {
                    if (it.tab == 0)
                        DefaultGuiItems.ITEM_BTN_SELECTED
                    else DefaultGuiItems.ITEM_BTN_ON
                } else DefaultGuiItems.ITEM_BTN_OFF
                item.clientsideProvider
            })
            .addIngredient('f', ClickyTabItem(1) {
                val item = if (fluidConfigGui != null) {
                    if (it.tab == 1)
                        DefaultGuiItems.FLUID_BTN_SELECTED
                    else DefaultGuiItems.FLUID_BTN_ON
                } else DefaultGuiItems.FLUID_BTN_OFF
                item.clientsideProvider
            })
            .setTabs(listOf(itemConfigGui?.gui, fluidConfigGui?.gui))
            .build()
    }
    
    fun openWindow(player: Player) {
        Window.builder()
            .setTitle(Component.translatable("menu.logistics.cable_config"))
            .setUpperGui(gui)
            .addCloseHandler { queueWriteChanges() }
            .open(player)
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
        NetworkManager.queue(endPoint.pos.chunkPos) { state ->
            val clustersToInit = HashSet<ProtoNetwork<*>>()
            val clustersToEnlarge = HashSet<ProtoNetwork<*>>()
            
            var hasChanged = false
            
            if (itemHolder != null && itemConfigGui != null && itemConfigGui.writeChanges()) {
                hasChanged = true
                applyChangesToState(state, DefaultNetworkTypes.ITEM, itemHolder, clustersToInit, clustersToEnlarge)
            }
            if (fluidHolder != null && fluidConfigGui != null && fluidConfigGui.writeChanges()) {
                hasChanged = true
                applyChangesToState(state, DefaultNetworkTypes.FLUID, fluidHolder, clustersToInit, clustersToEnlarge)
            }
            
            if (!hasChanged)
                return@queue false
            
            for (network in clustersToInit) {
                network.initCluster()
            }
            
            for (network in clustersToEnlarge) {
                network.enlargeCluster(endPoint)
            }
            
            endPoint.handleNetworkUpdate(state)
            bridge.handleNetworkUpdate(state)
            
            return@queue true
        }
    }
    
    private suspend fun <H : ContainerEndPointDataHolder<*>> applyChangesToState(
        state: NetworkState,
        type: NetworkType<*>,
        holder: H,
        clustersToInit: MutableSet<ProtoNetwork<*>>,
        clustersToEnlarge: MutableSet<ProtoNetwork<*>>
    ) {
        val network = state.getNetwork(bridge, type)!!
        network.markDirty()
        
        if (face !in holder.allowedFaces) {
            state.removeConnection(endPoint, type, face)
            state.removeConnection(bridge, type, face.oppositeFace)
            state.removeNetwork(endPoint, type, face)
            if (network.removeFace(endPoint, face)) {
                network.cluster?.forEach { previouslyClusteredNetwork ->
                    previouslyClusteredNetwork.invalidateCluster()
                    clustersToInit += previouslyClusteredNetwork
                }
            }
        } else {
            state.setConnection(endPoint, type, face)
            state.setConnection(bridge, type, face.oppositeFace)
            state.setNetwork(endPoint, face, network)
            if (network.addEndPoint(endPoint, face)) {
                clustersToEnlarge += network
            }
        }
    }
    
    fun closeForAllViewers() {
        gui.closeForAllViewers()
    }
    
}