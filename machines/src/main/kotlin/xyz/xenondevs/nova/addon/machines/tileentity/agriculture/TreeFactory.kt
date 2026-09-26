package xyz.xenondevs.nova.addon.machines.tileentity.agriculture

import net.minecraft.core.particles.ParticleTypes
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType
import org.joml.Vector3f
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.commons.provider.provider
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.inventory.get
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.nova.addon.machines.registry.Blocks.TREE_FACTORY
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.Models
import xyz.xenondevs.nova.addon.machines.util.energyConsumption
import xyz.xenondevs.nova.addon.machines.util.maxIdleTime
import xyz.xenondevs.nova.addon.machines.util.speedMultipliedValue
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.GlobalValues
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.network.sendTo
import xyz.xenondevs.nova.packetentity.packetItemDisplay
import xyz.xenondevs.nova.registry.RegistryEntry
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.registry.entries.ItemTypeEntries
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.invui.dsl.with
import xyz.xenondevs.nova.util.BlockSideSet
import xyz.xenondevs.nova.util.dropItem
import xyz.xenondevs.nova.world.item.itemTypeEntry
import xyz.xenondevs.nova.util.particle.color
import xyz.xenondevs.nova.util.particle.particle
import org.bukkit.block.Block
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT

import xyz.xenondevs.nova.world.item.guiItemProvider
import java.awt.Color

private class PlantConfiguration(
    val miniature: RegistryEntry.Paper<ItemType>,
    val loot: ItemStack,
    val color: Color
)

private val PLANTS = mapOf(
    ItemTypeEntries.OAK_SAPLING to PlantConfiguration(Models.OAK_TREE_MINIATURE, ItemType.OAK_LOG.createItemStack(), Color(43, 82, 39)),
    ItemTypeEntries.SPRUCE_SAPLING to PlantConfiguration(Models.SPRUCE_TREE_MINIATURE, ItemType.SPRUCE_LOG.createItemStack(), Color(43, 87, 60)),
    ItemTypeEntries.BIRCH_SAPLING to PlantConfiguration(Models.BIRCH_TREE_MINIATURE, ItemType.BIRCH_LOG.createItemStack(), Color(49, 63, 35)),
    ItemTypeEntries.JUNGLE_SAPLING to PlantConfiguration(Models.JUNGLE_TREE_MINIATURE, ItemType.JUNGLE_LOG.createItemStack(), Color(51, 127, 43)),
    ItemTypeEntries.ACACIA_SAPLING to PlantConfiguration(Models.ACACIA_TREE_MINIATURE, ItemType.ACACIA_LOG.createItemStack(), Color(113, 125, 75)),
    ItemTypeEntries.DARK_OAK_SAPLING to PlantConfiguration(Models.DARK_OAK_TREE_MINIATURE, ItemType.DARK_OAK_LOG.createItemStack(), Color(26, 65, 17)),
    ItemTypeEntries.MANGROVE_PROPAGULE to PlantConfiguration(Models.MANGROVE_TREE_MINIATURE, ItemType.MANGROVE_LOG.createItemStack(), Color(32, 47, 14)),
    ItemTypeEntries.CRIMSON_FUNGUS to PlantConfiguration(Models.CRIMSON_TREE_MINIATURE, ItemType.CRIMSON_STEM.createItemStack(), Color(121, 0, 0)),
    ItemTypeEntries.WARPED_FUNGUS to PlantConfiguration(Models.WARPED_TREE_MINIATURE, ItemType.WARPED_STEM.createItemStack(), Color(22, 124, 132)),
    ItemTypeEntries.RED_MUSHROOM to PlantConfiguration(Models.GIANT_RED_MUSHROOM_MINIATURE, ItemType.RED_MUSHROOM.createItemStack(3), Color(192, 39, 37)),
    ItemTypeEntries.BROWN_MUSHROOM to PlantConfiguration(Models.GIANT_BROWN_MUSHROOM_MINIATURE, ItemType.BROWN_MUSHROOM.createItemStack(3), Color(149, 112, 80))
)

private val BLOCKED_SIDES = BlockSideSet(front = true, left = true, right = true, top = true)

