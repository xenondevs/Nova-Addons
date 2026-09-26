package xyz.xenondevs.nova.addon.machines.tileentity.energy

import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.SlotProvider
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.Vec3
import org.bukkit.block.Block
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.MutableProvider
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.combinedProvider
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.gui.energyProgressItem
import xyz.xenondevs.nova.addon.machines.registry.BlockStateProperties
import xyz.xenondevs.nova.addon.machines.registry.Blocks.FURNACE_GENERATOR
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.util.speedMultipliedValue
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.BlockSideSet
import xyz.xenondevs.nova.util.MINECRAFT_SERVER
import xyz.xenondevs.nova.util.PacketTask
import xyz.xenondevs.nova.util.advance
import xyz.xenondevs.nova.util.axis
import xyz.xenondevs.nova.util.item.isNotNullOrEmpty
import xyz.xenondevs.nova.util.nmsBlockEntity
import xyz.xenondevs.nova.util.nmsBlockState
import xyz.xenondevs.nova.util.nmsPos
import xyz.xenondevs.nova.util.particle.particle
import xyz.xenondevs.nova.util.unwrap
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.world.block.state.property.DefaultBlockStateProperties
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.item.itemType
import java.util.*
import kotlin.math.roundToInt

private val BLOCKED_SIDES = BlockSideSet(front = true)

private val MAX_ENERGY = FURNACE_GENERATOR.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = FURNACE_GENERATOR.config.entry<Long>("energy_per_tick")
private val BURN_TIME_MULTIPLIER = FURNACE_GENERATOR.config.entry<Double>("burn_time_multiplier")

class FurnaceGenerator(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val inventory = storedInventory("fuel", 1, ::handleInventoryUpdate)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, EXTRACT, BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(inventory to INSERT, blockedSides = BLOCKED_SIDES)
    
    private val energyPerTick: Long by speedMultipliedValue(ENERGY_PER_TICK, upgradeHolder)
    private val burnTimeMultiplier: Provider<Double> = combinedProvider(
        BURN_TIME_MULTIPLIER,
        upgradeHolder.getValueProvider(UpgradeTypes.SPEED),
        upgradeHolder.getValueProvider(UpgradeTypes.EFFICIENCY)
    ).map { (multiplier, speed, eff) -> multiplier / speed * eff }
    
    private var currentBurnTime: MutableProvider<Int> = storedValue("burnTime") { 0 }
    private val rawBurnTime: MutableProvider<Int> = storedValue("totalBurnTime") { 0 }
    private val totalBurnTime: Provider<Int> = combinedProvider(rawBurnTime, burnTimeMultiplier)
        .map { (burnTime, multiplier) -> (burnTime * multiplier).roundToInt() }
    
    private val particleTask = PacketTask(
        particle(ParticleTypes.SMOKE) {
            val facing = blockState.getOrThrow(DefaultBlockStateProperties.FACING_HORIZONTAL)
            location(block.location.add(.5, .0, .5).advance(facing, 0.6).apply { y += 0.8 })
            offset(BlockSide.RIGHT.getBlockFace(facing).axis!!, 0.15f)
            offsetY(0.1f)
            speed(0f)
            amount(5)
        },
        1,
        ::getViewers
    )
    
    private var active: Boolean = false
        set(value) {
            if (field == value)
                return
            field = value
            
            updateBlockState(blockState.apply { this[BlockStateProperties.ACTIVE] = value })
            if (value) {
                particleTask.start()
            } else {
                particleTask.stop()
            }
        }
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.GENERIC_1X1_WITH_BAR) {
        upperGui by gui(
            "s . . . . . . . e",
            "u . . . i . . . e",
            ". . . . ! . . . e",
        ) {
            'i' by inventory
            '!' by energyProgressItem(currentBurnTime, totalBurnTime)
            's' by openSideConfigItem(mapOf(itemHolder.getNetworkedInventory(inventory) to "inventory.machines.fuel"))
            'u' by openUpgradesItem(upgradeHolder)
            'e' by energyBar(energyHolder)
        }
    }
    
    override fun handleDisable() {
        particleTask.stop()
    }
    
    override fun handleTick() {
        var currentBurnTime by currentBurnTime
        val totalBurnTime by totalBurnTime
        
        if (currentBurnTime >= totalBurnTime) {
            tryBurnItem()
        }
        
        if (currentBurnTime < totalBurnTime) {
            currentBurnTime++
            energyHolder.energy += energyPerTick
            active = true
            
            if (currentBurnTime >= totalBurnTime) {
                currentBurnTime = 0
                rawBurnTime.set(0)
                tryBurnItem()
            }
        } else {
            active = false
        }
    }
    
    private fun tryBurnItem() {
        var currentBurnTime by currentBurnTime
        val fuelStack = inventory.getItem(0)
        if (energyHolder.energy < energyHolder.maxEnergy && fuelStack != null) {
            val ctx = LootContext.Builder(
                LootParams.Builder(MINECRAFT_SERVER.overworld())
                    .withParameter(LootContextParams.BLOCK_STATE, blockState.nmsBlockState)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(block.nmsPos))
                    .withParameter(LootContextParams.BLOCK_ENTITY, block.nmsBlockEntity!!)
                    .withParameter(LootContextParams.CONTAINER, SlotProvider { null })
                    .create(LootContextParamSets.CONTAINER_PROCESS)
            ).create(Optional.empty())
            val itemBurnTime = fuelStack.unwrap().get(DataComponents.COOKING_FUEL)
                ?.burnTime
                ?.get(ctx, 0)
                ?: 0
            if (itemBurnTime > 0) {
                rawBurnTime.set(itemBurnTime)
                currentBurnTime = 0
                
                val remains = fuelStack.itemType.craftingRemainingItem?.createItemStack()
                if (remains.isNotNullOrEmpty()) {
                    inventory.setItem(null, 0, remains)
                } else inventory.addItemAmount(null, 0, -1)
            }
        }
    }
    
    private fun handleInventoryUpdate(event: ItemPreUpdateEvent) {
        if (event.updateReason != null) { // not done by the tileEntity itself
            val newItem = event.newItem
            if (newItem != null && !newItem.unwrap().has(DataComponents.COOKING_FUEL)) {
                // illegal item
                event.isCancelled = true
            }
        }
    }
    
}
