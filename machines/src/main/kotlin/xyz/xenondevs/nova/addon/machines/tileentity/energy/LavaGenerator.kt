package xyz.xenondevs.nova.addon.machines.tileentity.energy

import net.minecraft.core.particles.ParticleTypes
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.enumSetOf
import xyz.xenondevs.commons.provider.combinedProvider
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.addon.machines.registry.BlockStateProperties
import xyz.xenondevs.nova.addon.machines.registry.Blocks.LAVA_GENERATOR
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedFluidContainer
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.FluidBar
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.PacketTask
import xyz.xenondevs.nova.util.advance
import xyz.xenondevs.nova.util.axis
import xyz.xenondevs.nova.util.particle.particle
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.state.property.DefaultBlockStateProperties
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.BUFFER
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType
import kotlin.math.roundToLong

private val BLOCKED_SIDES = enumSetOf(BlockSide.FRONT)

private val ENERGY_CAPACITY = LAVA_GENERATOR.config.entry<Long>("energy_capacity")
private val FLUID_CAPACITY = LAVA_GENERATOR.config.entry<Long>("fluid_capacity")
private val ENERGY_PER_MB = LAVA_GENERATOR.config.entry<Double>("energy_per_mb")
private val BURN_RATE = LAVA_GENERATOR.config.entry<Double>("burn_rate")

class LavaGenerator(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.FLUID)
    private val fluidContainer = storedFluidContainer("tank", setOf(FluidType.LAVA), FLUID_CAPACITY, upgradeHolder)
    private val fluidHolder = storedFluidHolder(fluidContainer to BUFFER, blockedSides = BLOCKED_SIDES)
    private val energyHolder = storedEnergyHolder(ENERGY_CAPACITY, upgradeHolder, EXTRACT, BLOCKED_SIDES)
    
    private val burnRate: Double by combinedProvider(
        BURN_RATE,
        upgradeHolder.getValueProvider(UpgradeTypes.SPEED),
        upgradeHolder.getValueProvider(UpgradeTypes.EFFICIENCY)
    ) { burnRate, speed, efficiency -> burnRate * speed / efficiency }
    private val energyPerTick: Long by combinedProvider(
        ENERGY_PER_MB,
        BURN_RATE,
        upgradeHolder.getValueProvider(UpgradeTypes.SPEED)
    ) { energyPerMb, burnRate, speed -> (energyPerMb * burnRate * speed).roundToLong() }
    
    private var active = blockState.getOrThrow(BlockStateProperties.ACTIVE)
        set(active) {
            if (field != active) {
                field = active
                updateBlockState(blockState.with(BlockStateProperties.ACTIVE, active))
            }
        }
    private var burnProgress = 0.0
    
    private val smokeParticleTask = PacketTask(
        listOf(
            particle(ParticleTypes.SMOKE) {
                val facing = blockState.getOrThrow(DefaultBlockStateProperties.FACING)
                location(pos.location.add(0.5, 0.6, 0.5).advance(facing, 0.6))
                offset(BlockSide.RIGHT.getBlockFace(facing).axis, 0.15f)
                offsetY(0.1f)
                speed(0f)
                amount(1)
            }
        ),
        3,
        ::getViewers
    )
    
    private val lavaParticleTask = PacketTask(
        listOf(
            particle(ParticleTypes.LAVA) {
                val facing = blockState.getOrThrow(DefaultBlockStateProperties.FACING)
                location(pos.location.advance(facing, 0.6).apply { y += 0.6 })
                offset(BlockSide.RIGHT.getBlockFace(facing).axis, 0.15f)
                offsetY(0.1f)
            }
        ),
        200,
        ::getViewers
    )
    
    override fun handleDisable() {
        super.handleDisable()
        smokeParticleTask.stop()
        lavaParticleTask.stop()
    }
    
    override fun handleTick() {
        if (energyHolder.energy == energyHolder.maxEnergy || fluidContainer.isEmpty()) {
            if (active) {
                active = false
                smokeParticleTask.stop()
                lavaParticleTask.stop()
            }
            
            return
        } else if (!active) {
            active = true
            smokeParticleTask.start()
            lavaParticleTask.start()
        }
        
        val lavaAmount = fluidContainer.amount
        if (lavaAmount >= burnRate) {
            energyHolder.energy += energyPerTick
            
            burnProgress += burnRate
            if (burnProgress > 1) {
                val burnt = burnProgress.toLong()
                
                burnProgress -= burnt
                fluidContainer.takeFluid(burnt)
            }
        } else {
            energyHolder.energy += (lavaAmount * ENERGY_PER_MB.get()).toLong()
            fluidContainer.clear()
        }
    }
    
    @TileEntityMenuClass
    inner class LavaGeneratorMenu : GlobalTileEntityMenu() {
        
        private val sideConfigGui = SideConfigMenu(
            this@LavaGenerator,
            mapOf(fluidContainer to "container.nova.lava_tank"),
            ::openWindow
        )
        
        override val gui = Gui.builder()
            .setStructure(
                "1 - - - - - - - 2",
                "| s # # # # f e |",
                "| u # # # # f e |",
                "| # # # # # f e |",
                "3 - - - - - - - 4")
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('e', EnergyBar(3, energyHolder))
            .addIngredient('f', FluidBar(3, fluidHolder, fluidContainer))
            .build()
        
    }
    
}