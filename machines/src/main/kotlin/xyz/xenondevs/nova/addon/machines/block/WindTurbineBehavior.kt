package xyz.xenondevs.nova.addon.machines.block

import xyz.xenondevs.nova.addon.machines.registry.BlockStateProperties
import xyz.xenondevs.nova.addon.machines.registry.Blocks
import xyz.xenondevs.nova.addon.machines.registry.ContextParamTypes
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.BlockBreak
import xyz.xenondevs.nova.context.intention.BlockPlace
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.registry.tags.BlockTypeTags
import xyz.xenondevs.nova.util.BlockUtils
import org.bukkit.block.Block
import xyz.xenondevs.nova.world.block.behavior.BlockBehavior
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.blockType
import xyz.xenondevs.nova.world.block.novaBlockState

object WindTurbineBehavior : BlockBehavior {
    
    override suspend fun canPlace(block: Block, data: NovaBlockState, ctx: Context<BlockPlace>): Boolean {
        for (i in 0..3) {
            val sectionPos = block.getRelative(0, i, 0)
            
            if (sectionPos.blockType !in BlockTypeTags.REPLACEABLE || sectionPos.novaBlockState != null)
                return false
            
            if (!ProtectionManager.canPlace(ctx))
                return false
        }
        
        return true
    }
    
    override fun handlePlace(block: Block, state: NovaBlockState, ctx: Context<BlockPlace>) {
        for (i in 1..3) {
            val sectionCtx = Context.intention(BlockPlace)
                .param(BlockPlace.BLOCK, block.getRelative(0, i, 0))
                .param(
                    BlockPlace.BLOCK_STATE,
                    (Blocks.WIND_TURBINE_EXTRA.get().createBlockData() as NovaBlockState)
                        .apply { this[BlockStateProperties.TURBINE_SECTION] = i - 1 }
                )
                .param(BlockPlace.BLOCK_PLACE_EFFECTS, false)
                .build()
            BlockUtils.placeBlock(sectionCtx)
        }
    }
    
    override fun handleBreak(block: Block, state: NovaBlockState, ctx: Context<BlockBreak>) {
        if (ctx[ContextParamTypes.WIND_TURBINE_RECURSIVE])
            return
        
        for (i in 1..3) {
            val sectionCtx = ctx.toBuilder()
                .param(BlockBreak.BLOCK, block.getRelative(0, i, 0))
                .param(ContextParamTypes.WIND_TURBINE_RECURSIVE, true)
                .build()
            BlockUtils.breakBlockNaturally(sectionCtx)
        }
    }
    
}
