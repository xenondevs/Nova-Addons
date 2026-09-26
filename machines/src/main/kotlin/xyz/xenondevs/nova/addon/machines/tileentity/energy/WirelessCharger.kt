package xyz.xenondevs.nova.addon.machines.tileentity.energy

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.nova.addon.machines.registry.Blocks.WIRELESS_CHARGER
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.util.speedMultipliedValue
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedRegion
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import org.bukkit.block.Block
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.item.behavior.Chargeable
import xyz.xenondevs.nova.world.item.getBehaviorOrNull
import xyz.xenondevs.nova.world.item.itemType
import xyz.xenondevs.nova.world.region.Region
import xyz.xenondevs.nova.world.region.VisualRegion

private val MAX_ENERGY = WIRELESS_CHARGER.config.entry<Long>("capacity")
private val CHARGE_SPEED = WIRELESS_CHARGER.config.entry<Long>("charge_speed")
private val MIN_RANGE = WIRELESS_CHARGER.config.entry<Int>("range", "min")
private val MAX_RANGE = WIRELESS_CHARGER.config.entry<Int>("range", "max")
private val DEFAULT_RANGE by WIRELESS_CHARGER.config.entry<Int>("range", "default")

class WirelessCharger(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.ENERGY, UpgradeTypes.RANGE)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT)
    
    private val chargePerTick by speedMultipliedValue(CHARGE_SPEED, upgradeHolder)
    private val region = storedRegion("region.default", MIN_RANGE, MAX_RANGE, DEFAULT_RANGE, upgradeHolder) { Region.surrounding(pos, it) }
    
    private var players: List<Player> = emptyList()
    private var findPlayersCooldown = 0
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.CENTER_BAR) {
        upperGui by gui(
            "s . . . e . . . p",
            "v . . . e . . . n",
            "u . . . e . . . m",
        ) {
            's' by openSideConfigItem()
            'v' by region.visualizeRegionItem
            'p' by region.increaseSizeItem
            'm' by region.decreaseSizeItem
            'n' by region.displaySizeItem
            'u' by openUpgradesItem(upgradeHolder)
            'e' by energyBar(energyHolder)
        }
    }
    
    override fun handleTick() {
        var energyTransferred: Long
        
        if (--findPlayersCooldown <= 0) {
            findPlayersCooldown = 100
            players = block.world.players.filter { it.location in region }
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
        val chargeable = itemStack?.itemType?.getBehaviorOrNull<Chargeable>()
        
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
    
}
