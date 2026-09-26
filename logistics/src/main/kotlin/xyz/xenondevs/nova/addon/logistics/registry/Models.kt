package xyz.xenondevs.nova.addon.logistics.registry

import org.joml.Vector3d
import xyz.xenondevs.nova.addon.logistics.Logistics.item
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.resources.builder.data.ItemModel
import xyz.xenondevs.nova.resources.builder.data.TintSource
import xyz.xenondevs.nova.resources.builder.layout.item.ItemModelCreationScope
import xyz.xenondevs.nova.resources.builder.layout.item.ItemModelSelectorScope
import xyz.xenondevs.nova.resources.builder.layout.item.RangeDispatchItemModelProperty
import java.awt.Color

@Init(stage = InitStage.PRE_PACK)
object Models {
    
    val CABLE_ATTACHMENT = item("cable_attachment") {
        hidden(true)
        modelDefinition {
            model = numberedModels(0..15) {
                getModel("block/cable/attachments/$it")
            }
        }
    }
    
    val TANK_WATER_LEVELS = fluidLevels("fluid_tank/water", true)
    val TANK_LAVA_LEVELS = fluidLevels("fluid_tank/lava")
    
    private fun fluidLevels(name: String, water: Boolean = false) = item(name) {
        hidden(true)
        modelDefinition { model = fluidLevelModel(name, water) }
    }
    
}

internal fun ItemModelCreationScope<ItemModelSelectorScope>.fluidLevelModel(
    name: String,
    water: Boolean = false
): ItemModel = rangeDispatch(RangeDispatchItemModelProperty.CustomModelData(0)) {
    fallback = empty()
    for (level in 1..100) {
        entry[level] = model {
            if (water) tintSource[0] = TintSource.CustomModelData(Color(0x3F76E4))
            model = {
                getModel("block/$name").scale(
                    pivot = Vector3d(0.0, 1.0, 0.0),
                    scale = Vector3d(1.0, level / 100.0, 1.0),
                    scaleUV = true
                )
            }
        }
    }
}