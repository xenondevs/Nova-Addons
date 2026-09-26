package xyz.xenondevs.nova.addon.machines.tileentity.energy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.joml.Quaternionf
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.nova.addon.machines.registry.Blocks
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.Models
import xyz.xenondevs.nova.addon.machines.util.addDisplay
import xyz.xenondevs.nova.addon.machines.util.efficiencyMultipliedValue
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.packetentity.PacketItemDisplay
import xyz.xenondevs.nova.packetentity.despawn
import xyz.xenondevs.nova.packetentity.updateMetadata
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.util.BlockSideSet
import xyz.xenondevs.nova.util.yaw
import org.bukkit.block.Block
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.state.property.DefaultBlockStateProperties
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

private val BLOCKED_SIDES = BlockSideSet(left = true, right = true, back = true, bottom = true, top = true)

private val MAX_ENERGY = Blocks.WIND_TURBINE.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = Blocks.WIND_TURBINE.config.entry<Long>("energy_per_tick")
private val PLAY_ANIMATION by Blocks.WIND_TURBINE.config.entry<Boolean>("animation")

class WindTurbine(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, EXTRACT, BLOCKED_SIDES)
    
    private val turbineModel = ArrayList<PacketItemDisplay>()
    private val altitude = (block.y - block.world.minHeight) / (block.world.maxHeight - block.world.minHeight - 1).toDouble()
    private val rotationPerTick = altitude * 15.0
    private val energyPerTick by efficiencyMultipliedValue(ENERGY_PER_TICK, upgradeHolder).map { (it * altitude).roundToLong() }
    
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
        spawnModels()
    }
    
    override fun handleDisable() {
        super.handleDisable()
        turbineModel.despawn()
        turbineModel.clear()
    }
    
    private fun spawnModels() {
        val location = block.location.add(0.5, 3.5, 0.5)
        location.yaw = blockState.getOrThrow(DefaultBlockStateProperties.FACING_HORIZONTAL).yaw
        
        turbineModel.addDisplay(Models.WIND_TURBINE_ROTOR_MIDDLE, location)
        for (blade in 0..2) {
            turbineModel.addDisplay(
                Models.WIND_TURBINE_ROTOR_BLADE,
                location,
                rightRotation = Quaternionf().setAngleAxis(
                    (Math.PI * 2 / 3 * blade).toFloat(),
                    0f, 0f, 1f
                )
            )
        }
    }
    
    override fun handleTick() {
        energyHolder.energy += energyPerTick
    }
    
    override fun handleEnableTicking() {
        if (!PLAY_ANIMATION)
            return
        
        CoroutineScope(coroutineSupervisor!!).launch {
            while (true) {
                rotate()
                delay(50.milliseconds)
            }
        }
    }
    
    private fun rotate() {
        turbineModel.updateMetadata {
            transformationInterpolationStartDeltaTicks = 0
            leftRotation = leftRotation.rotateZ(
                Math.toRadians(rotationPerTick).toFloat(),
                Quaternionf()
            )
        }
    }
    
}