package xyz.xenondevs.nova.addon.machines.util

import org.bukkit.Location
import org.bukkit.block.Block
import xyz.xenondevs.nova.world.region.DynamicRegion
import xyz.xenondevs.nova.world.region.Region

val Region.blockSequence: Sequence<Block>
    get() = blockSequence(min, max)

val DynamicRegion.blockSequence: Sequence<Block>
    get() = blockSequence(min, max)

private fun blockSequence(min: Location, max: Location): Sequence<Block> =
    BlockIterator(min, max).asSequence()

operator fun Region.iterator(): Iterator<Block> =
    BlockIterator(min, max)

operator fun DynamicRegion.iterator(): Iterator<Block> =
    BlockIterator(min, max)

private class BlockIterator(
    private val min: Location,
    private val max: Location
) : Iterator<Block> {
    
    private var x = min.blockX
    private var y = min.blockY
    private var z = min.blockZ
    
    override fun hasNext(): Boolean {
        return x < max.blockX && y < max.blockY && z < max.blockZ
    }
    
    override fun next(): Block {
        if (!hasNext())
            throw NoSuchElementException()
        
        val block = min.world.getBlockAt(x, y, z)
        if (++x >= max.blockX) {
            x = min.blockX
            if (++y >= max.blockY) {
                y = min.blockY
                z++
            }
        }
        return block
    }
    
}