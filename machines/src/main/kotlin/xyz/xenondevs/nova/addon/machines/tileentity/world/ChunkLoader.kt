package xyz.xenondevs.nova.addon.machines.tileentity.world

import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.notifyWindows
import xyz.xenondevs.nova.data.config.NovaConfig
import xyz.xenondevs.nova.data.config.configReloadable
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.addon.machines.registry.Blocks.CHUNK_LOADER
import xyz.xenondevs.nova.tileentity.ChunkLoadManager
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.upgrade.Upgradable
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.OpenUpgradesItem
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu
import xyz.xenondevs.nova.ui.item.AddNumberItem
import xyz.xenondevs.nova.ui.item.DisplayNumberItem
import xyz.xenondevs.nova.ui.item.RemoveNumberItem
import xyz.xenondevs.nova.util.getSurroundingChunks
import xyz.xenondevs.nova.world.pos
import xyz.xenondevs.nova.addon.simpleupgrades.ConsumerEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes

private val MAX_ENERGY = configReloadable { NovaConfig[CHUNK_LOADER].getLong("capacity") }
private val ENERGY_PER_CHUNK by configReloadable { NovaConfig[CHUNK_LOADER].getLong("energy_per_chunk") }
private val MAX_RANGE by configReloadable { NovaConfig[CHUNK_LOADER].getInt("max_range") }

class ChunkLoader(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState), Upgradable {
    
    override val upgradeHolder = getUpgradeHolder(UpgradeTypes.ENERGY, UpgradeTypes.EFFICIENCY)
    override val energyHolder = ConsumerEnergyHolder(this, MAX_ENERGY, upgradeHolder = upgradeHolder) { createSideConfig(NetworkConnectionType.INSERT) }
    
    private var range = retrieveData("range") { 0 }
    private var chunks = chunk.getSurroundingChunks(range, true)
    private var active = false
    
    private var energyPerTick = 0
    
    init {
        reload()
    }
    
    override fun reload() {
        super.reload()
        energyPerTick = (ENERGY_PER_CHUNK * chunks.size / upgradeHolder.getValue(UpgradeTypes.EFFICIENCY)).toInt()
    }
    
    override fun saveData() {
        super.saveData()
        storeData("range", range)
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
        this.range = range
        setChunksForceLoaded(false)
        chunks = chunk.getSurroundingChunks(range, true)
        active = false
        reload()
    }
    
    override fun handleRemoved(unload: Boolean) {
        super.handleRemoved(unload)
        if (!unload) setChunksForceLoaded(false)
    }
    
    @TileEntityMenuClass
    inner class ChunkLoaderMenu : GlobalTileEntityMenu() {
        
        private val sideConfigGui = SideConfigMenu(
            this@ChunkLoader,
            ::openWindow
        )
        
        private val rangeItems = ArrayList<Item>()
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - 2 e",
                "| u # m n p # | e",
                "3 - - - - - - 4 e")
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('p', AddNumberItem({ 0..MAX_RANGE }, { range }, ::setRange, "menu.nova.region.increase").also(rangeItems::add))
            .addIngredient('m', RemoveNumberItem({ 0..MAX_RANGE }, { range }, ::setRange, "menu.nova.region.decrease").also(rangeItems::add))
            .addIngredient('n', DisplayNumberItem ({ range + 1 }, "menu.nova.region.size").also(rangeItems::add))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('e', EnergyBar(3, energyHolder))
            .build()
        
        private fun setRange(range: Int) {
            this@ChunkLoader.setRange(range)
            rangeItems.notifyWindows()
        }
        
    }
    
}