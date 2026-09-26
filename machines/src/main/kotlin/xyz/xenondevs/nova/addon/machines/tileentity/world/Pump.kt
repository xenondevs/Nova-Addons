package xyz.xenondevs.nova.addon.machines.tileentity.world

import kotlinx.coroutines.runBlocking
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockType
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.rotateRight
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.dsl.item
import xyz.xenondevs.nova.addon.machines.registry.Blocks.PUMP
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.util.energyConsumption
import xyz.xenondevs.nova.addon.machines.util.maxIdleTime
import xyz.xenondevs.nova.addon.machines.util.sourceFluidType
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedFluidContainer
import xyz.xenondevs.nova.addon.simpleupgrades.storedRegion
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.ui.menu.fluidBar
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.nova.util.CubeFaceSet
import xyz.xenondevs.nova.util.HORIZONTAL_FACES
import xyz.xenondevs.nova.util.VERTICAL_FACES
import xyz.xenondevs.nova.util.advance
import xyz.xenondevs.nova.util.novaSoundGroup
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.blockType
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.world.item.guiItemProvider
import xyz.xenondevs.nova.world.region.Region
import xyz.xenondevs.nova.world.region.VisualRegion
import java.util.*
import kotlin.random.Random

private val BLOCKED_FACES = CubeFaceSet(north = true, east = true, south = true, west = true, down = true)

private val ENERGY_CAPACITY = PUMP.config.entry<Long>("energy_capacity")
private val ENERGY_PER_TICK = PUMP.config.entry<Long>("energy_per_tick")
private val FLUID_CAPACITY = PUMP.config.entry<Long>("fluid_capacity")
private val REPLACEMENT_BLOCK by PUMP.config.entry<BlockType>(BlockType.DIRT, "replacement_block")
private val IDLE_TIME = PUMP.config.entry<Int>("idle_time")

private val MIN_RANGE = PUMP.config.entry<Int>("range", "min")
private val MAX_RANGE = PUMP.config.entry<Int>("range", "max")
private val DEFAULT_RANGE by PUMP.config.entry<Int>("range", "default")

