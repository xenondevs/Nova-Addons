package xyz.xenondevs.nova.addon.machines.tileentity.processing

import org.bukkit.Bukkit
import org.bukkit.Keyed
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Recipe
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPostUpdateEvent
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.window.Window
import xyz.xenondevs.nova.addon.machines.gui.ProgressArrowItem
import xyz.xenondevs.nova.addon.machines.registry.Blocks.AUTO_CRAFTER
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.util.efficiencyDividedValue
import xyz.xenondevs.nova.addon.machines.util.maxIdleTime
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.item.BackItem
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.item.craftingRemainingItem
import xyz.xenondevs.nova.util.playClickSound
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.item.DefaultGuiItems

private val MAX_ENERGY = AUTO_CRAFTER.config.entry<Long>("max_energy")
private val ENERGY_PER_TICK = AUTO_CRAFTER.config.entry<Long>("energy_per_tick")
private val CRAFTING_TIME = AUTO_CRAFTER.config.entry<Int>("crafting_time")

private fun getCraftingRecipe(matrix: Array<ItemStack?>, world: World): Recipe? {
    @Suppress("UNCHECKED_CAST") // Bukkit's method params are not annotated properly, null elements are ok
    return Bukkit.getCraftingRecipe(matrix as Array<ItemStack>, world)
}

class AutoCrafter(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val recipeInv = storedInventory("recipe", 9, ::putItemRecipe) { validateCraftingRecipe() }
    private val resultInv = storedInventory("result", 1, ::preventSteal)
    private val inputInv = storedInventory("input", 9, false, IntArray(9) { 1 }, ::putInputItem, ::validateCraftingIngredients)
    private val outputInv = storedInventory("output", 9, ::preventOutputInput)
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.ENERGY, UpgradeTypes.EFFICIENCY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT)
    private val itemHolder = storedItemHolder(inputInv to INSERT, outputInv to EXTRACT)
    
    private val energyPerTick by efficiencyDividedValue(ENERGY_PER_TICK, upgradeHolder)
    private val maxIdleTime by maxIdleTime(CRAFTING_TIME, upgradeHolder)
    
    private var currentRecipe: Recipe? = null
    private var hasRecipe = false
    private var idleTime = 0
    
    override fun handleEnable() {
        super.handleEnable()
        currentRecipe = getCraftingRecipe(recipeInv.items, pos.world)
        val input = getCraftingRecipe(inputInv.items, pos.world)
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
        val recipe = getCraftingRecipe(matrix, pos.world)
        if (recipe != null) {
            resultInv.setItem(SELF_UPDATE_REASON, 0, recipe.result)
            currentRecipe = recipe
            val input = getCraftingRecipe(inputInv.items, pos.world)
            hasRecipe = input != null && (input as Keyed).key == (currentRecipe as Keyed).key
        } else {
            resultInv.setItem(SELF_UPDATE_REASON, 0, ItemStack(Material.AIR))
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
            val input = getCraftingRecipe(inputInv.items, pos.world)
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
            val input = getCraftingRecipe(inputInv.items, pos.world)
            hasRecipe = input != null && (input as Keyed).key == (currentRecipe as Keyed).key
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
    private inner class AutoCrafterMenu : GlobalTileEntityMenu(GuiTextures.AUTO_CRAFTER_RECIPE) {
        
        private val openInventoryMenuItem = Item.builder()
            .setItemProvider(GuiItems.INVENTORY_BTN.clientsideProvider)
            .addClickHandler { _, click ->
                click.player.playClickSound()
                inventoryWindow.open(click.player)
            }.build()
        
        private val sideConfigGui = SideConfigMenu(
            this@AutoCrafter,
            mapOf(
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
            .addIngredient('<', BackItem(DefaultGuiItems.TP_ARROW_LEFT_ON.clientsideProvider) { openWindow(it) })
            .build()
        
        private val inventoryWindow = Window.single()
            .setGui(autoCrafterInventoryGui)
            .setTitle(GuiTextures.AUTO_CRAFTER_INVENTORY.getTitle("block.machines.auto_crafter.inv_window.title"))
        
    }
    
}