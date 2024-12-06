package xyz.xenondevs.nova.addon.machines.tileentity.processing

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Display.Brightness
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.enumSetOf
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.item.AbstractItem
import xyz.xenondevs.invui.item.Click
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.nova.addon.machines.gui.LeftRightFluidProgressItem
import xyz.xenondevs.nova.addon.machines.registry.Blocks.COBBLESTONE_GENERATOR
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.Models
import xyz.xenondevs.nova.addon.machines.util.efficiencyDividedValue
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
import xyz.xenondevs.nova.util.advance
import xyz.xenondevs.nova.util.axis
import xyz.xenondevs.nova.util.particle.particle
import xyz.xenondevs.nova.util.playClickSound
import xyz.xenondevs.nova.util.sendTo
import xyz.xenondevs.nova.util.yaw
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.state.property.DefaultBlockStateProperties
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.*
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.container.FluidContainer
import xyz.xenondevs.nova.world.fakeentity.impl.FakeItemDisplay
import xyz.xenondevs.nova.world.item.NovaItem
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

private const val MAX_STATE = 99
private val BLOCKED_SIDES = enumSetOf(BlockSide.FRONT, BlockSide.LEFT, BlockSide.RIGHT)

private val ENERGY_CAPACITY = COBBLESTONE_GENERATOR.config.entry<Long>("energy_capacity")
private val ENERGY_PER_TICK = COBBLESTONE_GENERATOR.config.entry<Long>("energy_per_tick")
private val WATER_CAPACITY = COBBLESTONE_GENERATOR.config.entry<Long>("water_capacity")
private val LAVA_CAPACITY = COBBLESTONE_GENERATOR.config.entry<Long>("lava_capacity")
private val MB_PER_TICK = COBBLESTONE_GENERATOR.config.entry<Long>("mb_per_tick")

class CobblestoneGenerator(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.FLUID)
    private val inventory = storedInventory("inventory", 3, ::handleInventoryUpdate)
    private val waterTank = storedFluidContainer("water", setOf(FluidType.WATER), WATER_CAPACITY, upgradeHolder, false, ::updateWaterLevel)
    private val lavaTank = storedFluidContainer("lava", setOf(FluidType.LAVA), LAVA_CAPACITY, upgradeHolder, false, ::updateLavaLevel)
    
    private val energyHolder = storedEnergyHolder(ENERGY_CAPACITY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(inventory to EXTRACT, blockedSides = BLOCKED_SIDES)
    private val fluidHolder = storedFluidHolder(waterTank to BUFFER, lavaTank to BUFFER, blockedSides = BLOCKED_SIDES)
    
    private val energyPerTick by efficiencyDividedValue(ENERGY_PER_TICK, upgradeHolder)
    private val mbPerTick by speedMultipliedValue(MB_PER_TICK, upgradeHolder)
    
    private var mode by storedValue("mode") { Mode.COBBLESTONE }
    private var currentMode = mode
    private var mbUsed = 0L
    
    private val waterLevel: FakeItemDisplay
    private val lavaLevel: FakeItemDisplay
    private val particleEffect: ClientboundLevelParticlesPacket
    
    init {
        val facing = blockState.getOrThrow(DefaultBlockStateProperties.FACING)
        
        val displayEntityLocation = pos.location.add(0.5, 0.5, 0.5)
        displayEntityLocation.yaw = facing.yaw
        waterLevel = FakeItemDisplay(displayEntityLocation, false)
        lavaLevel = FakeItemDisplay(displayEntityLocation, false) { _, meta ->
            meta.brightness = Brightness(15, 15)
        }
        
        particleEffect = particle(ParticleTypes.LARGE_SMOKE) {
            location(pos.location.add(0.5, 0.0, 0.5).advance(facing, 0.6).apply { y += 0.6 })
            offset(BlockSide.RIGHT.getBlockFace(facing).axis, 0.15f)
            amount(5)
            speed(0.03f)
        }
    }
    
    override fun handleEnable() {
        super.handleEnable()
        updateWaterLevel()
        updateLavaLevel()
        waterLevel.register()
        lavaLevel.register()
    }
    
    override fun handleDisable() {
        super.handleDisable()
        waterLevel.remove()
        lavaLevel.remove()
    }
    
    private fun updateWaterLevel() {
        val item = if (!waterTank.isEmpty()) {
            val state = getFluidState(waterTank)
            Models.COBBLESTONE_GENERATOR_WATER_LEVELS.createClientsideItemBuilder().addCustomModelData(state).get()
        } else null
        waterLevel.updateEntityData(true) { itemStack = item }
    }
    
    private fun updateLavaLevel() {
        val item = if (!lavaTank.isEmpty()) {
            val state = getFluidState(lavaTank)
            Models.COBBLESTONE_GENERATOR_LAVA_LEVELS.createClientsideItemBuilder().addCustomModelData(state).get()
        } else null
        lavaLevel.updateEntityData(true) { itemStack = item }
    }
    
    private fun getFluidState(container: FluidContainer) =
        (container.amount.toDouble() / container.capacity.toDouble() * MAX_STATE.toDouble()).roundToInt().coerceIn(0..MAX_STATE)
    
    override fun handleTick() {
        val mbToTake = min(mbPerTick, 1000 - mbUsed)
        
        if (waterTank.amount >= mbToTake
            && lavaTank.amount >= mbToTake
            && energyHolder.energy >= energyPerTick
            && inventory.canHold(currentMode.product)
        ) {
            energyHolder.energy -= energyPerTick
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
                
                pos.playSound(Sound.BLOCK_LAVA_EXTINGUISH, 0.1f, Random.nextDouble(0.5, 1.95).toFloat())
                particleEffect.sendTo(getViewers())
            }
            
            menuContainer.forEachMenu<CobblestoneGeneratorMenu> { it.progressItem.percentage = mbUsed / 1000.0 }
        }
    }
    
    private fun handleInventoryUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = !event.isRemove && event.updateReason != SELF_UPDATE_REASON
    }
    
    @TileEntityMenuClass
    inner class CobblestoneGeneratorMenu : GlobalTileEntityMenu() {
        
        private val sideConfigGui = SideConfigMenu(
            this@CobblestoneGenerator,
            mapOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.output"),
            mapOf(waterTank to "container.nova.water_tank", lavaTank to "container.nova.lava_tank"),
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
    
    enum class Mode(val takeWater: Boolean, val takeLava: Boolean, val product: ItemStack, val uiItem: NovaItem) {
        COBBLESTONE(false, false, ItemStack(Material.COBBLESTONE), GuiItems.COBBLESTONE_MODE_BTN),
        STONE(true, false, ItemStack(Material.STONE), GuiItems.STONE_MODE_BTN),
        OBSIDIAN(false, true, ItemStack(Material.OBSIDIAN), GuiItems.OBSIDIAN_MODE_BTN)
    }
    
}
