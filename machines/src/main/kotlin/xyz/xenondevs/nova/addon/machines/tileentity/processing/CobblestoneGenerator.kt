package xyz.xenondevs.nova.addon.machines.tileentity.processing

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.entity.Display
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.combinedProvider
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.dsl.item
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.item.ItemBuilder
import xyz.xenondevs.nova.addon.machines.gui.leftRightFluidProgressItem
import xyz.xenondevs.nova.addon.machines.registry.BlockStateProperties
import xyz.xenondevs.nova.addon.machines.registry.Blocks.COBBLESTONE_GENERATOR
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.Models
import xyz.xenondevs.nova.addon.machines.util.energyConsumption
import xyz.xenondevs.nova.addon.machines.util.speedMultipliedValue
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedFluidContainer
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.network.sendTo
import xyz.xenondevs.nova.packetentity.PacketItemDisplay
import xyz.xenondevs.nova.packetentity.packetItemDisplay
import xyz.xenondevs.nova.registry.RegistryEntry
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.ui.menu.fluidBar
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.BlockSideSet
import xyz.xenondevs.nova.util.advance
import xyz.xenondevs.nova.util.axis
import xyz.xenondevs.nova.util.item.setCustomModelDataFloat
import xyz.xenondevs.nova.util.particle.particle
import xyz.xenondevs.nova.util.playClickSound
import xyz.xenondevs.nova.util.playSound
import xyz.xenondevs.nova.util.waterColor
import xyz.xenondevs.nova.util.yaw
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.world.block.state.property.DefaultBlockStateProperties
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.*
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.world.item.guiItemProvider
import kotlin.math.min
import kotlin.math.round
import kotlin.random.Random

private const val COBBLESTONE_MAX_STATE = 99
private val BLOCKED_SIDES = BlockSideSet(front = true, left = true, right = true)

private val ENERGY_CAPACITY = COBBLESTONE_GENERATOR.config.entry<Long>("energy_capacity")
private val ENERGY_PER_TICK = COBBLESTONE_GENERATOR.config.entry<Long>("energy_per_tick")
private val COBBLESTONE_WATER_CAPACITY = COBBLESTONE_GENERATOR.config.entry<Long>("water_capacity")
private val COBBLESTONE_LAVA_CAPACITY = COBBLESTONE_GENERATOR.config.entry<Long>("lava_capacity")
private val MB_PER_TICK = COBBLESTONE_GENERATOR.config.entry<Long>("mb_per_tick")

