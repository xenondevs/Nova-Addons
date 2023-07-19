package xyz.xenondevs.nova.addon.logistics.tileentity

import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.immutable.provider
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.data.config.NovaConfig
import xyz.xenondevs.nova.data.config.Reloadable
import xyz.xenondevs.nova.data.config.configReloadable
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.addon.logistics.registry.Blocks
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

private val BASIC_CAPACITY: Provider<Long> = configReloadable { NovaConfig[Blocks.BASIC_POWER_CELL].getLong("capacity") }
private val ADVANCED_CAPACITY: Provider<Long> = configReloadable { NovaConfig[Blocks.ADVANCED_POWER_CELL].getLong("capacity") }
private val ELITE_CAPACITY: Provider<Long> = configReloadable { NovaConfig[Blocks.ELITE_POWER_CELL].getLong("capacity") }
private val ULTIMATE_CAPACITY: Provider<Long> = configReloadable { NovaConfig[Blocks.ULTIMATE_POWER_CELL].getLong("capacity") }

fun createBasicPowerCell(blockState: NovaTileEntityState) = PowerCell(
    false,
    BASIC_CAPACITY,
    blockState
)

fun createAdvancedPowerCell(blockState: NovaTileEntityState) = PowerCell(
    false,
    ADVANCED_CAPACITY,
    blockState
)

fun createElitePowerCell(blockState: NovaTileEntityState) = PowerCell(
    false,
    ELITE_CAPACITY,
    blockState
)

fun createUltimatePowerCell(blockState: NovaTileEntityState) = PowerCell(
    false,
    ULTIMATE_CAPACITY,
    blockState
)

fun createCreativePowerCell(blockState: NovaTileEntityState) = PowerCell(
    true,
    provider(Long.MAX_VALUE),
    blockState
)
