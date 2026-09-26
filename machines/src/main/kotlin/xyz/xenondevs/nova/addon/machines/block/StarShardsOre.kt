package xyz.xenondevs.nova.addon.machines.block

import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.BlockBreak
import org.bukkit.block.Block
import xyz.xenondevs.nova.world.block.behavior.BlockBehavior
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.blockType
import xyz.xenondevs.nova.world.item.createItemStack
import xyz.xenondevs.nova.world.block.itemTypeOrNull
import kotlin.random.Random

object StarShardsOre : BlockBehavior {
    
    override fun getDrops(block: Block, state: NovaBlockState, ctx: Context<BlockBreak>): List<ItemStack> {
        if (!ctx[BlockBreak.BLOCK_DROPS])
            return emptyList()
        
        if (ctx[BlockBreak.TOOL_ITEM_STACK].getEnchantmentLevel(Enchantment.SILK_TOUCH) == 1)
            return listOf(state.blockType.itemTypeOrNull!!.createItemStack())
        
        return listOf(Items.STAR_SHARDS.createItemStack(Random.nextInt(1, 4)))
    }
    
    override fun getExp(block: Block, state: NovaBlockState, ctx: Context<BlockBreak>): Int {
        if (!ctx[BlockBreak.BLOCK_EXP_DROPS])
            return 0
        
        return Random.nextInt(1, 4)
    }
    
}
