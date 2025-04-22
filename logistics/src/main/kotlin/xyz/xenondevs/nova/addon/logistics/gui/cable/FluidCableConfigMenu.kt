package xyz.xenondevs.nova.addon.logistics.gui.cable

import org.bukkit.block.BlockFace
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.ui.menu.item.AddNumberItem
import xyz.xenondevs.nova.ui.menu.item.DisplayNumberItem
import xyz.xenondevs.nova.ui.menu.item.RemoveNumberItem
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
        
        gui = Gui.builder()
            .setStructure(
                "# p # # # # # P #",
                "# d # e c i # D #",
                "# m # # # # # M #")
            .addIngredient('i', InsertItem().also(updatableItems::add))
            .addIngredient('e', ExtractItem().also(updatableItems::add))
            .addIngredient('P', AddNumberItem({ 0..100 }, { insertPriority }, { insertPriority = it; updateGui() }).also(updatableItems::add))
            .addIngredient('M', RemoveNumberItem({ 0..100 }, { insertPriority }, { insertPriority = it; updateGui() }).also(updatableItems::add))
            .addIngredient('D', DisplayNumberItem({ insertPriority }, "menu.logistics.cable_config.insert_priority").also(updatableItems::add))
            .addIngredient('p', AddNumberItem({ 0..100 }, { extractPriority }, { extractPriority = it; updateGui() }).also(updatableItems::add))
            .addIngredient('m', RemoveNumberItem({ 0..100 }, { extractPriority }, { extractPriority = it; updateGui() }).also(updatableItems::add))
            .addIngredient('d', DisplayNumberItem({ extractPriority }, "menu.logistics.cable_config.extract_priority").also(updatableItems::add))
            .addIngredient('c', SwitchChannelItem().also(updatableItems::add))
            .build()
    }
    
}