class CobblestoneGenerator(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.FLUID)
    private val inventory = storedInventory("inventory", 3, ::handleInventoryUpdate)
    private val waterTank = storedFluidContainer("water", setOf(FluidType.WATER), COBBLESTONE_WATER_CAPACITY, upgradeHolder, false)
    private val lavaTank = storedFluidContainer("lava", setOf(FluidType.LAVA), COBBLESTONE_LAVA_CAPACITY, upgradeHolder, false, ::updateLavaState)
    
    private val energyHolder = storedEnergyHolder(ENERGY_CAPACITY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(inventory to EXTRACT, blockedSides = BLOCKED_SIDES)
    private val fluidHolder = storedFluidHolder(waterTank to BUFFER, lavaTank to BUFFER, blockedSides = BLOCKED_SIDES)
    
    private val energyPerTick by energyConsumption(ENERGY_PER_TICK, upgradeHolder)
    private val mbPerTick by speedMultipliedValue(MB_PER_TICK, upgradeHolder)
    
    private val modeProvider = storedValue("mode") { Mode.COBBLESTONE }
    private var mode by modeProvider
    private var currentMode = mode
    private var mbUsed = 0L
    private val progress = mutableProvider(0.0)
    
    private val waterColor = block.waterColor
    
    private val waterLevel: PacketItemDisplay
    private val lavaLevel: PacketItemDisplay
    private val particleEffect: ClientboundLevelParticlesPacket
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.COBBLESTONE_GENERATOR) {
        upperGui by gui(
            "w l . i . . . s e",
            "w l > i . . . u e",
            "w l . i . . . m e",
        ) {
            's' by openSideConfigItem(
                mapOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.output"),
                mapOf(waterTank to "container.nova.water_tank", lavaTank to "container.nova.lava_tank")
            )
            'u' by openUpgradesItem(upgradeHolder)
            'm' by item {
                itemProvider by modeProvider.flatMap { it.uiItem.guiItemProvider }
                onClick {
                    if (clickType == ClickType.LEFT || clickType == ClickType.RIGHT) {
                        val direction = if (clickType == ClickType.LEFT) 1 else -1
                        mode = Mode.entries[(mode.ordinal + direction).mod(Mode.entries.size)]
                        player.playClickSound()
                    }
                }
            }
            'i' by inventory
            '>' by leftRightFluidProgressItem(progress)
            'w' by fluidBar(fluidHolder, waterTank)
            'l' by fluidBar(fluidHolder, lavaTank)
            'e' by energyBar(energyHolder)
        }
    }
    
    init {
        val facing = blockState.getOrThrow(DefaultBlockStateProperties.FACING_HORIZONTAL)
        val displayLocation = block.location.toCenterLocation()
            .apply { yaw = blockState.getOrThrow(DefaultBlockStateProperties.FACING_HORIZONTAL).yaw }
        
        waterLevel = packetItemDisplay {
            location by displayLocation
            metadata {
                itemStack by combinedProvider(
                    Models.COBBLESTONE_GENERATOR_WATER_LEVELS.guiItemProvider,
                    waterTank.amountProvider,
                    waterTank.capacityProvider
                ) { model, amount, capacity ->
                    if (amount == 0L) ItemStack.empty()
                    else {
                        val state = round(amount.toFloat() / capacity.toFloat() * COBBLESTONE_MAX_STATE)
                            .coerceIn(0f..COBBLESTONE_MAX_STATE.toFloat())
                        ItemBuilder(model.get())
                            .addCustomModelData(state)
                            .addCustomModelData(waterColor)
                            .build()
                    }
                }
            }
        }
        
        lavaLevel = packetItemDisplay {
            location by displayLocation
            metadata {
                brightnessOverride by Display.Brightness(15, 15)
                itemStack by combinedProvider(
                    Models.COBBLESTONE_GENERATOR_LAVA_LEVELS.guiItemProvider,
                    lavaTank.amountProvider,
                    lavaTank.capacityProvider
                ) { model, amount, capacity ->
                    if (amount == 0L) ItemStack.empty()
                    else {
                        val state = round(amount.toFloat() / capacity.toFloat() * COBBLESTONE_MAX_STATE)
                            .coerceIn(0f..COBBLESTONE_MAX_STATE.toFloat())
                        model.get().apply { setCustomModelDataFloat(0, state) }
                    }
                }
            }
        }
        
        particleEffect = particle(ParticleTypes.LARGE_SMOKE) {
            location(block.location.add(0.5, 0.0, 0.5).advance(facing, 0.6).apply { y += 0.6 })
            offset(BlockSide.RIGHT.getBlockFace(facing).axis!!, 0.15f)
            amount(5)
            speed(0.03f)
        }
    }
    
    override fun handleEnable() {
        super.handleEnable()
        updateLavaState()
        waterLevel.spawn()
        lavaLevel.spawn()
    }
    
    override fun handleDisable() {
        super.handleDisable()
        waterLevel.despawn()
        lavaLevel.despawn()
    }
    
    private fun updateLavaState() {
        val hasLava = !lavaTank.isEmpty()
        if (blockState.getOrThrow(BlockStateProperties.LAVA) != hasLava)
            updateBlockState(blockState.apply { this[BlockStateProperties.LAVA] = hasLava })
    }
    
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
                
                block.playSound(Sound.BLOCK_LAVA_EXTINGUISH, 0.1f, Random.nextDouble(0.5, 1.95).toFloat())
                particleEffect.sendTo(getViewers())
            }
            
            progress.set(mbUsed / 1000.0)
        }
    }
    
    private fun handleInventoryUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = !event.isRemove && event.updateReason != SELF_UPDATE_REASON
    }
    
    enum class Mode(val takeWater: Boolean, val takeLava: Boolean, val product: ItemStack, val uiItem: RegistryEntry.Paper<ItemType>) {
        COBBLESTONE(false, false, ItemType.COBBLESTONE.createItemStack(), GuiItems.COBBLESTONE_MODE_BTN),
        STONE(true, false, ItemType.STONE.createItemStack(), GuiItems.STONE_MODE_BTN),
        OBSIDIAN(false, true, ItemType.OBSIDIAN.createItemStack(), GuiItems.OBSIDIAN_MODE_BTN)
    }
    
}
