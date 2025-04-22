package xyz.xenondevs.nova.addon.machines.tileentity.world

import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.ui.menu.StaticFluidBar
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.container.NetworkedFluidContainer
import java.util.*

class InfiniteWaterSource(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val fluidContainer = InfiniteFluidContainer
    
    init {
        storedFluidHolder(fluidContainer to NetworkConnectionType.EXTRACT)
    }
    
    @TileEntityMenuClass
    inner class InfiniteWaterSourceMenu : GlobalTileEntityMenu() {
        
        private val sideConfigGui = SideConfigMenu(
            this@InfiniteWaterSource,
            mapOf(fluidContainer to "block.minecraft.water"),
            ::openWindow
        )
        
        override val gui = Gui.builder()
            .setStructure(
                "1 - - - - - - - 2",
                "| s # # f # # # |",
                "| # # # f # # # |",
                "| # # # f # # # |",
                "3 - - - - - - - 4")
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('f', StaticFluidBar(3, Long.MAX_VALUE, FluidType.WATER, Long.MAX_VALUE))
            .build()
        
    }
    
}

object InfiniteFluidContainer : NetworkedFluidContainer {
    
    override val uuid = UUID(0L, 0L)
    override val allowedTypes = setOf(FluidType.WATER)
    override val amount = Long.MAX_VALUE
    override val capacity = Long.MAX_VALUE
    override val type = FluidType.WATER
    
    override fun addFluid(type: FluidType, amount: Long): Long {
        return 0
    }
    
    override fun takeFluid(amount: Long): Long {
        return amount
    }
    
}
