package xyz.xenondevs.nova.addon.logistics.tileentity

import xyz.xenondevs.commons.collections.enumMap
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.addon.logistics.registry.GuiMaterials
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.fluid.FluidType
import xyz.xenondevs.nova.tileentity.network.fluid.container.FluidContainer
import xyz.xenondevs.nova.tileentity.network.fluid.holder.NovaFluidHolder
import xyz.xenondevs.nova.tileentity.network.item.holder.NovaItemHolder
import xyz.xenondevs.nova.ui.addIngredient
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu
import xyz.xenondevs.nova.util.CUBE_FACES
import xyz.xenondevs.nova.util.VoidingVirtualInventory
import java.util.*

private val ALL_INSERT_CONFIG = { CUBE_FACES.associateWithTo(enumMap()) { NetworkConnectionType.INSERT } }

class TrashCan(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState) {
    
    private val inventory = VoidingVirtualInventory(1)
    override val itemHolder = NovaItemHolder(
        this,
        inventory to NetworkConnectionType.INSERT,
        defaultConnectionConfig = ALL_INSERT_CONFIG
    )
    override val fluidHolder = NovaFluidHolder(this,
        VoidingFluidContainer to NetworkConnectionType.INSERT,
        defaultConnectionConfig = ALL_INSERT_CONFIG
    )
    
    override fun handleTick() = Unit
    
    @TileEntityMenuClass
    private inner class TrashCanMenu : GlobalTileEntityMenu() {
        
        private val SideConfigMenu = SideConfigMenu(
            this@TrashCan,
            listOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default"),
            listOf(VoidingFluidContainer to "container.nova.fluid_tank"),
            ::openWindow
        )
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s # # i # # # |",
                "3 - - - - - - - 4")
            .addIngredient('i', inventory, GuiMaterials.TRASH_CAN_PLACEHOLDER)
            .addIngredient('s', OpenSideConfigItem(SideConfigMenu))
            .build()
        
    }
    
}

object VoidingFluidContainer : FluidContainer(UUID(0, 1L), hashSetOf(FluidType.WATER, FluidType.LAVA), FluidType.NONE, 0, Long.MAX_VALUE) {
    override fun addFluid(type: FluidType, amount: Long) = Unit
    override fun tryAddFluid(type: FluidType, amount: Long) = amount
    override fun takeFluid(amount: Long) = Unit
    override fun tryTakeFluid(amount: Long) = 0L
    override fun accepts(type: FluidType, amount: Long) = true
    override fun clear() = Unit
    override fun isFull() = false
    override fun hasFluid() = false
    override fun isEmpty() = true
}