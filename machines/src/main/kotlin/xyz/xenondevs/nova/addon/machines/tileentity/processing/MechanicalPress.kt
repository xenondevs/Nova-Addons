package xyz.xenondevs.nova.addon.machines.tileentity.processing

import net.minecraft.resources.ResourceLocation
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import xyz.xenondevs.commons.provider.mutable.map
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.invui.item.impl.AbstractItem
import xyz.xenondevs.nova.data.config.NovaConfig
import xyz.xenondevs.nova.data.config.configReloadable
import xyz.xenondevs.nova.data.recipe.ConversionNovaRecipe
import xyz.xenondevs.nova.data.recipe.NovaRecipe
import xyz.xenondevs.nova.data.recipe.RecipeManager
import xyz.xenondevs.nova.data.recipe.RecipeType
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.addon.machines.gui.PressProgressItem
import xyz.xenondevs.nova.addon.machines.registry.Blocks.MECHANICAL_PRESS
import xyz.xenondevs.nova.addon.machines.registry.GuiMaterials
import xyz.xenondevs.nova.addon.machines.registry.RecipeTypes
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.item.holder.NovaItemHolder
import xyz.xenondevs.nova.tileentity.upgrade.Upgradable
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.OpenUpgradesItem
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide.FRONT
import xyz.xenondevs.nova.addon.simpleupgrades.ConsumerEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import kotlin.math.max

private val MAX_ENERGY = configReloadable { NovaConfig[MECHANICAL_PRESS].getLong("capacity") }
private val ENERGY_PER_TICK = configReloadable { NovaConfig[MECHANICAL_PRESS].getLong("energy_per_tick") }
private val PRESS_SPEED by configReloadable { NovaConfig[MECHANICAL_PRESS].getInt("speed") }

private enum class PressType(val recipeType: RecipeType<out ConversionNovaRecipe>) {
    PLATE(RecipeTypes.PLATE_PRESS),
    GEAR(RecipeTypes.GEAR_PRESS)
}

class MechanicalPress(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState), Upgradable {
    
    private val inputInv = getInventory("input", 1, ::handleInputUpdate)
    private val outputInv = getInventory("output", 1, ::handleOutputUpdate)
    
    override val upgradeHolder = getUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    override val energyHolder = ConsumerEnergyHolder(this, MAX_ENERGY, ENERGY_PER_TICK, null, upgradeHolder) { createSideConfig(NetworkConnectionType.INSERT, FRONT) }
    override val itemHolder = NovaItemHolder(
        this,
        inputInv to NetworkConnectionType.INSERT,
        outputInv to NetworkConnectionType.EXTRACT
    ) { createSideConfig(NetworkConnectionType.BUFFER, FRONT) }
    
    private var type by storedValue("pressType") { PressType.PLATE }
    private var timeLeft by storedValue("pressTime") { 0 }
    private var pressSpeed = 0
    
    private var currentRecipe: ConversionNovaRecipe? by storedValue<ResourceLocation>("currentRecipe").map(
        { RecipeManager.getRecipe(type.recipeType, it) },
        NovaRecipe::id
    )
    
    init {
        reload()
        if (currentRecipe == null) timeLeft = 0
    }
    
    override fun reload() {
        super.reload()
        pressSpeed = (PRESS_SPEED * upgradeHolder.getValue(UpgradeTypes.SPEED)).toInt()
    }
    
    override fun handleTick() {
        if (energyHolder.energy >= energyHolder.energyConsumption) {
            if (timeLeft == 0) takeItem()
            if (timeLeft != 0) { // is pressing
                timeLeft = max(timeLeft - pressSpeed, 0)
                energyHolder.energy -= energyHolder.energyConsumption
                
                if (timeLeft == 0) {
                    outputInv.putItem(SELF_UPDATE_REASON, 0, currentRecipe!!.result)
                    currentRecipe = null
                }
                
                menuContainer.forEachMenu(MechanicalPressMenu::updateProgress)
            }
        }
    }
    
    private fun takeItem() {
        val inputItem = inputInv.getItem(0)
        if (inputItem != null) {
            val recipe = RecipeManager.getConversionRecipeFor(type.recipeType, inputItem)
            if (recipe != null && outputInv.canHold(recipe.result)) {
                inputInv.addItemAmount(SELF_UPDATE_REASON, 0, -1)
                timeLeft = recipe.time
                currentRecipe = recipe
            }
        }
    }
    
    private fun handleInputUpdate(event: ItemPreUpdateEvent) {
        if (event.updateReason != SELF_UPDATE_REASON
            && event.newItem != null
            && RecipeManager.getConversionRecipeFor(type.recipeType, event.newItem!!) == null) {
            
            event.isCancelled = true
        }
    }
    
    private fun handleOutputUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = !event.isRemove && event.updateReason != SELF_UPDATE_REASON
    }
    
    override fun saveData() {
        super.saveData()
        storeData("currentRecipe", currentRecipe?.id)
    }
    
    
    @TileEntityMenuClass
    inner class MechanicalPressMenu : GlobalTileEntityMenu() {
        
        private val pressProgress = PressProgressItem()
        private val pressTypeItems = ArrayList<PressTypeItem>()
        
        private val sideConfigGui = SideConfigMenu(
            this@MechanicalPress,
            listOf(
                itemHolder.getNetworkedInventory(inputInv) to "inventory.nova.input",
                itemHolder.getNetworkedInventory(outputInv) to "inventory.nova.output",
            ),
            ::openWindow
        )
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| p g # i # # e |",
                "| # # # , # # e |",
                "| s u # o # # e |",
                "3 - - - - - - - 4")
            .addIngredient('i', inputInv)
            .addIngredient('o', outputInv)
            .addIngredient(',', pressProgress)
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('p', PressTypeItem(PressType.PLATE).apply(pressTypeItems::add))
            .addIngredient('g', PressTypeItem(PressType.GEAR).apply(pressTypeItems::add))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('e', EnergyBar(3, energyHolder))
            .build()
        
        init {
            updateProgress()
        }
        
        fun updateProgress() {
            val recipeTime = currentRecipe?.time ?: 0
            pressProgress.percentage = if (timeLeft == 0) 0.0 else (recipeTime - timeLeft).toDouble() / recipeTime.toDouble()
        }
        
        private inner class PressTypeItem(private val type: PressType) : AbstractItem() {
            
            override fun getItemProvider(): ItemProvider {
                return if (type == PressType.PLATE) {
                    if (this@MechanicalPress.type == PressType.PLATE) GuiMaterials.PLATE_BTN_OFF.clientsideProvider
                    else GuiMaterials.PLATE_BTN_ON.clientsideProvider
                } else {
                    if (this@MechanicalPress.type == PressType.GEAR) GuiMaterials.GEAR_BTN_OFF.clientsideProvider
                    else GuiMaterials.GEAR_BTN_ON.clientsideProvider
                }
            }
            
            override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) {
                if (this@MechanicalPress.type != type) {
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                    this@MechanicalPress.type = type
                    pressTypeItems.forEach(Item::notifyWindows)
                }
            }
            
        }
        
    }
    
}