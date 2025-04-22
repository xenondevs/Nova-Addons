package xyz.xenondevs.nova.addon.machines.tileentity.world

import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.combinedProvider
import xyz.xenondevs.commons.provider.map
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.notifyWindows
import xyz.xenondevs.nova.addon.machines.registry.Blocks.CHUNK_LOADER
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.item.AddNumberItem
import xyz.xenondevs.nova.ui.menu.item.DisplayNumberItem
import xyz.xenondevs.nova.ui.menu.item.RemoveNumberItem
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.getSurroundingChunks
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.ChunkLoadManager
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.pos
import kotlin.math.roundToLong

private val MAX_ENERGY = CHUNK_LOADER.config.entry<Long>("capacity")
private val ENERGY_PER_CHUNK = CHUNK_LOADER.config.entry<Long>("energy_per_chunk")
private val MAX_RANGE by CHUNK_LOADER.config.entry<Int>("max_range")

class ChunkLoader(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.ENERGY, UpgradeTypes.EFFICIENCY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT)
    
    private var range = storedValue("range") { 0 }
    private val chunks by range.map { pos.chunkPos.chunk!!.getSurroundingChunks(it, true) }
    private var active = false
    
    private val energyPerTick by combinedProvider(ENERGY_PER_CHUNK, range, upgradeHolder.getValueProvider(UpgradeTypes.EFFICIENCY))
        .map { (energyPerChunk, range, efficiency) ->
            val diameter = range * 2 + 1
            (energyPerChunk * diameter * diameter / efficiency).roundToLong()
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
    
    @TileEntityMenuClass
    inner class ChunkLoaderMenu : GlobalTileEntityMenu() {
        
        private val sideConfigGui = SideConfigMenu(
            this@ChunkLoader,
            ::openWindow
        )
        
        private val rangeItems = ArrayList<Item>()
        
        override val gui = Gui.builder()
            .setStructure(
                "1 - - - - - - 2 e",
                "| u # m n p # | e",
                "3 - - - - - - 4 e")
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('p', AddNumberItem({ 0..MAX_RANGE }, range::get, ::setRange, "menu.nova.region.increase").also(rangeItems::add))
            .addIngredient('m', RemoveNumberItem({ 0..MAX_RANGE }, range::get, ::setRange, "menu.nova.region.decrease").also(rangeItems::add))
            .addIngredient('n', DisplayNumberItem({ range.get() + 1 }, "menu.nova.region.size").also(rangeItems::add))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('e', EnergyBar(3, energyHolder))
            .build()
        
        private fun setRange(range: Int) {
            this@ChunkLoader.setRange(range)
            rangeItems.notifyWindows()
        }
        
    }
    
}