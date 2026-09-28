package xyz.xenondevs.nova.addon.machines.tileentity.world

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bukkit.block.Block
import org.bukkit.inventory.ItemType
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.nova.addon.machines.registry.Blocks.BLOCK_PLACER
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.util.efficiencyDividedValue
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.BlockPlace
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.registry.tags.BlockTypeTags
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.nova.util.BlockSideSet
import xyz.xenondevs.nova.util.BlockUtils
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.blockType
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.world.block.novaBlockState
import xyz.xenondevs.nova.world.block.state.property.DefaultBlockStateProperties
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.item.itemType
import kotlin.time.Duration.Companion.milliseconds

private val BLOCKED_SIDES = BlockSideSet(front = true)

private val MAX_ENERGY = BLOCK_PLACER.config.entry<Long>("capacity")
private val ENERGY_PER_PLACE = BLOCK_PLACER.config.entry<Long>("energy_per_place")

class BlockPlacer(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val inventory = storedInventory("inventory", 9) {}
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(inventory to INSERT, blockedSides = BLOCKED_SIDES)
    
    private val energyPerPlace by efficiencyDividedValue(ENERGY_PER_PLACE, upgradeHolder)
    
    private val placePos = block.getRelative(blockState.getOrThrow(DefaultBlockStateProperties.FACING_HORIZONTAL))
    private val placeBlock = placePos
    
    @Volatile
    private var permittedTypes: Set<ItemType> = emptySet()
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.GENERIC_3X3_WITH_BAR) {
        upperGui by gui(
            "s . . i i i . . e",
            "u . . i i i . . e",
            ". . . i i i . . e",
        ) {
            'i' by inventory
            's' by openSideConfigItem(mapOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default"))
            'u' by openUpgradesItem(upgradeHolder)
            'e' by energyBar(energyHolder)
        }
    }
    
    
    override fun handleTick() {
        if (energyHolder.energy >= energyPerPlace
            && !inventory.isEmpty
            && placeBlock.blockType in BlockTypeTags.REPLACEABLE
            && placePos.novaBlockState == null
        ) {
            if (placeBlock())
                energyHolder.energy -= energyPerPlace
        }
    }
    
    override fun handleEnableTicking() {
        CoroutineScope(coroutineSupervisor!!).launch {
            while (true) {
                permittedTypes = inventory.items.asSequence()
                    .mapNotNull { it?.itemType }
                    .filter { it.hasBlockType() }
                    .filterTo(HashSet()) { ProtectionManager.canPlace(this@BlockPlacer, it.createItemStack(), placePos) }
                delay(50.milliseconds)
            }
        }
    }
    
    private fun placeBlock(): Boolean {
        for ((index, item) in inventory.items.withIndex()) {
            if (item == null)
                continue
            if (item.itemType !in permittedTypes)
                continue
            
            val ctx = Context.intention(BlockPlace)
                .param(BlockPlace.BLOCK, placePos)
                .param(BlockPlace.BLOCK_ITEM_STACK, item)
                .param(BlockPlace.SOURCE_TILE_ENTITY, this)
                .build()
            if (BlockUtils.placeBlock(ctx)) {
                inventory.addItemAmount(SELF_UPDATE_REASON, index, -1)
                return true
            } else continue
        }
        
        return false
    }
    
}
