package xyz.xenondevs.nova.addon.machines.tileentity.processing

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.util.Brightness
import net.minecraft.world.item.ItemDisplayContext
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
import xyz.xenondevs.nmsutils.particle.particle
import xyz.xenondevs.nova.addon.machines.gui.LeftRightFluidProgressItem
import xyz.xenondevs.nova.addon.machines.registry.Blocks.COBBLESTONE_GENERATOR
import xyz.xenondevs.nova.addon.machines.registry.GuiMaterials
import xyz.xenondevs.nova.addon.machines.registry.Models
import xyz.xenondevs.nova.addon.simpleupgrades.ConsumerEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.getFluidContainer
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.data.config.entry
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.item.NovaItem
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.fluid.FluidType
import xyz.xenondevs.nova.tileentity.network.fluid.container.FluidContainer
import xyz.xenondevs.nova.tileentity.network.fluid.holder.NovaFluidHolder
import xyz.xenondevs.nova.tileentity.network.item.holder.NovaItemHolder
import xyz.xenondevs.nova.tileentity.upgrade.Upgradable
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.FluidBar
import xyz.xenondevs.nova.ui.OpenUpgradesItem
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.advance
import xyz.xenondevs.nova.util.axis
import xyz.xenondevs.nova.util.nmsCopy
import xyz.xenondevs.nova.util.sendTo
import xyz.xenondevs.nova.world.fakeentity.impl.FakeItemDisplay
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.random.Random

private const val MAX_STATE = 99

private val ENERGY_CAPACITY = COBBLESTONE_GENERATOR.config.entry<Long>("energy_capacity")
private val ENERGY_PER_TICK = COBBLESTONE_GENERATOR.config.entry<Long>("energy_per_tick")
private val WATER_CAPACITY = COBBLESTONE_GENERATOR.config.entry<Long>("water_capacity")
private val LAVA_CAPACITY = COBBLESTONE_GENERATOR.config.entry<Long>("lava_capacity")
private val MB_PER_TICK by COBBLESTONE_GENERATOR.config.entry<Long>("mb_per_tick")

class CobblestoneGenerator(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState), Upgradable {
    
