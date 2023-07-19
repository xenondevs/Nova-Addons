package xyz.xenondevs.nova.addon.machines.tileentity.world

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import xyz.xenondevs.commons.collections.rotateRight
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.impl.AbstractItem
import xyz.xenondevs.nova.data.config.NovaConfig
import xyz.xenondevs.nova.data.config.configReloadable
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.addon.machines.registry.Blocks.PUMP
import xyz.xenondevs.nova.addon.machines.registry.GuiMaterials
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.fluid.FluidType
import xyz.xenondevs.nova.tileentity.network.fluid.holder.NovaFluidHolder
import xyz.xenondevs.nova.tileentity.upgrade.Upgradable
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.FluidBar
import xyz.xenondevs.nova.ui.OpenUpgradesItem
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.HORIZONTAL_FACES
import xyz.xenondevs.nova.util.VERTICAL_FACES
import xyz.xenondevs.nova.util.advance
import xyz.xenondevs.nova.util.isSourceFluid
import xyz.xenondevs.nova.util.item.playPlaceSoundEffect
import xyz.xenondevs.nova.util.sourceFluidType
import xyz.xenondevs.nova.world.region.Region
import xyz.xenondevs.nova.world.region.VisualRegion
import xyz.xenondevs.nova.addon.simpleupgrades.ConsumerEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.getFluidContainer
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import java.util.*

private val ENERGY_CAPACITY = configReloadable { NovaConfig[PUMP].getLong("energy_capacity") }
private val ENERGY_PER_TICK = configReloadable { NovaConfig[PUMP].getLong("energy_per_tick") }
private val FLUID_CAPACITY = configReloadable { NovaConfig[PUMP].getLong("fluid_capacity") }
private val REPLACEMENT_BLOCK by configReloadable { Material.valueOf(NovaConfig[PUMP].getString("replacement_block")!!) }
private val IDLE_TIME by configReloadable { NovaConfig[PUMP].getLong("idle_time") }

private val MIN_RANGE = configReloadable { NovaConfig[PUMP].getInt("range.min") }
private val MAX_RANGE = configReloadable { NovaConfig[PUMP].getInt("range.max") }
private val DEFAULT_RANGE by configReloadable { NovaConfig[PUMP].getInt("range.default") }

class Pump(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState), Upgradable {
    
    override val upgradeHolder = getUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.RANGE, UpgradeTypes.FLUID)
    private val fluidTank = getFluidContainer("tank", hashSetOf(FluidType.WATER, FluidType.LAVA), FLUID_CAPACITY, upgradeHolder = upgradeHolder)
    override val energyHolder = ConsumerEnergyHolder(this, ENERGY_CAPACITY, ENERGY_PER_TICK, upgradeHolder = upgradeHolder) { createExclusiveSideConfig(NetworkConnectionType.INSERT, BlockSide.TOP) }
    override val fluidHolder = NovaFluidHolder(this, fluidTank to NetworkConnectionType.EXTRACT) { createExclusiveSideConfig(NetworkConnectionType.EXTRACT, BlockSide.TOP) }
    
    private var mode = retrieveData("mode") { PumpMode.REPLACE }
    private val region = getUpgradableRegion(UpgradeTypes.RANGE, MIN_RANGE, MAX_RANGE, DEFAULT_RANGE) {
        idleTime = maxIdleTime // resets idle time for the case that the pump has already finished
        
        val range = it.toDouble()
        val min = location.clone().subtract(range - 1, range, range - 1)
        val max = location.clone().add(range, 0.0, range)
        Region(min, max)
    }
    
    private var maxIdleTime = 0
    private var idleTime = 0
    
    private var lastBlock: Block? = null
    private var sortedFaces = LinkedList(HORIZONTAL_FACES)
    
    init {
        reload()
    }
    
    override fun reload() {
        super.reload()
        
        maxIdleTime = (IDLE_TIME / upgradeHolder.getValue(UpgradeTypes.SPEED)).toInt()
        if (idleTime > maxIdleTime) idleTime = maxIdleTime
    }
    
    override fun handleTick() {
        if (energyHolder.energy >= energyHolder.energyConsumption && fluidTank.accepts(FluidType.WATER, 1000)) {
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
            energyHolder.energy -= energyHolder.energyConsumption
            idleTime = maxIdleTime
        } else {
            lastBlock = null
            idleTime = 60 * 20 // ByteZ' Idee
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
            if (fluidTank.accepts(fluidType) && newBlock in region && ProtectionManager.canBreak(this, null, newBlock.location).get()) {
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
                val block = location.clone().advance(BlockFace.DOWN).block
                val fluidType = block.sourceFluidType ?: return@repeat
                if (fluidTank.accepts(fluidType) && ProtectionManager.canBreak(this, null, block.location).get())
                    return block to fluidType
                return@repeat
            }
            for (x in -r..r) {
                for (y in -r - 1 until 0) {
                    for (z in -r..r) {
                        if ((x != -r && x != r) && (y != -r - 1 && y != -1) && (z != -r && z != r))
                            continue
                        val block = location.clone().add(x.toDouble(), y.toDouble(), z.toDouble()).block
                        val fluidType = block.sourceFluidType ?: continue
                        if (fluidTank.accepts(fluidType) && ProtectionManager.canBreak(this, null, block.location).get())
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
    
    override fun handleRemoved(unload: Boolean) {
        super.handleRemoved(unload)
        VisualRegion.removeRegion(uuid)
    }
    
    override fun saveData() {
        super.saveData()
        storeData("mode", mode)
    }
    
    @TileEntityMenuClass
    inner class PumpMenu(player: Player) : IndividualTileEntityMenu(player) {
        
        private val sideConfigGui = SideConfigMenu(
            this@Pump,
            fluidContainerNames = listOf(fluidTank to "container.nova.fluid_tank"),
            openPrevious = ::openWindow
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
            
            override fun getItemProvider() =
                (if (mode == PumpMode.PUMP) GuiMaterials.PUMP_MODE_BTN else GuiMaterials.PUMP_REPLACE_MODE_BTN).clientsideProvider
            
            override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) {
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