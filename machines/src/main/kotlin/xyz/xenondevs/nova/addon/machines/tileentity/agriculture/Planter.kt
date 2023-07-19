package xyz.xenondevs.nova.addon.machines.tileentity.agriculture

import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.item.impl.AbstractItem
import xyz.xenondevs.nova.data.config.NovaConfig
import xyz.xenondevs.nova.data.config.configReloadable
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.item.tool.ToolCategory
import xyz.xenondevs.nova.item.tool.VanillaToolCategories
import xyz.xenondevs.nova.addon.machines.registry.Blocks.PLANTER
import xyz.xenondevs.nova.addon.machines.registry.GuiMaterials
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.item.holder.NovaItemHolder
import xyz.xenondevs.nova.tileentity.upgrade.Upgradable
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.OpenUpgradesItem
import xyz.xenondevs.nova.ui.addIngredient
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.item.DamageableUtils
import xyz.xenondevs.nova.util.item.PlantUtils
import xyz.xenondevs.nova.util.item.isTillable
import xyz.xenondevs.nova.world.region.Region
import xyz.xenondevs.nova.addon.simpleupgrades.ConsumerEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes

private val MAX_ENERGY = configReloadable { NovaConfig[PLANTER].getLong("capacity") }
private val ENERGY_PER_TICK = configReloadable { NovaConfig[PLANTER].getLong("energy_per_tick") }
private val ENERGY_PER_PLANT = configReloadable { NovaConfig[PLANTER].getLong("energy_per_plant") }
private val IDLE_TIME by configReloadable { NovaConfig[PLANTER].getInt("idle_time") }
private val MIN_RANGE = configReloadable { NovaConfig[PLANTER].getInt("range.min") }
private val MAX_RANGE = configReloadable { NovaConfig[PLANTER].getInt("range.max") }
private val DEFAULT_RANGE by configReloadable { NovaConfig[PLANTER].getInt("range.default") }

class Planter(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState), Upgradable {
    
