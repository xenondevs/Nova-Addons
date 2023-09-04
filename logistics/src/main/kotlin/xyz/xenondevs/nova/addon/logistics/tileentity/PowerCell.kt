package xyz.xenondevs.nova.addon.logistics.tileentity

import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.immutable.provider
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.addon.logistics.registry.Blocks
import xyz.xenondevs.nova.data.config.Reloadable
import xyz.xenondevs.nova.data.config.entry
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType.BUFFER
import xyz.xenondevs.nova.tileentity.network.energy.holder.BufferEnergyHolder
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu

class PowerCell(
    creative: Boolean,
    maxEnergy: Provider<Long>,
    blockState: NovaTileEntityState
) : NetworkedTileEntity(blockState), Reloadable {
    
    override val energyHolder = BufferEnergyHolder(this, maxEnergy, creative) { createSideConfig(BUFFER) }
    
    override fun handleTick() = Unit
    
    @TileEntityMenuClass
    inner class PowerCellMenu : GlobalTileEntityMenu() {
        
        private val SideConfigMenu = SideConfigMenu(this@PowerCell, ::openWindow)
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s # # e # # # |",
                "| # # # e # # # |",
                "| # # # e # # # |",
                "3 - - - - - - - 4")
            .addIngredient('s', OpenSideConfigItem(SideConfigMenu))
            .addIngredient('e', EnergyBar(3, energyHolder))
            .build()
        
    }
    
}

fun createBasicPowerCell(blockState: NovaTileEntityState): PowerCell = PowerCell(
    false,
    Blocks.BASIC_POWER_CELL.config.entry<Long>("capacity"),
    blockState
)

fun createAdvancedPowerCell(blockState: NovaTileEntityState): PowerCell = PowerCell(
    false,
    Blocks.ADVANCED_POWER_CELL.config.entry<Long>("capacity"),
    blockState
)

fun createElitePowerCell(blockState: NovaTileEntityState): PowerCell = PowerCell(
    false,
    Blocks.ELITE_POWER_CELL.config.entry<Long>("capacity"),
    blockState
)

fun createUltimatePowerCell(blockState: NovaTileEntityState): PowerCell = PowerCell(
    false,
    Blocks.ULTIMATE_POWER_CELL.config.entry<Long>("capacity"),
    blockState
)

fun createCreativePowerCell(blockState: NovaTileEntityState): PowerCell = PowerCell(
    true,
    provider(Long.MAX_VALUE),
    blockState
)
