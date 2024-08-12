package xyz.xenondevs.nova.addon.machines.util

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.Levelled
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType

/**
 * Checks if this block is a source fluid.
 */
fun Block.isSourceFluid(): Boolean {
    return blockData is Levelled && (blockData as Levelled).level == 0
}

/**
 * Gets the fluid type of this block or null if it's not a (source) fluid.
 */
val Block.sourceFluidType: FluidType?
    get() {
        val blockData = blockData
        if (blockData is Levelled && blockData.level == 0) {
            return when (type) {
                Material.WATER, Material.BUBBLE_COLUMN -> FluidType.WATER
                Material.LAVA -> FluidType.LAVA
                else -> null
            }
        }
        
        return null
    }