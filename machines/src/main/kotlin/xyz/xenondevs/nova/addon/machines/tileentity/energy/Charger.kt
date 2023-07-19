package xyz.xenondevs.nova.addon.machines.tileentity.energy

import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.data.config.NovaConfig
import xyz.xenondevs.nova.data.config.configReloadable
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.item.behavior.Chargeable
import xyz.xenondevs.nova.addon.machines.registry.Blocks.CHARGER
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.item.holder.NovaItemHolder
import xyz.xenondevs.nova.tileentity.network.item.inventory.NetworkedVirtualInventory
import xyz.xenondevs.nova.tileentity.upgrade.Upgradable
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.OpenUpgradesItem
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu
import xyz.xenondevs.nova.util.item.novaItem
import xyz.xenondevs.nova.addon.simpleupgrades.ConsumerEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes

private val MAX_ENERGY = configReloadable { NovaConfig[CHARGER].getLong("capacity") }
private val ENERGY_PER_TICK = configReloadable { NovaConfig[CHARGER].getLong("charge_speed") }

class Charger(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState), Upgradable {
    
    private val inventory = getInventory("inventory", 1, ::handleInventoryUpdate)
    override val upgradeHolder = getUpgradeHolder(UpgradeTypes.ENERGY, UpgradeTypes.SPEED)
    override val energyHolder = ConsumerEnergyHolder(this, MAX_ENERGY, ENERGY_PER_TICK, null, upgradeHolder) { createSideConfig(NetworkConnectionType.INSERT) }
    override val itemHolder = NovaItemHolder(this, inventory to NetworkConnectionType.BUFFER) { createSideConfig(NetworkConnectionType.BUFFER) }
    
    private fun handleInventoryUpdate(event: ItemPreUpdateEvent) {
        if (event.isAdd || event.isSwap) {
            // cancel adding non-chargeable or fully charged items
            val newStack = event.newItem!!
            val chargeable = newStack.novaItem?.getBehavior(Chargeable::class)
            event.isCancelled = chargeable == null || chargeable.getEnergy(newStack) >= chargeable.options.maxEnergy
        } else if (event.updateReason == NetworkedVirtualInventory.UPDATE_REASON) {
            // prevent item networks from removing not fully charged items
            val previousStack = event.previousItem
            val chargeable = previousStack?.novaItem?.getBehavior(Chargeable::class) ?: return
            event.isCancelled = chargeable.getEnergy(previousStack) < chargeable.options.maxEnergy
        }
    }
    
    override fun handleTick() {
        val currentItem = inventory.getUnsafeItem(0)
        val chargeable = currentItem?.novaItem?.getBehavior(Chargeable::class)
        if (chargeable != null) {
            val itemCharge = chargeable.getEnergy(currentItem)
            if (itemCharge < chargeable.options.maxEnergy) {
                val chargeEnergy = minOf(energyHolder.energyConsumption, energyHolder.energy, chargeable.options.maxEnergy - itemCharge)
                chargeable.addEnergy(currentItem, chargeEnergy)
                energyHolder.energy -= chargeEnergy
                
                inventory.notifyWindows()
            }
        }
    }
    
    @TileEntityMenuClass
    inner class ChargerMenu : GlobalTileEntityMenu() {
        
        private val sideConfigGui = SideConfigMenu(
            this@Charger,
            listOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default"),
            ::openWindow
        )
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s # # # # # e |",
                "| u # # i # # e |",
                "| # # # # # # e |",
                "3 - - - - - - - 4")
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('i', inventory)
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('e', EnergyBar(3, energyHolder))
            .build()
        
    }
    
}