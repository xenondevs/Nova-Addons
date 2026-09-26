package xyz.xenondevs.nova.addon.machines.block

import org.bukkit.inventory.ItemStack
import xyz.xenondevs.nova.addon.machines.registry.BlockStateProperties
import xyz.xenondevs.nova.addon.machines.registry.ContextParamTypes
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.BlockBreak
import xyz.xenondevs.nova.context.intention.BlockInteract
import xyz.xenondevs.nova.util.BlockUtils
import org.bukkit.block.Block
import xyz.xenondevs.nova.world.InteractionResult
import xyz.xenondevs.nova.world.block.behavior.BlockBehavior
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.novaBlockState
import xyz.xenondevs.nova.world.block.novaTileEntity
import xyz.xenondevs.nova.world.item.createItemStack

object WindTurbineSectionBehavior : BlockBehavior {
    
    override fun use(block: Block, state: NovaBlockState, ctx: Context<BlockInteract>): InteractionResult {
        val basePos = block.getRelative(0, -state.getOrThrow(BlockStateProperties.TURBINE_SECTION) - 1, 0)
        val baseState = basePos.novaBlockState
        if (baseState != null) {
            val baseCtx = ctx.toBuilder()
                .param(BlockInteract.BLOCK, basePos)
                .build()
            
            return basePos.novaTileEntity?.use(baseCtx) ?: InteractionResult.Pass
        }
        
        return InteractionResult.Pass
    }
    
    override fun handleBreak(block: Block, state: NovaBlockState, ctx: Context<BlockBreak>) {
        if (ctx[ContextParamTypes.WIND_TURBINE_RECURSIVE])
            return
        
        val basePos = block.getRelative(0, -state.getOrThrow(BlockStateProperties.TURBINE_SECTION) - 1, 0)
        
        for (i in 0..3) {
            val extraPos = basePos.getRelative(0, i, 0)
            if (extraPos == block)
                continue
            
            val sectionCtx = ctx.toBuilder()
                .param(BlockBreak.BLOCK, basePos.getRelative(0, i, 0))
                .param(ContextParamTypes.WIND_TURBINE_RECURSIVE, true)
                .build()
            BlockUtils.breakBlockNaturally(sectionCtx)
        }
    }
    
    override fun pickBlockCreative(block: Block, state: NovaBlockState, ctx: Context<BlockInteract>): ItemStack =
        Items.WIND_TURBINE.createItemStack()
    
}
