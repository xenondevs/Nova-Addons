package xyz.xenondevs.nova.addon.machines.tileentity.energy

import org.bukkit.Material
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.data.config.NovaConfig
import xyz.xenondevs.nova.data.config.configReloadable
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.addon.machines.registry.Blocks.SOLAR_PANEL
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.upgrade.Upgradable
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.OpenUpgradesItem
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.item.isGlass
import xyz.xenondevs.nova.util.runTaskTimer
import xyz.xenondevs.nova.util.untilHeightLimit
import xyz.xenondevs.nova.addon.simpleupgrades.ProviderEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import kotlin.math.abs
import kotlin.math.roundToInt

private val MAX_ENERGY = configReloadable { NovaConfig[SOLAR_PANEL].getLong("capacity") }
private val ENERGY_PER_TICK = configReloadable { NovaConfig[SOLAR_PANEL].getLong("energy_per_tick") }

class SolarPanel(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState), Upgradable {
    
    override val upgradeHolder = getUpgradeHolder(UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    override val energyHolder = ProviderEnergyHolder(this, MAX_ENERGY, ENERGY_PER_TICK, upgradeHolder, UpgradeTypes.EFFICIENCY) {
        createExclusiveSideConfig(NetworkConnectionType.EXTRACT, BlockSide.BOTTOM)
    }
    
    private val obstructionTask = runTaskTimer(0, 20 * 5, ::checkSkyObstruction)
    private var obstructed = true
    
    private fun checkSkyObstruction() {
        obstructed = false
        location.untilHeightLimit(false) {
            val material = it.block.type
            if (material != Material.AIR && !material.isGlass()) {
                obstructed = true
                return@untilHeightLimit false
            }
            return@untilHeightLimit true
        }
    }
    
    override fun handleTick() {
        energyHolder.energy += calculateCurrentEnergyOutput()
    }
    
    private fun calculateCurrentEnergyOutput(): Int {
        val time = location.world!!.time
        if (!obstructed && time < 13_000) {
            val bestTime = 6_500
            val multiplier = (bestTime - abs(bestTime - time)) / bestTime.toDouble()
            return (energyHolder.energyGeneration * multiplier).roundToInt()
        }
        return 0
    }
    
    override fun handleRemoved(unload: Boolean) {
        super.handleRemoved(unload)
        obstructionTask.cancel()
    }
    
    @TileEntityMenuClass
    inner class SolarPanelMenu : GlobalTileEntityMenu() {
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| u # # e # # # |",
                "| # # # e # # # |",
                "| # # # e # # # |",
                "3 - - - - - - - 4")
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('e', EnergyBar(3, energyHolder))
            .build()
        
    }
    
}