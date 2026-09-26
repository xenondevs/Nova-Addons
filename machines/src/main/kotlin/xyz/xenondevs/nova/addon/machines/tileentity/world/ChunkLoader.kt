package xyz.xenondevs.nova.addon.machines.tileentity.world

import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.combinedProvider
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.commons.provider.provider
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.nova.addon.machines.registry.Blocks.CHUNK_LOADER
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.ui.menu.item.addNumberItem
import xyz.xenondevs.nova.ui.menu.item.displayNumberItem
import xyz.xenondevs.nova.ui.menu.item.removeNumberItem
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.nova.util.getSurroundingChunks
import org.bukkit.block.Block
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.ChunkLoadManager
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.chunkPos
import xyz.xenondevs.nova.world.pos
import kotlin.math.roundToLong

private val MAX_ENERGY = CHUNK_LOADER.config.entry<Long>("capacity")
private val ENERGY_PER_CHUNK = CHUNK_LOADER.config.entry<Long>("energy_per_chunk")
private val MAX_RANGE by CHUNK_LOADER.config.entry<Int>("max_range")

class ChunkLoader(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.ENERGY, UpgradeTypes.EFFICIENCY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT)
    
    private var range = storedValue("range") { 0 }
    private val menuRange = mutableProvider(range.get())
    private val chunks by range.map { block.chunkPos.chunk!!.getSurroundingChunks(it, true) }
    private var active = false
    
    private val energyPerTick by combinedProvider(ENERGY_PER_CHUNK, range, upgradeHolder.getValueProvider(UpgradeTypes.EFFICIENCY))
        .map { (energyPerChunk, range, efficiency) ->
            val diameter = range * 2 + 1
            (energyPerChunk * diameter * diameter / efficiency).roundToLong()
        }
    private val displayRange = menuRange.map { it + 1 }
    private val rangeLimit = provider(0..MAX_RANGE)
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.CHUNK_LOADER) {
        upperGui by gui(
            "s u . m n p . . e",
        ) {
            's' by openSideConfigItem()
            'p' by addNumberItem(rangeLimit, menuRange, "menu.nova.region.increase")
            'm' by removeNumberItem(rangeLimit, menuRange, "menu.nova.region.decrease")
            'n' by displayNumberItem(displayRange, "menu.nova.region.size")
            'u' by openUpgradesItem(upgradeHolder)
            'e' by energyBar(energyHolder)
        }
    }
    
    init {
        menuRange.subscribe(::setRange)
    }
    
    override fun handleDisable() {
        super.handleDisable()
        setChunksForceLoaded(false)
    }
    
    override fun handleTick() {
        if (energyHolder.energy >= energyPerTick) {
            energyHolder.energy -= energyPerTick
            if (!active) {
                setChunksForceLoaded(true)
                active = true
            }
        } else if (active) {
            setChunksForceLoaded(false)
            active = false
        }
    }
    
    private fun setChunksForceLoaded(state: Boolean) {
        chunks.forEach {
            if (state) ChunkLoadManager.submitChunkLoadRequest(it.pos, uuid)
            else ChunkLoadManager.revokeChunkLoadRequest(it.pos, uuid)
        }
    }
    
    private fun setRange(range: Int) {
        if (active) setChunksForceLoaded(false)
        this.range.set(range)
        if (active) setChunksForceLoaded(true)
    }
    
}
