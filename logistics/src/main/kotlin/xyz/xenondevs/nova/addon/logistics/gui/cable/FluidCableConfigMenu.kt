package xyz.xenondevs.nova.addon.logistics.gui.cable

import org.bukkit.block.BlockFace
import xyz.xenondevs.commons.provider.provider
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.ui.menu.item.addNumberItem
import xyz.xenondevs.nova.ui.menu.item.displayNumberItem
import xyz.xenondevs.nova.ui.menu.item.removeNumberItem
import xyz.xenondevs.nova.world.block.tileentity.network.node.NetworkEndPoint
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidNetwork
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.holder.FluidHolder

class FluidCableConfigMenu(
    endPoint: NetworkEndPoint,
    holder: FluidHolder,
    face: BlockFace
) : ContainerCableConfigMenu<FluidHolder>(endPoint, holder, face, FluidNetwork.CHANNEL_AMOUNT) {
    
    val gui: Gui
    
    init {
        updateValues()
        
        val priorityRange = provider(0..100)
        gui = gui(
            "p . . c . . P",
            "d . e . i . D",
            "m . . . . . M"
        ) {
            'i' by insertItem()
            'e' by extractItem()
            'P' by addNumberItem(priorityRange, insertPriority)
            'M' by removeNumberItem(priorityRange, insertPriority)
            'D' by displayNumberItem(insertPriority, "menu.logistics.cable_config.insert_priority")
            'p' by addNumberItem(priorityRange, extractPriority)
            'm' by removeNumberItem(priorityRange, extractPriority)
            'd' by displayNumberItem(extractPriority, "menu.logistics.cable_config.extract_priority")
            'c' by switchChannelItem()
        }
    }
    
}
