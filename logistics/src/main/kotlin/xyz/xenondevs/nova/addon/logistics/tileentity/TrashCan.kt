package xyz.xenondevs.nova.addon.logistics.tileentity

import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.addon.logistics.registry.GuiItems
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.type.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.tileentity.network.type.fluid.container.NetworkedFluidContainer
import xyz.xenondevs.nova.ui.menu.addIngredient
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.VoidingVirtualInventory
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import java.util.*

class TrashCan(pos: BlockPos, state: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, state, data) {
    
    private val inventory = VoidingVirtualInventory(1)
    private val itemHolder = storedItemHolder(inventory to NetworkConnectionType.INSERT)
    private val fluidHolder = storedFluidHolder(VoidingFluidContainer to NetworkConnectionType.INSERT)
    
    override fun handleTick() = Unit
    
    @TileEntityMenuClass
    private inner class TrashCanMenu : GlobalTileEntityMenu() {
        
        private val sideConfigMenu = SideConfigMenu(
            this@TrashCan,
            mapOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default"),
            mapOf(VoidingFluidContainer to "container.nova.fluid_tank"),
            ::openWindow
        )
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s # # i # # # |",
                "3 - - - - - - - 4")
            .addIngredient('i', inventory, GuiItems.TRASH_CAN_PLACEHOLDER)
            .addIngredient('s', OpenSideConfigItem(sideConfigMenu))
            .build()
        
    }
    
}

object VoidingFluidContainer : NetworkedFluidContainer {
    
    override val allowedTypes = FluidType.entries.toSet()
    override val amount = 0L
    override val capacity = Long.MAX_VALUE
    override val type = null
    override val uuid = UUID(0L, 1L)
    
    override fun addFluid(type: FluidType, amount: Long): Long {
        return amount
    }
    
    override fun takeFluid(amount: Long): Long {
        return 0
    }
    
}