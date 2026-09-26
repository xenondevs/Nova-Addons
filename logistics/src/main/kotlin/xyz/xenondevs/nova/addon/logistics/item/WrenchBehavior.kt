package xyz.xenondevs.nova.addon.logistics.item

import net.kyori.adventure.key.Key.key
import net.kyori.adventure.text.Component
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.commons.collections.after
import xyz.xenondevs.commons.collections.firstInstanceOfOrNull
import xyz.xenondevs.nova.addon.logistics.Logistics
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.BlockInteract
import xyz.xenondevs.nova.context.intention.ItemUse
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.registry.registryEntrySetOf
import xyz.xenondevs.nova.util.item.retrieveData
import xyz.xenondevs.nova.util.item.storeData
import xyz.xenondevs.nova.util.runTask
import xyz.xenondevs.nova.util.toString
import xyz.xenondevs.nova.world.InteractionResult
import xyz.xenondevs.nova.world.block.blockType
import xyz.xenondevs.nova.world.block.name
import xyz.xenondevs.nova.world.block.tileentity.network.NetworkManager
import xyz.xenondevs.nova.world.block.tileentity.network.node.ContainerEndPointDataHolder
import xyz.xenondevs.nova.world.block.tileentity.network.node.NetworkEndPoint
import xyz.xenondevs.nova.world.block.tileentity.network.type.DefaultNetworkTypes
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkType
import xyz.xenondevs.nova.world.block.tileentity.network.type.energy.holder.EnergyHolder
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.holder.FluidHolder
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.holder.ItemHolder
import xyz.xenondevs.nova.world.chunkPos
import xyz.xenondevs.nova.world.format.NetworkState
import xyz.xenondevs.nova.world.item.ItemAction
import xyz.xenondevs.nova.world.item.behavior.ItemBehavior

internal object WrenchBehavior : ItemBehavior {
    
    private val WRENCH_MODE_KEY = key(Logistics, "wrench_mode")
    private val NETWORK_TYPES by registryEntrySetOf(DefaultNetworkTypes.ENERGY, DefaultNetworkTypes.ITEM, DefaultNetworkTypes.FLUID).map { it.toList() }
    
    private var ItemStack.wrenchMode: NetworkType<*>
        get() = retrieveData(WRENCH_MODE_KEY) ?: DefaultNetworkTypes.ITEM.get()
        set(mode) {
            storeData(WRENCH_MODE_KEY, mode)
        }
    
    // cycle end point config
    override fun useOnBlock(itemStack: ItemStack, block: Block, ctx: Context<BlockInteract>): InteractionResult {
        val player = ctx[BlockInteract.SOURCE_PLAYER]
            ?: return InteractionResult.Pass
        val face = ctx[BlockInteract.CLICKED_BLOCK_FACE]
            ?: return InteractionResult.Pass
        
        val endPoint = NetworkManager.getNode(block)
        if (endPoint is NetworkEndPoint) {
            val mode = itemStack.wrenchMode
            
            if (
                ProtectionManager.canUseBlock(player, itemStack, block) &&
                ProtectionManager.canUseBlock(player, itemStack, block.getRelative(face))
            ) {
                cycleEndPointConfig(player, endPoint, mode, face)
            }
            
            return InteractionResult.Success(swing = true, action = ItemAction.None)
        }
        
        return InteractionResult.Pass
    }
    
    private fun cycleEndPointConfig(player: Player, endPoint: NetworkEndPoint, netType: NetworkType<*>, face: BlockFace) {
        NetworkManager.queue(endPoint.block.chunkPos) { state ->
            val conType = when (netType.entry) {
                DefaultNetworkTypes.ENERGY -> {
                    val energyHolder = endPoint.holders.firstInstanceOfOrNull<EnergyHolder>()
                        ?: return@queue false
                    cycleConnectionConfig(state, endPoint, energyHolder, netType, face)
                }
                
                DefaultNetworkTypes.ITEM -> {
                    val itemHolder = endPoint.holders.firstInstanceOfOrNull<ItemHolder>()
                        ?: return@queue false
                    cycleConnectionConfig(state, endPoint, itemHolder, netType, face)
                }
                
                DefaultNetworkTypes.FLUID -> {
                    val fluidHolder = endPoint.holders.firstInstanceOfOrNull<FluidHolder>()
                        ?: return@queue false
                    cycleConnectionConfig(state, endPoint, fluidHolder, netType, face)
                }
                
                else -> throw UnsupportedOperationException()
            }
            
            state.handleEndPointAllowedFacesChange(endPoint, netType, face)
            runTask { runPostCyclingActions(player, endPoint, netType, face, conType) }
            return@queue true
        }
    }
    
    // cycle wrench mode
    override fun use(itemStack: ItemStack, ctx: Context<ItemUse>): InteractionResult {
        val currentMode = itemStack.wrenchMode
        val newMode = NETWORK_TYPES[(NETWORK_TYPES.indexOf(currentMode) + 1) % NETWORK_TYPES.size]
        itemStack.wrenchMode = newMode
        
        ctx[ItemUse.SOURCE_PLAYER]?.sendActionBar(
            Component.translatable(
                "item.logistics.wrench.toggle_mode",
                Component.translatable("item.logistics.wrench.network.${newMode.key.toString(".")}")
            )
        )
        
        return InteractionResult.Success(swing = true, ItemAction.ConvertStack(itemStack))
    }
    
    private fun runPostCyclingActions(
        player: Player,
        endPoint: NetworkEndPoint,
        netType: NetworkType<*>, face: BlockFace,
        conType: NetworkConnectionType
    ) {
        player.sendActionBar(Component.translatable(
            "item.logistics.wrench.use",
            Component.translatable("item.logistics.wrench.face.${face.name.lowercase()}"),
            endPoint.block.blockType.name,
            Component.translatable("item.logistics.wrench.connection.${conType.name.lowercase()}"),
            Component.translatable("item.logistics.wrench.network.${netType.key.toString(".")}"),
        ))
    }
    
    private suspend fun cycleConnectionConfig(
        state: NetworkState,
        endPoint: NetworkEndPoint, energyHolder: EnergyHolder,
        type: NetworkType<*>, face: BlockFace
    ): NetworkConnectionType {
        if (face in energyHolder.blockedFaces)
            return NetworkConnectionType.NONE
        
        val currentType = energyHolder.connectionConfig[face]
        val newType = energyHolder.allowedConnectionType.supertypes.after(currentType)
        energyHolder.connectionConfig = energyHolder.connectionConfig.with(face, newType)
        
        if (newType != currentType) {
            state.getNetwork(endPoint, type, face)?.markDirty()
        }
        
        return newType
    }
    
    private suspend fun cycleConnectionConfig(
        state: NetworkState,
        endPoint: NetworkEndPoint, holder: ContainerEndPointDataHolder<*>,
        type: NetworkType<*>, face: BlockFace
    ): NetworkConnectionType {
        if (face in holder.blockedFaces)
            return NetworkConnectionType.NONE
        
        val currentType = holder.connectionConfig[face]
        val container = holder.containerConfig[face]!!
        val newType = holder.containers[container]!!.supertypes.after(currentType)
        holder.connectionConfig = holder.connectionConfig.with(face, newType)
        
        if (newType != currentType) {
            state.getNetwork(endPoint, type, face)?.markDirty()
        }
        
        return newType
    }
    
}
