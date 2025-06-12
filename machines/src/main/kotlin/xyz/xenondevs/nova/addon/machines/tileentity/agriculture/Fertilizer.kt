package xyz.xenondevs.nova.addon.machines.tileentity.agriculture

import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.BoneMealItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import org.bukkit.Material
import org.bukkit.entity.Player
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.enumSetOf
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.registry.Blocks.FERTILIZER
import xyz.xenondevs.nova.addon.machines.util.energyConsumption
import xyz.xenondevs.nova.addon.machines.util.iterator
import xyz.xenondevs.nova.addon.machines.util.maxIdleTime
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedRegion
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.EntityUtils
import xyz.xenondevs.nova.util.serverLevel
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.pos
import xyz.xenondevs.nova.world.region.Region
import xyz.xenondevs.nova.world.region.VisualRegion

private val BLOCKED_SIDES = enumSetOf(BlockSide.FRONT)

private val MAX_ENERGY = FERTILIZER.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = FERTILIZER.config.entry<Long>("energy_per_tick")
private val ENERGY_PER_FERTILIZE = FERTILIZER.config.entry<Long>("energy_per_fertilize")
private val IDLE_TIME = FERTILIZER.config.entry<Int>("idle_time")
private val MIN_RANGE = FERTILIZER.config.entry<Int>("range", "min")
private val MAX_RANGE = FERTILIZER.config.entry<Int>("range", "max")
private val DEFAULT_RANGE by FERTILIZER.config.entry<Int>("range", "default")

class Fertilizer(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val fertilizerInventory = storedInventory("fertilizer", 12, this::handleInventoryUpdate)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.RANGE)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(fertilizerInventory to INSERT, blockedSides = BLOCKED_SIDES)
    
    private val fakePlayer = EntityUtils.createFakePlayer(pos.location)
    private val region = storedRegion("region.default", MIN_RANGE, MAX_RANGE, DEFAULT_RANGE, upgradeHolder) {
        val size = 1 + it * 2
        Region.inFrontOf(this, size, size, 1, 0)
    }
    
    private val energyPerTick by energyConsumption(ENERGY_PER_TICK, upgradeHolder)
    private val energyPerFertilize by energyConsumption(ENERGY_PER_FERTILIZE, upgradeHolder)
    private val maxIdleTime by maxIdleTime(IDLE_TIME, upgradeHolder)
    private var timePassed = 0
    
    override fun handleTick() {
        if (energyHolder.energy >= energyPerTick) {
            energyHolder.energy -= energyPerTick
            if (energyHolder.energy >= energyPerFertilize) {
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
            if (item == null)
                continue
            
            for (block in region) {
                val consumed = BoneMealItem.applyBonemeal(
                    UseOnContext(
                        pos.world.serverLevel,
                        fakePlayer,
                        InteractionHand.MAIN_HAND,
                        ItemStack(Items.BONE_MEAL),
                        BlockHitResult(Vec3.ZERO, Direction.DOWN, block.pos.nmsPos, false)
                    )
                ).consumesAction()
                if (consumed) {
                    energyHolder.energy -= energyPerFertilize
                    fertilizerInventory.addItemAmount(SELF_UPDATE_REASON, index, -1)
                    return
                }
            }
        }
    }
    
    private fun handleInventoryUpdate(event: ItemPreUpdateEvent) {
        if ((event.isAdd || event.isSwap) && event.newItem?.type != Material.BONE_MEAL)
            event.isCancelled = true
    }
    
    override fun handleDisable() {
        super.handleDisable()
        VisualRegion.removeRegion(uuid)
    }
    
    @TileEntityMenuClass
    inner class FertilizerMenu(player: Player) : IndividualTileEntityMenu(player) {
        
        private val sideConfigGui = SideConfigMenu(
            this@Fertilizer,
            mapOf(itemHolder.getNetworkedInventory(fertilizerInventory) to "inventory.machines.fertilizer"),
            ::openWindow
        )
        
        override val gui = Gui.builder()
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
            .addIngredient('v', region.visualizeRegionItem)
            .addIngredient('p', region.increaseSizeItem)
            .addIngredient('m', region.decreaseSizeItem)
            .addIngredient('n', region.displaySizeItem)
            .build()
        
    }
    
}
