package xyz.xenondevs.nova.addon.logistics.registry

import xyz.xenondevs.nova.addon.logistics.Logistics
import xyz.xenondevs.nova.util.ResourceLocation
import xyz.xenondevs.nova.world.block.state.property.impl.BooleanProperty

object BlockStateProperties {
    
    val NORTH = BooleanProperty(ResourceLocation(Logistics, "north"))
    val EAST = BooleanProperty(ResourceLocation(Logistics, "east"))
    val SOUTH = BooleanProperty(ResourceLocation(Logistics, "south"))
    val WEST = BooleanProperty(ResourceLocation(Logistics, "west"))
    val UP = BooleanProperty(ResourceLocation(Logistics, "up"))
    val DOWN = BooleanProperty(ResourceLocation(Logistics, "down"))
    
}

object ScopedBlockStateProperties {
    
    val NORTH = BlockStateProperties.NORTH.scope { false }
    val EAST = BlockStateProperties.EAST.scope { false }
    val SOUTH = BlockStateProperties.SOUTH.scope { false }
    val WEST = BlockStateProperties.WEST.scope { false }
    val UP = BlockStateProperties.UP.scope { false }
    val DOWN = BlockStateProperties.DOWN.scope { false }
    
}