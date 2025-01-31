package xyz.xenondevs.nova.addon.machines.tileentity.world

import kotlinx.coroutines.runBlocking
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.enumSetOf
import xyz.xenondevs.commons.collections.rotateRight
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.AbstractItem
import xyz.xenondevs.invui.item.Click
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.nova.addon.machines.registry.Blocks.PUMP
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.util.energyConsumption
import xyz.xenondevs.nova.addon.machines.util.isSourceFluid
import xyz.xenondevs.nova.addon.machines.util.maxIdleTime
import xyz.xenondevs.nova.addon.machines.util.sourceFluidType
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedFluidContainer
import xyz.xenondevs.nova.addon.simpleupgrades.storedRegion
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.FluidBar
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.HORIZONTAL_FACES
import xyz.xenondevs.nova.util.VERTICAL_FACES
import xyz.xenondevs.nova.util.advance
import xyz.xenondevs.nova.util.item.playPlaceSoundEffect
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.world.pos
import xyz.xenondevs.nova.world.region.Region
import xyz.xenondevs.nova.world.region.VisualRegion
import java.util.*

private val BLOCKED_FACES = enumSetOf(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.DOWN)

private val ENERGY_CAPACITY = PUMP.config.entry<Long>("energy_capacity")
private val ENERGY_PER_TICK = PUMP.config.entry<Long>("energy_per_tick")
private val FLUID_CAPACITY = PUMP.config.entry<Long>("fluid_capacity")
private val REPLACEMENT_BLOCK by PUMP.config.entry<Material>("replacement_block")
private val IDLE_TIME = PUMP.config.entry<Int>("idle_time")

private val MIN_RANGE = PUMP.config.entry<Int>("range", "min")
private val MAX_RANGE = PUMP.config.entry<Int>("range", "max")
private val DEFAULT_RANGE by PUMP.config.entry<Int>("range", "default")

class Pump(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.RANGE, UpgradeTypes.FLUID)
    private val fluidTank = storedFluidContainer("tank", setOf(FluidType.WATER, FluidType.LAVA), FLUID_CAPACITY, upgradeHolder)
    private val energyHolder = storedEnergyHolder(ENERGY_CAPACITY, upgradeHolder, NetworkConnectionType.INSERT, BLOCKED_FACES)
    private val fluidHolder = storedFluidHolder(fluidTank to NetworkConnectionType.EXTRACT, blockedFaces = BLOCKED_FACES)
    
    private val energyPerTick by energyConsumption(ENERGY_PER_TICK, upgradeHolder)
    private val maxIdleTime by maxIdleTime(IDLE_TIME, upgradeHolder)
    
    private var mode by storedValue("mode") { PumpMode.REPLACE }
    private val region = storedRegion("region.default", MIN_RANGE, MAX_RANGE, DEFAULT_RANGE, upgradeHolder) {
        idleTime = maxIdleTime // resets idle time for the case that the pump has already finished
        
        val range = it.toDouble()
        val min = pos.location.subtract(range - 1, range, range - 1)
        val max = pos.location.add(range, 0.0, range)
        Region(min, max)
    }
    
    private var idleTime = 0
    
    private var lastBlock: Block? = null
    private var sortedFaces = LinkedList(HORIZONTAL_FACES)
    
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
                block.type = REPLACEMENT_BLOCK
                REPLACEMENT_BLOCK.playPlaceSoundEffect(block.location)
            } else if (!block.isInfiniteWaterSource()) {
                block.type = Material.AIR
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
            if (fluidTank.accepts(fluidType) && newBlock in region && runBlocking { ProtectionManager.canBreak(this@Pump, null, newBlock.pos) }) { // TODO: non-blocking
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
                val block = pos.advance(BlockFace.DOWN).block
                val fluidType = block.sourceFluidType ?: return@repeat
                if (fluidTank.accepts(fluidType) && runBlocking { ProtectionManager.canBreak(this@Pump, null, block.pos) }) // TODO: non-blocking
                    return block to fluidType
                return@repeat
            }
            for (x in -r..r) {
                for (y in -r - 1..<0) {
                    for (z in -r..r) {
                        if ((x != -r && x != r) && (y != -r - 1 && y != -1) && (z != -r && z != r))
                            continue
                        val block = pos.add(x, y, z).block
                        val fluidType = block.sourceFluidType ?: continue
                        if (fluidTank.accepts(fluidType) && runBlocking { ProtectionManager.canBreak(this@Pump, null, block.pos) }) // TODO: non-blocking
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
            if ((newBlock.type == Material.WATER || newBlock.type == Material.BUBBLE_COLUMN) && newBlock.isSourceFluid())
                if (++waterCount > 1)
                    return true
        }
        return false
    }
    
    @TileEntityMenuClass
    inner class PumpMenu(player: Player) : IndividualTileEntityMenu(player) {
        
        private val sideConfigGui = SideConfigMenu(
            this@Pump,
            mapOf(fluidTank to "container.nova.fluid_tank"),
            ::openWindow
        )
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s p # f # e M |",
                "| u n # f # e # |",
                "| v m # f # e # |",
                "3 - - - - - - - 4")
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('v', region.createVisualizeRegionItem(player))
            .addIngredient('p', region.increaseSizeItem)
            .addIngredient('n', region.displaySizeItem)
            .addIngredient('m', region.decreaseSizeItem)
            .addIngredient('M', PumpModeItem())
            .addIngredient('e', EnergyBar(3, energyHolder))
            .addIngredient('f', FluidBar(3, fluidHolder, fluidTank))
            .build()
        
        private inner class PumpModeItem : AbstractItem() {
            
            override fun getItemProvider(player: Player): ItemProvider =
                when (mode) {
                    PumpMode.PUMP -> GuiItems.PUMP_MODE_BTN.clientsideProvider
                    PumpMode.REPLACE -> GuiItems.PUMP_REPLACE_MODE_BTN.clientsideProvider
                }
            
            override fun handleClick(clickType: ClickType, player: Player, click: Click) {
                mode = if (mode == PumpMode.PUMP) PumpMode.REPLACE else PumpMode.PUMP
                notifyWindows()
            }
            
        }
        
    }
    
}

private enum class PumpMode {
    PUMP, // Replace fluid with air
    REPLACE // Replace fluid with block
}