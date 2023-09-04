package xyz.xenondevs.nova.addon.machines.tileentity.agriculture

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.Ageable
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.registry.Blocks.FERTILIZER
import xyz.xenondevs.nova.addon.simpleupgrades.ConsumerEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.data.config.entry
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.item.holder.NovaItemHolder
import xyz.xenondevs.nova.tileentity.upgrade.Upgradable
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.OpenUpgradesItem
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.item.PlantUtils
import xyz.xenondevs.nova.util.item.isFullyAged
import xyz.xenondevs.nova.world.region.VisualRegion

private val MAX_ENERGY = FERTILIZER.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = FERTILIZER.config.entry<Long>("energy_per_tick")
private val ENERGY_PER_FERTILIZE = FERTILIZER.config.entry<Long>("energy_per_fertilize")
private val IDLE_TIME by FERTILIZER.config.entry<Int>("idle_time")
private val MIN_RANGE = FERTILIZER.config.entry<Int>("range", "min")
private val MAX_RANGE = FERTILIZER.config.entry<Int>("range", "max")
private val DEFAULT_RANGE by FERTILIZER.config.entry<Int>("range", "default")

class Fertilizer(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState), Upgradable {
    
    private val fertilizerInventory = getInventory("fertilizer", 12, ::handleFertilizerUpdate)
    override val upgradeHolder = getUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.RANGE)
    override val energyHolder = ConsumerEnergyHolder(this, MAX_ENERGY, ENERGY_PER_TICK, ENERGY_PER_FERTILIZE, upgradeHolder) { createSideConfig(NetworkConnectionType.INSERT, BlockSide.FRONT) }
    override val itemHolder = NovaItemHolder(this, fertilizerInventory to NetworkConnectionType.INSERT) { createSideConfig(NetworkConnectionType.INSERT, BlockSide.FRONT) }
    
    private var maxIdleTime = 0
    private var timePassed = 0
    private val region = getUpgradableRegion(UpgradeTypes.RANGE, MIN_RANGE, MAX_RANGE, DEFAULT_RANGE) { getBlockFrontRegion(it, it, 1, 0) }
    
    init {
        reload()
    }
    
    override fun reload() {
        super.reload()
        maxIdleTime = (IDLE_TIME / upgradeHolder.getValue(UpgradeTypes.SPEED)).toInt()
        if (timePassed > maxIdleTime) timePassed = maxIdleTime
    }
    
    override fun handleTick() {
        if (energyHolder.energy >= energyHolder.energyConsumption) {
            energyHolder.energy -= energyHolder.energyConsumption
            if (energyHolder.energy >= energyHolder.specialEnergyConsumption) {
                if (timePassed++ >= maxIdleTime) {
                    timePassed = 0
                    if (!fertilizerInventory.isEmpty)
                        fertilizeNextPlant()
                }
            }
        }
    }
    
    private fun fertilizeNextPlant() {
        for ((index, item) in fertilizerInventory.items.withIndex()) {
            if (item == null) continue
            val plant = getNextPlant() ?: return
            PlantUtils.fertilize(plant)
            
            energyHolder.energy -= energyHolder.specialEnergyConsumption
            fertilizerInventory.addItemAmount(SELF_UPDATE_REASON, index, -1)
            break
        }
    }
    
    private fun getNextPlant(): Block? =
        region.blocks
            .firstOrNull {
                (it.blockData is Ageable && !it.isFullyAged())
                    && ProtectionManager.canUseBlock(this, ItemStack(Material.BONE_MEAL), it.location).get()
            }
    
    private fun handleFertilizerUpdate(event: ItemPreUpdateEvent) {
        if ((event.isAdd || event.isSwap) && event.newItem?.type != Material.BONE_MEAL)
            event.isCancelled = true
    }
    
    override fun handleRemoved(unload: Boolean) {
        super.handleRemoved(unload)
        VisualRegion.removeRegion(uuid)
    }
    
    @TileEntityMenuClass
    inner class FertilizerMenu(player: Player) : IndividualTileEntityMenu(player) {
        
        private val sideConfigGui = SideConfigMenu(
            this@Fertilizer,
            listOf(itemHolder.getNetworkedInventory(fertilizerInventory) to "inventory.machines.fertilizer"),
            ::openWindow
        )
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s p i i i i e |",
                "| v n i i i i e |",
                "| u m i i i i e |",
                "3 - - - - - - - 4")
            .addIngredient('i', fertilizerInventory)
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('e', EnergyBar(3, energyHolder))
            .addIngredient('v', region.createVisualizeRegionItem(player))
            .addIngredient('p', region.increaseSizeItem)
            .addIngredient('m', region.decreaseSizeItem)
            .addIngredient('n', region.displaySizeItem)
            .build()
        
    }
    
}
