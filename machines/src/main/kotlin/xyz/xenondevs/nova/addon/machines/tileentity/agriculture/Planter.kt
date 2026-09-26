package xyz.xenondevs.nova.addon.machines.tileentity.agriculture

import kotlinx.coroutines.runBlocking
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.BlockType
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.dsl.item
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.registry.Blocks.PLANTER
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.util.PlantUtils
import xyz.xenondevs.nova.addon.machines.util.blockSequence
import xyz.xenondevs.nova.addon.machines.util.energyConsumption
import xyz.xenondevs.nova.addon.machines.util.isTillable
import xyz.xenondevs.nova.addon.machines.util.iterator
import xyz.xenondevs.nova.addon.machines.util.maxIdleTime
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedRegion
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.invui.dsl.with
import xyz.xenondevs.nova.util.BlockSideSet
import xyz.xenondevs.nova.util.below
import xyz.xenondevs.nova.util.item.damage
import xyz.xenondevs.nova.util.playClickSound
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.blockType
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.item.guiItemProvider
import xyz.xenondevs.nova.registry.tags.ItemTypeTags
import xyz.xenondevs.nova.world.item.itemType
import xyz.xenondevs.nova.world.region.Region

private val BLOCKED_SIDES = BlockSideSet(front = true)

private val MAX_ENERGY = PLANTER.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = PLANTER.config.entry<Long>("energy_per_tick")
private val ENERGY_PER_PLANT = PLANTER.config.entry<Long>("energy_per_plant")
private val IDLE_TIME = PLANTER.config.entry<Int>("idle_time")
private val MIN_RANGE = PLANTER.config.entry<Int>("range", "min")
private val MAX_RANGE = PLANTER.config.entry<Int>("range", "max")
private val DEFAULT_RANGE by PLANTER.config.entry<Int>("range", "default")

class Planter(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
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
    
    private val autoTillProvider = storedValue("autoTill") { true }
    private var autoTill by autoTillProvider
    private var timePassed = 0
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.PLANTER) {
        upperGui by gui(
            "s u v . . . . p e",
            "i i i . . h . n e",
            "i i i . . f . m e",
        ) {
            'i' by inputInventory
            'h' by (hoesInventory with GuiItems.HOE_PLACEHOLDER)
            's' by openSideConfigItem(
                mapOf(
                    itemHolder.getNetworkedInventory(inputInventory) to "inventory.nova.input",
                    itemHolder.getNetworkedInventory(hoesInventory) to "inventory.machines.hoes",
                )
            )
            'f' by item {
                itemProvider by autoTillProvider.flatMap {
                    (if (it) GuiItems.HOE_BTN_ON else GuiItems.HOE_BTN_OFF).guiItemProvider
                }
                onClick {
                    autoTill = !autoTill
                    player.playClickSound()
                }
            }
            'u' by openUpgradesItem(upgradeHolder)
            'v' by plantRegion.visualizeRegionItem
            'p' by plantRegion.increaseSizeItem
            'm' by plantRegion.decreaseSizeItem
            'n' by plantRegion.displaySizeItem
            'e' by energyBar(energyHolder)
        }
    }
    
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
                if (soil.blockType.isTillable() && autoTill && !hoesInventory.isEmpty) tillDirt(soil)
                
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
            val soilType = soilBlock.blockType
            
            // if the plant block is already occupied continue
            if (!block.blockType.isAir)
                continue
            
            val soilTypeApplicable = PlantUtils.canBePlaced(seedStack, block)
            if (soilTypeApplicable) {
                // if the seed can be placed on the soil block, only the permission needs to be checked
                val hasPermissions = runBlocking { ProtectionManager.canPlace(this@Planter, seedStack, block) } // TODO: non-blocking
                if (hasPermissions)
                    return block
            } else {
                // if the seed can not be placed on the soil block, check if this seed requires farmland and if it does
                // check if the soil block can be tilled
                val requiresFarmland = PlantUtils.requiresFarmland(seedStack)
                val isOrCanBeFarmland = soilType == BlockType.FARMLAND || (soilType.isTillable() && autoTill && !emptyHoes)
                if (requiresFarmland && !isOrCanBeFarmland)
                    continue
                
                // the block can be tilled, now check for both planting and tilling permissions
                val hasPermissions = runBlocking {
                    ProtectionManager.canPlace(this@Planter, seedStack, block) &&
                        ProtectionManager.canUseBlock(this@Planter, hoesInventory.getItem(0), soilBlock)
                } // TODO: non-blocking
                if (hasPermissions)
                    return block
            }
        }
        return null
    }
    
    private fun getNextTillableBlock(): Block? {
        return plantRegion.blockSequence.firstOrNull {
            it.blockType.isTillable()
                && runBlocking { ProtectionManager.canUseBlock(this@Planter, hoesInventory.getItem(0), it) } // TODO: non-blocking
        }
    }
    
    private fun tillDirt(block: Block) {
        block.blockType = BlockType.FARMLAND
        block.world.playSound(block.location, Sound.ITEM_HOE_TILL, 1f, 1f)
        useHoe()
    }
    
    private fun handleHoeUpdate(event: ItemPreUpdateEvent) {
        if ((event.isAdd || event.isSwap) && event.newItem!!.itemType !in ItemTypeTags.HOES)
            event.isCancelled = true
    }
    
    private fun handleSeedUpdate(event: ItemPreUpdateEvent) {
        if (!event.isRemove && !PlantUtils.isSeed(event.newItem!!))
            event.isCancelled = true
    }
    
    private fun useHoe() {
        if (hoesInventory.isEmpty)
            return
        
        hoesInventory.modifyItem(null, 0) { it?.damage(1, block.world) }
    }
    
}
