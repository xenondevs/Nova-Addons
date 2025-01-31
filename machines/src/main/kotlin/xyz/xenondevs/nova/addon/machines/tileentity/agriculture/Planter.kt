package xyz.xenondevs.nova.addon.machines.tileentity.agriculture

import kotlinx.coroutines.runBlocking
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.enumSetOf
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.item.AbstractItem
import xyz.xenondevs.invui.item.Click
import xyz.xenondevs.nova.addon.machines.registry.Blocks.PLANTER
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.util.PlantUtils
import xyz.xenondevs.nova.addon.machines.util.blockSequence
import xyz.xenondevs.nova.addon.machines.util.energyConsumption
import xyz.xenondevs.nova.addon.machines.util.isTillable
import xyz.xenondevs.nova.addon.machines.util.iterator
import xyz.xenondevs.nova.addon.machines.util.maxIdleTime
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedRegion
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.addIngredient
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.below
import xyz.xenondevs.nova.util.item.damage
import xyz.xenondevs.nova.util.playClickSound
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.item.tool.ToolCategory
import xyz.xenondevs.nova.world.item.tool.VanillaToolCategories
import xyz.xenondevs.nova.world.pos
import xyz.xenondevs.nova.world.region.Region

private val BLOCKED_SIDES = enumSetOf(BlockSide.FRONT)

private val MAX_ENERGY = PLANTER.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = PLANTER.config.entry<Long>("energy_per_tick")
private val ENERGY_PER_PLANT = PLANTER.config.entry<Long>("energy_per_plant")
private val IDLE_TIME = PLANTER.config.entry<Int>("idle_time")
private val MIN_RANGE = PLANTER.config.entry<Int>("range", "min")
private val MAX_RANGE = PLANTER.config.entry<Int>("range", "max")
private val DEFAULT_RANGE by PLANTER.config.entry<Int>("range", "default")

class Planter(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val inputInventory = storedInventory("input", 6, ::handleSeedUpdate)
    private val hoesInventory = storedInventory("hoes", 1, ::handleHoeUpdate)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.RANGE)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(inputInventory to INSERT, hoesInventory to INSERT, blockedSides = BLOCKED_SIDES)
    
    private val energyPerTick by energyConsumption(ENERGY_PER_TICK, upgradeHolder)
    private val energyPerPlant by energyConsumption(ENERGY_PER_PLANT, upgradeHolder)
    private val maxIdleTime by maxIdleTime(IDLE_TIME, upgradeHolder)
    
    private lateinit var soilRegion: Region
    private val plantRegion = storedRegion("region.default", MIN_RANGE, MAX_RANGE, DEFAULT_RANGE, upgradeHolder) {
        val size = 1 + it * 2
        soilRegion = Region.inFrontOf(this, size, size, 1, -1)
        Region.inFrontOf(this, size, size, 1, 0)
    }
    
    private var autoTill by storedValue("autoTill") { true }
    private var timePassed = 0
    
    override fun handleTick() {
        if (energyHolder.energy >= energyPerTick) {
            energyHolder.energy -= energyPerTick
            if (energyHolder.energy >= energyPerPlant && timePassed++ >= maxIdleTime) {
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
                val plant = getNextPlantBlock(item) ?: continue
                val soil = plant.below
                energyHolder.energy -= energyPerPlant
                
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
                energyHolder.energy -= energyPerPlant
                tillDirt(block)
            }
        }
    }
    
    private fun getNextPlantBlock(seedStack: ItemStack): Block? {
        val emptyHoes = hoesInventory.isEmpty
        for (block in plantRegion) {
            val soilBlock = block.below
            val soilType = soilBlock.type
            
            // if the plant block is already occupied continue
            if (!block.type.isAir)
                continue
            
            val soilTypeApplicable = PlantUtils.canBePlaced(seedStack, soilBlock)
            if (soilTypeApplicable) {
                // if the seed can be placed on the soil block, only the permission needs to be checked
                val hasPermissions = runBlocking { ProtectionManager.canPlace(this@Planter, seedStack, block.pos) } // TODO: non-blocking
                if (hasPermissions)
                    return block
            } else {
                // if the seed can not be placed on the soil block, check if this seed requires farmland and if it does
                // check if the soil block can be tilled
                val requiresFarmland = PlantUtils.requiresFarmland(seedStack)
                val isOrCanBeFarmland = soilType == Material.FARMLAND || (soilType.isTillable() && autoTill && !emptyHoes)
                if (requiresFarmland && !isOrCanBeFarmland)
                    continue
                
                // the block can be tilled, now check for both planting and tilling permissions
                val hasPermissions = runBlocking {
                    ProtectionManager.canPlace(this@Planter, seedStack, block.pos) &&
                        ProtectionManager.canUseBlock(this@Planter, hoesInventory.getItem(0), soilBlock.pos)
                } // TODO: non-blocking
                if (hasPermissions)
                    return block
            }
        }
        return null
    }
    
    private fun getNextTillableBlock(): Block? {
        return plantRegion.blockSequence.firstOrNull {
            it.type.isTillable()
                && runBlocking { ProtectionManager.canUseBlock(this@Planter, hoesInventory.getItem(0), it.pos) } // TODO: non-blocking
        }
    }
    
    private fun tillDirt(block: Block) {
        block.type = Material.FARMLAND
        pos.world.playSound(block.location, Sound.ITEM_HOE_TILL, 1f, 1f)
        useHoe()
    }
    
    private fun handleHoeUpdate(event: ItemPreUpdateEvent) {
        if ((event.isAdd || event.isSwap) && VanillaToolCategories.HOE !in ToolCategory.ofItem(event.newItem))
            event.isCancelled = true
    }
    
    private fun handleSeedUpdate(event: ItemPreUpdateEvent) {
        if (!event.isRemove && !PlantUtils.isSeed(event.newItem!!))
            event.isCancelled = true
    }
    
    private fun useHoe() {
        if (hoesInventory.isEmpty)
            return
        
        hoesInventory.modifyItem(null, 0) { it?.damage(1, pos.world) }
    }
    
    @TileEntityMenuClass
    inner class PlanterMenu(player: Player) : IndividualTileEntityMenu(player) {
        
        private val sideConfigGui = SideConfigMenu(
            this@Planter,
            mapOf(
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
            .addIngredient('h', hoesInventory, GuiItems.HOE_PLACEHOLDER)
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
            
            override fun getItemProvider(player: Player) =
                (if (autoTill) GuiItems.HOE_BTN_ON else GuiItems.HOE_BTN_OFF).clientsideProvider
            
            override fun handleClick(clickType: ClickType, player: Player, click: Click) {
                autoTill = !autoTill
                notifyWindows()
                
                player.playClickSound()
            }
            
        }
        
    }
    
}