package xyz.xenondevs.nova.addon.machines.registry

import xyz.xenondevs.nova.addon.machines.Machines
import xyz.xenondevs.nova.addon.registry.ArmorRegistry
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage

@Init(stage = InitStage.PRE_PACK)
object Armor : ArmorRegistry by Machines.registry {
    
    val STAR = armor("star") {
        texture {
            texture("armor/star_layer_1", "armor/star_layer_2")
            emissivityMap("armor/star_layer_1_emissivity_map", "armor/star_layer_2_emissivity_map")
        }
    }
    
}