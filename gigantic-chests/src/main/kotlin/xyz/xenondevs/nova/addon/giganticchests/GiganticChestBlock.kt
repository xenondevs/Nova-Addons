package xyz.xenondevs.nova.addon.giganticchests

import org.bukkit.block.Block
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.BlockInteract
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.behavior.BlockBehavior
import xyz.xenondevs.nova.world.item.createItemStack

object GiganticChestBlock : BlockBehavior {
    
    override fun pickBlockCreative(block: Block, state: NovaBlockState, ctx: Context<BlockInteract>) =
        Items.GIGANTIC_CHEST_PART.createItemStack()
    
}