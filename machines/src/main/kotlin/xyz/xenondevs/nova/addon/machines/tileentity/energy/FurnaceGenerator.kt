package xyz.xenondevs.nova.addon.machines.tileentity.energy

import net.minecraft.core.particles.ParticleTypes
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.enumSetOf
import xyz.xenondevs.commons.provider.MutableProvider
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.combinedProvider
import xyz.xenondevs.commons.provider.map
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.gui.EnergyProgressItem
import xyz.xenondevs.nova.addon.machines.registry.BlockStateProperties
import xyz.xenondevs.nova.addon.machines.registry.Blocks.FURNACE_GENERATOR
import xyz.xenondevs.nova.addon.machines.util.speedMultipliedValue
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.PacketTask
import xyz.xenondevs.nova.util.advance
import xyz.xenondevs.nova.util.axis
import xyz.xenondevs.nova.util.item.craftingRemainingItem
import xyz.xenondevs.nova.util.particle.particle
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.state.property.DefaultBlockStateProperties
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.item.behavior.Fuel
import kotlin.math.roundToInt

private val BLOCKED_SIDES = enumSetOf(BlockSide.FRONT)

private val MAX_ENERGY = FURNACE_GENERATOR.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = FURNACE_GENERATOR.config.entry<Long>("energy_per_tick")
private val BURN_TIME_MULTIPLIER = FURNACE_GENERATOR.config.entry<Double>("burn_time_multiplier")

class FurnaceGenerator(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
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
    
    private var currentBurnTime: Int by storedValue("burnTime") { 0 }
    private val rawBurnTime: MutableProvider<Int> = storedValue("totalBurnTime") { 0 }
    private val totalBurnTime: Int by combinedProvider(rawBurnTime, burnTimeMultiplier)
        .map { (burnTime, multiplier) -> (burnTime * multiplier).roundToInt() }
    
    private val particleTask = PacketTask(
        particle(ParticleTypes.SMOKE) {
            val facing = blockState.getOrThrow(DefaultBlockStateProperties.FACING)
            location(pos.location.add(.5, .0, .5).advance(facing, 0.6).apply { y += 0.8 })
            offset(BlockSide.RIGHT.getBlockFace(facing).axis, 0.15f)
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
            
            updateBlockState(blockState.with(BlockStateProperties.ACTIVE, value))
            if (value) {
                particleTask.start()
            } else {
                particleTask.stop()
            }
        }
    
    override fun handleDisable() {
        particleTask.stop()
    }
    
    override fun handleTick() {
        if (currentBurnTime >= totalBurnTime) {
            tryBurnItem()
        }
        
        if (currentBurnTime < totalBurnTime) {
            currentBurnTime++
            energyHolder.energy += energyPerTick
            active = true
            
            menuContainer.forEachMenu<FurnaceGeneratorMenu> {
                it.progressItem.percentage = (totalBurnTime - currentBurnTime) / totalBurnTime.toDouble()
            }
            
            if (currentBurnTime >= totalBurnTime) {
                currentBurnTime = 0
                rawBurnTime.set(0)
                tryBurnItem()
            }
        } else {
            active = false
            menuContainer.forEachMenu<FurnaceGeneratorMenu> { it.progressItem.percentage = 0.0 }
        }
    }
    
    private fun tryBurnItem() {
        val fuelStack = inventory.getItem(0)
        if (energyHolder.energy < energyHolder.maxEnergy && fuelStack != null) {
            val itemBurnTime = Fuel.getBurnTime(fuelStack)
            if (itemBurnTime != null) {
                rawBurnTime.set(itemBurnTime)
                currentBurnTime = 0
                
                val remains = fuelStack.craftingRemainingItem
                if (remains != null) {
                    inventory.setItem(null, 0, remains)
                } else inventory.addItemAmount(null, 0, -1)
            }
        }
    }
    
    private fun handleInventoryUpdate(event: ItemPreUpdateEvent) {
        if (event.updateReason != null) { // not done by the tileEntity itself
            val newItem = event.newItem
            if (newItem != null && !Fuel.isFuel(newItem)) {
                // illegal item
                event.isCancelled = true
            }
        }
    }
    
    @TileEntityMenuClass
    inner class FurnaceGeneratorMenu : GlobalTileEntityMenu() {
        
        val progressItem = EnergyProgressItem()
        
        private val sideConfigGui = SideConfigMenu(
            this@FurnaceGenerator,
            mapOf(itemHolder.getNetworkedInventory(inventory) to "inventory.machines.fuel"),
            ::openWindow
        )
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s # # # # # e |",
                "| u # # i # # e |",
                "| # # # ! # # e |",
                "| # # # # # # e |",
                "3 - - - - - - - 4")
            .addIngredient('i', inventory)
            .addIngredient('!', progressItem)
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('e', EnergyBar(4, energyHolder))
            .build()
        
    }
    
}
