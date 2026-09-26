package xyz.xenondevs.nova.addon.machines.registry

import net.kyori.adventure.key.Key.key
import xyz.xenondevs.nova.addon.machines.Machines
import xyz.xenondevs.nova.world.block.state.property.BooleanProperty
import xyz.xenondevs.nova.world.block.state.property.IntProperty

object BlockStateProperties {
    
    val ACTIVE = BooleanProperty(key(Machines, "active"))
    val LAVA = BooleanProperty(key(Machines, "lava"))
    val TURBINE_SECTION = IntProperty(key(Machines, "turbine_section"), 0..2)
    
}
