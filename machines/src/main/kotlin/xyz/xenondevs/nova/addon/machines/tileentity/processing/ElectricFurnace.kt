package xyz.xenondevs.nova.addon.machines.tileentity.processing

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.SmeltingRecipe
import org.bukkit.World
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.commons.provider.mutable.map
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.inventory.event.PlayerUpdateReason
import xyz.xenondevs.nova.data.config.NovaConfig
import xyz.xenondevs.nova.data.config.configReloadable
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.addon.machines.gui.ProgressArrowItem
import xyz.xenondevs.nova.addon.machines.registry.Blocks.ELECTRIC_FURNACE
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.item.holder.NovaItemHolder
import xyz.xenondevs.nova.tileentity.upgrade.Upgradable
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.OpenUpgradesItem
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.MINECRAFT_SERVER
import xyz.xenondevs.nova.util.NMSUtils.REGISTRY_ACCESS
import xyz.xenondevs.nova.util.bukkitCopy
import xyz.xenondevs.nova.util.intValue
import xyz.xenondevs.nova.util.nmsCopy
import xyz.xenondevs.nova.util.serverLevel
import xyz.xenondevs.nova.util.spawnExpOrb
import xyz.xenondevs.nova.addon.simpleupgrades.ConsumerEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes

private fun getRecipe(input: ItemStack, world: World): SmeltingRecipe? {
    return MINECRAFT_SERVER.recipeManager.getAllRecipesFor(RecipeType.SMELTING)
        .firstOrNull { it.matches(SimpleContainer(input.nmsCopy), world.serverLevel) }
}

private val MAX_ENERGY = configReloadable { NovaConfig[ELECTRIC_FURNACE].getLong("capacity") }
private val ENERGY_PER_TICK = configReloadable { NovaConfig[ELECTRIC_FURNACE].getLong("energy_per_tick") }
private val COOK_SPEED by configReloadable { NovaConfig[ELECTRIC_FURNACE].getInt("cook_speed") }

class ElectricFurnace(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState), Upgradable {
    
    private val inputInventory = getInventory("input", 1, ::handleInputInventoryUpdate)
    private val outputInventory = getInventory("output", 1, ::handleOutputInventoryUpdate)
    
    override val upgradeHolder = getUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    override val energyHolder = ConsumerEnergyHolder(this, MAX_ENERGY, ENERGY_PER_TICK, null, upgradeHolder) { createSideConfig(NetworkConnectionType.INSERT, BlockSide.FRONT) }
    override val itemHolder = NovaItemHolder(
        this,
        inputInventory to NetworkConnectionType.INSERT,
        outputInventory to NetworkConnectionType.EXTRACT
    ) { createSideConfig(NetworkConnectionType.BUFFER, BlockSide.FRONT) }
    
    private var currentRecipe: SmeltingRecipe? by storedValue<ResourceLocation>("currentRecipe").map(
        { MINECRAFT_SERVER.recipeManager.byKey(it).orElse(null) as? SmeltingRecipe },
        SmeltingRecipe::getId
    )
    private var timeCooked: Int by storedValue("timeCooked") { 0 }
    private var experience: Float by storedValue("experience") { 0f }
    
    private var cookSpeed = 0
    
    private var active: Boolean = false
        set(active) {
            if (field != active) {
                field = active
                blockState.modelProvider.update(active.intValue)
            }
        }
    
    init {
        reload()
    }
    
    override fun reload() {
        super.reload()
        cookSpeed = (COOK_SPEED * upgradeHolder.getValue(UpgradeTypes.SPEED)).toInt()
    }
    
    private fun handleInputInventoryUpdate(event: ItemPreUpdateEvent) {
        val itemStack = event.newItem
        if (itemStack != null) {
            if (getRecipe(itemStack, world) == null) event.isCancelled = true
        }
    }
    
    private fun handleOutputInventoryUpdate(event: ItemPreUpdateEvent) {
        val updateReason = event.updateReason
        if (updateReason == SELF_UPDATE_REASON) return
        
        if (event.isRemove) {
            if (updateReason is PlayerUpdateReason) {
                val player = updateReason.player
                if (event.newItem == null) { // took all items
                    experience -= pos.block.spawnExpOrb(experience.toInt(), player.location)
                } else {
                    val amount = event.removedAmount
                    val experiencePerItem = experience / event.previousItem!!.amount
                    val experience = amount * experiencePerItem
                    
                    this.experience -= pos.block.spawnExpOrb(experience.toInt(), player.location)
                }
            }
        } else event.isCancelled = true
    }
    
    override fun getExp(): Int = experience.toInt()
    
    override fun handleTick() {
        if (energyHolder.energy >= energyHolder.energyConsumption) {
            if (currentRecipe == null) {
                val item = inputInventory.getItem(0)
                if (item != null) {
                    val recipe = getRecipe(item, world)
                    if (recipe != null && outputInventory.canHold(recipe.getResultItem(REGISTRY_ACCESS).bukkitCopy)) {
                        currentRecipe = recipe
                        inputInventory.addItemAmount(null, 0, -1)
                        
                        active = true
                    } else active = false
                } else active = false
            }
            
            val currentRecipe = currentRecipe
            if (currentRecipe != null) {
                energyHolder.energy -= energyHolder.energyConsumption
                timeCooked += cookSpeed
                
                if (timeCooked >= currentRecipe.cookingTime) {
                    outputInventory.addItem(SELF_UPDATE_REASON, currentRecipe.getResultItem(REGISTRY_ACCESS).bukkitCopy)
                    experience += currentRecipe.experience
                    timeCooked = 0
                    this.currentRecipe = null
                }
                
                menuContainer.forEachMenu(ElectricFurnaceMenu::updateProgress)
            }
        } else active = false
    }
    
    @TileEntityMenuClass
    inner class ElectricFurnaceMenu : GlobalTileEntityMenu() {
        
        private val progressItem = ProgressArrowItem()
        
        private val sideConfigGui = SideConfigMenu(
            this@ElectricFurnace,
            listOf(
                itemHolder.getNetworkedInventory(inputInventory) to "inventory.nova.input",
                itemHolder.getNetworkedInventory(outputInventory) to "inventory.nova.output"
            ),
            ::openWindow
        )
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s u # # # # e |",
                "| i # > # o # e |",
                "| # # # # # # e |",
                "3 - - - - - - - 4")
            .addIngredient('i', inputInventory)
            .addIngredient('o', outputInventory)
            .addIngredient('>', progressItem)
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('e', EnergyBar(3, energyHolder))
            .build()
        
        init {
            updateProgress()
        }
        
        fun updateProgress() {
            val cookTime = currentRecipe?.cookingTime ?: 0
            progressItem.percentage = if (timeCooked == 0) 0.0 else timeCooked.toDouble() / cookTime.toDouble()
        }
        
    }
    
}
