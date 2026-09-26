package xyz.xenondevs.nova.addon.machines.registry

import xyz.xenondevs.nova.addon.machines.Machines
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.registry.registryEntrySetOf
import xyz.xenondevs.nova.ui.waila.info.DefaultWailaInfoProviders
import xyz.xenondevs.nova.world.block.NovaBlockState

@Init(stage = InitStage.PRE_WORLD)
object WailaInfoProviders {
    
    init {
        Machines.wailaInfoProvider<NovaBlockState>("wind_turbine_extra") {
            blocks = registryEntrySetOf(Blocks.WIND_TURBINE_EXTRA)
            infoProvider { player, block, blockState ->
                val section: Int = blockState.getOrThrow(BlockStateProperties.TURBINE_SECTION)
                val baseBlock = block.getRelative(0, -section - 1, 0)
                DefaultWailaInfoProviders.DEFAULT.get().getInfo(player, baseBlock, baseBlock.blockData)
            }
        }
    }
    
}