package xyz.xenondevs.nova.addon.jetpacks.registry

import org.bukkit.inventory.ItemType
import org.joml.Vector3f
import xyz.xenondevs.nova.addon.jetpacks.Jetpacks.registerAttachmentType
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.registry.RegistryEntry
import xyz.xenondevs.nova.world.item.createItemStack
import xyz.xenondevs.nova.world.player.attachment.AttachmentType
import xyz.xenondevs.nova.world.player.attachment.HideDownItemAttachment

@Init(stage = InitStage.PRE_PACK)
object Attachments {
    
    private fun registerJetpackAttachment(name: String, item: RegistryEntry.Paper<ItemType>): RegistryEntry.Nova<AttachmentType<*>> =
        registerAttachmentType(name) { player ->
            HideDownItemAttachment(
                40f, player,
                item.createItemStack(),
                Vector3f(0f, -0.5f, -0.15f)
            )
        }
    
    val BASIC_JETPACK = registerJetpackAttachment("basic_jetpack", Items.BASIC_JETPACK)
    val ADVANCED_JETPACK = registerJetpackAttachment("advanced_jetpack", Items.ADVANCED_JETPACK)
    val ELITE_JETPACK = registerJetpackAttachment("elite_jetpack", Items.ELITE_JETPACK)
    val ULTIMATE_JETPACK = registerJetpackAttachment("ultimate_jetpack", Items.ULTIMATE_JETPACK)
    
}