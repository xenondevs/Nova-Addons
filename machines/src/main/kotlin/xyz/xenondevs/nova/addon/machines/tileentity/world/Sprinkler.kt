package xyz.xenondevs.nova.addon.machines.tileentity.world

import net.minecraft.core.particles.ParticleTypes
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.type.Farmland
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPhysicsEvent
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.enumSetOf
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.addon.machines.registry.Blocks.SPRINKLER
import xyz.xenondevs.nova.addon.machines.util.efficiencyDividedValue
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedFluidContainer
import xyz.xenondevs.nova.addon.simpleupgrades.storedRegion
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.FluidBar
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.particle.particle
import xyz.xenondevs.nova.util.registerEvents
import xyz.xenondevs.nova.util.sendTo
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.BUFFER
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.world.region.Region
import xyz.xenondevs.nova.world.region.VisualRegion
import kotlin.math.min

private val BLOCKED_FACES = enumSetOf(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP)

private val WATER_CAPACITY = SPRINKLER.config.entry<Long>("water_capacity")
private val WATER_PER_MOISTURE_LEVEL = SPRINKLER.config.entry<Long>("water_per_moisture_level")
private val MIN_RANGE = SPRINKLER.config.entry<Int>("range", "min")
private val MAX_RANGE = SPRINKLER.config.entry<Int>("range", "max")
private val DEFAULT_RANGE by SPRINKLER.config.entry<Int>("range", "default")

class Sprinkler(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.EFFICIENCY, UpgradeTypes.FLUID, UpgradeTypes.RANGE)
    private val tank = storedFluidContainer("tank", setOf(FluidType.WATER), WATER_CAPACITY, upgradeHolder)
    private val fluidHolder = storedFluidHolder(tank to BUFFER, blockedFaces = BLOCKED_FACES)
    
    private val waterPerMoistureLevel by efficiencyDividedValue(WATER_PER_MOISTURE_LEVEL, upgradeHolder)
    private val region = storedRegion("region.default", MIN_RANGE, MAX_RANGE, DEFAULT_RANGE, upgradeHolder) {
        val d = it + 0.5
        Region(
            pos.location.add(-d + 0.5, -0.5, -d + 0.5),
            pos.location.add(d + 0.5, 0.5, d + 0.5)
        )
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
    
    @TileEntityMenuClass
    inner class SprinklerMenu(player: Player) : IndividualTileEntityMenu(player) {
        
        private val sideConfigGui = SideConfigMenu(
            this@Sprinkler,
            mapOf(tank to "container.nova.fluid_tank"),
            ::openWindow
        )
        
        override val gui = Gui.builder()
            .setStructure(
                "1 - - - - - - - 2",
                "| s # # f # # p |",
                "| u # # f # # d |",
                "| v # # f # # m |",
                "3 - - - - - - - 4")
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('v', region.visualizeRegionItem)
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