package xyz.xenondevs.nova.addon.simpleupgrades.serialization

import xyz.xenondevs.cbf.Cbf
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypeRegistry
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitFun
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.serialization.cbf.byNameBinarySerializer

@Init(stage = InitStage.PRE_PACK)
internal object BinaryAdapters {
    
    @InitFun
    private fun registerBinaryAdapters() {
        Cbf.registerSerializer(UpgradeTypeRegistry.REGISTRY.byNameBinarySerializer())
    }
    
}