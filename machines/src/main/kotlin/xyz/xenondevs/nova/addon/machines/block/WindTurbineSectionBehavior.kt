package xyz.xenondevs.nova.addon.machines.block

import xyz.xenondevs.nova.addon.machines.registry.BlockStateProperties
import xyz.xenondevs.nova.addon.machines.registry.ContextParamTypes
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.DefaultContextIntentions.BlockBreak
import xyz.xenondevs.nova.context.intention.DefaultContextIntentions.BlockInteract
import xyz.xenondevs.nova.context.param.DefaultContextParamTypes
import xyz.xenondevs.nova.util.BlockUtils
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.behavior.BlockBehavior
import xyz.xenondevs.nova.world.block.state.NovaBlockState

object WindTurbineSectionBehavior : BlockBehavior {
    
    override fun handleInteract(pos: BlockPos, state: NovaBlockState, ctx: Context<BlockInteract>): Boolean {
        val basePos = pos.add(0, -state.getOrThrow(BlockStateProperties.TURBINE_SECTION) - 1, 0)
        val baseState = basePos.novaBlockState
        if (baseState != null) {
            val baseCtx = Context.from(ctx)
                .param(DefaultContextParamTypes.BLOCK_POS, basePos)
                .build()
            
            return baseState.block.handleInteract(basePos, baseState, baseCtx)
        }
        
        return false
    }
    
    override fun handleBreak(pos: BlockPos, state: NovaBlockState, ctx: Context<BlockBreak>) {
        if (ctx[ContextParamTypes.WIND_TURBINE_RECURSIVE])
            return
        
        val basePos = pos.add(0, -state.getOrThrow(BlockStateProperties.TURBINE_SECTION) - 1, 0)
        
        for (i in 0..3) {
            val extraPos = basePos.add(0, i, 0)
            if (extraPos == pos)
                continue
            
            val sectionCtx = Context.from(ctx)
                .param(DefaultContextParamTypes.BLOCK_POS, basePos.add(0, i, 0))
                .param(ContextParamTypes.WIND_TURBINE_RECURSIVE, true)
                .build()
            BlockUtils.breakBlockNaturally(sectionCtx)
        }
    }
    
}
