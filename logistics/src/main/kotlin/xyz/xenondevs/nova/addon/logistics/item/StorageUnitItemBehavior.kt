package xyz.xenondevs.nova.addon.logistics.item

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.nova.util.component.adventure.withoutPreFormatting
import xyz.xenondevs.nova.util.item.ItemUtils
import xyz.xenondevs.nova.util.item.retrieveData
import xyz.xenondevs.nova.world.block.tileentity.TileEntity
import xyz.xenondevs.nova.world.item.behavior.ItemBehavior

object StorageUnitItemBehavior : ItemBehavior {
    
    override fun modifyClientSideStack(player: Player?, server: ItemStack, client: ItemStack): ItemStack {
        val tileEntityData: Compound = server.retrieveData(TileEntity.TILE_ENTITY_DATA_KEY) ?: return client
        val type: ItemStack = tileEntityData["type"] ?: return client
        val amount: Int = tileEntityData["amount"] ?: return client
        
        val lore = client.lore() ?: mutableListOf()
        lore += Component.text()
            .color(NamedTextColor.GRAY)
            .append(Component.translatable("${amount}x "))
            .append(ItemUtils.getName(type))
            .build()
            .withoutPreFormatting()
        client.lore(lore)
        
        return client
    }
    
}