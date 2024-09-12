package xyz.xenondevs.nova.addon.machines.block

import org.bukkit.entity.Player
import xyz.xenondevs.nova.addon.machines.registry.BlockStateProperties
import xyz.xenondevs.nova.addon.machines.registry.Blocks
import xyz.xenondevs.nova.ui.waila.info.NovaWailaInfoProvider
import xyz.xenondevs.nova.ui.waila.info.WailaInfo
import xyz.xenondevs.nova.ui.waila.info.impl.DefaultNovaWailaInfoProvider
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState

object WindTurbineWailaInfoProvider : NovaWailaInfoProvider(setOf(Blocks.WIND_TURBINE_EXTRA)) {
    
    override fun getInfo(player: Player, pos: BlockPos, blockState: NovaBlockState): WailaInfo {
        val section = blockState.getOrThrow(BlockStateProperties.TURBINE_SECTION)
        return DefaultNovaWailaInfoProvider.getInfo(player, pos.add(0, -section - 1, 0), Blocks.WIND_TURBINE.defaultBlockState)
    }
    
}