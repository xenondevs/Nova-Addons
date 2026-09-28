package xyz.xenondevs.nova.addon.logistics.registry

import xyz.xenondevs.nova.addon.logistics.Logistics
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.registry.registryEntrySetOf
import xyz.xenondevs.nova.ui.waila.info.DefaultWailaInfoProviders
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.blockType

@Init(stage = InitStage.PRE_PACK)
object WailaInfoProviders {
    
    init {
        Logistics.wailaInfoProvider<NovaBlockState>("facaded_cable") {
            blocks = registryEntrySetOf(
                Blocks.FACADED_BASIC_CABLE,
                Blocks.FACADED_ADVANCED_CABLE,
                Blocks.FACADED_ELITE_CABLE,
                Blocks.FACADED_ULTIMATE_CABLE,
                Blocks.FACADED_CREATIVE_CABLE
            )
            infoProvider(DefaultWailaInfoProviders.DEFAULT) { _, _, blockState, info ->
                info.copy(
                    icon = when (blockState.blockType) {
                        Blocks.FACADED_BASIC_CABLE.get() -> Blocks.BASIC_CABLE
                        Blocks.FACADED_ADVANCED_CABLE.get() -> Blocks.ADVANCED_CABLE
                        Blocks.FACADED_ELITE_CABLE.get() -> Blocks.ELITE_CABLE
                        Blocks.FACADED_ULTIMATE_CABLE.get() -> Blocks.ULTIMATE_CABLE
                        else -> Blocks.CREATIVE_CABLE
                    }.key
                )
            }
        }
    }
    
}