    override val upgradeHolder = getUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.FLUID)
    
    private val inventory = getInventory("inventory", 3, ::handleInventoryUpdate)
    private val waterTank = getFluidContainer("water", setOf(FluidType.WATER), WATER_CAPACITY, 0, ::updateWaterLevel, upgradeHolder)
    private val lavaTank = getFluidContainer("lava", setOf(FluidType.LAVA), LAVA_CAPACITY, 0, ::updateLavaLevel, upgradeHolder)
    
    override val energyHolder = ConsumerEnergyHolder(this, ENERGY_CAPACITY, ENERGY_PER_TICK, null, upgradeHolder) { createSideConfig(NetworkConnectionType.INSERT, BlockSide.FRONT) }
    override val itemHolder = NovaItemHolder(this, inventory to NetworkConnectionType.EXTRACT) { createSideConfig(NetworkConnectionType.EXTRACT, BlockSide.FRONT) }
    override val fluidHolder = NovaFluidHolder(this, waterTank to NetworkConnectionType.BUFFER, lavaTank to NetworkConnectionType.BUFFER) { createSideConfig(NetworkConnectionType.INSERT, BlockSide.FRONT) }
    
    private var mode = retrieveData("mode") { Mode.COBBLESTONE }
    private var mbPerTick = 0L
    
    private var currentMode = mode
    private var mbUsed = 0L
    
    private val waterLevel = FakeItemDisplay(location.add(.5, .5, .5)) { _, data -> data.itemDisplay = ItemDisplayContext.HEAD }
    private val lavaLevel = FakeItemDisplay(location.add(.5, .5, .5)) { _, data -> data.itemDisplay = ItemDisplayContext.HEAD; data.brightness = Brightness.FULL_BRIGHT }
    
    private val particleEffect = particle(ParticleTypes.LARGE_SMOKE) {
        location(centerLocation.advance(getFace(BlockSide.FRONT), 0.6).apply { y += 0.6 })
        offset(getFace(BlockSide.RIGHT).axis, 0.15f)
        amount(5)
        speed(0.03f)
    }
    
    init {
        reload()
        updateWaterLevel()
        updateLavaLevel()
    }
    
    override fun reload() {
        super.reload()
        mbPerTick = (MB_PER_TICK * upgradeHolder.getValue(UpgradeTypes.SPEED)).roundToLong()
    }
    
    private fun updateWaterLevel() {
        val item = if (!waterTank.isEmpty()) {
            val state = getFluidState(waterTank)
            Models.COBBLESTONE_GENERATOR_WATER_LEVELS.clientsideProviders[state].get()
        } else null
        waterLevel.updateEntityData(true) { itemStack = item.nmsCopy }
    }
    
    private fun updateLavaLevel() {
        val item = if (!lavaTank.isEmpty()) {
            val state = getFluidState(lavaTank)
            Models.COBBLESTONE_GENERATOR_LAVA_LEVELS.clientsideProviders[state].get()
        } else null
        lavaLevel.updateEntityData(true) { itemStack = item.nmsCopy }
    }
    
    private fun getFluidState(container: FluidContainer) =
        (container.amount.toDouble() / container.capacity.toDouble() * MAX_STATE.toDouble()).roundToInt().coerceIn(0..MAX_STATE)
    
    override fun handleTick() {
        val mbToTake = min(mbPerTick, 1000 - mbUsed)
        
        if (waterTank.amount >= mbToTake
            && lavaTank.amount >= mbToTake
            && energyHolder.energy >= energyHolder.energyConsumption
            && inventory.canHold(currentMode.product)
        ) {
            energyHolder.energy -= energyHolder.energyConsumption
            mbUsed += mbToTake
            
            when {
                currentMode.takeLava -> lavaTank
                currentMode.takeWater -> waterTank
                else -> null
            }?.takeFluid(mbToTake)
            
            if (mbUsed >= 1000) {
                mbUsed = 0
                inventory.addItem(SELF_UPDATE_REASON, currentMode.product)
                currentMode = mode
                
                playSoundEffect(Sound.BLOCK_LAVA_EXTINGUISH, 0.1f, Random.nextDouble(0.5, 1.95).toFloat())
                particleEffect.sendTo(getViewers())
            }
            
            menuContainer.forEachMenu<CobblestoneGeneratorMenu> { it.progressItem.percentage = mbUsed / 1000.0 }
        }
    }
    
    private fun handleInventoryUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = !event.isRemove && event.updateReason != SELF_UPDATE_REASON
    }
    
    override fun handleRemoved(unload: Boolean) {
        super.handleRemoved(unload)
        waterLevel.remove()
        lavaLevel.remove()
    }
    
    override fun saveData() {
        super.saveData()
        storeData("mode", mode)
    }
    
    @TileEntityMenuClass
    inner class CobblestoneGeneratorMenu : GlobalTileEntityMenu() {
        
        private val sideConfigGui = SideConfigMenu(
            this@CobblestoneGenerator,
            listOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.output"),
            listOf(waterTank to "container.nova.water_tank", lavaTank to "container.nova.lava_tank"),
            ::openWindow
        )
        
        val progressItem = LeftRightFluidProgressItem()
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| w l # i # s e |",
                "| w l > i # u e |",
                "| w l # i # m e |",
                "3 - - - - - - - 4")
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('m', ChangeModeItem())
            .addIngredient('i', inventory)
            .addIngredient('>', progressItem)
            .addIngredient('w', FluidBar(3, fluidHolder, waterTank))
            .addIngredient('l', FluidBar(3, fluidHolder, lavaTank))
            .addIngredient('e', EnergyBar(3, energyHolder))
            .build()
        
        private inner class ChangeModeItem : AbstractItem() {
            
            override fun getItemProvider(): ItemProvider =
                mode.uiItem.clientsideProvider
            
            override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) {
                if (clickType == ClickType.LEFT || clickType == ClickType.RIGHT) {
                    val direction = if (clickType == ClickType.LEFT) 1 else -1
                    mode = Mode.entries[(mode.ordinal + direction).mod(Mode.entries.size)]
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                    notifyWindows()
                }
            }
            
        }
        
    }
    
    enum class Mode(val takeWater: Boolean, val takeLava: Boolean, val product: ItemStack, val uiItem: NovaItem) {
        COBBLESTONE(false, false, ItemStack(Material.COBBLESTONE), GuiMaterials.COBBLESTONE_MODE_BTN),
        STONE(true, false, ItemStack(Material.STONE), GuiMaterials.STONE_MODE_BTN),
        OBSIDIAN(false, true, ItemStack(Material.OBSIDIAN), GuiMaterials.OBSIDIAN_MODE_BTN)
    }
    
}
