package xyz.xenondevs.nova.addon.machines.tileentity.mob

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Animals
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.item.builder.ItemBuilder
import xyz.xenondevs.invui.item.builder.setDisplayName
import xyz.xenondevs.nova.data.config.NovaConfig
import xyz.xenondevs.nova.data.config.configReloadable
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.item.DefaultGuiItems
import xyz.xenondevs.nova.addon.machines.registry.Blocks.BREEDER
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.item.holder.NovaItemHolder
import xyz.xenondevs.nova.tileentity.upgrade.Upgradable
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.OpenUpgradesItem
import xyz.xenondevs.nova.ui.VerticalBar
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.getSurroundingChunks
import xyz.xenondevs.nova.util.item.FoodUtils
import xyz.xenondevs.nova.util.item.canBredNow
import xyz.xenondevs.nova.util.item.genericMaxHealth
import xyz.xenondevs.nova.world.region.VisualRegion
import xyz.xenondevs.nova.addon.simpleupgrades.ConsumerEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import kotlin.math.min

private val MAX_ENERGY = configReloadable { NovaConfig[BREEDER].getLong("capacity") }
private val ENERGY_PER_TICK = configReloadable { NovaConfig[BREEDER].getLong("energy_per_tick") }
private val ENERGY_PER_BREED = configReloadable { NovaConfig[BREEDER].getLong("energy_per_breed") }
private val IDLE_TIME by configReloadable { NovaConfig[BREEDER].getInt("idle_time") }
private val BREED_LIMIT by configReloadable { NovaConfig[BREEDER].getInt("breed_limit") }
private val MIN_RANGE = configReloadable { NovaConfig[BREEDER].getInt("range.min") }
private val MAX_RANGE = configReloadable { NovaConfig[BREEDER].getInt("range.max") }
private val DEFAULT_RANGE by configReloadable { NovaConfig[BREEDER].getInt("range.default") }

class Breeder(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState), Upgradable {
    
    private val inventory = getInventory("inventory", 9, ::handleInventoryUpdate)
    override val upgradeHolder = getUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.RANGE)
    override val energyHolder = ConsumerEnergyHolder(this, MAX_ENERGY, ENERGY_PER_TICK, ENERGY_PER_BREED, upgradeHolder) { createSideConfig(NetworkConnectionType.INSERT) }
    override val itemHolder = NovaItemHolder(this, inventory to NetworkConnectionType.INSERT) { createSideConfig(NetworkConnectionType.INSERT, BlockSide.FRONT) }
    
    private val region = getUpgradableRegion(UpgradeTypes.RANGE, MIN_RANGE, MAX_RANGE, DEFAULT_RANGE) { getBlockFrontRegion(it, it, 4, -1) }
    
    private var timePassed = 0
    private var maxIdleTime = 0
    
    init {
        reload()
    }
    
    override fun reload() {
        super.reload()
        
        maxIdleTime = (IDLE_TIME / upgradeHolder.getValue(UpgradeTypes.SPEED)).toInt()
        if (timePassed > maxIdleTime) timePassed = maxIdleTime
    }
    
    override fun handleTick() {
        if (energyHolder.energy >= energyHolder.energyConsumption) {
            energyHolder.energy -= energyHolder.energyConsumption
            
            if (timePassed++ >= maxIdleTime) {
                timePassed = 0
                
                val breedableEntities =
                    location
                        .chunk
                        .getSurroundingChunks(1, includeCurrent = true, ignoreUnloaded = true)
                        .flatMap { it.entities.asList() }
                        .filterIsInstance<Animals>()
                        .filter { it.canBredNow && it.location in region }
                
                var breedsLeft = min((energyHolder.energy / energyHolder.specialEnergyConsumption).toInt(), BREED_LIMIT)
                for (animal in breedableEntities) {
                    val success = if (FoodUtils.requiresHealing(animal)) tryHeal(animal)
                    else tryBreed(animal)
                    
                    if (success) {
                        breedsLeft--
                        energyHolder.energy -= energyHolder.specialEnergyConsumption
                        if (breedsLeft == 0) break
                    }
                }
            }
        }
        
        menuContainer.forEachMenu<BreederMenu> { it.idleBar.percentage = timePassed / maxIdleTime.toDouble() }
    }
    
    private fun tryHeal(animal: Animals): Boolean {
        for ((index, item) in inventory.items.withIndex()) {
            if (item == null) continue
            
            val healAmount = FoodUtils.getHealAmount(animal, item.type)
            if (healAmount > 0) {
                animal.health = min(animal.health + healAmount, animal.genericMaxHealth)
                inventory.addItemAmount(SELF_UPDATE_REASON, index, -1)
                
                val remains = FoodUtils.getItemRemains(item.type)
                if (remains != null)
                    inventory.setItem(SELF_UPDATE_REASON, index, ItemStack(remains))
                
                return true
            }
        }
        
        return false
    }
    
    private fun tryBreed(animal: Animals): Boolean {
        for ((index, item) in inventory.items.withIndex()) {
            if (item == null) continue
            
            if (FoodUtils.canUseBreedFood(animal, item.type)) {
                animal.loveModeTicks = 600
                inventory.addItemAmount(SELF_UPDATE_REASON, index, -1)
                
                val remains = FoodUtils.getItemRemains(item.type)
                if (remains != null)
                    inventory.setItem(SELF_UPDATE_REASON, index, ItemStack(remains))
                
                return true
            }
        }
        
        return false
    }
    
    private fun handleInventoryUpdate(event: ItemPreUpdateEvent) {
        if (event.updateReason != SELF_UPDATE_REASON && !event.isRemove && !FoodUtils.isFood(event.newItem!!.type))
            event.isCancelled = true
    }
    
    override fun handleRemoved(unload: Boolean) {
        super.handleRemoved(unload)
        VisualRegion.removeRegion(uuid)
    }
    
    @TileEntityMenuClass
    inner class BreederMenu(player: Player) : IndividualTileEntityMenu(player) {
        
        private val sideConfigGui = SideConfigMenu(
            this@Breeder,
            listOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default"),
            ::openWindow
        )
        
        val idleBar = object : VerticalBar(3) {
            override val barItem = DefaultGuiItems.BAR_GREEN
            override fun modifyItemBuilder(itemBuilder: ItemBuilder) =
                itemBuilder.setDisplayName(Component.translatable(
                    "menu.machines.breeder.idle",
                    NamedTextColor.GRAY,
                    Component.text(maxIdleTime - timePassed)
                ))
        }
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s p i i i b e |",
                "| r n i i i b e |",
                "| u m i i i b e |",
                "3 - - - - - - - 4")
            .addIngredient('i', inventory)
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('r', region.createVisualizeRegionItem(player))
            .addIngredient('p', region.increaseSizeItem)
            .addIngredient('m', region.decreaseSizeItem)
            .addIngredient('n', region.displaySizeItem)
            .addIngredient('e', EnergyBar(3, energyHolder))
            .addIngredient('b', idleBar)
            .build()
        
    }
    
}