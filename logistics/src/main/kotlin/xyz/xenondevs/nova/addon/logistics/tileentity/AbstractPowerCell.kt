package xyz.xenondevs.nova.addon.logistics.tileentity

import org.bukkit.block.BlockFace
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.cbf.entry
import xyz.xenondevs.commons.collections.enumMap
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.provider
import xyz.xenondevs.commons.provider.orElseLazily
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.addon.logistics.registry.Blocks
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.CUBE_FACES
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType
import xyz.xenondevs.nova.world.block.tileentity.network.type.energy.holder.EnergyHolder

private class InfiniteEnergyHolder(compound: Provider<Compound>) : EnergyHolder {
    
    override val blockedFaces: Set<BlockFace>
        get() = emptySet()
    override val allowedConnectionType = NetworkConnectionType.EXTRACT
    override val connectionConfig: MutableMap<BlockFace, NetworkConnectionType>
        by compound.entry<MutableMap<BlockFace, NetworkConnectionType>>("connectionConfig")
            .orElseLazily { CUBE_FACES.associateWithTo(enumMap()) { NetworkConnectionType.EXTRACT } }
    
    override var energy = Long.MAX_VALUE
    override val maxEnergy = Long.MAX_VALUE
    
}

abstract class AbstractPowerCell(pos: BlockPos, state: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, state, data) {
    
    protected abstract val energyHolder: EnergyHolder
    protected abstract val energyBar: EnergyBar
    
    @TileEntityMenuClass
    inner class PowerCellMenu : GlobalTileEntityMenu() {
        
        private val SideConfigMenu = SideConfigMenu(this@AbstractPowerCell, ::openWindow)
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s # # e # # # |",
                "| # # # e # # # |",
                "| # # # e # # # |",
                "3 - - - - - - - 4")
            .addIngredient('s', OpenSideConfigItem(SideConfigMenu))
            .addIngredient('e', energyBar)
            .build()
        
    }
    
}

abstract class PowerCell(capacity: Provider<Long>, pos: BlockPos, state: NovaBlockState, data: Compound) : AbstractPowerCell(pos, state, data) {
    final override val energyHolder = storedEnergyHolder(capacity, NetworkConnectionType.BUFFER)
    final override val energyBar = EnergyBar(3, energyHolder)
}

class CreativePowerCell(pos: BlockPos, state: NovaBlockState, data: Compound) : AbstractPowerCell(pos, state, data) {
    
    override val energyHolder: EnergyHolder
    override val energyBar = EnergyBar(3, provider(Long.MAX_VALUE), provider(Long.MAX_VALUE), { 0L }, { 0L })
    
    init {
        val energyHolder = InfiniteEnergyHolder(storedValue("energyHolder", ::Compound))
        holders += energyHolder
        this.energyHolder = energyHolder
    }
    
}

private val BASIC_CAPACITY = Blocks.BASIC_POWER_CELL.config.entry<Long>("capacity")
private val ADVANCED_CAPACITY = Blocks.ADVANCED_POWER_CELL.config.entry<Long>("capacity")
private val ELITE_CAPACITY = Blocks.ELITE_POWER_CELL.config.entry<Long>("capacity")
private val ULTIMATE_CAPACITY = Blocks.ULTIMATE_POWER_CELL.config.entry<Long>("capacity")

class BasicPowerCell(pos: BlockPos, state: NovaBlockState, data: Compound) : PowerCell(BASIC_CAPACITY, pos, state, data)
class AdvancedPowerCell(pos: BlockPos, state: NovaBlockState, data: Compound) : PowerCell(ADVANCED_CAPACITY, pos, state, data)
class ElitePowerCell(pos: BlockPos, state: NovaBlockState, data: Compound) : PowerCell(ELITE_CAPACITY, pos, state, data)
class UltimatePowerCell(pos: BlockPos, state: NovaBlockState, data: Compound) : PowerCell(ULTIMATE_CAPACITY, pos, state, data)
