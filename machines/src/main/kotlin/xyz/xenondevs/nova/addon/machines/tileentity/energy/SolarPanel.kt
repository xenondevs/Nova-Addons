package xyz.xenondevs.nova.addon.machines.tileentity.energy

import org.bukkit.scheduler.BukkitTask
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.nova.addon.machines.registry.Blocks.SOLAR_PANEL
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.util.efficiencyMultipliedValue
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.util.CubeFaceSet
import xyz.xenondevs.nova.util.runTaskTimer
import xyz.xenondevs.nova.util.untilHeightLimit
import org.bukkit.block.Block
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.blockType
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import kotlin.math.abs
import kotlin.math.roundToLong

private val BLOCKED_FACES = CubeFaceSet(north = true, east = true, south = true, west = true, up = true)

private val MAX_ENERGY = SOLAR_PANEL.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = SOLAR_PANEL.config.entry<Long>("energy_per_tick")

class SolarPanel(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, EXTRACT, BLOCKED_FACES)
    
    private val peakEnergyOutput by efficiencyMultipliedValue(ENERGY_PER_TICK, upgradeHolder)
    
    private lateinit var obstructionTask: BukkitTask
    private var obstructed = true
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.CENTER_BAR) {
        upperGui by gui(
            "u . . . e . . . .",
            ". . . . e . . . .",
            ". . . . e . . . .",
        ) {
            'u' by openUpgradesItem(upgradeHolder)
            'e' by energyBar(energyHolder)
        }
    }
    
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
        block.location.untilHeightLimit(false) { // TODO: may be replaceable with heightmap lookup
            val blockType = it.block.blockType
            if (!blockType.isAir && "glass" !in blockType.key.value()) {
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
        val time = block.world.time
        if (!obstructed && time < 13_000) {
            val bestTime = 6_500
            val multiplier = (bestTime - abs(bestTime - time)) / bestTime.toDouble()
            return (peakEnergyOutput * multiplier).roundToLong()
        }
        return 0
    }
    
}
