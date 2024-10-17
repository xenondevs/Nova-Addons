package xyz.xenondevs.nova.addon.machines.tileentity.world

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.enumSetOf
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.addon.machines.registry.Blocks.BLOCK_PLACER
import xyz.xenondevs.nova.addon.machines.util.efficiencyDividedValue
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.DefaultContextIntentions
import xyz.xenondevs.nova.context.param.DefaultContextParamTypes
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.BlockUtils
import xyz.xenondevs.nova.util.item.isReplaceable
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.state.property.DefaultBlockStateProperties
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.format.WorldDataManager

private val BLOCKED_SIDES = enumSetOf(BlockSide.FRONT)

private val MAX_ENERGY = BLOCK_PLACER.config.entry<Long>("capacity")
private val ENERGY_PER_PLACE = BLOCK_PLACER.config.entry<Long>("energy_per_place")

class BlockPlacer(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val inventory = storedInventory("inventory", 9) {}
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(inventory to INSERT, blockedSides = BLOCKED_SIDES)
    
    private val energyPerPlace by efficiencyDividedValue(ENERGY_PER_PLACE, upgradeHolder)
    
    private val placePos = pos.advance(blockState.getOrThrow(DefaultBlockStateProperties.FACING))
    private val placeBlock = placePos.block
    
    @Volatile
    private var permittedTypes: Set<ItemStack> = emptySet()
    
    override fun handleTick() {
        if (energyHolder.energy >= energyPerPlace
            && !inventory.isEmpty
            && placeBlock.type.isReplaceable()
            && WorldDataManager.getBlockState(placePos) == null
        ) {
            if (placeBlock())
                energyHolder.energy -= energyPerPlace
        }
    }
    
    override fun handleEnableTicking() {
        CoroutineScope(coroutineSupervisor).launch {
            while (true) {
                permittedTypes = inventory.items.asSequence()
                    .filterNotNull()
                    .onEach { it.amount = 1 }
                    .filterTo(HashSet()) { ProtectionManager.canPlace(this@BlockPlacer, it, placePos) }
                delay(50)
            }
        }
    }
    
    private fun placeBlock(): Boolean {
        for ((index, item) in inventory.items.withIndex()) {
            if (item == null)
                continue
            if (item.clone().apply { amount = 1 } !in permittedTypes)
                continue
            
            val ctx = Context.intention(DefaultContextIntentions.BlockPlace)
                .param(DefaultContextParamTypes.BLOCK_POS, placePos)
                .param(DefaultContextParamTypes.BLOCK_ITEM_STACK, item)
                .param(DefaultContextParamTypes.SOURCE_TILE_ENTITY, this)
                .build()
            if (BlockUtils.placeBlock(ctx)) {
                inventory.addItemAmount(SELF_UPDATE_REASON, index, -1)
                return true
            } else continue
        }
        
        return false
    }
    
    @TileEntityMenuClass
    inner class BlockPlacerMenu : GlobalTileEntityMenu() {
        
        private val sideConfigGui = SideConfigMenu(
            this@BlockPlacer,
            mapOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default"),
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