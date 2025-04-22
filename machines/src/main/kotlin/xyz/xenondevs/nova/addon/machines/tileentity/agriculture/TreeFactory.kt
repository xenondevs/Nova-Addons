package xyz.xenondevs.nova.addon.machines.tileentity.agriculture

import net.minecraft.core.particles.ParticleTypes
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.joml.Vector3f
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.enumSetOf
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.registry.Blocks.TREE_FACTORY
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.Models
import xyz.xenondevs.nova.addon.machines.util.energyConsumption
import xyz.xenondevs.nova.addon.machines.util.maxIdleTime
import xyz.xenondevs.nova.addon.machines.util.speedMultipliedValue
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.GlobalValues
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.addIngredient
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.dropItem
import xyz.xenondevs.nova.util.particle.color
import xyz.xenondevs.nova.util.particle.particle
import xyz.xenondevs.nova.util.sendTo
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.fakeentity.impl.FakeItemDisplay
import xyz.xenondevs.nova.world.item.NovaItem
import java.awt.Color

private class PlantConfiguration(val miniature: NovaItem, val loot: ItemStack, val color: Color)

private val PLANTS = mapOf(
    Material.OAK_SAPLING to PlantConfiguration(Models.OAK_TREE_MINIATURE, ItemStack(Material.OAK_LOG), Color(43, 82, 39)),
    Material.SPRUCE_SAPLING to PlantConfiguration(Models.SPRUCE_TREE_MINIATURE, ItemStack(Material.SPRUCE_LOG), Color(43, 87, 60)),
    Material.BIRCH_SAPLING to PlantConfiguration(Models.BIRCH_TREE_MINIATURE, ItemStack(Material.BIRCH_LOG), Color(49, 63, 35)),
    Material.JUNGLE_SAPLING to PlantConfiguration(Models.JUNGLE_TREE_MINIATURE, ItemStack(Material.JUNGLE_LOG), Color(51, 127, 43)),
    Material.ACACIA_SAPLING to PlantConfiguration(Models.ACACIA_TREE_MINIATURE, ItemStack(Material.ACACIA_LOG), Color(113, 125, 75)),
    Material.DARK_OAK_SAPLING to PlantConfiguration(Models.DARK_OAK_TREE_MINIATURE, ItemStack(Material.DARK_OAK_LOG), Color(26, 65, 17)),
    Material.MANGROVE_PROPAGULE to PlantConfiguration(Models.MANGROVE_TREE_MINIATURE, ItemStack(Material.MANGROVE_LOG), Color(32, 47, 14)),
    Material.CRIMSON_FUNGUS to PlantConfiguration(Models.CRIMSON_TREE_MINIATURE, ItemStack(Material.CRIMSON_STEM), Color(121, 0, 0)),
    Material.WARPED_FUNGUS to PlantConfiguration(Models.WARPED_TREE_MINIATURE, ItemStack(Material.WARPED_STEM), Color(22, 124, 132)),
    Material.RED_MUSHROOM to PlantConfiguration(Models.GIANT_RED_MUSHROOM_MINIATURE, ItemStack(Material.RED_MUSHROOM, 3), Color(192, 39, 37)),
    Material.BROWN_MUSHROOM to PlantConfiguration(Models.GIANT_BROWN_MUSHROOM_MINIATURE, ItemStack(Material.BROWN_MUSHROOM, 3), Color(149, 112, 80))
)

private val BLOCKED_SIDES = enumSetOf(BlockSide.FRONT, BlockSide.LEFT, BlockSide.RIGHT, BlockSide.TOP)

private val MAX_ENERGY = TREE_FACTORY.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = TREE_FACTORY.config.entry<Long>("energy_per_tick")
private val PROGRESS_PER_TICK = TREE_FACTORY.config.entry<Double>("progress_per_tick")
private val IDLE_TIME = TREE_FACTORY.config.entry<Int>("idle_time")

