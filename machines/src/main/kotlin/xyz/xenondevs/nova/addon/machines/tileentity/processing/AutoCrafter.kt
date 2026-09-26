package xyz.xenondevs.nova.addon.machines.tileentity.processing

import org.bukkit.Bukkit
import org.bukkit.Keyed
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Recipe
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.provider
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.dsl.item
import xyz.xenondevs.invui.dsl.window
import xyz.xenondevs.invui.inventory.event.ItemPostUpdateEvent
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.gui.progressArrowItem
import xyz.xenondevs.nova.addon.machines.registry.Blocks.AUTO_CRAFTER
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.util.energyConsumption
import xyz.xenondevs.nova.addon.machines.util.maxIdleTime
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.ui.menu.item.backItem
import xyz.xenondevs.nova.ui.menu.locale
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.nova.ui.overlay.guitexture.getTitle
import xyz.xenondevs.nova.util.item.isNotNullOrEmpty
import xyz.xenondevs.nova.util.playClickSound
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.item.DefaultGuiItems
import xyz.xenondevs.nova.world.item.guiItemProvider
import xyz.xenondevs.nova.world.item.itemType

private val MAX_ENERGY = AUTO_CRAFTER.config.entry<Long>("max_energy")
private val ENERGY_PER_TICK = AUTO_CRAFTER.config.entry<Long>("energy_per_tick")
private val CRAFTING_TIME = AUTO_CRAFTER.config.entry<Int>("crafting_time")

private fun getCraftingRecipe(matrix: Array<ItemStack?>, world: World): Recipe? {
    @Suppress("UNCHECKED_CAST") // Bukkit's method params are not annotated properly, null elements are ok
    return Bukkit.getCraftingRecipe(matrix as Array<ItemStack>, world)
}

class AutoCrafter(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val recipeInv = storedInventory("recipe", 9, ::putItemRecipe) { validateCraftingRecipe() }
    private val resultInv = storedInventory("result", 1, ::preventSteal)
    private val inputInv = storedInventory("input", 9, false, IntArray(9) { 1 }, ::putInputItem, ::validateCraftingIngredients)
    private val outputInv = storedInventory("output", 9, ::preventOutputInput)
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.ENERGY, UpgradeTypes.EFFICIENCY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT)
    private val itemHolder = storedItemHolder(inputInv to INSERT, outputInv to EXTRACT)
    
    private val energyPerTick by energyConsumption(ENERGY_PER_TICK, upgradeHolder)
    private val maxIdleTime by maxIdleTime(CRAFTING_TIME, upgradeHolder)
    
    private var currentRecipe: Recipe? = null
    private var hasRecipe = false
    private var idleTime = 0
    
    override val menu: TileEntityMenu = TileEntityMenu.cachedWindow(GuiTextures.AUTO_CRAFTER_RECIPE) {
        val inventoryGui = gui(
            "< i i i . o o o .",
            ". i i i . o o o .",
            ". i i i . o o o .",
        ) {
            'i' by inputInv
            'o' by outputInv
            '<' by backItem(window, DefaultGuiItems.TP_ARROW_LEFT_ON.guiItemProvider)
        }
        val inventoryWindow = window(viewer) {
            title by GuiTextures.AUTO_CRAFTER_INVENTORY.getTitle("block.machines.auto_crafter.inv_window.title", locale)
            upperGui by inventoryGui
        }
        menu.register(inventoryWindow)
        
        upperGui by gui(
            "s r r r . . . . e",
            "i r r r . . o . e",
            "u r r r . . . . e",
        ) {
            'r' by recipeInv
            'i' by item {
                itemProvider by GuiItems.INVENTORY_BTN.guiItemProvider
                onClick {
                    player.playClickSound()
                    inventoryWindow.open()
                }
            }
            'o' by resultInv
            'u' by openUpgradesItem(upgradeHolder)
            's' by openSideConfigItem(
                mapOf(
                    itemHolder.getNetworkedInventory(inputInv) to "inventory.nova.input",
                    itemHolder.getNetworkedInventory(outputInv) to "inventory.nova.output",
                )
            )
            '>' by progressArrowItem(provider(0.0)) // TODO
            'e' by energyBar(energyHolder)
        }
    }
    
    override fun handleEnable() {
        super.handleEnable()
        currentRecipe = getCraftingRecipe(recipeInv.items, block.world)
        val input = getCraftingRecipe(inputInv.items, block.world)
        hasRecipe = currentRecipe != null && input != null && (input as Keyed).key == (currentRecipe as Keyed).key
    }
    
    override fun handleTick() {
        if (hasRecipe && energyHolder.energy >= energyPerTick) {
            energyHolder.energy -= energyPerTick
            if (idleTime++ >= maxIdleTime) {
                idleTime = 0
                craft()
            }
        }
    }
    
    private fun putItemRecipe(event: ItemPreUpdateEvent) {
        if (event.updateReason == SELF_UPDATE_REASON)
            return
        
        event.isCancelled = true
        recipeInv.setItem(SELF_UPDATE_REASON, event.slot, event.newItem)
    }
    
    private fun validateCraftingRecipe() {
        val matrix = recipeInv.items
        val recipe = getCraftingRecipe(matrix, block.world)
        if (recipe != null) {
            resultInv.setItem(SELF_UPDATE_REASON, 0, recipe.result)
            currentRecipe = recipe
            val input = getCraftingRecipe(inputInv.items, block.world)
            hasRecipe = input != null && (input as Keyed).key == (currentRecipe as Keyed).key
        } else {
            resultInv.setItem(SELF_UPDATE_REASON, 0, ItemStack.empty())
            currentRecipe = null
            hasRecipe = false
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
            val input = getCraftingRecipe(inputInv.items, block.world)
            hasRecipe = currentRecipe != null && input != null && (input as Keyed).key == (currentRecipe as Keyed).key
        }
    }
    
    private fun preventOutputInput(event: ItemPreUpdateEvent) {
        if (!event.isRemove && event.updateReason != SELF_UPDATE_REASON)
            event.isCancelled = true
    }
    
    private fun preventSteal(event: ItemPreUpdateEvent) {
        if (event.updateReason != SELF_UPDATE_REASON)
            event.isCancelled = true
    }
    
    private fun craft() {
        if (hasRecipe) {
            val result = currentRecipe!!.result
            if (outputInv.canHold(result)) {
                val resultItems = getCraftingResult()
                resultItems.add(0, result)
                if (outputInv.canHold(resultItems))
                    addToOutputAndCleanUp(resultItems)
            }
            val input = getCraftingRecipe(inputInv.items, block.world)
            hasRecipe = input != null && (input as Keyed).key == (currentRecipe as Keyed).key
        }
    }
    
    private fun getCraftingResult(): ArrayList<ItemStack> {
        val resultItems = ArrayList<ItemStack>()
        for (i in 0..<9) {
            val remaining = inputInv.getItem(i)?.itemType?.craftingRemainingItem?.createItemStack()
            if (remaining.isNotNullOrEmpty())
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
    
}
