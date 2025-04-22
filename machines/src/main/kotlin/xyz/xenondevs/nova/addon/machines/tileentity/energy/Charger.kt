package xyz.xenondevs.nova.addon.machines.tileentity.energy

import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.registry.Blocks.CHARGER
import xyz.xenondevs.nova.addon.machines.util.speedMultipliedValue
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
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
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.BUFFER
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.inventory.NetworkedVirtualInventory
import xyz.xenondevs.nova.world.item.behavior.Chargeable

private val MAX_ENERGY = CHARGER.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = CHARGER.config.entry<Long>("charge_speed")

class Charger(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val inventory = storedInventory("inventory", 1, ::handleInventoryUpdate)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.ENERGY, UpgradeTypes.SPEED)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT)
    private val itemHolder = storedItemHolder(inventory to BUFFER)
    
    private val energyPerTick by speedMultipliedValue(ENERGY_PER_TICK, upgradeHolder)
    
    private fun handleInventoryUpdate(event: ItemPreUpdateEvent) {
        if (event.isAdd || event.isSwap) {
            // cancel adding non-chargeable or fully charged items
            val newStack = event.newItem!!
            val chargeable = newStack.novaItem?.getBehaviorOrNull(Chargeable::class)
            event.isCancelled = chargeable == null || chargeable.getEnergy(newStack) >= chargeable.maxEnergy
        } else if (event.updateReason == NetworkedVirtualInventory.UPDATE_REASON) {
            // prevent item networks from removing not fully charged items
            val previousStack = event.previousItem
            val chargeable = previousStack?.novaItem?.getBehaviorOrNull(Chargeable::class) ?: return
            event.isCancelled = chargeable.getEnergy(previousStack) < chargeable.maxEnergy
        }
    }
    
    override fun handleTick() {
        val currentItem = inventory.getUnsafeItem(0)
        val chargeable = currentItem?.novaItem?.getBehaviorOrNull(Chargeable::class)
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
    
    @TileEntityMenuClass
    inner class ChargerMenu : GlobalTileEntityMenu() {
        
        private val sideConfigGui = SideConfigMenu(
            this@Charger,
            mapOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default"),
            ::openWindow
        )
        
        override val gui = Gui.builder()
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