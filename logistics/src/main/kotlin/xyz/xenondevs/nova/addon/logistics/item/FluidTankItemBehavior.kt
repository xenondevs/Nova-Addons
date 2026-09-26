package xyz.xenondevs.nova.addon.logistics.item

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.nova.addon.logistics.tileentity.FLUID_TANK_MAX_STATE
import xyz.xenondevs.nova.util.item.retrieveData
import xyz.xenondevs.nova.util.item.setCustomModelDataFloat
import xyz.xenondevs.nova.util.item.setCustomModelDataString
import xyz.xenondevs.nova.world.block.tileentity.TileEntity
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.world.item.behavior.ItemBehavior
import kotlin.math.round

class FluidTankItemBehavior(private val capacity: Provider<Long>) : ItemBehavior {
    
    override fun modifyClientSideStack(player: Player?, server: ItemStack, client: ItemStack): ItemStack {
        val container: Compound = server
            .retrieveData<Compound>(TileEntity.TILE_ENTITY_DATA_KEY)
            ?.get<Compound>("tank") 
            ?: return client
        val amount: Long = container["amount"] 
            ?: return client
        val type: FluidType = container["type"]
            ?: return client
        if (amount == 0L) 
            return client
        
        client.setCustomModelDataString(0, type.name.lowercase())
        client.setCustomModelDataFloat(0, round(amount.toFloat() / capacity.get().toFloat() * FLUID_TANK_MAX_STATE)
            .coerceIn(0f, FLUID_TANK_MAX_STATE.toFloat()))
        return client
    }
    
}