    private val inputInventory = getInventory("input", 6, ::handleSeedUpdate)
    private val hoesInventory = getInventory("hoes", 1, ::handleHoeUpdate)
    override val upgradeHolder = getUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.RANGE)
    override val energyHolder = ConsumerEnergyHolder(this, MAX_ENERGY, ENERGY_PER_TICK, ENERGY_PER_PLANT, upgradeHolder) { createSideConfig(NetworkConnectionType.INSERT, BlockSide.FRONT) }
    override val itemHolder = NovaItemHolder(
        this,
        inputInventory to NetworkConnectionType.INSERT,
        hoesInventory to NetworkConnectionType.INSERT
    ) { createSideConfig(NetworkConnectionType.INSERT, BlockSide.FRONT) }
    
    private var autoTill = retrieveData("autoTill") { true }
    private var maxIdleTime = 0
    private var timePassed = 0
    
    private lateinit var soilRegion: Region
    private val plantRegion = getUpgradableRegion(UpgradeTypes.RANGE, MIN_RANGE, MAX_RANGE, DEFAULT_RANGE) {
        soilRegion = getBlockFrontRegion(it, it, 1, -1)
        getBlockFrontRegion(it, it, 1, 0)
    }
    
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
            energyHolder.energy -= energyHolder.energyConsumption // idle energy consumption
            
            if (energyHolder.energy >= energyHolder.specialEnergyConsumption && timePassed++ >= maxIdleTime) {
                timePassed = 0
                placeNextSeed()
            }
        }
    }
    
    private fun placeNextSeed() {
        if (!inputInventory.isEmpty) {
            // loop over items until a placeable seed has been found
            for ((index, item) in inputInventory.items.withIndex()) {
                if (item == null) continue
                
                // find a location to place this seed or skip to the next one if there isn't one
                val (plant, soil) = getNextPlantBlock(item) ?: continue
                energyHolder.energy -= energyHolder.specialEnergyConsumption
                
                // till dirt if possible
                if (soil.type.isTillable() && autoTill && !hoesInventory.isEmpty) tillDirt(soil)
                
                // plant the seed
                PlantUtils.placeSeed(item, plant, true)
                
                // remove one from the seed stack
                inputInventory.addItemAmount(SELF_UPDATE_REASON, index, -1)
                
                // break the loop as a seed has been placed
                break
            }
        } else if (autoTill && !hoesInventory.isEmpty) {
            val block = getNextTillableBlock()
            if (block != null) {
                energyHolder.energy -= energyHolder.specialEnergyConsumption
                tillDirt(block)
            }
        }
    }
    
    private fun getNextPlantBlock(seedStack: ItemStack): Pair<Block, Block>? {
        val emptyHoes = hoesInventory.isEmpty
        val index = plantRegion.withIndex().indexOfFirst { (index, block) ->
            val soilBlock = soilRegion[index]
            val soilType = soilBlock.type
            
            // if the plant block is already occupied return false
            if (!block.type.isAir)
                return@indexOfFirst false
            
            val soilTypeApplicable = PlantUtils.canBePlaced(seedStack, soilBlock)
            if (soilTypeApplicable) {
                // if the seed can be placed on the soil block, only the permission needs to be checked
                return@indexOfFirst ProtectionManager.canPlace(this, seedStack, block.location).get()
            } else {
                // if the seed can not be placed on the soil block, check if this seed requires farmland and if it does
                // check if the soil block can be tilled
                val requiresFarmland = PlantUtils.requiresFarmland(seedStack)
                val isOrCanBeFarmland = soilType == Material.FARMLAND || (soilType.isTillable() && autoTill && !emptyHoes)
                if (requiresFarmland && !isOrCanBeFarmland)
                    return@indexOfFirst false
                
                // the block can be tilled, now check for both planting and tilling permissions
                return@indexOfFirst ProtectionManager.canPlace(this, seedStack, block.location).get() &&
                    ProtectionManager.canUseBlock(this, hoesInventory.getItem(0), soilBlock.location).get()
            }
        }
        
        if (index == -1)
            return null
        return plantRegion[index] to soilRegion[index]
    }
    
    private fun getNextTillableBlock(): Block? {
        return plantRegion.firstOrNull {
            it.type.isTillable()
                && ProtectionManager.canUseBlock(this, hoesInventory.getItem(0), it.location).get()
        }
    }
    
    private fun tillDirt(block: Block) {
        block.type = Material.FARMLAND
        world.playSound(block.location, Sound.ITEM_HOE_TILL, 1f, 1f)
        useHoe()
    }
    
    private fun handleHoeUpdate(event: ItemPreUpdateEvent) {
        if ((event.isAdd || event.isSwap) && ToolCategory.ofItem(event.newItem) != VanillaToolCategories.HOE)
            event.isCancelled = true
    }
    
    private fun handleSeedUpdate(event: ItemPreUpdateEvent) {
        if (!event.isRemove && !PlantUtils.isSeed(event.newItem!!))
            event.isCancelled = true
    }
    
    private fun useHoe() {
        if (hoesInventory.isEmpty)
            return
        
        hoesInventory.setItem(null, 0, DamageableUtils.damageItem(hoesInventory.getItem(0)!!))
    }
    
    override fun saveData() {
        super.saveData()
        storeData("autoTill", autoTill)
    }
    
    @TileEntityMenuClass
    inner class PlanterMenu(player: Player) : IndividualTileEntityMenu(player) {
        
        private val sideConfigGui = SideConfigMenu(
            this@Planter,
            listOf(
                itemHolder.getNetworkedInventory(inputInventory) to "inventory.nova.input",
                itemHolder.getNetworkedInventory(hoesInventory) to "inventory.machines.hoes",
            ),
            ::openWindow
        )
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s u v # # p e |",
                "| i i i # h n e |",
                "| i i i # f m e |",
                "3 - - - - - - - 4")
            .addIngredient('i', inputInventory)
            .addIngredient('h', hoesInventory, GuiMaterials.HOE_PLACEHOLDER)
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('f', AutoTillingItem())
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('v', plantRegion.createVisualizeRegionItem(player))
            .addIngredient('p', plantRegion.increaseSizeItem)
            .addIngredient('m', plantRegion.decreaseSizeItem)
            .addIngredient('n', plantRegion.displaySizeItem)
            .addIngredient('e', EnergyBar(3, energyHolder))
            .build()
        
        private inner class AutoTillingItem : AbstractItem() {
            
            override fun getItemProvider() =
                (if (autoTill) GuiMaterials.HOE_BTN_ON else GuiMaterials.HOE_BTN_OFF).clientsideProvider
            
            override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) {
                autoTill = !autoTill
                notifyWindows()
                
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
            }
            
        }
        
    }
    
}