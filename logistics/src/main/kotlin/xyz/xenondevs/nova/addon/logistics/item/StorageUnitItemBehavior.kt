package xyz.xenondevs.nova.addon.logistics.item

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.nova.data.serialization.cbf.NamespacedCompound
import xyz.xenondevs.nova.item.behavior.ItemBehavior
import xyz.xenondevs.nova.item.logic.PacketItemData
import xyz.xenondevs.nova.tileentity.TileEntity
import xyz.xenondevs.nova.util.item.ItemUtils

object StorageUnitItemBehavior : ItemBehavior {
    
    override fun updatePacketItemData(data: NamespacedCompound, itemData: PacketItemData) {
        val tileEntityData: Compound = data[TileEntity.TILE_ENTITY_DATA_KEY] ?: return
        val type: ItemStack = tileEntityData["type"] ?: return
        val amount: Int = tileEntityData["amount"] ?: return
        
        itemData.addLore(
            Component.text()
                .color(NamedTextColor.GRAY)
                .append(Component.translatable("${amount}x "))
                .append(ItemUtils.getName(type))
                .build()
        )
    }
    
}