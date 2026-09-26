package xyz.xenondevs.nova.addon.jetpacks.registry

import xyz.xenondevs.nova.addon.jetpacks.Jetpacks.itemTag
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage

@Init(stage = InitStage.PRE_PACK)
object ItemTags {
    
    val BASIC_JETPACKS = itemTag("basic_jetpacks") {
        add(Items.BASIC_JETPACK, Items.ARMORED_BASIC_JETPACK)
    }
    
    val ADVANCED_JETPACKS = itemTag("advanced_jetpacks") {
        add(Items.ADVANCED_JETPACK, Items.ARMORED_ADVANCED_JETPACK)
    }
    
    val ELITE_JETPACKS = itemTag("elite_jetpacks") {
        add(Items.ELITE_JETPACK, Items.ARMORED_ELITE_JETPACK)
    }
    
    val ULTIMATE_JETPACKS = itemTag("ultimate_jetpacks") {
        add(Items.ULTIMATE_JETPACK, Items.ARMORED_ULTIMATE_JETPACK)
    }
    
}