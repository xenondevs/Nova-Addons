package xyz.xenondevs.nova.addon.logistics.registry

import xyz.xenondevs.nova.addon.logistics.Logistics
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.registry.registryEntrySetOf
import xyz.xenondevs.nova.ui.waila.WailaIcon
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.blockType

@Init(stage = InitStage.PRE_PACK)
object WailaIconProviders {
    
    init {
        Logistics.wailaIconProvider<NovaBlockState>("facaded_cable") {
            blocks = registryEntrySetOf(
                Blocks.FACADED_BASIC_CABLE,
                Blocks.FACADED_ADVANCED_CABLE,
                Blocks.FACADED_ELITE_CABLE,
                Blocks.FACADED_ULTIMATE_CABLE,
                Blocks.FACADED_CREATIVE_CABLE
            )
            iconProvider { blockState ->
                WailaIcon.Item(
                    itemType = when (blockState.blockType) {
                        Blocks.FACADED_BASIC_CABLE.get() -> Items.BASIC_CABLE
                        Blocks.FACADED_ADVANCED_CABLE.get() -> Items.ADVANCED_CABLE
                        Blocks.FACADED_ELITE_CABLE.get() -> Items.ELITE_CABLE
                        Blocks.FACADED_ULTIMATE_CABLE.get() -> Items.ULTIMATE_CABLE
                        else -> Items.CREATIVE_CABLE
                    }.get()
                )
            }
        }
    }
    
}