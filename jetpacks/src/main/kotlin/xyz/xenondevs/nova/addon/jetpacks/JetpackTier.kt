package xyz.xenondevs.nova.addon.jetpacks

import org.bukkit.inventory.ItemType
import xyz.xenondevs.nova.addon.jetpacks.ability.JetpackFlyAbility
import xyz.xenondevs.nova.addon.jetpacks.registry.Abilities
import xyz.xenondevs.nova.addon.jetpacks.registry.Attachments
import xyz.xenondevs.nova.addon.jetpacks.registry.ItemTags
import xyz.xenondevs.nova.registry.RegistryEntry
import xyz.xenondevs.nova.registry.RegistryEntrySet
import xyz.xenondevs.nova.world.player.ability.AbilityType
import xyz.xenondevs.nova.world.player.attachment.AttachmentType

enum class JetpackTier(
    val abilityType: RegistryEntry.Nova<AbilityType<JetpackFlyAbility>>,
    val attachmentType: RegistryEntry.Nova<AttachmentType<*>>,
    val items: RegistryEntrySet.Paper<ItemType>,
) {
    
    BASIC(Abilities.BASIC_JETPACK_FLY, Attachments.BASIC_JETPACK, ItemTags.BASIC_JETPACKS),
    ADVANCED(Abilities.ADVANCED_JETPACK_FLY, Attachments.ADVANCED_JETPACK, ItemTags.ADVANCED_JETPACKS),
    ELITE(Abilities.ELITE_JETPACK_FLY, Attachments.ELITE_JETPACK, ItemTags.ELITE_JETPACKS),
    ULTIMATE(Abilities.ULTIMATE_JETPACK_FLY, Attachments.ULTIMATE_JETPACK, ItemTags.ULTIMATE_JETPACKS);
    
}