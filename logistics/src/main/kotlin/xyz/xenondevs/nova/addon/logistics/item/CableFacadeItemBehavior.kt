package xyz.xenondevs.nova.addon.logistics.item

import org.bukkit.block.Block
import org.bukkit.block.BlockType
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.nova.addon.logistics.registry.BlockStateProperties
import xyz.xenondevs.nova.addon.logistics.registry.Blocks
import xyz.xenondevs.nova.addon.logistics.registry.FacadeType
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.BlockInteract
import xyz.xenondevs.nova.context.intention.BlockPlace
import xyz.xenondevs.nova.util.BlockUtils
import xyz.xenondevs.nova.world.InteractionResult
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.blockTypeEntry
import xyz.xenondevs.nova.world.item.ItemAction
import xyz.xenondevs.nova.world.item.behavior.ItemBehavior

class CableFacadeItemBehavior(private val facadeType: FacadeType) : ItemBehavior {
    
    private val basic by Blocks.FACADED_BASIC_CABLE.map(::facadeState)
    private val advanced by Blocks.FACADED_ADVANCED_CABLE.map(::facadeState)
    private val elite by Blocks.FACADED_ELITE_CABLE.map(::facadeState)
    private val ultimate by Blocks.FACADED_ULTIMATE_CABLE.map(::facadeState)
    private val creative by Blocks.FACADED_CREATIVE_CABLE.map(::facadeState)
    
    override fun useOnBlock(itemStack: ItemStack, block: Block, ctx: Context<BlockInteract>): InteractionResult {
        val type = block.blockTypeEntry
        val newState = when (type) {
            Blocks.BASIC_CABLE -> basic
            Blocks.ADVANCED_CABLE -> advanced
            Blocks.ELITE_CABLE -> elite
            Blocks.ULTIMATE_CABLE -> ultimate
            Blocks.CREATIVE_CABLE -> creative
            else -> return InteractionResult.Pass
        }
        
        BlockUtils.placeBlock(
            Context.intention(BlockPlace)
                .param(BlockPlace.BLOCK, block)
                .param(BlockPlace.BLOCK_STATE, newState)
                .param(BlockPlace.SOURCE_ENTITY, ctx[BlockInteract.SOURCE_ENTITY])
                .param(BlockPlace.SOURCE_TILE_ENTITY, ctx[BlockInteract.SOURCE_TILE_ENTITY])
                .build()
        )
        
        return InteractionResult.Success(swing = true, ItemAction.Consume())
    }
    
    private fun facadeState(type: BlockType) =
        type.createBlockData()
            .let { it as NovaBlockState }
            .apply { set(BlockStateProperties.FACADE, facadeType) } 
    
}