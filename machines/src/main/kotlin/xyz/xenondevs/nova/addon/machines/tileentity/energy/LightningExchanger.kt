package xyz.xenondevs.nova.addon.machines.tileentity.energy

import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.weather.LightningStrikeEvent
import org.bukkit.event.weather.LightningStrikeEvent.Cause
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.addon.machines.registry.Blocks.LIGHTNING_EXCHANGER
import xyz.xenondevs.nova.addon.simpleupgrades.ProviderEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.data.config.entry
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.TileEntityManager
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.upgrade.Upgradable
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.OpenUpgradesItem
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.advance
import xyz.xenondevs.nova.util.registerEvents
import kotlin.math.min
import kotlin.random.Random

private val MAX_ENERGY = LIGHTNING_EXCHANGER.config.entry<Long>("capacity")
private val CONVERSION_RATE by LIGHTNING_EXCHANGER.config.entry<Long>("conversion_rate")
private val MIN_BURST by LIGHTNING_EXCHANGER.config.entry<Long>("burst", "min")
private val MAX_BURST by LIGHTNING_EXCHANGER.config.entry<Long>("burst", "max")

class LightningExchanger(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState), Upgradable {
    
    override val upgradeHolder = getUpgradeHolder(UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    override val energyHolder = ProviderEnergyHolder(this, MAX_ENERGY, upgradeHolder) {
        createExclusiveSideConfig(NetworkConnectionType.EXTRACT, BlockSide.BOTTOM)
    }
    
    private var minBurst = 0L
    private var maxBurst = 0L
    private var toCharge = 0L
    
    init {
        reload()
    }
    
    override fun reload() {
        super.reload()
        minBurst = (MIN_BURST * upgradeHolder.getValue(UpgradeTypes.EFFICIENCY)).toLong()
        maxBurst = (MAX_BURST * upgradeHolder.getValue(UpgradeTypes.EFFICIENCY)).toLong()
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
            val tile = TileEntityManager.getTileEntity(struckBlock.location.advance(BlockFace.DOWN), false)
            if (tile !is LightningExchanger)
                return
            tile.addEnergyBurst()
            
        }
    }
}
