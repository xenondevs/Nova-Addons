package xyz.xenondevs.nova.addon.machines.tileentity.agriculture

import net.minecraft.world.entity.projectile.FishingHook
import net.minecraft.world.level.storage.loot.BuiltInLootTables
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.gui.IdleBar
import xyz.xenondevs.nova.addon.machines.registry.Blocks.AUTO_FISHER
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.util.energyConsumption
import xyz.xenondevs.nova.addon.machines.util.maxIdleTime
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.GlobalValues
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.addIngredient
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.EntityUtils
import xyz.xenondevs.nova.util.MINECRAFT_SERVER
import xyz.xenondevs.nova.util.item.damage
import xyz.xenondevs.nova.util.serverLevel
import xyz.xenondevs.nova.util.toVec3
import xyz.xenondevs.nova.util.unwrap
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import net.minecraft.world.item.ItemStack as MojangStack

private val MAX_ENERGY = AUTO_FISHER.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = AUTO_FISHER.config.entry<Long>("energy_per_tick")
private val IDLE_TIME = AUTO_FISHER.config.entry<Int>("idle_time")

class AutoFisher(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val inventory = storedInventory("inventory", 12, ::handleInventoryUpdate)
    private val fishingRodInventory = storedInventory("fishingRod", 1, ::handleFishingRodInventoryUpdate)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT)
    private val itemHolder = storedItemHolder(inventory to EXTRACT, fishingRodInventory to INSERT)
    private val fakePlayer = EntityUtils.createFakePlayer(pos.location)
    
    private val energyPerTick by energyConsumption(ENERGY_PER_TICK, upgradeHolder)
    private val maxIdleTimeProvider = maxIdleTime(IDLE_TIME, upgradeHolder)
    private val mxIdleTime by maxIdleTimeProvider
    
    private val timePassedProvider = mutableProvider(0)
    private var timePassed by timePassedProvider
    
    override fun handleTick() {
        if (energyHolder.energy >= energyPerTick && !fishingRodInventory.isEmpty && pos.below.block.type == Material.WATER) {
            if (!GlobalValues.DROP_EXCESS_ON_GROUND && !inventory.hasEmptySlot())
                return
            
            energyHolder.energy -= energyPerTick
            
            timePassed++
            if (timePassed >= mxIdleTime) {
                timePassed = 0
                fish()
            }
        }
    }
    
    private fun fish() {
        // Bukkit's LootTable API isn't applicable in this use case
        
        val rodItem = fishingRodInventory.getItem(0)!!
        val luck = rodItem.enchantments[Enchantment.LUCK_OF_THE_SEA] ?: 0
        
        // the fake fishing hook is required for the "in_open_water" check as the
        // fishing location affects the loot table
        val fakeFishingHook = FishingHook(fakePlayer, pos.world.serverLevel, luck, 0)
        
        val params = LootParams.Builder(pos.world.serverLevel)
            .withParameter(LootContextParams.ORIGIN, pos.location.toVec3())
            .withParameter(LootContextParams.TOOL, rodItem.unwrap().copy())
            .withParameter(LootContextParams.THIS_ENTITY, fakeFishingHook)
            .withLuck(luck.toFloat())
            .create(LootContextParamSets.FISHING)
        
        MINECRAFT_SERVER.reloadableRegistries().getLootTable(BuiltInLootTables.FISHING).getRandomItems(params).asSequence()
            .map(MojangStack::asBukkitMirror)
            .forEach {
                val leftover = inventory.addItem(SELF_UPDATE_REASON, it)
                if (GlobalValues.DROP_EXCESS_ON_GROUND && leftover != 0) {
                    it.amount = leftover
                    pos.world.dropItemNaturally(pos.add(0, 1, 0).location, it)
                }
            }
        
        // damage the rod item
        useRod()
    }
    
    private fun useRod() {
        fishingRodInventory.modifyItem(SELF_UPDATE_REASON, 0) { it?.damage(1, pos.world) }
    }
    
    private fun handleInventoryUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = event.updateReason != SELF_UPDATE_REASON && event.isAdd
    }
    
    private fun handleFishingRodInventoryUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = event.isAdd && event.newItem?.type != Material.FISHING_ROD
    }
    
    @TileEntityMenuClass
    inner class AutoFisherMenu : GlobalTileEntityMenu() {
        
        private val sideConfigGui = SideConfigMenu(
            this@AutoFisher,
            mapOf(
                itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default",
                itemHolder.getNetworkedInventory(fishingRodInventory) to "inventory.machines.fishing_rod"
            ),
            ::openWindow
        )
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s u # # f p e |",
                "| i i i i # p e |",
                "| i i i i # p e |",
                "3 - - - - - - - 4")
            .addIngredient('i', inventory)
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('f', fishingRodInventory, GuiItems.FISHING_ROD_PLACEHOLDER)
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('e', EnergyBar(3, energyHolder))
            .addIngredient('p', IdleBar(3, "menu.machines.auto_fisher.idle", timePassedProvider, maxIdleTimeProvider))
            .build()
        
    }
    
}