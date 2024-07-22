package xyz.xenondevs.nova.addon.machines.util

import org.bukkit.Location
import org.bukkit.block.Block
import xyz.xenondevs.nova.world.region.DynamicRegion
import xyz.xenondevs.nova.world.region.Region

val Region.blocks: Sequence<Block>
    get() = blockSequence(min, max)

val DynamicRegion.blocks: Sequence<Block>
    get() = blockSequence(min, max)

private fun blockSequence(min: Location, max: Location): Sequence<Block> {
    return sequence {
        for (x in (min.blockX)..(max.blockX)) {
            for (y in (min.blockY)..(max.blockY)) {
                for (z in (min.blockZ)..(max.blockZ)) {
                    yield(min.world.getBlockAt(x, y, z))
                }
            }
        }
    }
}