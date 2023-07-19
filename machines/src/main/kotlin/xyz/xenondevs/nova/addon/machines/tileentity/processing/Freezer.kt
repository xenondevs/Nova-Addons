package xyz.xenondevs.nova.addon.machines.tileentity.processing

import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.invui.item.impl.AbstractItem
import xyz.xenondevs.nova.data.config.NovaConfig
import xyz.xenondevs.nova.data.config.configReloadable
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.item.NovaItem
import xyz.xenondevs.nova.addon.machines.gui.LeftRightFluidProgressItem
import xyz.xenondevs.nova.addon.machines.registry.Blocks.FREEZER
import xyz.xenondevs.nova.addon.machines.registry.GuiMaterials
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.fluid.FluidType
import xyz.xenondevs.nova.tileentity.network.fluid.holder.NovaFluidHolder
import xyz.xenondevs.nova.tileentity.network.item.holder.NovaItemHolder
import xyz.xenondevs.nova.tileentity.upgrade.Upgradable
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.FluidBar
import xyz.xenondevs.nova.ui.OpenUpgradesItem
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.addon.simpleupgrades.ConsumerEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.getFluidContainer
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import java.lang.Long.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val WATER_CAPACITY = configReloadable { NovaConfig[FREEZER].getLong("water_capacity") }
private val ENERGY_CAPACITY = configReloadable { NovaConfig[FREEZER].getLong("energy_capacity") }
private val ENERGY_PER_TICK = configReloadable { NovaConfig[FREEZER].getLong("energy_per_tick") }
private val MB_PER_TICK by configReloadable { NovaConfig[FREEZER].getLong("mb_per_tick") }

class Freezer(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState), Upgradable {
    
    override val upgradeHolder = getUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.FLUID)
    private val inventory = getInventory("inventory", 6, ::handleInventoryUpdate)
    private val waterTank = getFluidContainer("water", setOf(FluidType.WATER), WATER_CAPACITY, 0, upgradeHolder = upgradeHolder)
    
    override val fluidHolder = NovaFluidHolder(this, waterTank to NetworkConnectionType.BUFFER) { createSideConfig(NetworkConnectionType.INSERT, BlockSide.FRONT) }
    override val itemHolder = NovaItemHolder(this, inventory to NetworkConnectionType.EXTRACT) { createSideConfig(NetworkConnectionType.EXTRACT, BlockSide.FRONT) }
    override val energyHolder = ConsumerEnergyHolder(this, ENERGY_CAPACITY, ENERGY_PER_TICK, null, upgradeHolder) { createSideConfig(NetworkConnectionType.INSERT, BlockSide.FRONT) }
    
    private val snowSpawnBlock = location.clone().apply { y += 1 }.block
    
    private var mbPerTick = 0L
    private var mbUsed = 0L
    
    private var mode = retrieveData("mode") { Mode.ICE }
    
    init {
        reload()
    }
    
    override fun reload() {
        super.reload()
        mbPerTick = (MB_PER_TICK * upgradeHolder.getValue(UpgradeTypes.SPEED)).roundToLong()
    }
    
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
        if (waterTank.amount >= mbToTake && energyHolder.energy >= energyHolder.energyConsumption && inventory.canHold(mode.product)) {
            if (snowSpawnBlock.type.isAir) snowSpawnBlock.type = Material.SNOW
            
            energyHolder.energy -= energyHolder.energyConsumption
            mbUsed += mbToTake
            waterTank.takeFluid(mbToTake)
            if (mbUsed >= mbMaxPerOperation) {
                mbUsed = 0
                inventory.addItem(SELF_UPDATE_REASON, mode.product)
            }
            
            menuContainer.forEachMenu(FreezerMenu::updateProgress)
        }
    }
    
    override fun saveData() {
        super.saveData()
        storeData("mode", mode)
    }
    
    @TileEntityMenuClass
    inner class FreezerMenu : GlobalTileEntityMenu() {
        
        private val progressItem = LeftRightFluidProgressItem()
        private val sideConfigGui = SideConfigMenu(this@Freezer,
            listOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.output"),
            listOf(waterTank to "container.nova.water_tank"),
            ::openWindow
        )
        
        override val gui = Gui.normal()
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
            
            override fun getItemProvider(): ItemProvider =
                mode.uiItem.clientsideProvider
            
            override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) {
                if (clickType == ClickType.LEFT || clickType == ClickType.RIGHT) {
                    val direction = if (clickType == ClickType.LEFT) 1 else -1
                    mode = Mode.values()[(mode.ordinal + direction).mod(Mode.values().size)]
                    
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                    notifyWindows()
                }
            }
        }
    }
    
    enum class Mode(val product: ItemStack, val uiItem: NovaItem, val maxCostMultiplier: Int) {
        ICE(ItemStack(Material.ICE), GuiMaterials.ICE_MODE_BTN, 1),
        PACKED_ICE(ItemStack(Material.PACKED_ICE), GuiMaterials.PACKED_ICE_MODE_BTN, 9),
        BLUE_ICE(ItemStack(Material.BLUE_ICE), GuiMaterials.BLUE_ICE_MODE_BTN, 81)
    }
    
}
