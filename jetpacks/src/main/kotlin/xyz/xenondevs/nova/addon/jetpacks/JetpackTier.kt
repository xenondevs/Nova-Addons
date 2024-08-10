package xyz.xenondevs.nova.addon.jetpacks

import xyz.xenondevs.nova.addon.jetpacks.ability.JetpackFlyAbility
import xyz.xenondevs.nova.addon.jetpacks.registry.Abilities
import xyz.xenondevs.nova.addon.jetpacks.registry.Attachments
import xyz.xenondevs.nova.addon.jetpacks.registry.Items
import xyz.xenondevs.nova.world.item.NovaItem
import xyz.xenondevs.nova.world.player.ability.AbilityType
import xyz.xenondevs.nova.world.player.attachment.AttachmentType

enum class JetpackTier(
    lazyAbilityType: () -> AbilityType<JetpackFlyAbility>,
    lazyAttachmentType: () -> AttachmentType<*>,
    lazyItems: () -> List<NovaItem>
) {
    
    BASIC({ Abilities.BASIC_JETPACK_FLY }, { Attachments.BASIC_JETPACK }, { listOf(Items.BASIC_JETPACK, Items.ARMORED_BASIC_JETPACK) }),
    ADVANCED({ Abilities.ADVANCED_JETPACK_FLY }, { Attachments.ADVANCED_JETPACK }, { listOf(Items.ADVANCED_JETPACK, Items.ARMORED_ADVANCED_JETPACK) }),
    ELITE({ Abilities.ELITE_JETPACK_FLY }, { Attachments.ELITE_JETPACK }, { listOf(Items.ELITE_JETPACK, Items.ARMORED_ELITE_JETPACK) }),
    ULTIMATE({ Abilities.ULTIMATE_JETPACK_FLY }, { Attachments.ULTIMATE_JETPACK }, { listOf(Items.ULTIMATE_JETPACK, Items.ARMORED_ULTIMATE_JETPACK) });
    
    val abilityType by lazy(lazyAbilityType)
    val attachmentType by lazy(lazyAttachmentType)
    val items by lazy(lazyItems)
    
}