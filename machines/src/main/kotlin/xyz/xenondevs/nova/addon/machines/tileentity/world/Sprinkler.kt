package xyz.xenondevs.nova.addon.machines.tileentity.world

import net.minecraft.core.particles.ParticleTypes
import org.bukkit.block.Block
import org.bukkit.block.BlockType
import org.bukkit.block.data.type.Farmland
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPhysicsEvent
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.nova.addon.machines.registry.Blocks.SPRINKLER
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.util.efficiencyDividedValue
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedFluidContainer
import xyz.xenondevs.nova.addon.simpleupgrades.storedRegion
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.network.sendTo
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.ui.menu.fluidBar
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.nova.util.CubeFaceSet
import xyz.xenondevs.nova.util.particle.particle
import xyz.xenondevs.nova.util.registerEvents
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.blockType
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.BUFFER
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.world.region.Region
import xyz.xenondevs.nova.world.region.VisualRegion
import kotlin.math.min

private val BLOCKED_FACES = CubeFaceSet(north = true, east = true, south = true, west = true, up = true)

private val WATER_CAPACITY = SPRINKLER.config.entry<Long>("water_capacity")
private val WATER_PER_MOISTURE_LEVEL = SPRINKLER.config.entry<Long>("water_per_moisture_level")
private val MIN_RANGE = SPRINKLER.config.entry<Int>("range", "min")
private val MAX_RANGE = SPRINKLER.config.entry<Int>("range", "max")
private val DEFAULT_RANGE by SPRINKLER.config.entry<Int>("range", "default")

class Sprinkler(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.EFFICIENCY, UpgradeTypes.FLUID, UpgradeTypes.RANGE)
    private val tank = storedFluidContainer("tank", setOf(FluidType.WATER), WATER_CAPACITY, upgradeHolder)
    private val fluidHolder = storedFluidHolder(tank to BUFFER, blockedFaces = BLOCKED_FACES)
    
    private val waterPerMoistureLevel by efficiencyDividedValue(WATER_PER_MOISTURE_LEVEL, upgradeHolder)
    private val region = storedRegion("region.default", MIN_RANGE, MAX_RANGE, DEFAULT_RANGE, upgradeHolder) {
        val d = it + 0.5
        Region(
            block.location.add(-d + 0.5, -0.5, -d + 0.5),
            block.location.add(d + 0.5, 0.5, d + 0.5)
        )
    }
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.CENTER_BAR) {
        upperGui by gui(
            "s . . . f . . . p",
            "u . . . f . . . d",
            "v . . . f . . . m",
        ) {
            's' by openSideConfigItem(containers = mapOf(tank to "container.nova.fluid_tank"))
            'u' by openUpgradesItem(upgradeHolder)
            'v' by region.visualizeRegionItem
            'p' by region.increaseSizeItem
            'm' by region.decreaseSizeItem
            'd' by region.displaySizeItem
            'f' by fluidBar(fluidHolder, tank)
        }
    }
    
    override fun handleEnable() {
        super.handleEnable()
        sprinklers += this
    }
    
    override fun handleDisable() {
        super.handleDisable()
        sprinklers -= this
        VisualRegion.removeRegion(uuid)
    }
    
    companion object : Listener {
        
        private val sprinklers = ArrayList<Sprinkler>()
        
        init {
            registerEvents()
        }
        
        @EventHandler
        fun handleBlockPhysics(event: BlockPhysicsEvent) {
            if (event.block.blockType == BlockType.FARMLAND && event.block == event.sourceBlock) {
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
