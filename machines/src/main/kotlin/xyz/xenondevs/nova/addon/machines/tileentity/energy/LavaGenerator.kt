package xyz.xenondevs.nova.addon.machines.tileentity.energy

import net.minecraft.core.particles.ParticleTypes
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.combinedProvider
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.nova.addon.machines.registry.BlockStateProperties
import xyz.xenondevs.nova.addon.machines.registry.Blocks.LAVA_GENERATOR
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedFluidContainer
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.ui.menu.fluidBar
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.BlockSideSet
import xyz.xenondevs.nova.util.PacketTask
import xyz.xenondevs.nova.util.advance
import xyz.xenondevs.nova.util.axis
import xyz.xenondevs.nova.util.particle.particle
import org.bukkit.block.Block
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.state.property.DefaultBlockStateProperties
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.BUFFER
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType
import kotlin.math.roundToLong

private val BLOCKED_SIDES = BlockSideSet(front = true)

private val ENERGY_CAPACITY = LAVA_GENERATOR.config.entry<Long>("energy_capacity")
private val FLUID_CAPACITY = LAVA_GENERATOR.config.entry<Long>("fluid_capacity")
private val ENERGY_PER_MB = LAVA_GENERATOR.config.entry<Double>("energy_per_mb")
private val BURN_RATE = LAVA_GENERATOR.config.entry<Double>("burn_rate")

class LavaGenerator(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
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
                updateBlockState(blockState.apply { this[BlockStateProperties.ACTIVE] = active })
            }
        }
    private var burnProgress = 0.0
    
    private val smokeParticleTask = PacketTask(
        listOf(
            particle(ParticleTypes.SMOKE) {
                val facing = blockState.getOrThrow(DefaultBlockStateProperties.FACING_HORIZONTAL)
                location(block.location.add(0.5, 0.6, 0.5).advance(facing, 0.6))
                offset(BlockSide.RIGHT.getBlockFace(facing).axis!!, 0.15f)
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
                val facing = blockState.getOrThrow(DefaultBlockStateProperties.FACING_HORIZONTAL)
                location(block.location.advance(facing, 0.6).apply { y += 0.6 })
                offset(BlockSide.RIGHT.getBlockFace(facing).axis!!, 0.15f)
                offsetY(0.1f)
            }
        ),
        200,
        ::getViewers
    )
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.LAVA_GENERATOR) {
        upperGui by gui(
            "s . . . f . . . e",
            "u . . . f . . . e",
            ". . . . f . . . e",
        ) {
            's' by openSideConfigItem(containers = mapOf(fluidContainer to "container.nova.lava_tank"))
            'u' by openUpgradesItem(upgradeHolder)
            'e' by energyBar(energyHolder)
            'f' by fluidBar(fluidHolder, fluidContainer)
        }
    }
    
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
    
}
