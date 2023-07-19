package xyz.xenondevs.nova.addon.machines.tileentity.world

import org.bukkit.block.BlockFace
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.data.config.NovaConfig
import xyz.xenondevs.nova.data.config.configReloadable
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.addon.machines.registry.Blocks.BLOCK_PLACER
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.item.holder.NovaItemHolder
import xyz.xenondevs.nova.tileentity.upgrade.Upgradable
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.OpenUpgradesItem
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.advance
import xyz.xenondevs.nova.util.item.isReplaceable
import xyz.xenondevs.nova.util.place
import xyz.xenondevs.nova.world.block.BlockManager
import xyz.xenondevs.nova.world.block.context.BlockPlaceContext
import xyz.xenondevs.nova.world.pos
import xyz.xenondevs.nova.addon.simpleupgrades.ConsumerEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes

private val MAX_ENERGY = configReloadable { NovaConfig[BLOCK_PLACER].getLong("capacity") }
private val ENERGY_PER_PLACE = configReloadable { NovaConfig[BLOCK_PLACER].getLong("energy_per_place") }

class BlockPlacer(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState), Upgradable {
    
    private val inventory = getInventory("inventory", 9) {}
    override val upgradeHolder = getUpgradeHolder(UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    override val energyHolder = ConsumerEnergyHolder(this, MAX_ENERGY, ENERGY_PER_PLACE, null, upgradeHolder) { createSideConfig(NetworkConnectionType.INSERT, BlockSide.FRONT) }
    override val itemHolder = NovaItemHolder(this, inventory to NetworkConnectionType.INSERT) { createSideConfig(NetworkConnectionType.INSERT, BlockSide.FRONT) }
    
    private val placePos = location.clone().advance(getFace(BlockSide.FRONT)).pos
    private val placeBlock = placePos.block
    
    private fun placeBlock(): Boolean {
        for ((index, item) in inventory.items.withIndex()) {
            if (item == null) continue
            
            val ctx = BlockPlaceContext(placePos, item, this, location, ownerUUID, placePos.copy(y = placePos.y - 1), BlockFace.UP)
            if (placePos.block.place(ctx)) {
                inventory.addItemAmount(SELF_UPDATE_REASON, index, -1)
            } else continue
            
        }
        
        return false
    }
    
    override fun handleTick() {
        if (energyHolder.energy >= energyHolder.energyConsumption
            && !inventory.isEmpty
            && placeBlock.type.isReplaceable()
            && BlockManager.getBlockState(placePos) == null
            && ProtectionManager.canPlace(this, inventory.items.firstNotNullOf { it }, placePos.location).get()
        ) {
            if (placeBlock()) energyHolder.energy -= energyHolder.energyConsumption
        }
    }
    
    @TileEntityMenuClass
    inner class BlockPlacerMenu : GlobalTileEntityMenu() {
        
        private val sideConfigGui = SideConfigMenu(
            this@BlockPlacer,
            listOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default"),
            ::openWindow
        )
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s # i i i # e |",
                "| u # i i i # e |",
                "| # # i i i # e |",
                "3 - - - - - - - 4")
            .addIngredient('i', inventory)
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('e', EnergyBar(3, energyHolder))
            .build()
        
    }
    
}