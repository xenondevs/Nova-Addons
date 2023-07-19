package xyz.xenondevs.nova.addon.machines.tileentity.world

import net.minecraft.core.particles.ParticleTypes
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.type.Farmland
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPhysicsEvent
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nmsutils.particle.particle
import xyz.xenondevs.nova.data.config.NovaConfig
import xyz.xenondevs.nova.data.config.configReloadable
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.addon.machines.registry.Blocks.SPRINKLER
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.fluid.FluidType
import xyz.xenondevs.nova.tileentity.network.fluid.holder.NovaFluidHolder
import xyz.xenondevs.nova.tileentity.upgrade.Upgradable
import xyz.xenondevs.nova.ui.FluidBar
import xyz.xenondevs.nova.ui.OpenUpgradesItem
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.center
import xyz.xenondevs.nova.util.registerEvents
import xyz.xenondevs.nova.util.sendTo
import xyz.xenondevs.nova.world.region.Region
import xyz.xenondevs.nova.world.region.VisualRegion
import xyz.xenondevs.nova.addon.simpleupgrades.getFluidContainer
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import kotlin.math.min
import kotlin.math.roundToLong

private val WATER_CAPACITY = configReloadable { NovaConfig[SPRINKLER].getLong("water_capacity") }
private val WATER_PER_MOISTURE_LEVEL by configReloadable { NovaConfig[SPRINKLER].getLong("water_per_moisture_level") }
private val MIN_RANGE = configReloadable { NovaConfig[SPRINKLER].getInt("range.min") }
private val MAX_RANGE = configReloadable { NovaConfig[SPRINKLER].getInt("range.max") }
private val DEFAULT_RANGE by configReloadable { NovaConfig[SPRINKLER].getInt("range.default") }

class Sprinkler(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState), Upgradable {
    
    override val upgradeHolder = getUpgradeHolder(UpgradeTypes.EFFICIENCY, UpgradeTypes.FLUID, UpgradeTypes.RANGE)
    private val tank = getFluidContainer("tank", hashSetOf(FluidType.WATER), WATER_CAPACITY, upgradeHolder = upgradeHolder)
    override val fluidHolder = NovaFluidHolder(this, tank to NetworkConnectionType.BUFFER) { createExclusiveSideConfig(NetworkConnectionType.INSERT, BlockSide.BOTTOM) }
    
    private var maxRange = 0
    private var waterPerMoistureLevel = 0L
    
    private val region = getUpgradableRegion(UpgradeTypes.RANGE, MIN_RANGE, MAX_RANGE, DEFAULT_RANGE) {
        val d = it + 0.5
        Region(
            location.center().add(-d, -1.0, -d),
            location.center().add(d, 0.0, d)
        )
    }
    
    init {
        reload()
        sprinklers += this
    }
    
    override fun handleRemoved(unload: Boolean) {
        super.handleRemoved(unload)
        sprinklers -= this
        VisualRegion.removeRegion(uuid)
    }
    
    override fun reload() {
        super.reload()
        waterPerMoistureLevel = (WATER_PER_MOISTURE_LEVEL / upgradeHolder.getValue(UpgradeTypes.EFFICIENCY)).roundToLong()
    }
    
    @TileEntityMenuClass
    inner class SprinklerMenu(player: Player) : IndividualTileEntityMenu(player) {
        
        private val sideConfigGui = SideConfigMenu(
            this@Sprinkler,
            fluidContainerNames = listOf(tank to "container.nova.fluid_tank"),
            openPrevious = ::openWindow
        )
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s # # f # # p |",
                "| u # # f # # d |",
                "| v # # f # # m |",
                "3 - - - - - - - 4")
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('v', region.createVisualizeRegionItem(player))
            .addIngredient('p', region.increaseSizeItem)
            .addIngredient('m', region.decreaseSizeItem)
            .addIngredient('d', region.displaySizeItem)
            .addIngredient('f', FluidBar(3, fluidHolder, tank))
            .build()
        
    }
    
    companion object : Listener {
        
        private val sprinklers = ArrayList<Sprinkler>()
        
        init {
            registerEvents()
        }
        
        @EventHandler
        fun handleBlockPhysics(event: BlockPhysicsEvent) {
            if (event.changedType == Material.FARMLAND && event.block == event.sourceBlock) {
                val block = event.block
                val location = block.location.add(0.5, 0.5, 0.5)
                val farmland = block.blockData as Farmland
                if (farmland.moisture >= farmland.maximumMoisture) return
                
                val requiredMoisture = farmland.maximumMoisture - farmland.moisture
                var addedMoisture = 0
                for (sprinkler in sprinklers) {
                    if (location !in sprinkler.region) continue
                    val moistureFromSprinkler = min(requiredMoisture - addedMoisture, (sprinkler.tank.amount / sprinkler.waterPerMoistureLevel).toInt())
                    sprinkler.tank.takeFluid(moistureFromSprinkler * sprinkler.waterPerMoistureLevel)
                    addedMoisture += moistureFromSprinkler
                    if (addedMoisture == requiredMoisture) break
                }
                
                if (addedMoisture > 0) {
                    farmland.moisture += addedMoisture
                    block.setBlockData(farmland, false)
                    
                    showWaterParticles(block, sprinklers[0].getViewers())
                }
            }
        }
        
        private fun showWaterParticles(block: Block, players: List<Player>) {
            particle(ParticleTypes.SPLASH, block.location.apply { add(0.5, 1.0, 0.5) }) {
                offset(0.2, 0.1, 0.2)
                speed(1f)
                amount(20)
            }.sendTo(players)
        }
        
    }
    
}