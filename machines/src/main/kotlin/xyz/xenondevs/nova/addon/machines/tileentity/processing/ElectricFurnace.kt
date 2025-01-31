@file:Suppress("UNCHECKED_CAST")

package xyz.xenondevs.nova.addon.machines.tileentity.processing

import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.SingleRecipeInput
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.inventory.CookingRecipe
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.enumSetOf
import xyz.xenondevs.commons.provider.mapNonNull
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.inventory.event.PlayerUpdateReason
import xyz.xenondevs.nova.addon.machines.gui.ProgressArrowItem
import xyz.xenondevs.nova.addon.machines.registry.BlockStateProperties
import xyz.xenondevs.nova.addon.machines.registry.Blocks.ELECTRIC_FURNACE
import xyz.xenondevs.nova.addon.machines.util.energyConsumption
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
import xyz.xenondevs.nova.util.MINECRAFT_SERVER
import xyz.xenondevs.nova.util.serverLevel
import xyz.xenondevs.nova.util.spawnExpOrb
import xyz.xenondevs.nova.util.unwrap
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT

private fun getRecipe(input: ItemStack, world: World): CookingRecipe<*>? {
    return MINECRAFT_SERVER.recipeManager.recipes.byType(RecipeType.SMELTING)
        .firstOrNull { it.value().matches(SingleRecipeInput(input.unwrap().copy()), world.serverLevel) }
        ?.toBukkitRecipe() as? CookingRecipe<*>
}

private val BLOCKED_SIDES = enumSetOf(BlockSide.FRONT)

private val MAX_ENERGY = ELECTRIC_FURNACE.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = ELECTRIC_FURNACE.config.entry<Long>("energy_per_tick")
private val COOK_SPEED = ELECTRIC_FURNACE.config.entry<Int>("cook_speed")

class ElectricFurnace(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val inputInventory = storedInventory("input", 1, ::handleInputInventoryUpdate)
    private val outputInventory = storedInventory("output", 1, ::handleOutputInventoryUpdate)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(inputInventory to INSERT, outputInventory to EXTRACT, blockedSides = BLOCKED_SIDES)
    
    private var currentRecipe: CookingRecipe<*>? by storedValue<NamespacedKey>("currentRecipe").mapNonNull(
        { Bukkit.getRecipe(it) as? CookingRecipe<*> },
        CookingRecipe<*>::getKey
    )
    private var timeCooked: Int by storedValue("timeCooked") { 0 }
    private var experience: Float by storedValue("experience") { 0f }
    
    private val energyPerTick by energyConsumption(ENERGY_PER_TICK, upgradeHolder)
    private val cookSpeed by speedMultipliedValue(COOK_SPEED, upgradeHolder)
    
    private var active: Boolean = blockState.getOrThrow(BlockStateProperties.ACTIVE)
        set(active) {
            if (field != active) {
                field = active
                updateBlockState(blockState.with(BlockStateProperties.ACTIVE, active))
            }
        }
    
    private fun handleInputInventoryUpdate(event: ItemPreUpdateEvent) {
        val itemStack = event.newItem
        if (itemStack != null && getRecipe(itemStack, pos.world) == null) {
            event.isCancelled = true
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
        if (energyHolder.energy >= energyPerTick) {
            if (currentRecipe == null) {
                val item = inputInventory.getItem(0)
                if (item != null) {
                    val recipe = getRecipe(item, pos.world)
                    if (recipe != null && outputInventory.canHold(recipe.result)) {
                        currentRecipe = recipe
                        inputInventory.addItemAmount(null, 0, -1)
                        
                        active = true
                    } else active = false
                } else active = false
            }
            
            val currentRecipe = currentRecipe
            if (currentRecipe != null) {
                energyHolder.energy -= energyPerTick
                timeCooked += cookSpeed
                
                if (timeCooked >= currentRecipe.cookingTime) {
                    outputInventory.addItem(SELF_UPDATE_REASON, currentRecipe.result)
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
            mapOf(
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
