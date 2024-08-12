package xyz.xenondevs.nova.addon.logistics.item

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.nova.serialization.cbf.NamespacedCompound
import xyz.xenondevs.nova.util.component.adventure.withoutPreFormatting
import xyz.xenondevs.nova.util.item.ItemUtils
import xyz.xenondevs.nova.world.block.tileentity.TileEntity
import xyz.xenondevs.nova.world.item.behavior.ItemBehavior

object StorageUnitItemBehavior : ItemBehavior {
    
    override fun modifyClientSideStack(player: Player?, itemStack: ItemStack, data: NamespacedCompound): ItemStack {
        val tileEntityData: Compound = data[TileEntity.TILE_ENTITY_DATA_KEY] ?: return itemStack
        val type: ItemStack = tileEntityData["type"] ?: return itemStack
        val amount: Int = tileEntityData["amount"] ?: return itemStack
        
        val lore = itemStack.lore() ?: mutableListOf()
        lore += Component.text()
            .color(NamedTextColor.GRAY)
            .append(Component.translatable("${amount}x "))
            .append(ItemUtils.getName(type))
            .build()
            .withoutPreFormatting()
        itemStack.lore(lore)
        
        return itemStack
    }
    
}