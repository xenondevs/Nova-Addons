package xyz.xenondevs.nova.addon.machines.tileentity.agriculture

import net.md_5.bungee.api.chat.TranslatableComponent
import net.minecraft.world.entity.projectile.FishingHook
import net.minecraft.world.level.storage.loot.BuiltInLootTables
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.Vec3
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.item.builder.ItemBuilder
import xyz.xenondevs.nova.addon.machines.registry.GuiMaterials
import xyz.xenondevs.nova.data.config.GlobalValues
import xyz.xenondevs.nova.data.config.NovaConfig
import xyz.xenondevs.nova.data.config.configReloadable
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.item.DefaultGuiItems
import xyz.xenondevs.nova.addon.machines.registry.Blocks.AUTO_FISHER
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.item.holder.NovaItemHolder
import xyz.xenondevs.nova.tileentity.upgrade.Upgradable
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.OpenUpgradesItem
import xyz.xenondevs.nova.ui.VerticalBar
import xyz.xenondevs.nova.ui.addIngredient
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.EntityUtils
import xyz.xenondevs.nova.util.MINECRAFT_SERVER
import xyz.xenondevs.nova.util.bukkitMirror
import xyz.xenondevs.nova.util.item.DamageableUtils
import xyz.xenondevs.nova.util.nmsCopy
import xyz.xenondevs.nova.util.serverLevel
import xyz.xenondevs.nova.addon.simpleupgrades.ConsumerEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import net.minecraft.world.item.ItemStack as MojangStack

private val MAX_ENERGY = configReloadable { NovaConfig[AUTO_FISHER].getLong("capacity") }
private val ENERGY_PER_TICK = configReloadable { NovaConfig[AUTO_FISHER].getLong("energy_per_tick") }
private val IDLE_TIME by configReloadable { NovaConfig[AUTO_FISHER].getInt("idle_time") }

class AutoFisher(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState), Upgradable {
    
    private val inventory = getInventory("inventory", 12, ::handleInventoryUpdate)
    private val fishingRodInventory = getInventory("fishingRod", 1, ::handleFishingRodInventoryUpdate)
    override val upgradeHolder = getUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    override val energyHolder = ConsumerEnergyHolder(this, MAX_ENERGY, ENERGY_PER_TICK, null, upgradeHolder) { createSideConfig(NetworkConnectionType.INSERT, BlockSide.BOTTOM) }
    override val itemHolder = NovaItemHolder(
        this,
        inventory to NetworkConnectionType.EXTRACT,
        fishingRodInventory to NetworkConnectionType.INSERT
    ) { createSideConfig(NetworkConnectionType.BUFFER, BlockSide.BOTTOM) }
    
    private var timePassed = 0
    private var maxIdleTime = 0
    
    private val waterBlock = location.clone().subtract(0.0, 1.0, 0.0).block
    private val level = world.serverLevel
    private val position = Vec3(centerLocation.x, location.y - 0.5, centerLocation.z)
    private val itemDropLocation = location.clone().add(0.0, 1.0, 0.0)
    private val fakePlayer = EntityUtils.createFakePlayer(location)
    
    init {
        reload()
    }
    
    override fun reload() {
        super.reload()
        maxIdleTime = (IDLE_TIME / upgradeHolder.getValue(UpgradeTypes.SPEED)).toInt()
        if (timePassed > maxIdleTime) timePassed = maxIdleTime
    }
    
    override fun handleTick() {
        if (energyHolder.energy >= energyHolder.energyConsumption && !fishingRodInventory.isEmpty && waterBlock.type == Material.WATER) {
            if (!GlobalValues.DROP_EXCESS_ON_GROUND && !inventory.hasEmptySlot()) return
            
            energyHolder.energy -= energyHolder.energyConsumption
            
            timePassed++
            if (timePassed >= maxIdleTime) {
                timePassed = 0
                fish()
            }
            
            menuContainer.forEachMenu<AutoFisherMenu> { it.idleBar.percentage = timePassed.toDouble() / maxIdleTime.toDouble() }
        }
    }
    
    private fun fish() {
        // Bukkit's LootTable API isn't applicable in this use case
        
        val rodItem = fishingRodInventory.getItem(0)!!
        val luck = rodItem.enchantments[Enchantment.LUCK] ?: 0
        
        // the fake fishing hook is required for the "in_open_water" check as the
        // fishing location affects the loot table
        val fakeFishingHook = FishingHook(fakePlayer, level, luck, 0)
        
        val params = LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, position)
            .withParameter(LootContextParams.TOOL, rodItem.nmsCopy)
            .withParameter(LootContextParams.THIS_ENTITY, fakeFishingHook)
            .withLuck(luck.toFloat())
            .create(LootContextParamSets.FISHING)
        
        MINECRAFT_SERVER.lootData.getLootTable(BuiltInLootTables.FISHING).getRandomItems(params).asSequence()
            .map(MojangStack::bukkitMirror)
            .forEach {
                val leftover = inventory.addItem(SELF_UPDATE_REASON, it)
                if (GlobalValues.DROP_EXCESS_ON_GROUND && leftover != 0) {
                    it.amount = leftover
                    world.dropItemNaturally(itemDropLocation, it)
                }
            }
        
        // damage the rod item
        useRod()
    }
    
    private fun useRod() {
        val itemStack = fishingRodInventory.getItem(0)!!
        fishingRodInventory.setItem(SELF_UPDATE_REASON, 0, DamageableUtils.damageItem(itemStack))
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
            listOf(
                itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default",
                itemHolder.getNetworkedInventory(fishingRodInventory) to "inventory.machines.fishing_rod"
            ),
            ::openWindow
        )
        
        val idleBar = object : VerticalBar(height = 3) {
            override val barItem = DefaultGuiItems.BAR_GREEN
            override fun modifyItemBuilder(itemBuilder: ItemBuilder) =
                itemBuilder.setDisplayName(TranslatableComponent("menu.machines.auto_fisher.idle", maxIdleTime - timePassed))
        }
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s u # # f p e |",
                "| i i i i # p e |",
                "| i i i i # p e |",
                "3 - - - - - - - 4")
            .addIngredient('i', inventory)
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('f', fishingRodInventory, GuiMaterials.FISHING_ROD_PLACEHOLDER)
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('e', EnergyBar(3, energyHolder))
            .addIngredient('p', idleBar)
            .build()
        
    }
    
}