package xyz.xenondevs.nova.addon.logistics.registry

import xyz.xenondevs.nova.addon.logistics.Logistics
import xyz.xenondevs.nova.util.Key
import xyz.xenondevs.nova.world.block.state.property.impl.BooleanProperty

object BlockStateProperties {
    
    val NORTH = BooleanProperty(Key(Logistics, "north"))
    val EAST = BooleanProperty(Key(Logistics, "east"))
    val SOUTH = BooleanProperty(Key(Logistics, "south"))
    val WEST = BooleanProperty(Key(Logistics, "west"))
    val UP = BooleanProperty(Key(Logistics, "up"))
    val DOWN = BooleanProperty(Key(Logistics, "down"))
    
}

object ScopedBlockStateProperties {
    
    val NORTH = BlockStateProperties.NORTH.scope { false }
    val EAST = BlockStateProperties.EAST.scope { false }
    val SOUTH = BlockStateProperties.SOUTH.scope { false }
    val WEST = BlockStateProperties.WEST.scope { false }
    val UP = BlockStateProperties.UP.scope { false }
    val DOWN = BlockStateProperties.DOWN.scope { false }
    
}