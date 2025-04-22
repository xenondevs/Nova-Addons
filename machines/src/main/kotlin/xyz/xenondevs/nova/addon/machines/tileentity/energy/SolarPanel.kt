package xyz.xenondevs.nova.addon.machines.tileentity.energy

import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.scheduler.BukkitTask
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.enumSetOf
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.addon.machines.registry.Blocks.SOLAR_PANEL
import xyz.xenondevs.nova.addon.machines.util.efficiencyMultipliedValue
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.util.item.isGlass
import xyz.xenondevs.nova.util.runTaskTimer
import xyz.xenondevs.nova.util.untilHeightLimit
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import kotlin.math.abs
import kotlin.math.roundToLong

private val BLOCKED_FACES = enumSetOf(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP)

private val MAX_ENERGY = SOLAR_PANEL.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = SOLAR_PANEL.config.entry<Long>("energy_per_tick")

class SolarPanel(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, EXTRACT, BLOCKED_FACES)
    
    private val peakEnergyOutput by efficiencyMultipliedValue(ENERGY_PER_TICK, upgradeHolder)
    
    private lateinit var obstructionTask: BukkitTask
    private var obstructed = true
    
    override fun handleEnable() {
        super.handleEnable()
        obstructionTask = runTaskTimer(0, 20 * 5, ::checkSkyObstruction)
    }
    
    override fun handleDisable() {
        super.handleDisable()
        obstructionTask.cancel()
    }
    
    private fun checkSkyObstruction() {
        obstructed = false
        pos.location.untilHeightLimit(false) { // TODO: may be replaceable with heightmap lookup
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
    
    private fun calculateCurrentEnergyOutput(): Long {
        val time = pos.world.time
        if (!obstructed && time < 13_000) {
            val bestTime = 6_500
            val multiplier = (bestTime - abs(bestTime - time)) / bestTime.toDouble()
            return (peakEnergyOutput * multiplier).roundToLong()
        }
        return 0
    }
    
    @TileEntityMenuClass
    inner class SolarPanelMenu : GlobalTileEntityMenu() {
        
        override val gui = Gui.builder()
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