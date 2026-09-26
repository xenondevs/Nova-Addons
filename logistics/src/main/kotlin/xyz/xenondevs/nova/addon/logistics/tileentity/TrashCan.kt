package xyz.xenondevs.nova.addon.logistics.tileentity

import org.bukkit.block.Block
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.inventory.VirtualInventory
import xyz.xenondevs.invui.inventory.event.UpdateReason
import xyz.xenondevs.nova.addon.logistics.registry.GuiTextures
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.container.NetworkedFluidContainer
import java.util.*

class TrashCan(pos: Block, state: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, state, data) {
    
    private val inventory = VirtualInventory(1).apply { addPostUpdateHandler { setItem(UpdateReason.SUPPRESSED, 0, null) } }
    private val itemHolder = storedItemHolder(inventory to NetworkConnectionType.INSERT)
    private val fluidHolder = storedFluidHolder(VoidingFluidContainer to NetworkConnectionType.INSERT)
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.TRASH_CAN) {
        upperGui by gui(
            "s . . . i . . . .",
        ) {
            's' by openSideConfigItem(
                mapOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default"),
                mapOf(VoidingFluidContainer to "container.nova.fluid_tank"),
            )
            'i' by inventory
        }
    }
    
    override fun handleTick() = Unit
    
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