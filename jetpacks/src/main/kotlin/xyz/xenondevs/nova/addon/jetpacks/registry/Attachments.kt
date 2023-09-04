package xyz.xenondevs.nova.addon.jetpacks.registry

import xyz.xenondevs.nova.addon.jetpacks.Jetpacks
import xyz.xenondevs.nova.addon.registry.AttachmentTypeRegistry
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.item.NovaItem
import xyz.xenondevs.nova.player.attachment.AttachmentType
import xyz.xenondevs.nova.player.attachment.HideDownItemAttachment

@Init(stage = InitStage.POST_PACK_PRE_WORLD)
object Attachments : AttachmentTypeRegistry by Jetpacks.registry {
    
    private fun registerJetpackAttachment(name: String, material: NovaItem): AttachmentType<*> =
        registerAttachmentType(name) { HideDownItemAttachment(40f, it, material.clientsideProvider.get()) }
    
    val BASIC_JETPACK = registerJetpackAttachment("basic_jetpack", Items.BASIC_JETPACK)
    val ADVANCED_JETPACK = registerJetpackAttachment("advanced_jetpack", Items.ADVANCED_JETPACK)
    val ELITE_JETPACK = registerJetpackAttachment("elite_jetpack", Items.ELITE_JETPACK)
    val ULTIMATE_JETPACK = registerJetpackAttachment("ultimate_jetpack", Items.ULTIMATE_JETPACK)
    
}