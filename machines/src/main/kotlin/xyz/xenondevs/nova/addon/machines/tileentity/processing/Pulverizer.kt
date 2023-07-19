package xyz.xenondevs.nova.addon.machines.tileentity.processing

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.resources.ResourceLocation
import xyz.xenondevs.commons.provider.mutable.map
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nmsutils.particle.particle
import xyz.xenondevs.nova.data.config.NovaConfig
import xyz.xenondevs.nova.data.config.configReloadable
import xyz.xenondevs.nova.data.recipe.NovaRecipe
import xyz.xenondevs.nova.data.recipe.RecipeManager
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.addon.machines.gui.ProgressArrowItem
import xyz.xenondevs.nova.addon.machines.gui.PulverizerProgressItem
import xyz.xenondevs.nova.addon.machines.recipe.PulverizerRecipe
import xyz.xenondevs.nova.addon.machines.registry.Blocks.PULVERIZER
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
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.advance
import xyz.xenondevs.nova.addon.simpleupgrades.ConsumerEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import kotlin.math.max

private val MAX_ENERGY = configReloadable { NovaConfig[PULVERIZER].getLong("capacity") }
private val ENERGY_PER_TICK = configReloadable { NovaConfig[PULVERIZER].getLong("energy_per_tick") }
private val PULVERIZE_SPEED by configReloadable { NovaConfig[PULVERIZER].getInt("speed") }

class Pulverizer(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState), Upgradable {
    
    private val inputInv = getInventory("input", 1, ::handleInputUpdate)
    private val outputInv = getInventory("output", 2, ::handleOutputUpdate)
    
    override val upgradeHolder = getUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    override val energyHolder = ConsumerEnergyHolder(this, MAX_ENERGY, ENERGY_PER_TICK, null, upgradeHolder) { createSideConfig(NetworkConnectionType.INSERT, BlockSide.FRONT) }
    override val itemHolder = NovaItemHolder(
        this,
        inputInv to NetworkConnectionType.INSERT,
        outputInv to NetworkConnectionType.EXTRACT
    ) { createSideConfig(NetworkConnectionType.BUFFER, BlockSide.FRONT) }
    
    private var timeLeft by storedValue("pulverizerTime") { 0 }
    private var pulverizeSpeed = 0
    
    private var currentRecipe: PulverizerRecipe? by storedValue<ResourceLocation>("currentRecipe").map(
        { RecipeManager.getRecipe(RecipeTypes.PULVERIZER, it) },
        NovaRecipe::id
    )
    
    private val particleTask = createPacketTask(listOf(
        particle(ParticleTypes.SMOKE) {
            location(centerLocation.advance(getFace(BlockSide.FRONT), 0.6).apply { y += 0.8 })
            offset(0.05, 0.2, 0.05)
            speed(0f)
        }
    ), 6)
    
    init {
        reload()
        if (currentRecipe == null) timeLeft = 0
    }
    
    override fun reload() {
        super.reload()
        pulverizeSpeed = (PULVERIZE_SPEED * upgradeHolder.getValue(UpgradeTypes.SPEED)).toInt()
    }
    
    override fun handleTick() {
        if (energyHolder.energy >= energyHolder.energyConsumption) {
            if (timeLeft == 0) {
                takeItem()
                
                if (particleTask.isRunning()) particleTask.stop()
            } else {
                timeLeft = max(timeLeft - pulverizeSpeed, 0)
                energyHolder.energy -= energyHolder.energyConsumption
                
                if (!particleTask.isRunning()) particleTask.start()
                
                if (timeLeft == 0) {
                    outputInv.addItem(SELF_UPDATE_REASON, currentRecipe!!.result)
                    currentRecipe = null
                }
                
                menuContainer.forEachMenu(PulverizerMenu::updateProgress)
            }
            
        } else if (particleTask.isRunning()) particleTask.stop()
    }
    
    private fun takeItem() {
        val inputItem = inputInv.getItem(0)
        if (inputItem != null) {
            val recipe = RecipeManager.getConversionRecipeFor(RecipeTypes.PULVERIZER, inputItem)!!
            val result = recipe.result
            if (outputInv.canHold(result)) {
                inputInv.addItemAmount(SELF_UPDATE_REASON, 0, -1)
                timeLeft = recipe.time
                currentRecipe = recipe
            }
        }
    }
    
    private fun handleInputUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = event.newItem != null && RecipeManager.getConversionRecipeFor(RecipeTypes.PULVERIZER, event.newItem!!) == null
    }
    
    private fun handleOutputUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = !event.isRemove && event.updateReason != SELF_UPDATE_REASON
    }
    
    @TileEntityMenuClass
    inner class PulverizerMenu : GlobalTileEntityMenu() {
        
        private val mainProgress = ProgressArrowItem()
        private val pulverizerProgress = PulverizerProgressItem()
        
        private val sideConfigGui = SideConfigMenu(
            this@Pulverizer,
            listOf(
                itemHolder.getNetworkedInventory(inputInv) to "inventory.nova.input",
                itemHolder.getNetworkedInventory(outputInv) to "inventory.nova.output"
            ),
            ::openWindow
        )
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s u # # # # e |",
                "| i # , # o a e |",
                "| c # # # # # e |",
                "3 - - - - - - - 4")
            .addIngredient('i', inputInv)
            .addIngredient('o', outputInv)
            .addIngredient(',', mainProgress)
            .addIngredient('c', pulverizerProgress)
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('e', EnergyBar(3, energyHolder))
            .build()
        
        init {
            updateProgress()
        }
        
        fun updateProgress() {
            val recipeTime = currentRecipe?.time ?: 0
            val percentage = if (timeLeft == 0) 0.0 else (recipeTime - timeLeft).toDouble() / recipeTime.toDouble()
            mainProgress.percentage = percentage
            pulverizerProgress.percentage = percentage
        }
        
    }
    
}