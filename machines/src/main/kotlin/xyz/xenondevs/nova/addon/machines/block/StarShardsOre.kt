package xyz.xenondevs.nova.addon.machines.block

import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.DefaultContextIntentions.BlockBreak
import xyz.xenondevs.nova.context.param.DefaultContextParamTypes
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.behavior.BlockBehavior
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import kotlin.random.Random

object StarShardsOre : BlockBehavior {
    
    override fun getDrops(pos: BlockPos, state: NovaBlockState, ctx: Context<BlockBreak>): List<ItemStack> {
        if (!ctx[DefaultContextParamTypes.BLOCK_DROPS])
            return emptyList()
        
        if (ctx[DefaultContextParamTypes.TOOL_ITEM_STACK]?.getEnchantmentLevel(Enchantment.SILK_TOUCH) == 1)
            return listOf(state.block.item!!.createItemStack())
        
        return listOf(Items.STAR_SHARDS.createItemStack(Random.nextInt(1, 4)))
    }
    
    override fun getExp(pos: BlockPos, state: NovaBlockState, ctx: Context<BlockBreak>): Int {
        if (!ctx[DefaultContextParamTypes.BLOCK_EXP_DROPS])
            return 0
        
        return Random.nextInt(1, 4)
    }
    
}