package xyz.xenondevs.nova.addon.machines.registry

import xyz.xenondevs.nova.addon.machines.Machines
import xyz.xenondevs.nova.addon.registry.EquipmentRegistry
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage

@Init(stage = InitStage.PRE_PACK)
object Equipment : EquipmentRegistry by Machines.registry {
    
    val STAR = equipment("star") {
        humanoid {
            layer {
                texture("star")
            }
        }
        humanoidLeggings {
            layer {
                texture("star")
            }
        }
    }
    
}