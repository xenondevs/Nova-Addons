package xyz.xenondevs.nova.addon.machines.tileentity.processing

import org.bukkit.Bukkit
import org.bukkit.Keyed
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Recipe
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPostUpdateEvent
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.window.Window
import xyz.xenondevs.invui.window.type.context.setTitle
import xyz.xenondevs.nova.addon.machines.gui.ProgressArrowItem
import xyz.xenondevs.nova.addon.machines.registry.Blocks.AUTO_CRAFTER
import xyz.xenondevs.nova.addon.machines.registry.GuiMaterials
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.simpleupgrades.ConsumerEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.data.config.entry
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.item.holder.NovaItemHolder
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.OpenUpgradesItem
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu
import xyz.xenondevs.nova.ui.item.BackItem
import xyz.xenondevs.nova.ui.item.clickableItem
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.item.craftingRemainingItem
import xyz.xenondevs.nova.util.playClickSound
import kotlin.math.max
import kotlin.math.roundToInt

private val MAX_ENERGY = AUTO_CRAFTER.config.entry<Long>("max_energy")
private val ENERGY_PER_TICK = AUTO_CRAFTER.config.entry<Long>("energy_per_tick")
private val CRAFTING_SPEED = AUTO_CRAFTER.config.entry<Double>("speed")

private fun getCraftingRecipe(matrix: Array<ItemStack?>, world: World): Recipe? {
    @Suppress("UNCHECKED_CAST") // Bukkit's method params are not annotated properly, null elements are ok
    return Bukkit.getCraftingRecipe(matrix as Array<ItemStack>, world)
}

class AutoCrafter(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState) {
    
    private var speed = 1
    private var remainingTime by storedValue<Int>("remaining_time") { 0 }
    
    private val recipeInv = getInventory("recipe", 9, ::putItemRecipe) { validateCraftingRecipe() }
    private val resultInv = getInventory("result", 1, ::preventSteal)
    private val inputInv = getInventory("input", 9, ::putInputItem, ::validateCraftingIngredients)
    private val outputInv = getInventory("output", 9, ::preventOutputInput)
    
