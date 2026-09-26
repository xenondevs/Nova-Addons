package xyz.xenondevs.nova.addon.machines.tileentity.agriculture

import net.minecraft.world.entity.projectile.FishingHook
import net.minecraft.world.level.storage.loot.BuiltInLootTables
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import org.bukkit.block.Block
import org.bukkit.block.BlockType
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemType
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.dsl.with
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.gui.idleBar
import xyz.xenondevs.nova.addon.machines.registry.Blocks.AUTO_FISHER
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.util.energyConsumption
import xyz.xenondevs.nova.addon.machines.util.maxIdleTime
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.GlobalValues
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.nova.util.EntityUtils
import xyz.xenondevs.nova.util.MINECRAFT_SERVER
import xyz.xenondevs.nova.util.asBukkitMirror
import xyz.xenondevs.nova.util.below
import xyz.xenondevs.nova.util.item.damage
import xyz.xenondevs.nova.util.serverLevel
import xyz.xenondevs.nova.util.toVec3
import xyz.xenondevs.nova.util.unwrap
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.blockType
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.item.itemType
import net.minecraft.world.item.ItemStack as MojangStack

private val MAX_ENERGY = AUTO_FISHER.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = AUTO_FISHER.config.entry<Long>("energy_per_tick")
private val IDLE_TIME = AUTO_FISHER.config.entry<Int>("idle_time")

class AutoFisher(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val inventory = storedInventory("inventory", 12, ::handleInventoryUpdate)
    private val fishingRodInventory = storedInventory("fishingRod", 1, ::handleFishingRodInventoryUpdate)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT)
    private val itemHolder = storedItemHolder(inventory to EXTRACT, fishingRodInventory to INSERT)
    private val fakePlayer = EntityUtils.createFakePlayer(block.location)
    
    private val energyPerTick by energyConsumption(ENERGY_PER_TICK, upgradeHolder)
    private val maxIdleTimeProvider = maxIdleTime(IDLE_TIME, upgradeHolder)
    private val mxIdleTime by maxIdleTimeProvider
    
    private val timePassedProvider = mutableProvider(0)
    private var timePassed by timePassedProvider
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.AUTO_FISHER) {
        upperGui by gui(
            "s u . . . . . p e",
            "i i i i . f . p e",
            "i i i i . . . p e",
        ) {
            'i' by inventory
            's' by openSideConfigItem(
                mapOf(
                    itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default",
                    itemHolder.getNetworkedInventory(fishingRodInventory) to "inventory.machines.fishing_rod"
                )
            )
            'f' by (fishingRodInventory with GuiItems.FISHING_ROD_PLACEHOLDER)
            'u' by openUpgradesItem(upgradeHolder)
            'e' by energyBar(energyHolder)
            'p' by idleBar("menu.machines.auto_fisher.idle", timePassedProvider, maxIdleTimeProvider)
        }
    }
    
    override fun handleTick() {
        if (energyHolder.energy >= energyPerTick && !fishingRodInventory.isEmpty && block.below.blockType == BlockType.WATER) {
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
        val fakeFishingHook = FishingHook(fakePlayer, block.world.serverLevel, luck, 0)
        
        val params = LootParams.Builder(block.world.serverLevel)
            .withParameter(LootContextParams.ORIGIN, block.location.toVec3())
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
                    block.world.dropItemNaturally(block.getRelative(0, 1, 0).location, it)
                }
            }
        
        // damage the rod item
        useRod()
    }
    
    private fun useRod() {
        fishingRodInventory.modifyItem(SELF_UPDATE_REASON, 0) { it?.damage(1, block.world) }
    }
    
    private fun handleInventoryUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = event.updateReason != SELF_UPDATE_REASON && event.isAdd
    }
    
    private fun handleFishingRodInventoryUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = event.isAdd && event.newItem?.itemType != ItemType.FISHING_ROD
    }
    
}
