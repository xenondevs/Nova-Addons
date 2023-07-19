package xyz.xenondevs.nova.addon.machines.tileentity.energy

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.data.config.NovaConfig
import xyz.xenondevs.nova.data.config.configReloadable
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.item.behavior.Chargeable
import xyz.xenondevs.nova.addon.machines.registry.Blocks.WIRELESS_CHARGER
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.upgrade.Upgradable
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.OpenUpgradesItem
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu
import xyz.xenondevs.nova.util.item.novaItem
import xyz.xenondevs.nova.world.region.VisualRegion
import xyz.xenondevs.nova.addon.simpleupgrades.ConsumerEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes

private val MAX_ENERGY = configReloadable { NovaConfig[WIRELESS_CHARGER].getLong("capacity") }
private val CHARGE_SPEED = configReloadable { NovaConfig[WIRELESS_CHARGER].getLong("charge_speed") }
private val MIN_RANGE = configReloadable { NovaConfig[WIRELESS_CHARGER].getInt("range.min") }
private val MAX_RANGE = configReloadable { NovaConfig[WIRELESS_CHARGER].getInt("range.max") }
private val DEFAULT_RANGE by configReloadable { NovaConfig[WIRELESS_CHARGER].getInt("range.default") }

class WirelessCharger(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState), Upgradable {
    
    override val upgradeHolder = getUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.ENERGY, UpgradeTypes.RANGE)
    override val energyHolder = ConsumerEnergyHolder(this, MAX_ENERGY, CHARGE_SPEED, null, upgradeHolder) { createSideConfig(NetworkConnectionType.INSERT) }
    
    private val region = getUpgradableRegion(UpgradeTypes.RANGE, MIN_RANGE, MAX_RANGE, DEFAULT_RANGE, ::getSurroundingRegion)
    
    private var players: List<Player> = emptyList()
    private var findPlayersCooldown = 0
    
    override fun handleTick() {
        var energyTransferred: Long
        
        if (--findPlayersCooldown <= 0) {
            findPlayersCooldown = 100
            players = world.players.filter { it.location in region }
        }
        
        if (energyHolder.energy != 0L && players.isNotEmpty()) {
            playerLoop@ for (player in players) {
                energyTransferred = 0L
                if (energyHolder.energy == 0L) break
                for (itemStack in player.inventory) {
                    energyTransferred += chargeItemStack(energyTransferred, itemStack)
                    if (energyHolder.energy == 0L) break@playerLoop
                    if (energyTransferred == energyHolder.energyConsumption) break
                }
            }
        }
    }
    
    private fun chargeItemStack(alreadyTransferred: Long, itemStack: ItemStack?): Long {
        val chargeable = itemStack?.novaItem?.getBehavior(Chargeable::class)
        
        if (chargeable != null) {
            val maxEnergy = chargeable.options.maxEnergy
            val currentEnergy = chargeable.getEnergy(itemStack)
            
            val energyToTransfer = minOf(energyHolder.energyConsumption - alreadyTransferred, maxEnergy - currentEnergy, energyHolder.energy)
            energyHolder.energy -= energyToTransfer
            chargeable.addEnergy(itemStack, energyToTransfer)
            
            return energyToTransfer
        }
        
        return 0
    }
    
    override fun handleRemoved(unload: Boolean) {
        super.handleRemoved(unload)
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