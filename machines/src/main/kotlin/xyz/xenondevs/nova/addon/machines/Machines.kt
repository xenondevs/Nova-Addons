package xyz.xenondevs.nova.addon.machines

import xyz.xenondevs.cbf.Cbf
import xyz.xenondevs.nova.addon.Addon
import xyz.xenondevs.nova.addon.machines.tileentity.processing.brewing.PotionBottleType
import xyz.xenondevs.nova.addon.machines.tileentity.processing.brewing.PotionEffectProviderBinarySerializer
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitFun
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.update.ProjectDistributor

@Init(stage = InitStage.PRE_WORLD)
object Machines : Addon() {
    
    override val projectDistributors = listOf(
        ProjectDistributor.modrinth("nova-machines"),
        ProjectDistributor.hangar("xenondevs/Machines")
    )
    
    @InitFun
    private fun init() {
        Cbf.registerSerializer(PotionEffectProviderBinarySerializer)
        Cbf.addOrdinalEnums(PotionBottleType::class)
    }
    
}