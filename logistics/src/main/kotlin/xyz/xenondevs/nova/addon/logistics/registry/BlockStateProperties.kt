package xyz.xenondevs.nova.addon.logistics.registry

import net.kyori.adventure.key.Key.key
import xyz.xenondevs.nova.addon.logistics.Logistics
import xyz.xenondevs.nova.world.block.state.property.BooleanProperty
import xyz.xenondevs.nova.world.block.state.property.EnumProperty

object BlockStateProperties {
    
    val LAVA = BooleanProperty(key(Logistics, "lava"))
    val NORTH = BooleanProperty(key(Logistics, "north"))
    val EAST = BooleanProperty(key(Logistics, "east"))
    val SOUTH = BooleanProperty(key(Logistics, "south"))
    val WEST = BooleanProperty(key(Logistics, "west"))
    val UP = BooleanProperty(key(Logistics, "up"))
    val DOWN = BooleanProperty(key(Logistics, "down"))
    
    val FACADE = EnumProperty<FacadeType>(key(Logistics, "facade"))
    
}
