package xyz.xenondevs.nova.addon.machines.registry

import xyz.xenondevs.nova.addon.machines.Machines.registerWailaInfoProvider
import xyz.xenondevs.nova.addon.machines.block.WindTurbineWailaInfoProvider
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage

@Init(stage = InitStage.PRE_WORLD)
object WailaInfoProviders {
    
    init {
        registerWailaInfoProvider("wind_turbine_extra", WindTurbineWailaInfoProvider)
    }
    
}