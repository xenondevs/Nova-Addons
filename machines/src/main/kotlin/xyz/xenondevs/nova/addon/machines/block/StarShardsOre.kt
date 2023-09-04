package xyz.xenondevs.nova.addon.machines.block

import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.data.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.BlockBehavior
import xyz.xenondevs.nova.world.block.context.BlockBreakContext
import kotlin.random.Random

object StarShardsOre : BlockBehavior.Default<NovaBlockState>() {
    
    override fun getDrops(state: NovaBlockState, ctx: BlockBreakContext): List<ItemStack> {
        var drops = super.getDrops(state, ctx)
        if (drops.isNotEmpty() && ctx.item?.getEnchantmentLevel(Enchantment.SILK_TOUCH) == 0)
            drops = listOf(Items.STAR_SHARDS.createItemStack(Random.nextInt(1, 4)))
        return drops
    }
    
    override fun getExp(state: NovaBlockState, ctx: BlockBreakContext) = Random.nextInt(1, 4)

}