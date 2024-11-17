package xyz.xenondevs.nova.addon.machines.tileentity.energy

import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.weather.LightningStrikeEvent
import org.bukkit.event.weather.LightningStrikeEvent.Cause
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.enumSetOf
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.addon.machines.registry.Blocks.LIGHTNING_EXCHANGER
import xyz.xenondevs.nova.addon.machines.util.efficiencyMultipliedValue
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.util.advance
import xyz.xenondevs.nova.util.registerEvents
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.format.WorldDataManager
import xyz.xenondevs.nova.world.pos
import kotlin.math.min
import kotlin.random.Random

private val BLOCKED_FACES = enumSetOf(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP)

private val MAX_ENERGY = LIGHTNING_EXCHANGER.config.entry<Long>("capacity")
private val CONVERSION_RATE by LIGHTNING_EXCHANGER.config.entry<Long>("conversion_rate")
private val MIN_BURST = LIGHTNING_EXCHANGER.config.entry<Long>("burst", "min")
private val MAX_BURST = LIGHTNING_EXCHANGER.config.entry<Long>("burst", "max")

class LightningExchanger(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, EXTRACT, BLOCKED_FACES)
    private val minBurst by efficiencyMultipliedValue(MIN_BURST, upgradeHolder)
    private val maxBurst by efficiencyMultipliedValue(MAX_BURST, upgradeHolder)
    private var toCharge = 0L
    
    override fun handleTick() {
        val charge = min(CONVERSION_RATE, toCharge)
        energyHolder.energy += charge
        toCharge -= charge
    }
    
    fun addEnergyBurst() {
        val leeway = energyHolder.maxEnergy - energyHolder.energy - toCharge
        toCharge += (if (leeway <= maxBurst) leeway else Random.nextLong(minBurst, maxBurst))
    }
    
    @TileEntityMenuClass
    inner class LightningExchangerMenu : GlobalTileEntityMenu() {
        
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
    
    private companion object LightningHandler : Listener {
        
        init {
            registerEvents()
        }
        
        @EventHandler
        fun handleLightning(event: LightningStrikeEvent) {
            val struckBlock = event.lightning.location.advance(BlockFace.DOWN).block
            if (event.cause != Cause.WEATHER || struckBlock.type != Material.LIGHTNING_ROD)
                return
            
            val tileEntity = WorldDataManager.getTileEntity(struckBlock.pos.advance(BlockFace.DOWN))
            if (tileEntity is LightningExchanger) {
                tileEntity.addEnergyBurst()
            }
        }
        
    }
    
}
