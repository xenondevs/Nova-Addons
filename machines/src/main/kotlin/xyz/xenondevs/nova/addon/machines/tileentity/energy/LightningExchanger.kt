package xyz.xenondevs.nova.addon.machines.tileentity.energy

import org.bukkit.block.BlockType
import org.bukkit.block.BlockFace
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.weather.LightningStrikeEvent
import org.bukkit.event.weather.LightningStrikeEvent.Cause
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.nova.addon.machines.registry.Blocks.LIGHTNING_EXCHANGER
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
import xyz.xenondevs.nova.util.advance
import xyz.xenondevs.nova.util.registerEvents
import org.bukkit.block.Block
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.blockType
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.novaTileEntity
import kotlin.math.min
import kotlin.random.Random

private val BLOCKED_FACES = CubeFaceSet(north = true, east = true, south = true, west = true, up = true)

private val MAX_ENERGY = LIGHTNING_EXCHANGER.config.entry<Long>("capacity")
private val CONVERSION_RATE by LIGHTNING_EXCHANGER.config.entry<Long>("conversion_rate")
private val MIN_BURST = LIGHTNING_EXCHANGER.config.entry<Long>("burst", "min")
private val MAX_BURST = LIGHTNING_EXCHANGER.config.entry<Long>("burst", "max")

class LightningExchanger(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, EXTRACT, BLOCKED_FACES)
    private val minBurst by efficiencyMultipliedValue(MIN_BURST, upgradeHolder)
    private val maxBurst by efficiencyMultipliedValue(MAX_BURST, upgradeHolder)
    private var toCharge = 0L
    
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
    
    
    override fun handleTick() {
        val charge = min(CONVERSION_RATE, toCharge)
        energyHolder.energy += charge
        toCharge -= charge
    }
    
    fun addEnergyBurst() {
        val leeway = energyHolder.maxEnergy - energyHolder.energy - toCharge
        toCharge += (if (leeway <= maxBurst) leeway else Random.nextLong(minBurst, maxBurst))
    }
    
    
    private companion object LightningHandler : Listener {
        
        init {
            registerEvents()
        }
        
        @EventHandler
        fun handleLightning(event: LightningStrikeEvent) {
            val struckBlock = event.lightning.location.advance(BlockFace.DOWN).block
            if (event.cause != Cause.WEATHER || struckBlock.blockType != BlockType.LIGHTNING_ROD)
                return
            
            val tileEntity = struckBlock.getRelative(BlockFace.DOWN).novaTileEntity
            if (tileEntity is LightningExchanger) {
                tileEntity.addEnergyBurst()
            }
        }
        
    }
    
}