class Pump(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.RANGE, UpgradeTypes.FLUID)
    private val fluidTank = storedFluidContainer("tank", setOf(FluidType.WATER, FluidType.LAVA), FLUID_CAPACITY, upgradeHolder)
    private val energyHolder = storedEnergyHolder(ENERGY_CAPACITY, upgradeHolder, NetworkConnectionType.INSERT, BLOCKED_FACES)
    private val fluidHolder = storedFluidHolder(fluidTank to NetworkConnectionType.EXTRACT, blockedFaces = BLOCKED_FACES)
    
    private val energyPerTick by energyConsumption(ENERGY_PER_TICK, upgradeHolder)
    private val maxIdleTime by maxIdleTime(IDLE_TIME, upgradeHolder)
    
    private val modeProvider = storedValue("mode") { PumpMode.REPLACE }
    private var mode by modeProvider
    private val region = storedRegion("region.default", MIN_RANGE, MAX_RANGE, DEFAULT_RANGE, upgradeHolder) {
        idleTime = maxIdleTime // resets idle time for the case that the pump has already finished
        
        val range = it.toDouble()
        val min = block.location.subtract(range - 1, range, range - 1)
        val max = block.location.add(range, 0.0, range)
        Region(min, max)
    }
    
    private var idleTime = 0
    
    private var lastBlock: Block? = null
    private var sortedFaces = LinkedList(HORIZONTAL_FACES)
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.PUMP) {
        upperGui by gui(
            "s p . . . . . f e",
            "u n . . M . . f e",
            "v m . . . . . f e",
        ) {
            's' by openSideConfigItem(containers = mapOf(fluidTank to "container.nova.fluid_tank"))
            'u' by openUpgradesItem(upgradeHolder)
            'v' by region.visualizeRegionItem
            'p' by region.increaseSizeItem
            'n' by region.displaySizeItem
            'm' by region.decreaseSizeItem
            'M' by item {
                itemProvider by modeProvider.flatMap {
                    when (it) {
                        PumpMode.PUMP -> GuiItems.PUMP_MODE_BTN.guiItemProvider
                        PumpMode.REPLACE -> GuiItems.PUMP_REPLACE_MODE_BTN.guiItemProvider
                    }
                }
                onClick {
                    mode = if (mode == PumpMode.PUMP) PumpMode.REPLACE else PumpMode.PUMP
                }
            }
            'e' by energyBar(energyHolder)
            'f' by fluidBar(fluidHolder, fluidTank)
        }
    }
    
    override fun handleDisable() {
        super.handleDisable()
        VisualRegion.removeRegion(uuid)
    }
    
    override fun handleTick() {
        if (energyHolder.energy >= energyPerTick && fluidTank.accepts(FluidType.WATER, 1000)) {
            if (--idleTime <= 0)
                pumpNextBlock()
        }
    }
    
    private fun pumpNextBlock() {
        val (block, type) = getNextBlock()
        if (block != null && type != null) {
            if (mode == PumpMode.REPLACE) {
                block.blockType = REPLACEMENT_BLOCK
                block.world.playSound(
                    block.location,
                    block.novaSoundGroup.placeSound,
                    1f,
                    Random.nextDouble(0.8, 0.95).toFloat()
                )
            } else if (!block.isInfiniteWaterSource()) {
                block.blockType = BlockType.AIR
            }
            fluidTank.addFluid(type, 1000)
            lastBlock = block
            energyHolder.energy -= energyPerTick
            idleTime = maxIdleTime
        } else {
            lastBlock = null
            idleTime = 60 * 20
        }
    }
    
    private fun getNextBlock(): Pair<Block?, FluidType?> {
        var block: Block? = null
        var type: FluidType? = null
        if (lastBlock != null) {
            val pair = getRelativeBlock()
            block = pair.first
            type = pair.second
        }
        if (block == null) {
            val pair = searchBlock()
            block = pair.first
            type = pair.second
        }
        return block to type
    }
    
    private fun getRelativeBlock(): Pair<Block?, FluidType?> {
        val location = lastBlock!!.location
        val faces = VERTICAL_FACES + sortedFaces
        var block: Block? = null
        var type: FluidType? = null
        for (face in faces) {
            val newBlock = location.clone().advance(face, 1.0).block
            
            val fluidType = newBlock.sourceFluidType ?: continue
            if (fluidTank.accepts(fluidType) && newBlock in region && runBlocking { ProtectionManager.canBreak(this@Pump, null, newBlock) }) { // TODO: non-blocking
                if (face !in VERTICAL_FACES)
                    sortedFaces.rotateRight()
                block = newBlock
                type = fluidType
                break
            }
        }
        return block to type
    }
    
    private fun searchBlock(): Pair<Block?, FluidType?> {
        repeat(region.size) { r ->
            if (r == 0) {
                val block = block.getRelative(BlockFace.DOWN)
                val fluidType = block.sourceFluidType ?: return@repeat
                if (fluidTank.accepts(fluidType) && runBlocking { ProtectionManager.canBreak(this@Pump, null, block) }) // TODO: non-blocking
                    return block to fluidType
                return@repeat
            }
            for (x in -r..r) {
                for (y in -r - 1..<0) {
                    for (z in -r..r) {
                        if ((x != -r && x != r) && (y != -r - 1 && y != -1) && (z != -r && z != r))
                            continue
                        val block = block.getRelative(x, y, z)
                        val fluidType = block.sourceFluidType ?: continue
                        if (fluidTank.accepts(fluidType) && runBlocking { ProtectionManager.canBreak(this@Pump, null, block) }) // TODO: non-blocking
                            return block to fluidType
                    }
                }
            }
        }
        return null to null
    }
    
    private fun Block.isInfiniteWaterSource(): Boolean {
        var waterCount = 0
        for (it in HORIZONTAL_FACES) {
            val newBlock = location.clone().advance(it, 1.0).block
            if (newBlock.sourceFluidType == FluidType.WATER)
                if (++waterCount > 1)
                    return true
        }
        return false
    }
    
}

private enum class PumpMode {
    PUMP, // Replace fluid with air
    REPLACE // Replace fluid with block
}
