package xyz.xenondevs.nova.addon.machines.tileentity.energy

import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.registry.Blocks.CHARGER
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.util.speedMultipliedValue
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import org.bukkit.block.Block
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.BUFFER
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.inventory.NetworkedVirtualInventory
import xyz.xenondevs.nova.world.item.behavior.Chargeable
import xyz.xenondevs.nova.world.item.getBehaviorOrNull
import xyz.xenondevs.nova.world.item.itemType

private val MAX_ENERGY = CHARGER.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = CHARGER.config.entry<Long>("charge_speed")

class Charger(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val inventory = storedInventory("inventory", 1, ::handleInventoryUpdate)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.ENERGY, UpgradeTypes.SPEED)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT)
    private val itemHolder = storedItemHolder(inventory to BUFFER)
    
    private val energyPerTick by speedMultipliedValue(ENERGY_PER_TICK, upgradeHolder)
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.GENERIC_1X1_WITH_BAR) {
        upperGui by gui(
            "s . . . . . . . e",
            "u . . . i . . . e",
            ". . . . . . . . e",
        ) {
            's' by openSideConfigItem(mapOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default"))
            'i' by inventory
            'u' by openUpgradesItem(upgradeHolder)
            'e' by energyBar(energyHolder)
        }
    }
    
    private fun handleInventoryUpdate(event: ItemPreUpdateEvent) {
        if (event.isAdd || event.isSwap) {
            // cancel adding non-chargeable or fully charged items
            val newStack = event.newItem!!
            val chargeable = newStack.itemType.getBehaviorOrNull<Chargeable>()
            event.isCancelled = chargeable == null || chargeable.getEnergy(newStack) >= chargeable.maxEnergy
        } else if (event.updateReason == NetworkedVirtualInventory.UPDATE_REASON) {
            // prevent item networks from removing not fully charged items
            val previousStack = event.previousItem
            val chargeable = previousStack?.itemType?.getBehaviorOrNull<Chargeable>() ?: return
            event.isCancelled = chargeable.getEnergy(previousStack) < chargeable.maxEnergy
        }
    }
    
    override fun handleTick() {
        val currentItem = inventory.getUnsafeItem(0)
        val chargeable = currentItem?.itemType?.getBehaviorOrNull<Chargeable>()
        if (chargeable != null) {
            val itemCharge = chargeable.getEnergy(currentItem)
            if (itemCharge < chargeable.maxEnergy) {
                val chargeEnergy = minOf(energyPerTick, energyHolder.energy, chargeable.maxEnergy - itemCharge)
                chargeable.addEnergy(currentItem, chargeEnergy)
                energyHolder.energy -= chargeEnergy
                
                inventory.notifyWindows()
            }
        }
    }
    
}
