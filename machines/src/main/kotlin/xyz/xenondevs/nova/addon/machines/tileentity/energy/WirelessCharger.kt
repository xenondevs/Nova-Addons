package xyz.xenondevs.nova.addon.machines.tileentity.energy

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.addon.machines.registry.Blocks.WIRELESS_CHARGER
import xyz.xenondevs.nova.addon.machines.util.speedMultipliedValue
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedRegion
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.item.novaItem
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.item.behavior.Chargeable
import xyz.xenondevs.nova.world.region.Region
import xyz.xenondevs.nova.world.region.VisualRegion

private val MAX_ENERGY = WIRELESS_CHARGER.config.entry<Long>("capacity")
private val CHARGE_SPEED = WIRELESS_CHARGER.config.entry<Long>("charge_speed")
private val MIN_RANGE = WIRELESS_CHARGER.config.entry<Int>("range", "min")
private val MAX_RANGE = WIRELESS_CHARGER.config.entry<Int>("range", "max")
private val DEFAULT_RANGE by WIRELESS_CHARGER.config.entry<Int>("range", "default")

class WirelessCharger(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.ENERGY, UpgradeTypes.RANGE)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT)
    
    private val chargePerTick by speedMultipliedValue(CHARGE_SPEED, upgradeHolder)
    private val region = storedRegion("region.default", MIN_RANGE, MAX_RANGE, DEFAULT_RANGE, upgradeHolder) { Region.surrounding(pos, it) }
    
    private var players: List<Player> = emptyList()
    private var findPlayersCooldown = 0
    
    override fun handleTick() {
        var energyTransferred: Long
        
        if (--findPlayersCooldown <= 0) {
            findPlayersCooldown = 100
            players = pos.world.players.filter { it.location in region }
        }
        
        if (energyHolder.energy != 0L && players.isNotEmpty()) {
            playerLoop@ for (player in players) {
                energyTransferred = 0L
                if (energyHolder.energy == 0L)
                    break
                for (itemStack in player.inventory) {
                    energyTransferred += chargeItemStack(energyTransferred, itemStack)
                    if (energyHolder.energy == 0L)
                        break@playerLoop
                    if (energyTransferred >= chargePerTick)
                        break
                }
            }
        }
    }
    
    private fun chargeItemStack(alreadyTransferred: Long, itemStack: ItemStack?): Long {
        val chargeable = itemStack?.novaItem?.getBehaviorOrNull<Chargeable>()
        
        if (chargeable != null) {
            val maxEnergy = chargeable.maxEnergy
            val currentEnergy = chargeable.getEnergy(itemStack)
            
            val energyToTransfer = minOf(chargePerTick - alreadyTransferred, maxEnergy - currentEnergy, energyHolder.energy)
            energyHolder.energy -= energyToTransfer
            chargeable.addEnergy(itemStack, energyToTransfer)
            
            return energyToTransfer
        }
        
        return 0
    }
    
    override fun handleDisable() {
        super.handleDisable()
        VisualRegion.removeRegion(uuid)
    }
    
    @TileEntityMenuClass
    inner class WirelessChargerMenu(player: Player) : IndividualTileEntityMenu(player) {
        
        private val sideConfigGui = SideConfigMenu(
            this@WirelessCharger,
            ::openWindow
        )
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s # # e # # p |",
                "| v # # e # # n |",
                "| u # # e # # m |",
                "3 - - - - - - - 4")
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('v', region.createVisualizeRegionItem(player))
            .addIngredient('p', region.increaseSizeItem)
            .addIngredient('m', region.decreaseSizeItem)
            .addIngredient('n', region.displaySizeItem)
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('e', EnergyBar(3, energyHolder))
            .build()
        
    }
    
}