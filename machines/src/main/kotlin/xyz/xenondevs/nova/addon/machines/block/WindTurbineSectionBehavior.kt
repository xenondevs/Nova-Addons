package xyz.xenondevs.nova.addon.machines.block

import org.bukkit.inventory.ItemStack
import xyz.xenondevs.nova.addon.machines.registry.BlockStateProperties
import xyz.xenondevs.nova.addon.machines.registry.ContextParamTypes
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.BlockBreak
import xyz.xenondevs.nova.context.intention.BlockInteract
import xyz.xenondevs.nova.util.BlockUtils
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.InteractionResult
import xyz.xenondevs.nova.world.block.behavior.BlockBehavior
import xyz.xenondevs.nova.world.block.state.NovaBlockState

object WindTurbineSectionBehavior : BlockBehavior {
    
    override fun use(pos: BlockPos, state: NovaBlockState, ctx: Context<BlockInteract>): InteractionResult {
        val basePos = pos.add(0, -state.getOrThrow(BlockStateProperties.TURBINE_SECTION) - 1, 0)
        val baseState = basePos.novaBlockState
        if (baseState != null) {
            val baseCtx = ctx.toBuilder()
                .param(BlockInteract.BLOCK_POS, basePos)
                .build()
            
            return baseState.block.use(basePos, baseState, baseCtx)
        }
        
        return InteractionResult.Pass
    }
    
    override fun handleBreak(pos: BlockPos, state: NovaBlockState, ctx: Context<BlockBreak>) {
        if (ctx[ContextParamTypes.WIND_TURBINE_RECURSIVE])
            return
        
        val basePos = pos.add(0, -state.getOrThrow(BlockStateProperties.TURBINE_SECTION) - 1, 0)
        
        for (i in 0..3) {
            val extraPos = basePos.add(0, i, 0)
            if (extraPos == pos)
                continue
            
            val sectionCtx = ctx.toBuilder()
                .param(BlockBreak.BLOCK_POS, basePos.add(0, i, 0))
                .param(ContextParamTypes.WIND_TURBINE_RECURSIVE, true)
                .build()
            BlockUtils.breakBlockNaturally(sectionCtx)
        }
    }
    
    override fun pickBlockCreative(pos: BlockPos, state: NovaBlockState, ctx: Context<BlockInteract>): ItemStack {
        return Items.WIND_TURBINE.createItemStack()
    }
    
}