    private val upgradeHolder = getUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.ENERGY, UpgradeTypes.EFFICIENCY)
    override val itemHolder = NovaItemHolder(
        this,
        inputInv to NetworkConnectionType.INSERT,
        outputInv to NetworkConnectionType.EXTRACT
    ) { createSideConfig(NetworkConnectionType.BUFFER) }
    override val energyHolder = ConsumerEnergyHolder(
        this,
        MAX_ENERGY,
        ENERGY_PER_TICK,
        null,
        upgradeHolder
    ) { createSideConfig(NetworkConnectionType.INSERT) }
    
    private var currentRecipe: Recipe? = null
    private var valid = false
    
    override fun handleInitialized(first: Boolean) {
        super.handleInitialized(first)
        currentRecipe = getCraftingRecipe(recipeInv.items, world)
        val input = getCraftingRecipe(inputInv.items, world)
        valid = currentRecipe != null && input != null && (input as Keyed).key == (currentRecipe as Keyed).key
        speed = (CRAFTING_SPEED.value * upgradeHolder.getValue(UpgradeTypes.SPEED)).roundToInt()
    }
    
    override fun reload() {
        super.reload()
        speed = (CRAFTING_SPEED.value * upgradeHolder.getValue(UpgradeTypes.SPEED)).roundToInt()
    }
    
    override fun handleTick() {
        if (energyHolder.energy >= energyHolder.energyConsumption) {
            if (remainingTime == 0) craft()
            if (remainingTime != 0 && valid) {
                remainingTime = max(remainingTime - speed, 0)
                energyHolder.energy -= energyHolder.energyConsumption
            }
        }
    }
    
    private fun putItemRecipe(event: ItemPreUpdateEvent) {
        if (event.updateReason == SELF_UPDATE_REASON) return
        event.isCancelled = true
        recipeInv.setItem(SELF_UPDATE_REASON, event.slot, event.newItem)
    }
    
    private fun validateCraftingRecipe() {
        val matrix = recipeInv.items
        val recipe = getCraftingRecipe(matrix, world)
        if (recipe != null) {
            resultInv.setItem(SELF_UPDATE_REASON, 0, recipe.result)
            currentRecipe = recipe
            val input = getCraftingRecipe(inputInv.items, world)
            valid = input != null && (input as Keyed).key == (currentRecipe as Keyed).key
        } else {
            resultInv.setItem(SELF_UPDATE_REASON, 0, ItemStack(Material.AIR))
            currentRecipe = null
            valid = false
        }
    }
    
    private fun putInputItem(event: ItemPreUpdateEvent) {
        if (event.isRemove) return
        
        val item = recipeInv.getItem(event.slot)
        if (item == null || !item.isSimilar(event.newItem)) {
            event.isCancelled = true
        }
    }
    
    private fun validateCraftingIngredients(event: ItemPostUpdateEvent) {
        if (event.previousItem == null || event.newItem == null) {
            val input = getCraftingRecipe(inputInv.items, world)
            valid = currentRecipe != null && input != null && (input as Keyed).key == (currentRecipe as Keyed).key
        }
    }
    
    private fun preventOutputInput(event: ItemPreUpdateEvent) {
        if (!event.isRemove && event.updateReason != SELF_UPDATE_REASON) event.isCancelled = true
    }
    
    private fun preventSteal(event: ItemPreUpdateEvent) {
        if (event.updateReason != SELF_UPDATE_REASON)
            event.isCancelled = true
    }
    
    private fun craft() {
        if (valid) {
            val result = currentRecipe!!.result
            if (outputInv.canHold(result)) {
                val resultItems = getCraftingResult()
                resultItems.add(0, result)
                if (outputInv.canHold(resultItems))
                    addToOutputAndCleanUp(resultItems)
            }
            val input = getCraftingRecipe(inputInv.items, world)
            valid = input != null && (input as Keyed).key == (currentRecipe as Keyed).key
            remainingTime = 100
        }
    }
    
    private fun getCraftingResult(): ArrayList<ItemStack> {
        val resultItems = ArrayList<ItemStack>()
        for (i in 0..<9) {
            val remaining = inputInv.getItem(i)?.craftingRemainingItem
            if (remaining != null)
                resultItems += remaining
        }
        return resultItems
    }
    
    private fun addToOutputAndCleanUp(results: ArrayList<ItemStack>) {
        for (i in 0..<9)
            inputInv.addItemAmount(SELF_UPDATE_REASON, i, -1)
        for (result in results)
            outputInv.addItem(SELF_UPDATE_REASON, result)
    }
    
    
    @TileEntityMenuClass
    private inner class AutoCrafterMenu : GlobalTileEntityMenu(GuiTextures.AUTO_CRAFTER) {
        
        private val openInventoryMenuItem = clickableItem(GuiMaterials.INVENTORY_BUTTON.clientsideProvider) {
            it.playClickSound()
            inventoryWindow.open(it)
        }
        
        private val sideConfigGui = SideConfigMenu(
            this@AutoCrafter,
            listOf(
                itemHolder.getNetworkedInventory(inputInv) to "inventory.nova.input",
                itemHolder.getNetworkedInventory(outputInv) to "inventory.nova.output",
            ),
            ::openWindow
        )
        
        override val gui = Gui.normal()
            .setStructure(
                ". . . . . . . . .",
                "s r r r . . . . e",
                "i r r r . . o . e",
                "u r r r . . . . e",
                ". . . . . . . . .")
            .addIngredient('r', recipeInv)
            .addIngredient('i', openInventoryMenuItem)
            .addIngredient('o', resultInv)
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('>', ProgressArrowItem())
            .addIngredient('e', EnergyBar(3, energyHolder))
            .build()
        
        private val autoCrafterInventoryGui = Gui.normal()
            .setStructure(
                "< . . . . . . . .",
                ". i i i . o o o .",
                ". i i i . o o o .",
                ". i i i . o o o .",
                ". . . . . . . . .",
            )
            .addIngredient('i', inputInv)
            .addIngredient('o', outputInv)
            .addIngredient('<', BackItem { openWindow(it) })
            .build()
        
        private val inventoryWindow = Window.single()
            .setGui(autoCrafterInventoryGui)
            .setTitle(GuiTextures.AUTO_CRAFTER_INTERNAL_INVENTORY.getTitle("block.machines.auto_crafter.inv_window.title"))
    
    }
    
}