package xyz.xenondevs.nova.addon.machines.tileentity.processing

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.enumSetOf
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.item.AbstractItem
import xyz.xenondevs.invui.Click
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.nova.addon.machines.gui.LeftRightFluidProgressItem
import xyz.xenondevs.nova.addon.machines.registry.Blocks.FREEZER
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.util.energyConsumption
import xyz.xenondevs.nova.addon.machines.util.speedMultipliedValue
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedFluidContainer
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.FluidBar
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.playClickSound
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.world.item.NovaItem
import java.lang.Long.min
import kotlin.math.roundToInt

private val BLOCKED_SIDES = enumSetOf(BlockSide.FRONT)

private val WATER_CAPACITY = FREEZER.config.entry<Long>("water_capacity")
private val ENERGY_CAPACITY = FREEZER.config.entry<Long>("energy_capacity")
private val ENERGY_PER_TICK = FREEZER.config.entry<Long>("energy_per_tick")
private val MB_PER_TICK = FREEZER.config.entry<Long>("mb_per_tick")

class Freezer(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.FLUID)
    private val inventory = storedInventory("inventory", 6, ::handleInventoryUpdate)
    private val waterTank = storedFluidContainer("water", setOf(FluidType.WATER), WATER_CAPACITY, upgradeHolder)
    private val energyHolder = storedEnergyHolder(ENERGY_CAPACITY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(inventory to EXTRACT, blockedSides = BLOCKED_SIDES)
    private val fluidHolder = storedFluidHolder(waterTank to INSERT, blockedSides = BLOCKED_SIDES)
    
    private val energyPerTick by energyConsumption(ENERGY_PER_TICK, upgradeHolder)
    private val mbPerTick by speedMultipliedValue(MB_PER_TICK, upgradeHolder)
    private var mbUsed = 0L
    private var mode by storedValue("mode") { Mode.ICE }
    
    private fun handleInventoryUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = !event.isRemove && event.updateReason != SELF_UPDATE_REASON
    }
    
    override fun handleTick() {
        val mbMaxPerOperation = 1000 * mode.maxCostMultiplier
        
        if (mbUsed > mbMaxPerOperation && inventory.canHold(mode.product)) {
            val compensationCount = (mbUsed / mbMaxPerOperation.toDouble()).roundToInt()
            val compensationItems = ItemStack(Material.ICE, compensationCount)
            if (inventory.canHold(compensationItems)) {
                inventory.addItem(SELF_UPDATE_REASON, compensationItems) // Add ice from overflowing water to the inventory
                mbUsed -= compensationCount * mbMaxPerOperation // Take used up mb for the compensatory product
            }
        }
        val mbToTake = min(mbPerTick, mbMaxPerOperation - mbUsed)
        if (waterTank.amount >= mbToTake && energyHolder.energy >= energyPerTick && inventory.canHold(mode.product)) {
            val snowSpawnBlock = pos.add(0, 1, 0).block
            if (snowSpawnBlock.type.isAir)
                snowSpawnBlock.type = Material.SNOW
            
            energyHolder.energy -= energyPerTick
            mbUsed += mbToTake
            waterTank.takeFluid(mbToTake)
            if (mbUsed >= mbMaxPerOperation) {
                mbUsed = 0
                inventory.addItem(SELF_UPDATE_REASON, mode.product)
            }
            
            menuContainer.forEachMenu(FreezerMenu::updateProgress)
        }
    }
    
    @TileEntityMenuClass
    inner class FreezerMenu : GlobalTileEntityMenu() {
        
        private val progressItem = LeftRightFluidProgressItem()
        private val sideConfigGui = SideConfigMenu(this@Freezer,
            mapOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.output"),
            mapOf(waterTank to "container.nova.water_tank"),
            ::openWindow
        )
        
        override val gui = Gui.builder()
            .setStructure(
                "1 - - - - - - - 2",
                "| w # i i # s e |",
                "| w > i i # u e |",
                "| w # i i # m e |",
                "3 - - - - - - - 4")
            .addIngredient('i', inventory)
            .addIngredient('>', progressItem)
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('m', ChangeModeItem())
            .addIngredient('w', FluidBar(3, fluidHolder, waterTank))
            .addIngredient('e', EnergyBar(3, energyHolder))
            .build()
        
        fun updateProgress() {
            progressItem.percentage = mbUsed / (1000 * mode.maxCostMultiplier).toDouble()
        }
        
        private inner class ChangeModeItem : AbstractItem() {
            
            override fun getItemProvider(player: Player): ItemProvider =
                mode.uiItem.clientsideProvider
            
            override fun handleClick(clickType: ClickType, player: Player, click: Click) {
                if (clickType == ClickType.LEFT || clickType == ClickType.RIGHT) {
                    val direction = if (clickType == ClickType.LEFT) 1 else -1
                    mode = Mode.entries[(mode.ordinal + direction).mod(Mode.entries.size)]
                    
                    player.playClickSound()
                    notifyWindows()
                }
            }
        }
    }
    
    enum class Mode(val product: ItemStack, val uiItem: NovaItem, val maxCostMultiplier: Int) {
        ICE(ItemStack(Material.ICE), GuiItems.ICE_MODE_BTN, 1),
        PACKED_ICE(ItemStack(Material.PACKED_ICE), GuiItems.PACKED_ICE_MODE_BTN, 9),
        BLUE_ICE(ItemStack(Material.BLUE_ICE), GuiItems.BLUE_ICE_MODE_BTN, 81)
    }
    
}
