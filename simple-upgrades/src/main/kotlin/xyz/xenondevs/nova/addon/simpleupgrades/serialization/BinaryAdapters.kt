package xyz.xenondevs.nova.addon.simpleupgrades.serialization

import xyz.xenondevs.cbf.CBF
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypeRegistry
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitFun
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.serialization.cbf.adapter.byNameBinaryAdapter

@Init(stage = InitStage.PRE_PACK)
internal object BinaryAdapters {
    
    @InitFun
    private fun registerBinaryAdapters() {
        CBF.registerBinaryAdapter(UpgradeTypeRegistry.REGISTRY.byNameBinaryAdapter())
    }
    
}