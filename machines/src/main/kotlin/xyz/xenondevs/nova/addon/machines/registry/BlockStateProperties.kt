package xyz.xenondevs.nova.addon.machines.registry

import xyz.xenondevs.nova.addon.machines.Machines
import xyz.xenondevs.nova.util.ResourceLocation
import xyz.xenondevs.nova.world.block.state.property.impl.BooleanProperty
import xyz.xenondevs.nova.world.block.state.property.impl.IntProperty

object BlockStateProperties {
    
    val ACTIVE = BooleanProperty(ResourceLocation(Machines, "active"))
    val TURBINE_SECTION = IntProperty(ResourceLocation(Machines, "turbine_section"))
    
}

object ScopedBlockStateProperties {
    
    val ACTIVE = BlockStateProperties.ACTIVE.scope(false, true)
    val TURBINE_SECTION = BlockStateProperties.TURBINE_SECTION.scope(0..2)
    
}