package xyz.xenondevs.nova.addon.jetpacks.item

import io.papermc.paper.event.entity.EntityEquipmentChangedEvent
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.nova.addon.jetpacks.JetpackTier
import xyz.xenondevs.nova.world.item.behavior.ItemBehavior
import xyz.xenondevs.nova.world.player.ability.AbilityManager
import xyz.xenondevs.nova.world.player.attachment.AttachmentManager

class JetpackBehavior(
    private val tier: JetpackTier
) : ItemBehavior {
    
    override fun handleEquip(player: Player, itemStack: ItemStack, slot: EquipmentSlot, equipped: Boolean, event: EntityEquipmentChangedEvent) {
        if (!slot.isArmor)
            return
        
        if (equipped) {
            AttachmentManager.addAttachment(player, tier.attachmentType)
            AbilityManager.giveAbility(player, tier.abilityType)
        } else {
            AttachmentManager.removeAttachment(player, tier.attachmentType)
            AbilityManager.takeAbility(player, tier.abilityType)
        }
    }
    
}