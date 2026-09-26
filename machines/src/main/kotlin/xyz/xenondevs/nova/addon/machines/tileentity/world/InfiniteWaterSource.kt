package xyz.xenondevs.nova.addon.machines.tileentity.world

import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.provider
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.ui.menu.fluidBar
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import org.bukkit.block.Block
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.container.NetworkedFluidContainer
import java.util.*

class InfiniteWaterSource(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val fluidContainer = InfiniteFluidContainer
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.CENTER_BAR) {
        upperGui by gui(
            "s . . . f . . . .",
            ". . . . f . . . .",
            ". . . . f . . . .",
        ) {
            's' by openSideConfigItem(containers = mapOf(fluidContainer to "block.minecraft.water"))
            'f' by fluidBar(provider(FluidType.WATER), provider(Long.MAX_VALUE), provider(Long.MAX_VALUE))
        }
    }
    
    init {
        storedFluidHolder(fluidContainer to NetworkConnectionType.EXTRACT)
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
