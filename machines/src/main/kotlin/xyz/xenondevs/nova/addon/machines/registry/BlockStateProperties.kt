package xyz.xenondevs.nova.addon.machines.registry

import xyz.xenondevs.nova.addon.machines.Machines
import xyz.xenondevs.nova.util.Key
import xyz.xenondevs.nova.world.block.state.property.impl.BooleanProperty
import xyz.xenondevs.nova.world.block.state.property.impl.IntProperty

object BlockStateProperties {
    
    val ACTIVE = BooleanProperty(Key(Machines, "active"))
    val TURBINE_SECTION = IntProperty(Key(Machines, "turbine_section"))
    
}

object ScopedBlockStateProperties {
    
    val ACTIVE = BlockStateProperties.ACTIVE.scope(false, true)
    val TURBINE_SECTION = BlockStateProperties.TURBINE_SECTION.scope(0..2)
    
}