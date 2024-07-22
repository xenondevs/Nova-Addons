package xyz.xenondevs.nova.addon.machines.tileentity.mob

import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.builder.ItemBuilder
import xyz.xenondevs.invui.item.builder.setDisplayName
import xyz.xenondevs.nova.addon.machines.registry.Blocks.MOB_KILLER
import xyz.xenondevs.nova.addon.machines.util.efficiencyDividedValue
import xyz.xenondevs.nova.addon.machines.util.maxIdleTime
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedRegion
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.item.DefaultGuiItems
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.VerticalBar
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.EntityUtils
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.region.Region
import kotlin.math.min

private val MAX_ENERGY = MOB_KILLER.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = MOB_KILLER.config.entry<Long>("energy_per_tick")
private val ENERGY_PER_DAMAGE = MOB_KILLER.config.entry<Long>("energy_per_damage")
private val IDLE_TIME = MOB_KILLER.config.entry<Int>("idle_time")
private val KILL_LIMIT by MOB_KILLER.config.entry<Int>("kill_limit")
private val DAMAGE by MOB_KILLER.config.entry<Double>("damage")
private val MIN_RANGE = MOB_KILLER.config.entry<Int>("range", "min")
private val MAX_RANGE = MOB_KILLER.config.entry<Int>("range", "max")
private val DEFAULT_RANGE by MOB_KILLER.config.entry<Int>("range", "default")

class MobKiller(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.RANGE)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT)
    private val fakePlayer = EntityUtils.createFakePlayer(pos.location).bukkitEntity
    
    private val region = storedRegion("region.default", MIN_RANGE, MAX_RANGE, DEFAULT_RANGE, upgradeHolder) { size ->
        Region.inFrontOf(this, size, size, 4, -1)
    }
    
    private val energyPerTick by efficiencyDividedValue(ENERGY_PER_TICK, upgradeHolder)
    private val energyPerDamage by efficiencyDividedValue(ENERGY_PER_DAMAGE, upgradeHolder)
    private val maxIdleTime by maxIdleTime(IDLE_TIME, upgradeHolder)
    
    private var timePassed = 0
    
    override fun handleTick() {
        if (energyHolder.energy >= energyPerTick) {
            energyHolder.energy -= energyPerTick
            
            if (timePassed++ >= maxIdleTime) {
                timePassed = 0
                
                val killLimit = min((energyHolder.energy / energyPerDamage).toInt(), KILL_LIMIT)
                
                pos.world.entities
                    .asSequence()
                    .filterIsInstance<Mob>()
                    .filter { it.location in region && runBlocking { ProtectionManager.canHurtEntity(this@MobKiller, it, null) } } // TODO non-blocking
                    .take(killLimit)
                    .forEach { entity ->
                        energyHolder.energy -= energyPerDamage
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