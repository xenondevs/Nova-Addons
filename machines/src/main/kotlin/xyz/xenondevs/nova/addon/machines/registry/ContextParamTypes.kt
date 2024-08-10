package xyz.xenondevs.nova.addon.machines.registry

import xyz.xenondevs.nova.addon.machines.Machines
import xyz.xenondevs.nova.context.intention.DefaultContextIntentions.BlockBreak
import xyz.xenondevs.nova.context.param.ContextParamType
import xyz.xenondevs.nova.context.param.DefaultingContextParamType

object ContextParamTypes {
    
    val WIND_TURBINE_RECURSIVE: DefaultingContextParamType<Boolean> =
        ContextParamType.builder<Boolean>(Machines, "wind_turbine_recursive")
            .optionalIn(BlockBreak)
            .build(false)
    
}