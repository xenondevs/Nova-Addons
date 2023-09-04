package xyz.xenondevs.nova.addon.machines.registry

import xyz.xenondevs.nova.addon.machines.Machines
import xyz.xenondevs.nova.addon.registry.ItemRegistry
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage

@Init(stage = InitStage.PRE_PACK)
object Models : ItemRegistry by Machines.registry {
    
    // Tree Miniatures
    val OAK_TREE_MINIATURE = registerUnnamedItem("oak_tree_miniature")
    val SPRUCE_TREE_MINIATURE = registerUnnamedItem("spruce_tree_miniature")
    val BIRCH_TREE_MINIATURE = registerUnnamedItem("birch_tree_miniature")
    val JUNGLE_TREE_MINIATURE = registerUnnamedItem("jungle_tree_miniature")
    val ACACIA_TREE_MINIATURE = registerUnnamedItem("acacia_tree_miniature")
    val DARK_OAK_TREE_MINIATURE = registerUnnamedItem("dark_oak_tree_miniature")
    val MANGROVE_TREE_MINIATURE = registerUnnamedItem("mangrove_tree_miniature")
    val CRIMSON_TREE_MINIATURE = registerUnnamedItem("crimson_tree_miniature")
    val WARPED_TREE_MINIATURE = registerUnnamedItem("warped_tree_miniature")
    val GIANT_RED_MUSHROOM_MINIATURE = registerUnnamedItem("giant_red_mushroom_miniature")
    val GIANT_BROWN_MUSHROOM_MINIATURE = registerUnnamedItem("giant_brown_mushroom_miniature")
    
    // Water levels
    val COBBLESTONE_GENERATOR_WATER_LEVELS = registerUnnamedItem("cobblestone_generator_water_levels")
    val COBBLESTONE_GENERATOR_LAVA_LEVELS = registerUnnamedItem("cobblestone_generator_lava_levels")
    
}