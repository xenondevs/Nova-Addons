package xyz.xenondevs.nova.addon.logistics.gui.cable

import net.kyori.adventure.text.Component
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.gui.TabGui
import xyz.xenondevs.invui.window.Window
import xyz.xenondevs.invui.window.type.context.setTitle
import xyz.xenondevs.nova.item.DefaultGuiItems
import xyz.xenondevs.nova.tileentity.network.NetworkEndPoint
import xyz.xenondevs.nova.tileentity.network.NetworkManager
import xyz.xenondevs.nova.tileentity.network.fluid.holder.FluidHolder
import xyz.xenondevs.nova.tileentity.network.item.holder.ItemHolder
import xyz.xenondevs.nova.ui.item.ClickyTabItem

class CableConfigGui(
    val endPoint: NetworkEndPoint,
    val itemHolder: ItemHolder?,
    val fluidHolder: FluidHolder?,
    private val face: BlockFace
) {
    
    private val gui: Gui
    
    private val itemConfigGui = itemHolder?.let { ItemCableConfigGui(it, face) }
    private val fluidConfigGui = fluidHolder?.let { FluidCableConfigGui(it, face) }
    
    init {
        require(itemConfigGui != null || fluidConfigGui != null)
        
        gui = TabGui.normal()
            .setStructure(
                "# # # i # f # # #",
                "- - - - - - - - -",
                "x x x x x x x x x",
                "x x x x x x x x x",
                "x x x x x x x x x"
            )
            .addIngredient('i', ClickyTabItem(0) {
                (if (itemConfigGui != null) {
                    if (it.currentTab == 0)
                        DefaultGuiItems.ITEM_BTN_SELECTED
                    else DefaultGuiItems.ITEM_BTN_ON
                } else DefaultGuiItems.ITEM_BTN_OFF).clientsideProvider
            })
            .addIngredient('f', ClickyTabItem(1) {
                (if (fluidConfigGui != null) {
                    if (it.currentTab == 1)
                        DefaultGuiItems.FLUID_BTN_SELECTED
                    else DefaultGuiItems.FLUID_BTN_ON
                } else DefaultGuiItems.FLUID_BTN_OFF).clientsideProvider
            })
            .setTabs(listOf(itemConfigGui?.gui, fluidConfigGui?.gui))
            .build()
    }
    
    fun openWindow(player: Player) {
        Window.single {
            it.setViewer(player)
            it.setTitle(Component.translatable("menu.logistics.cable_config"))
            it.setGui(gui)
            it.addCloseHandler(::writeChanges)
        }.open()
    }
    
    fun closeForAllViewers() {
        gui.closeForAllViewers()
    }
    
    fun updateValues(updateButtons: Boolean = true) {
        itemConfigGui?.updateValues(updateButtons)
        fluidConfigGui?.updateValues(updateButtons)
    }
    
    private fun writeChanges() {
        NetworkManager.queueAsync {
            it.removeEndPoint(endPoint, false)
            
            itemConfigGui?.writeChanges()
            fluidConfigGui?.writeChanges()
            
            it.addEndPoint(endPoint, false).thenRun { endPoint.updateNearbyBridges() }
        }
    }
    
}