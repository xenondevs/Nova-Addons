package xyz.xenondevs.nova.addon.machines.tileentity.processing

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.core.particles.ParticleTypes
import org.bukkit.block.BlockFace
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPostUpdateEvent
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.item.builder.ItemBuilder
import xyz.xenondevs.invui.item.builder.setDisplayName
import xyz.xenondevs.nmsutils.particle.particle
import xyz.xenondevs.nmsutils.particle.vibration
import xyz.xenondevs.nova.data.config.NovaConfig
import xyz.xenondevs.nova.data.config.configReloadable
import xyz.xenondevs.nova.data.recipe.RecipeManager
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.item.DefaultGuiItems
import xyz.xenondevs.nova.addon.machines.registry.Blocks
import xyz.xenondevs.nova.addon.machines.registry.RecipeTypes
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.TileEntityPacketTask
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.item.holder.NovaItemHolder
import xyz.xenondevs.nova.tileentity.network.item.inventory.NetworkedVirtualInventory
import xyz.xenondevs.nova.tileentity.upgrade.Upgradable
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.OpenUpgradesItem
import xyz.xenondevs.nova.ui.VerticalBar
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.advance
import xyz.xenondevs.nova.util.nmsCopy
import xyz.xenondevs.nova.world.fakeentity.impl.FakeItem
import xyz.xenondevs.nova.addon.simpleupgrades.ConsumerEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes

private val MAX_ENERGY = configReloadable { NovaConfig[Blocks.CRYSTALLIZER].getLong("capacity") }
private val ENERGY_PER_TICK = configReloadable { NovaConfig[Blocks.CRYSTALLIZER].getLong("energy_per_tick") }

class Crystallizer(
    blockState: NovaTileEntityState
) : NetworkedTileEntity(blockState), Upgradable {
    
    private val inventory = getInventory("inventory", 1, intArrayOf(1), false, ::handleInventoryUpdate, ::handleInventoryUpdated)
    
    override val upgradeHolder = getUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    
    override val energyHolder = ConsumerEnergyHolder(this, MAX_ENERGY, ENERGY_PER_TICK, null, upgradeHolder) {
        createExclusiveSideConfig(NetworkConnectionType.INSERT, BlockSide.BOTTOM)
    }
    
    override val itemHolder = NovaItemHolder(this, inventory to NetworkConnectionType.BUFFER) {
        createExclusiveSideConfig(NetworkConnectionType.BUFFER, BlockSide.BOTTOM)
    }
    
    private val progressPerTick by upgradeHolder.getValueProvider(UpgradeTypes.SPEED)
    private var progress by storedValue("progress") { 0.0 }
    private var recipe = inventory.getItem(0)?.let { RecipeManager.getConversionRecipeFor(RecipeTypes.CRYSTALLIZER, it) }
    
    private val particleTask: TileEntityPacketTask
    private var displayState: Boolean
    private val itemDisplay: FakeItem
    
    init {
        // item display
        val itemStack = inventory.getItem(0).nmsCopy
        displayState = !itemStack.isEmpty
        itemDisplay = FakeItem(location.add(.5, .2, .5), displayState) { _, data ->
            data.item = itemStack
            data.hasNoGravity = true
        }
        
        // particle task
        val centerLocation = location.add(.5, .5, .5)
        val packets = listOf(BlockFace.NORTH, BlockFace.WEST, BlockFace.SOUTH, BlockFace.EAST).map {
            val startLocation = location.add(.5, .8, .5).advance(it, .4)
            particle(ParticleTypes.VIBRATION, startLocation) {
                vibration(centerLocation, 10)
            }
        }
        
        particleTask = createPacketTask(packets, 9)
    }
    
    override fun handleRemoved(unload: Boolean) {
        super.handleRemoved(unload)
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
        val itemStack = event.newItem.nmsCopy
        if (itemStack.isEmpty) {
            if (displayState) {
                itemDisplay.remove()
                displayState = false
            }
            return
        }
        
        itemDisplay.updateEntityData(displayState) {
            item = event.newItem.nmsCopy
        }
        
        if (!displayState) {
            itemDisplay.register()
            displayState = true
        }
    }
    
    override fun handleTick() {
        val recipe = recipe
        if (
            recipe != null
            && energyHolder.energy >= energyHolder.energyConsumption
        ) {
            energyHolder.energy -= energyHolder.energyConsumption
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
            listOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default"),
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