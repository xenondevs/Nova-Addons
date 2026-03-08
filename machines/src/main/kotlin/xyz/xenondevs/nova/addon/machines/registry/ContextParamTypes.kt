package xyz.xenondevs.nova.addon.machines.registry

import xyz.xenondevs.nova.addon.machines.Machines
import xyz.xenondevs.nova.context.DefaultingContextParamType
import xyz.xenondevs.nova.context.intention.BlockBreak
import xyz.xenondevs.nova.util.Key

object ContextParamTypes {
    
    val WIND_TURBINE_RECURSIVE = DefaultingContextParamType<Boolean, BlockBreak>(
        Key(Machines, "wind_turbine_recursive"),
        default = false
    )
    
}