package xyz.xenondevs.nova.addon.machines.block

import org.bukkit.Tag
import xyz.xenondevs.nova.addon.machines.registry.BlockStateProperties
import xyz.xenondevs.nova.addon.machines.registry.Blocks
import xyz.xenondevs.nova.addon.machines.registry.ContextParamTypes
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.DefaultContextIntentions.BlockBreak
import xyz.xenondevs.nova.context.intention.DefaultContextIntentions.BlockPlace
import xyz.xenondevs.nova.context.param.DefaultContextParamTypes
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.util.BlockUtils
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.behavior.BlockBehavior
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.format.WorldDataManager

object WindTurbineBehavior : BlockBehavior {
    
    override suspend fun canPlace(pos: BlockPos, state: NovaBlockState, ctx: Context<BlockPlace>): Boolean {
        for (i in 0..3) {
            val sectionPos = pos.add(0, i, 0)
            
            if (!Tag.REPLACEABLE.isTagged(sectionPos.block.type) || WorldDataManager.getBlockState(sectionPos) != null)
                return false
            
            if (!ProtectionManager.canPlace(ctx))
                return false
        }
        
        return true
    }
    
    override fun handlePlace(pos: BlockPos, state: NovaBlockState, ctx: Context<BlockPlace>) {
        for (i in 1..3) {
            val sectionCtx = Context.intention(BlockPlace)
                .param(DefaultContextParamTypes.BLOCK_POS, pos.add(0, i, 0))
                .param(
                    DefaultContextParamTypes.BLOCK_STATE_NOVA,
                    Blocks.WIND_TURBINE_EXTRA.defaultBlockState.with(BlockStateProperties.TURBINE_SECTION, i - 1)
                )
                .param(DefaultContextParamTypes.BLOCK_PLACE_EFFECTS, false)
                .build()
            BlockUtils.placeBlock(sectionCtx)
        }
    }
    
    override fun handleBreak(pos: BlockPos, state: NovaBlockState, ctx: Context<BlockBreak>) {
        if (ctx[ContextParamTypes.WIND_TURBINE_RECURSIVE])
            return
        
        for (i in 1..3) {
            val sectionCtx = Context.from(ctx)
                .param(DefaultContextParamTypes.BLOCK_POS, pos.add(0, i, 0))
                .param(ContextParamTypes.WIND_TURBINE_RECURSIVE, true)
                .build()
            BlockUtils.breakBlockNaturally(sectionCtx)
        }
    }
    
}
