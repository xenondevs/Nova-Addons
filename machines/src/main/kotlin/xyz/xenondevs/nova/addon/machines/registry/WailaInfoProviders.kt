package xyz.xenondevs.nova.addon.machines.registry

import xyz.xenondevs.nova.addon.machines.Machines
import xyz.xenondevs.nova.addon.machines.block.WindTurbineWailaInfoProvider
import xyz.xenondevs.nova.addon.registry.WailaInfoProviderRegistry
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage

@Init(stage = InitStage.PRE_WORLD)
object WailaInfoProviders : WailaInfoProviderRegistry by Machines.registry {
    
    init {
        registerWailaInfoProvider("wind_turbine_extra", WindTurbineWailaInfoProvider)
    }
    
}