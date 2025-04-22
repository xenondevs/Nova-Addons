package xyz.xenondevs.nova.addon.logistics.tileentity

import org.bukkit.entity.Display
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.provider
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.addon.logistics.registry.Blocks
import xyz.xenondevs.nova.addon.logistics.registry.Models
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.FluidBar
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.world.fakeentity.impl.FakeItemDisplay
import kotlin.math.roundToInt

private const val MAX_STATE = 99

open class FluidTank(
    capacity: Provider<Long>,
    pos: BlockPos, state: NovaBlockState, data: Compound
) : NetworkedTileEntity(pos, state, data) {
    
    val fluidContainer = storedFluidContainer("tank", hashSetOf(FluidType.WATER, FluidType.LAVA), capacity, true, ::handleFluidUpdate)
    private val fluidHolder = storedFluidHolder(fluidContainer to NetworkConnectionType.BUFFER)
    private val fluidLevel = FakeItemDisplay(pos.location.add(0.5, 0.5, 0.5), false)
    
    override fun handleEnable() {
        super.handleEnable()
        fluidLevel.register()
        updateFluidLevel()
    }
    
    override fun handleDisable() {
        super.handleDisable()
        fluidLevel.remove()
    }
    
    private fun handleFluidUpdate() {
        // Creative Fluid Tank
        if (fluidContainer.capacity == Long.MAX_VALUE && !fluidContainer.isEmpty() && !fluidContainer.isFull())
            fluidContainer.addFluid(fluidContainer.type!!, fluidContainer.capacity - fluidContainer.amount)
        
        updateFluidLevel()
    }
    
    private fun updateFluidLevel() {
        fluidLevel.updateEntityData(true) {
            brightness = if (fluidContainer.type == FluidType.LAVA)
                Display.Brightness(15, 15)
            else null
            
            itemStack = if (!fluidContainer.isEmpty()) {
                val state = (fluidContainer.amount.toDouble() / fluidContainer.capacity.toDouble() * MAX_STATE.toDouble()).roundToInt()
                when (fluidContainer.type) {
                    FluidType.LAVA -> Models.TANK_LAVA_LEVELS
                    FluidType.WATER -> Models.TANK_WATER_LEVELS
                    else -> throw IllegalStateException()
                }.createClientsideItemBuilder().addCustomModelData(state).get()
            } else null
        }
    }
    
    @TileEntityMenuClass
    inner class FluidTankMenu : GlobalTileEntityMenu() {
        
        private val sideConfigMenu = SideConfigMenu(
            this@FluidTank,
            mapOf(fluidContainer to "container.nova.fluid_tank"),
            ::openWindow
        )
        
        override val gui = Gui.builder()
            .setStructure(
                "1 - - - - - - - 2",
                "| s # # f # # # |",
                "| # # # f # # # |",
                "| # # # f # # # |",
                "3 - - - - - - - 4")
            .addIngredient('s', OpenSideConfigItem(sideConfigMenu))
            .addIngredient('f', FluidBar(3, fluidHolder, fluidContainer))
            .build()
        
    }
    
}

class BasicFluidTank(pos: BlockPos, state: NovaBlockState, data: Compound) :
    FluidTank(Blocks.BASIC_FLUID_TANK.config.entry<Long>("capacity"), pos, state, data)

class AdvancedFluidTank(pos: BlockPos, state: NovaBlockState, data: Compound) :
    FluidTank(Blocks.ADVANCED_FLUID_TANK.config.entry<Long>("capacity"), pos, state, data)

class EliteFluidTank(pos: BlockPos, state: NovaBlockState, data: Compound) :
    FluidTank(Blocks.ELITE_FLUID_TANK.config.entry<Long>("capacity"), pos, state, data)

class UltimateFluidTank(pos: BlockPos, state: NovaBlockState, data: Compound) :
    FluidTank(Blocks.ULTIMATE_FLUID_TANK.config.entry<Long>("capacity"), pos, state, data)

class CreativeFluidTank(pos: BlockPos, state: NovaBlockState, data: Compound) :
    FluidTank(provider(Long.MAX_VALUE), pos, state, data)