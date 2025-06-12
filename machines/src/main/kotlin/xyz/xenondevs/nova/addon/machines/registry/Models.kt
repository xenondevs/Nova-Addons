package xyz.xenondevs.nova.addon.machines.registry

import org.joml.Vector3d
import xyz.xenondevs.nova.addon.machines.Machines.item
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.world.item.NovaItem

@Init(stage = InitStage.PRE_PACK)
object Models {
    
    // Tree Miniatures
    val OAK_TREE_MINIATURE = modelItem("tree_miniature/oak")
    val SPRUCE_TREE_MINIATURE = modelItem("tree_miniature/spruce")
    val BIRCH_TREE_MINIATURE = modelItem("tree_miniature/birch")
    val JUNGLE_TREE_MINIATURE = modelItem("tree_miniature/jungle")
    val ACACIA_TREE_MINIATURE = modelItem("tree_miniature/acacia")
    val DARK_OAK_TREE_MINIATURE = modelItem("tree_miniature/dark_oak")
    val MANGROVE_TREE_MINIATURE = modelItem("tree_miniature/mangrove")
    val CRIMSON_TREE_MINIATURE = modelItem("tree_miniature/crimson")
    val WARPED_TREE_MINIATURE = modelItem("tree_miniature/warped")
    val GIANT_RED_MUSHROOM_MINIATURE = modelItem("tree_miniature/red_mushroom")
    val GIANT_BROWN_MUSHROOM_MINIATURE = modelItem("tree_miniature/brown_mushroom")
    
    // Water levels
    val COBBLESTONE_GENERATOR_WATER_LEVELS = fluidLevels("cobblestone_generator/water")
    val COBBLESTONE_GENERATOR_LAVA_LEVELS = fluidLevels("cobblestone_generator/lava")
    
    // Star Collector
    val STAR_COLLECTOR_ROD_ON = modelItem("star_collector/rod_on")
    val STAR_COLLECTOR_ROD_OFF = modelItem("star_collector/rod_off")
    
    // Wind Turbine
    val WIND_TURBINE_ROTOR_BLADE = modelItem("wind_turbine/rotor_blade")
    val WIND_TURBINE_ROTOR_MIDDLE = modelItem("wind_turbine/rotor_middle")
    
    // Quarry
    val SCAFFOLDING_FULL_HORIZONTAL = modelItem("scaffolding/full_horizontal")
    val SCAFFOLDING_FULL_VERTICAL = modelItem("scaffolding/full_vertical")
    val SCAFFOLDING_CORNER_DOWN = modelItem("scaffolding/corner_down")
    val SCAFFOLDING_SMALL_HORIZONTAL = modelItem("scaffolding/small_horizontal")
    val SCAFFOLDING_FULL_SLIM_VERTICAL = modelItem("scaffolding/full_slim_vertical")
    val SCAFFOLDING_SLIM_VERTICAL_DOWN = modelItem("scaffolding/slim_vertical_down")
    val NETHERITE_DRILL = modelItem("netherite_drill")
    
    private fun modelItem(name: String): NovaItem = item("model/$name") {
        hidden(true)
        modelDefinition { model = buildModel { getModel("block/$name") } }
    }
    
    private fun fluidLevels(name: String): NovaItem = item("model/$name") {
        hidden(true)
        modelDefinition { 
            model = rangedModels(101) {
                getModel("block/$name").scale(
                    Vector3d(0.0, 0.0, 0.0),
                    Vector3d(1.0, it / 100.0, 1.0),
                    true
                )
            }
        }
    }
    
}