class TreeFactory(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val inputInventory = storedInventory("input", 1, false, intArrayOf(1), ::handleInputInventoryUpdate)
    private val outputInventory = storedInventory("output", 9, ::handleOutputInventoryUpdate)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(outputInventory to EXTRACT, inputInventory to INSERT, blockedSides = BLOCKED_SIDES)
    
    private val energyPerTick by energyConsumption(ENERGY_PER_TICK, upgradeHolder)
    private val progressPerTick by speedMultipliedValue(PROGRESS_PER_TICK, upgradeHolder)
    private val maxIdleTime by maxIdleTime(IDLE_TIME, upgradeHolder) // TODO: idle time works backwards here
    
    private var plantType = inputInventory.getItem(0)?.type
    private val plant = FakeItemDisplay(pos.location.add(0.5, 1.0 / 16.0, 0.5), false) { _, meta ->
        meta.transformationInterpolationDuration = 1
    }
    
    private var growthProgress = 0.0
    private var idleTimeLeft = 0
    
    override fun handleEnable() {
        super.handleEnable()
        plant.register()
    }
    
    override fun handleDisable() {
        super.handleDisable()
        plant.remove()
    }
    
    override fun handleTick() {
        if (energyHolder.energy >= energyPerTick && plantType != null) {
            val plantLoot = PLANTS[plantType]!!.loot
            if (!GlobalValues.DROP_EXCESS_ON_GROUND && !outputInventory.canHold(plantLoot)) return
            
            energyHolder.energy -= energyPerTick
            
            if (idleTimeLeft == 0) {
                if (plantType != null) {
                    growthProgress += progressPerTick
                    if (growthProgress >= 1.0)
                        idleTimeLeft = maxIdleTime
                    
                    updatePlantEntity()
                }
            } else {
                idleTimeLeft--
                
                particle(ParticleTypes.DUST) {
                    color(PLANTS[plantType]!!.color)
                    location(pos.location.add(0.5, 0.5, 0.5))
                    offset(0.15, 0.15, 0.15)
                    speed(0.1f)
                    amount(5)
                }.sendTo(getViewers())
                
                if (idleTimeLeft == 0) {
                    growthProgress = 0.0
                    
                    val leftover = outputInventory.addItem(SELF_UPDATE_REASON, plantLoot)
                    if (GlobalValues.DROP_EXCESS_ON_GROUND && leftover > 0) {
                        val remains = plantLoot.clone().apply { amount = leftover }
                        val dropLoc = pos.location.add(0.5, 0.5, 0.5)
                        dropLoc.dropItem(remains)
                    }
                }
            }
        }
    }
    
    private fun updatePlantEntity() {
        val size = growthProgress.coerceIn(0.0..1.0).toFloat()
        plant.updateEntityData(true) {
            transformationInterpolationDelay = 0
            itemStack = plantType?.let { PLANTS[it]!!.miniature.clientsideProvider.get() }
            scale = Vector3f(size, size, size)
            translation = Vector3f(0.0f, 0.5f * size, 0.0f)
        }
    }
    
    private fun handleInputInventoryUpdate(event: ItemPreUpdateEvent) {
        if (event.newItem != null && event.newItem!!.type !in PLANTS.keys) {
            event.isCancelled = true
        } else {
            plantType = event.newItem?.type
            growthProgress = 0.0
            updatePlantEntity()
        }
    }
    
    
    private fun handleOutputInventoryUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = event.updateReason != SELF_UPDATE_REASON && !event.isRemove
    }
    
    @TileEntityMenuClass
    private inner class TreeFactoryMenu : GlobalTileEntityMenu() {
        
        private val sideConfigGui = SideConfigMenu(
            this@TreeFactory,
            mapOf(
                itemHolder.getNetworkedInventory(inputInventory) to "inventory.nova.input",
                itemHolder.getNetworkedInventory(outputInventory) to "inventory.nova.output"
            ),
            ::openWindow
        )
        
        override val gui = Gui.builder()
            .setStructure(
                "1 - - - - - - - 2",
                "| s u # # # # e |",
                "| # # # o o o e |",
                "| # i # o o o e |",
                "| # # # o o o e |",
                "3 - - - - - - - 4")
            .addIngredient('i', inputInventory, GuiItems.SAPLING_PLACEHOLDER)
            .addIngredient('o', outputInventory)
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('e', EnergyBar(4, energyHolder))
            .build()
        
    }
    
}