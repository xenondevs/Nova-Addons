package xyz.xenondevs.nova.addon.machines.registry

import xyz.xenondevs.nova.addon.machines.Machines
import xyz.xenondevs.nova.addon.registry.EquipmentRegistry
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.resources.builder.layout.equipment.InterpolationMode
import java.awt.Color

@Init(stage = InitStage.PRE_PACK)
object Equipment : EquipmentRegistry by Machines.registry {
    
    val STAR = equipment("star") {
        humanoid {
            layer {
                texture("star")
                emissivityMap("star_emissivity_map")
                dyeable(Color.WHITE)
            }
        }
        humanoidLeggings {
            layer {
                texture("star")
                emissivityMap("star_emissivity_map")
            }
        }
    }
    
    val EXAMPLE = animatedEquipment("example") {
        humanoid  {
            layer {
                texture(5, InterpolationMode.NONE, "frame_1", "frame_2", "frame_3")
            }
        }
     
        /* ... */
    }
    
}