private val MAX_ENERGY = TREE_FACTORY.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = TREE_FACTORY.config.entry<Long>("energy_per_tick")
private val PROGRESS_PER_TICK = TREE_FACTORY.config.entry<Double>("progress_per_tick")
private val IDLE_TIME = TREE_FACTORY.config.entry<Int>("idle_time")

class TreeFactory(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val inputInventory = storedInventory("input", 1, false, intArrayOf(1), ::handleInputInventoryUpdate)
    private val outputInventory = storedInventory("output", 9, ::handleOutputInventoryUpdate)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(outputInventory to EXTRACT, inputInventory to INSERT, blockedSides = BLOCKED_SIDES)
    
    private val energyPerTick by energyConsumption(ENERGY_PER_TICK, upgradeHolder)
    private val progressPerTick by speedMultipliedValue(PROGRESS_PER_TICK, upgradeHolder)
    private val maxIdleTime by maxIdleTime(IDLE_TIME, upgradeHolder)
    
    private val plantType = mutableProvider(inputInventory[0]?.itemTypeEntry)
    private val growthProgress = mutableProvider(0f)
    private var idleTimeLeft = 0
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.TREE_FACTORY) {
        upperGui by gui(
            "s . . o o o . . e",
            "u i . o o o . . e",
            ". . . o o o . . e",
        ) {
            'i' by (inputInventory with GuiItems.SAPLING_PLACEHOLDER)
            'o' by outputInventory
            's' by openSideConfigItem(
                mapOf(
                    itemHolder.getNetworkedInventory(inputInventory) to "inventory.nova.input",
                    itemHolder.getNetworkedInventory(outputInventory) to "inventory.nova.output"
                )
            )
            'u' by openUpgradesItem(upgradeHolder)
            'e' by energyBar(energyHolder)
        }
    }
    
    private val plant = packetItemDisplay {
        location by block.location.add(0.5, 1.0 / 16.0, 0.5)
        metadata {
            transformationInterpolationDuration by 1
            scale by growthProgress.map { Vector3f(it, it, it) }
            translation by growthProgress.map { Vector3f(0.0f, 0.5f * it, 0.0f) }
            itemStack by plantType
                .flatMap { PLANTS[it]?.miniature?.guiItemProvider ?: provider(ItemProvider.EMPTY) }
                .map { it.get() }
        }
    }
    
    override fun handleEnable() {
        super.handleEnable()
        plant.spawn()
    }
    
    override fun handleDisable() {
        super.handleDisable()
        plant.despawn()
    }
    
    override fun handleTick() {
        val plantType = plantType.get()
        if (energyHolder.energy >= energyPerTick && plantType != null) {
            val plantLoot = PLANTS[plantType]!!.loot
            if (!GlobalValues.DROP_EXCESS_ON_GROUND && !outputInventory.canHold(plantLoot))
                return
            
            energyHolder.energy -= energyPerTick
            
            if (idleTimeLeft == 0) {
                growthProgress.set((growthProgress.get() + progressPerTick.toFloat()).coerceAtMost(1f))
                if (growthProgress.get() >= 1.0)
                    idleTimeLeft = maxIdleTime
            } else {
                idleTimeLeft--
                
                particle(ParticleTypes.DUST) {
                    color(PLANTS[plantType]!!.color)
                    location(block.location.add(0.5, 0.5, 0.5))
                    offset(0.15, 0.15, 0.15)
                    speed(0.1f)
                    amount(5)
                }.sendTo(getViewers())
                
                if (idleTimeLeft == 0) {
                    growthProgress.set(0f)
                    
                    val leftover = outputInventory.addItem(SELF_UPDATE_REASON, plantLoot)
                    if (GlobalValues.DROP_EXCESS_ON_GROUND && leftover > 0) {
                        val remains = plantLoot.clone().apply { amount = leftover }
                        val dropLoc = block.location.add(0.5, 0.5, 0.5)
                        dropLoc.dropItem(remains)
                    }
                }
            }
        }
    }
    
    private fun handleInputInventoryUpdate(event: ItemPreUpdateEvent) {
        if (event.newItem != null && event.newItem!!.itemTypeEntry !in PLANTS.keys) {
            event.isCancelled = true
        } else {
            plantType.set(event.newItem?.itemTypeEntry)
            growthProgress.set(0f)
        }
    }
    
    private fun handleOutputInventoryUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = event.updateReason != SELF_UPDATE_REASON && !event.isRemove
    }
    
}
