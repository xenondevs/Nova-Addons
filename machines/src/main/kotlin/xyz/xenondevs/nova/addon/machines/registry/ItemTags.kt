package xyz.xenondevs.nova.addon.machines.registry

import xyz.xenondevs.nova.addon.machines.Machines.itemTag
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.registry.entries.ItemTypeEntries
import xyz.xenondevs.nova.registry.tags.ItemTypeTags

@Init(stage = InitStage.PRE_PACK)
object ItemTags {
    
    val PLANTABLE_SEEDS = itemTag("plantable_seeds") {
        add(ItemTypeTags.VILLAGER_PLANTABLE_SEEDS)
        add(ItemTypeTags.SAPLINGS)
        add(
            ItemTypeEntries.PUMPKIN_SEEDS,
            ItemTypeEntries.MELON_SEEDS,
            ItemTypeEntries.SWEET_BERRIES,
            ItemTypeEntries.CRIMSON_FUNGUS,
            ItemTypeEntries.WARPED_FUNGUS,
            ItemTypeEntries.NETHER_WART
        )
    }
    
    val ANIMAL_FOOD = itemTag("animal_food") {
        add(ItemTypeTags.PIGLIN_FOOD)
        add(ItemTypeTags.FOX_FOOD)
        add(ItemTypeTags.COW_FOOD)
        add(ItemTypeTags.GOAT_FOOD)
        add(ItemTypeTags.SHEEP_FOOD)
        add(ItemTypeTags.WOLF_FOOD)
        add(ItemTypeTags.CAT_FOOD)
        add(ItemTypeTags.HORSE_FOOD)
        add(ItemTypeTags.CAMEL_FOOD)
        add(ItemTypeTags.ARMADILLO_FOOD)
        add(ItemTypeTags.BEE_FOOD)
        add(ItemTypeTags.CHICKEN_FOOD)
        add(ItemTypeTags.FROG_FOOD)
        add(ItemTypeTags.HOGLIN_FOOD)
        add(ItemTypeTags.LLAMA_FOOD)
        add(ItemTypeTags.OCELOT_FOOD)
        add(ItemTypeTags.PANDA_FOOD)
        add(ItemTypeTags.PIG_FOOD)
        add(ItemTypeTags.RABBIT_FOOD)
        add(ItemTypeTags.STRIDER_FOOD)
        add(ItemTypeTags.TURTLE_FOOD)
        add(ItemTypeTags.PARROT_FOOD)
        add(ItemTypeTags.PARROT_POISONOUS_FOOD)
        add(ItemTypeTags.AXOLOTL_FOOD)
    }
    
}
