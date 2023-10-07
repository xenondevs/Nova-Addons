package xyz.xenondevs.nova.addon.machines.tileentity.mob

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.builder.ItemBuilder
import xyz.xenondevs.invui.item.builder.setDisplayName
import xyz.xenondevs.nova.addon.machines.registry.Blocks.MOB_KILLER
import xyz.xenondevs.nova.addon.simpleupgrades.ConsumerEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.data.config.entry
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.item.DefaultGuiItems
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.upgrade.Upgradable
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.OpenUpgradesItem
import xyz.xenondevs.nova.ui.VerticalBar
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu
import xyz.xenondevs.nova.util.EntityUtils
import kotlin.math.min

private val MAX_ENERGY = MOB_KILLER.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = MOB_KILLER.config.entry<Long>("energy_per_tick")
private val ENERGY_PER_DAMAGE = MOB_KILLER.config.entry<Long>("energy_per_damage")
private val IDLE_TIME by MOB_KILLER.config.entry<Int>("idle_time")
private val KILL_LIMIT by MOB_KILLER.config.entry<Int>("kill_limit")
private val DAMAGE by MOB_KILLER.config.entry<Double>("damage")
private val MIN_RANGE = MOB_KILLER.config.entry<Int>("range", "min")
private val MAX_RANGE = MOB_KILLER.config.entry<Int>("range", "max")
private val DEFAULT_RANGE by MOB_KILLER.config.entry<Int>("range", "default")

class MobKiller(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState), Upgradable {
    
    override val upgradeHolder = getUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.RANGE)
    override val energyHolder = ConsumerEnergyHolder(this, MAX_ENERGY, ENERGY_PER_TICK, ENERGY_PER_DAMAGE, upgradeHolder) { createSideConfig(NetworkConnectionType.INSERT) }
    private val fakePlayer = EntityUtils.createFakePlayer(location).bukkitEntity
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
                
                val killLimit = min((energyHolder.energy / energyHolder.specialEnergyConsumption).toInt(), KILL_LIMIT)
                
                location.world!!.entities
                    .asSequence()
                    .filterIsInstance<Mob>()
                    .filter { it.location in region && ProtectionManager.canHurtEntity(this, it, null).get() }
                    .take(killLimit)
                    .forEach { entity ->
                        energyHolder.energy -= energyHolder.specialEnergyConsumption
                        entity.damage(DAMAGE, fakePlayer)
                    }
            }
        }
        
        menuContainer.forEachMenu<MobKillerMenu> { it.idleBar.percentage = timePassed / maxIdleTime.toDouble() }
    }
    
    @TileEntityMenuClass
    inner class MobKillerMenu(player: Player) : IndividualTileEntityMenu(player) {
        
        private val sideConfigGui = SideConfigMenu(
            this@MobKiller,
            ::openWindow
        )
        
        val idleBar = object : VerticalBar(3) {
            override val barItem = DefaultGuiItems.BAR_GREEN
            override fun modifyItemBuilder(itemBuilder: ItemBuilder) =
                itemBuilder.setDisplayName(Component.translatable(
                    "menu.machines.mob_killer.idle",
                    NamedTextColor.GRAY,
                    Component.text(maxIdleTime - timePassed)
                ))
        }
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s # i # e # p |",
                "| r # i # e # n |",
                "| u # i # e # m |",
                "3 - - - - - - - 4")
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('r', region.createVisualizeRegionItem(player))
            .addIngredient('p', region.increaseSizeItem)
            .addIngredient('m', region.decreaseSizeItem)
            .addIngredient('n', region.displaySizeItem)
            .addIngredient('e', EnergyBar(3, energyHolder))
            .addIngredient('i', idleBar)
            .build()
        
    }
    
}