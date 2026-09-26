package xyz.xenondevs.nova.addon.machines.registry

import xyz.xenondevs.nova.addon.machines.Machines.blockTag
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.registry.entries.BlockTypeEntries
import xyz.xenondevs.nova.registry.tags.BlockTypeTags

@Init(stage = InitStage.PRE_PACK)
object BlockTags {
    
    val SIMPLE_HARVESTABLES = blockTag("simple_harvestables") {
        add(BlockTypeTags.LEAVES)
        add(BlockTypeTags.LOGS)
        add(BlockTypeTags.FLOWERS)
        add(BlockTypeTags.WART_BLOCKS)
        add(
            BlockTypeEntries.SHORT_GRASS,
            BlockTypeEntries.TALL_GRASS,
            BlockTypeEntries.BEE_NEST,
            BlockTypeEntries.PUMPKIN,
            BlockTypeEntries.MELON,
            BlockTypeEntries.SHROOMLIGHT,
            BlockTypeEntries.WEEPING_VINES,
            BlockTypeEntries.WEEPING_VINES_PLANT,
            BlockTypeEntries.MUSHROOM_STEM,
            BlockTypeEntries.RED_MUSHROOM_BLOCK,
            BlockTypeEntries.BROWN_MUSHROOM_BLOCK,
            BlockTypeEntries.VINE,
            BlockTypeEntries.MANGROVE_ROOTS,
            BlockTypeEntries.MUDDY_MANGROVE_ROOTS,
            BlockTypeEntries.MOSS_CARPET,
            BlockTypeEntries.PALE_MOSS_CARPET,
            BlockTypeEntries.PALE_HANGING_MOSS,
            BlockTypeEntries.CREAKING_HEART
        )
    }
    
    val TREE_ATTACHMENTS = blockTag("tree_attachments") {
        add(
            BlockTypeEntries.BEE_NEST,
            BlockTypeEntries.SHROOMLIGHT,
            BlockTypeEntries.WEEPING_VINES,
            BlockTypeEntries.WEEPING_VINES_PLANT,
            BlockTypeEntries.MANGROVE_PROPAGULE,
            BlockTypeEntries.VINE,
            BlockTypeEntries.MOSS_CARPET,
            BlockTypeEntries.PALE_MOSS_CARPET,
            BlockTypeEntries.PALE_HANGING_MOSS
        )
    }
    
}
