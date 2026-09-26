package xyz.xenondevs.nova.addon.logistics.tileentity

import org.bukkit.block.Block
import org.bukkit.entity.Display
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.combinedProvider
import xyz.xenondevs.commons.provider.provider
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.item.ItemBuilder
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.nova.addon.logistics.registry.Blocks
import xyz.xenondevs.nova.addon.logistics.registry.BlockStateProperties
import xyz.xenondevs.nova.addon.logistics.registry.GuiTextures
import xyz.xenondevs.nova.addon.logistics.registry.Models
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.packetentity.packetItemDisplay
import xyz.xenondevs.nova.ui.menu.fluidBar
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.nova.util.waterColor
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.world.item.guiItemProvider
import kotlin.math.round

internal const val FLUID_TANK_MAX_STATE = 99

open class FluidTank(
    capacity: Provider<Long>,
    pos: Block, state: NovaBlockState, compound: Compound
) : NetworkedTileEntity(pos, state, compound) {
    
    val fluidContainer = storedFluidContainer("tank", hashSetOf(FluidType.WATER, FluidType.LAVA), capacity, true, ::handleFluidUpdate)
    private val fluidHolder = storedFluidHolder(fluidContainer to NetworkConnectionType.BUFFER)
    private val waterColor = block.waterColor
    
    private val fluidLevel = packetItemDisplay {
        val fluidModel = fluidContainer.typeProvider.flatMap {
            when (it) {
                FluidType.LAVA -> Models.TANK_LAVA_LEVELS.guiItemProvider
                FluidType.WATER -> Models.TANK_WATER_LEVELS.guiItemProvider
                else -> provider(ItemProvider.EMPTY)
            }
        }
        
        location by pos.location.toCenterLocation()
        metadata {
            brightnessOverride by fluidContainer.typeProvider.map {
                if (it == FluidType.LAVA) Display.Brightness(15, 15) else null
            }
            
            itemStack by combinedProvider(
                fluidModel,
                fluidContainer.amountProvider,
                fluidContainer.capacityProvider
            ) { model, amount, capacity ->
                val state = round(amount.toFloat() / capacity.toFloat() * FLUID_TANK_MAX_STATE)
                    .coerceIn(0f..FLUID_TANK_MAX_STATE.toFloat())
                ItemBuilder(model.get())
                    .addCustomModelData(state)
                    .addCustomModelData(waterColor)
                    .build()
            }
        }
    }
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.FLUID_TANK) {
        upperGui by gui(
            "s . . . f . . . .",
            ". . . . f . . . .",
            ". . . . f . . . .",
        ) {
            's' by openSideConfigItem(mapOf(fluidContainer to "container.nova.fluid_tank"))
            'f' by fluidBar(fluidHolder, fluidContainer)
        }
    }
    
    override fun handleEnable() {
        super.handleEnable()
        updateLavaState()
        fluidLevel.spawn()
    }
    
    override fun handleDisable() {
        super.handleDisable()
        fluidLevel.despawn()
    }
    
    private fun handleFluidUpdate() {
        // Creative Fluid Tank
        if (fluidContainer.capacity == Long.MAX_VALUE && !fluidContainer.isEmpty() && !fluidContainer.isFull())
            fluidContainer.addFluid(fluidContainer.type!!, fluidContainer.capacity - fluidContainer.amount)
        updateLavaState()
    }
    
    private fun updateLavaState() {
        val hasLava = fluidContainer.type == FluidType.LAVA && !fluidContainer.isEmpty()
        if (blockState.getOrThrow(BlockStateProperties.LAVA) != hasLava)
            updateBlockState(blockState.apply { this[BlockStateProperties.LAVA] = hasLava })
    }
    
}

internal val FLUID_TANK_BASIC_CAPACITY = Blocks.BASIC_FLUID_TANK.config.entry<Long>("capacity")
internal val FLUID_TANK_ADVANCED_CAPACITY = Blocks.ADVANCED_FLUID_TANK.config.entry<Long>("capacity")
internal val FLUID_TANK_ELITE_CAPACITY = Blocks.ELITE_FLUID_TANK.config.entry<Long>("capacity")
internal val FLUID_TANK_ULTIMATE_CAPACITY = Blocks.ULTIMATE_FLUID_TANK.config.entry<Long>("capacity")

class BasicFluidTank(pos: Block, state: NovaBlockState, data: Compound) :
    FluidTank(FLUID_TANK_BASIC_CAPACITY, pos, state, data)

class AdvancedFluidTank(pos: Block, state: NovaBlockState, data: Compound) :
    FluidTank(FLUID_TANK_ADVANCED_CAPACITY, pos, state, data)

class EliteFluidTank(pos: Block, state: NovaBlockState, data: Compound) :
    FluidTank(FLUID_TANK_ELITE_CAPACITY, pos, state, data)

class UltimateFluidTank(pos: Block, state: NovaBlockState, data: Compound) :
    FluidTank(FLUID_TANK_ULTIMATE_CAPACITY, pos, state, data)

class CreativeFluidTank(pos: Block, state: NovaBlockState, data: Compound) :
    FluidTank(provider(Long.MAX_VALUE), pos, state, data)
