package xyz.xenondevs.nova.addon.logistics.tileentity

import net.minecraft.util.Brightness
import net.minecraft.world.item.ItemDisplayContext
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.immutable.provider
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.addon.logistics.registry.Blocks
import xyz.xenondevs.nova.addon.logistics.registry.Items
import xyz.xenondevs.nova.data.config.entry
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.fluid.FluidType
import xyz.xenondevs.nova.tileentity.network.fluid.holder.NovaFluidHolder
import xyz.xenondevs.nova.ui.FluidBar
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu
import xyz.xenondevs.nova.util.nmsCopy
import xyz.xenondevs.nova.world.fakeentity.impl.FakeItemDisplay
import kotlin.math.roundToInt

private const val MAX_STATE = 99

@Suppress("LeakingThis")
open class FluidTank(
    capacity: Provider<Long>,
    blockState: NovaTileEntityState
) : NetworkedTileEntity(blockState) {
    
    val fluidContainer = getFluidContainer("tank", hashSetOf(FluidType.WATER, FluidType.LAVA), capacity, 0, ::handleFluidUpdate)
    override val fluidHolder = NovaFluidHolder(this, fluidContainer to NetworkConnectionType.BUFFER) { createSideConfig(NetworkConnectionType.BUFFER) }
    private lateinit var fluidLevel: FakeItemDisplay
    
    override fun handleInitialized(first: Boolean) {
        super.handleInitialized(first)
        fluidLevel = FakeItemDisplay(location.add(.5, .5, .5)) { _, data -> data.itemDisplay = ItemDisplayContext.HEAD }
        updateFluidLevel()
    }
    
    private fun handleFluidUpdate() {
        // Creative Fluid Tank
        if (fluidContainer.capacity == Long.MAX_VALUE && fluidContainer.hasFluid() && !fluidContainer.isFull())
            fluidContainer.addFluid(fluidContainer.type!!, fluidContainer.capacity - fluidContainer.amount)
        
        updateFluidLevel()
    }
    
    override fun reload() {
        super.reload()
        updateFluidLevel()
    }
    
    private fun updateFluidLevel() {
        val stack = if (fluidContainer.hasFluid()) {
            val state = (fluidContainer.amount.toDouble() / fluidContainer.capacity.toDouble() * MAX_STATE.toDouble()).roundToInt()
            when (fluidContainer.type) {
                FluidType.LAVA -> Items.TANK_LAVA_LEVELS
                FluidType.WATER -> Items.TANK_WATER_LEVELS
                else -> throw IllegalStateException()
            }.clientsideProviders[state].get()
        } else null
        
        fluidLevel.updateEntityData(true) {
            brightness = if (fluidContainer.type == FluidType.LAVA) Brightness.FULL_BRIGHT else null
            itemStack = stack.nmsCopy
        }
    }
    
    override fun handleRemoved(unload: Boolean) {
        super.handleRemoved(unload)
        fluidLevel.remove()
    }
    
    @TileEntityMenuClass
    inner class FluidTankMenu : GlobalTileEntityMenu() {
        
        private val SideConfigMenu = SideConfigMenu(
            this@FluidTank,
            fluidContainerNames = listOf(fluidContainer to "container.nova.fluid_tank"),
            openPrevious = ::openWindow
        )
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s # # f # # # |",
                "| # # # f # # # |",
                "| # # # f # # # |",
                "3 - - - - - - - 4")
            .addIngredient('s', OpenSideConfigItem(SideConfigMenu))
            .addIngredient('f', FluidBar(3, fluidHolder, fluidContainer))
            .build()
        
    }
    
}

class BasicFluidTank(blockState: NovaTileEntityState) : FluidTank(Blocks.BASIC_FLUID_TANK.config.entry<Long>("capacity"), blockState)
class AdvancedFluidTank(blockState: NovaTileEntityState) : FluidTank(Blocks.ADVANCED_FLUID_TANK.config.entry<Long>("capacity"), blockState)
class EliteFluidTank(blockState: NovaTileEntityState) : FluidTank(Blocks.ELITE_FLUID_TANK.config.entry<Long>("capacity"), blockState)
class UltimateFluidTank(blockState: NovaTileEntityState) : FluidTank(Blocks.ULTIMATE_FLUID_TANK.config.entry<Long>("capacity"), blockState)
class CreativeFluidTank(blockState: NovaTileEntityState) : FluidTank(provider(Long.MAX_VALUE), blockState)