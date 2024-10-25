package xyz.xenondevs.nova.addon.logistics.registry

import org.joml.Vector3d
import xyz.xenondevs.nova.addon.logistics.Logistics
import xyz.xenondevs.nova.addon.registry.AddonHolder
import xyz.xenondevs.nova.addon.registry.ItemRegistry
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.world.item.NovaItem

@Init(stage = InitStage.PRE_PACK)
object Models : ItemRegistry, AddonHolder by Logistics {
    
    val CABLE_ATTACHMENT = item("cable_attachment") {
        hidden(true)
        models {
            selectModels(0..15, setDefault = true) {
                getModel("block/cable/attachments/$it")
            }
        }
    }
    
    val TANK_WATER_LEVELS = fluidLevels("fluid_tank/water")
    val TANK_LAVA_LEVELS = fluidLevels("fluid_tank/lava")
    
    private fun fluidLevels(name: String): NovaItem = item(name) {
        hidden(true)
        models {
            selectModels(0..100, setDefault = true) {
                getModel("block/$name").scale(
                    pivot = Vector3d(0.0, 0.0, 0.0),
                    scale = Vector3d(1.0, it / 100.0, 1.0),
                    scaleUV = true
                )
            }
        }
    }
    
}