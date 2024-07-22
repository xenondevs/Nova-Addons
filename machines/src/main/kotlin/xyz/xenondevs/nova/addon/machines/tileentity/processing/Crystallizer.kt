package xyz.xenondevs.nova.addon.machines.tileentity.processing

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.core.particles.ParticleTypes
import org.bukkit.block.BlockFace
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPostUpdateEvent
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.item.builder.ItemBuilder
import xyz.xenondevs.invui.item.builder.setDisplayName
import xyz.xenondevs.nova.addon.machines.registry.Blocks
import xyz.xenondevs.nova.addon.machines.registry.RecipeTypes
import xyz.xenondevs.nova.addon.machines.util.efficiencyDividedValue
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.data.recipe.RecipeManager
import xyz.xenondevs.nova.item.DefaultGuiItems
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.type.NetworkConnectionType.BUFFER
import xyz.xenondevs.nova.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.tileentity.network.type.item.inventory.NetworkedVirtualInventory
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.VerticalBar
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.PacketTask
import xyz.xenondevs.nova.util.advance
import xyz.xenondevs.nova.util.particle.particle
import xyz.xenondevs.nova.util.particle.vibration
import xyz.xenondevs.nova.util.unwrap
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.fakeentity.impl.FakeItem

private val MAX_ENERGY = Blocks.CRYSTALLIZER.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = Blocks.CRYSTALLIZER.config.entry<Long>("energy_per_tick")

class Crystallizer(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val inventory = storedInventory("inventory", 1, false, intArrayOf(1), ::handleInventoryUpdate, ::handleInventoryUpdated)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT)
    private val itemHolder = storedItemHolder(inventory to BUFFER)
    
    private val progressPerTick by upgradeHolder.getValueProvider(UpgradeTypes.SPEED)
    private val energyPerTick by efficiencyDividedValue(ENERGY_PER_TICK, upgradeHolder)
    private var progress by storedValue("progress") { 0.0 }
    private var recipe = inventory.getItem(0)?.let { RecipeManager.getConversionRecipeFor(RecipeTypes.CRYSTALLIZER, it) }
    
    private val particleTask: PacketTask
    private var displayState: Boolean
    private val itemDisplay: FakeItem
    
    init {
        // item display
        val itemStack = inventory.getItem(0)
        displayState = itemStack != null
        itemDisplay = FakeItem(pos.location.add(.5, .2, .5), false) { _, data ->
            data.item = itemStack
            data.hasNoGravity = true
        }
        
        // particle task
        val centerLocation = pos.location.add(.5, .5, .5)
        val packets = listOf(BlockFace.NORTH, BlockFace.WEST, BlockFace.SOUTH, BlockFace.EAST).map {
            val startLocation = pos.location.add(.5, .8, .5).advance(it, .4)
            particle(ParticleTypes.VIBRATION, startLocation) {
                vibration(centerLocation, 10)
            }
        }
        particleTask = PacketTask(packets, 9, ::getViewers)
    }
    
    override fun handleEnable() {
        super.handleEnable()
        if (displayState)
            itemDisplay.register()
    }
    
    override fun handleDisable() {
        super.handleDisable()
        itemDisplay.remove()
    }
    
    private fun handleInventoryUpdate(event: ItemPreUpdateEvent) {
        if (event.isRemove) {
            // prevent item networks from extracting items that are currently being processed
            if (event.updateReason == NetworkedVirtualInventory.UPDATE_REASON && recipe != null) {
                event.isCancelled = true
                return
            }
            
            this.recipe = null
            progress = 0.0
            return
        }
        
        val recipe = RecipeManager.getConversionRecipeFor(RecipeTypes.CRYSTALLIZER, event.newItem!!)
        if (recipe == null && event.updateReason != SELF_UPDATE_REASON) {
            event.isCancelled = true
        } else {
            this.recipe = recipe
            progress = 0.0
        }
    }
    
    private fun handleInventoryUpdated(event: ItemPostUpdateEvent) {
        val itemStack = event.newItem.unwrap().copy()
        if (itemStack.isEmpty) {
            if (displayState) {
                itemDisplay.remove()
                displayState = false
            }
            return
        }
        
        itemDisplay.updateEntityData(displayState) { item = event.newItem }
        
        if (!displayState) {
            itemDisplay.register()
            displayState = true
        }
    }
    
    override fun handleTick() {
        val recipe = recipe
        if (recipe != null && energyHolder.energy >= energyPerTick) {
            energyHolder.energy -= energyPerTick
            progress += progressPerTick
            
            if (progress >= recipe.time) {
                inventory.setItem(SELF_UPDATE_REASON, 0, recipe.result)
            }
            
            menuContainer.forEachMenu<CrystallizerMenu> { it.progressBar.percentage = progress / recipe.time }
            
            if (!particleTask.isRunning()) {
                particleTask.start()
            }
        } else if (particleTask.isRunning()) particleTask.stop()
    }
    
    @TileEntityMenuClass
    inner class CrystallizerMenu : GlobalTileEntityMenu() {
        
        private val sideConfigGui = SideConfigMenu(
            this@Crystallizer,
            mapOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default"),
            ::openWindow
        )
        
        val progressBar = object : VerticalBar(3) {
            override val barItem = DefaultGuiItems.BAR_GREEN
            override fun modifyItemBuilder(itemBuilder: ItemBuilder) =
                itemBuilder.setDisplayName(Component.translatable(
                    "menu.machines.crystallizer.idle",
                    NamedTextColor.GRAY
                ))
        }
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s # # # p # e |",
                "| u # i # p # e |",
                "| # # # # p # e |",
                "3 - - - - - - - 4")
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('i', inventory)
            .addIngredient('p', progressBar)
            .addIngredient('e', EnergyBar(3, energyHolder))
            .build()
        
    }
    
}