package xyz.xenondevs.nova.addon.logistics.item

import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.commons.collections.after
import xyz.xenondevs.commons.collections.firstInstanceOfOrNull
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.provider
import xyz.xenondevs.nova.addon.logistics.Logistics
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.serialization.cbf.NamespacedCompound
import xyz.xenondevs.nova.util.BlockUtils
import xyz.xenondevs.nova.util.Key
import xyz.xenondevs.nova.util.item.retrieveData
import xyz.xenondevs.nova.util.item.storeData
import xyz.xenondevs.nova.util.runTask
import xyz.xenondevs.nova.util.toString
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.tileentity.network.NetworkManager
import xyz.xenondevs.nova.world.block.tileentity.network.node.ContainerEndPointDataHolder
import xyz.xenondevs.nova.world.block.tileentity.network.node.NetworkEndPoint
import xyz.xenondevs.nova.world.block.tileentity.network.type.DefaultNetworkTypes
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkType
import xyz.xenondevs.nova.world.block.tileentity.network.type.energy.holder.EnergyHolder
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.holder.FluidHolder
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.holder.ItemHolder
import xyz.xenondevs.nova.world.format.NetworkState
import xyz.xenondevs.nova.world.item.behavior.ItemBehavior
import xyz.xenondevs.nova.world.player.WrappedPlayerInteractEvent
import xyz.xenondevs.nova.world.player.swingHandEventless
import xyz.xenondevs.nova.world.pos

internal object WrenchBehavior : ItemBehavior {
    
    private val WRENCH_MODE_KEY = Key(Logistics, "wrench_mode")
    private val NETWORK_TYPES = arrayOf(DefaultNetworkTypes.ENERGY, DefaultNetworkTypes.ITEM, DefaultNetworkTypes.FLUID)
    
    override val defaultCompound: Provider<NamespacedCompound> = provider {
        NamespacedCompound().apply {
            this[WRENCH_MODE_KEY] = DefaultNetworkTypes.ITEM.id.toString()
        }
    }
    
    private var ItemStack.wrenchMode: NetworkType<*>
        get() = retrieveData(WRENCH_MODE_KEY) ?: DefaultNetworkTypes.ITEM
        set(mode) {
            storeData(WRENCH_MODE_KEY, mode)
        }
    
    override fun handleInteract(player: Player, itemStack: ItemStack, action: Action, wrappedEvent: WrappedPlayerInteractEvent) {
        if (wrappedEvent.actionPerformed || !player.isSneaking || !action.isRightClick)
            return
        
        if (action == Action.RIGHT_CLICK_BLOCK) {
            cycleEndPointConfig(player, wrappedEvent.event.clickedBlock!!.pos, itemStack, wrappedEvent)
        } else {
            cycleWrenchMode(player, itemStack, wrappedEvent)
        }
    }
    
    private fun cycleWrenchMode(player: Player, itemStack: ItemStack, event: WrappedPlayerInteractEvent) {
        val currentMode = itemStack.wrenchMode
        val newMode = NETWORK_TYPES[(NETWORK_TYPES.indexOf(currentMode) + 1) % NETWORK_TYPES.size]
        itemStack.wrenchMode = newMode
        
        player.swingHandEventless(event.event.hand!!)
        player.sendActionBar(
            Component.translatable(
                "item.logistics.wrench.toggle_mode",
                Component.translatable("item.logistics.wrench.network.${newMode.id.toString(".")}")
            )
        )
        
        event.event.isCancelled = true
        event.actionPerformed = true
    }
    
    private fun cycleEndPointConfig(player: Player, pos: BlockPos, itemStack: ItemStack, wrappedEvent: WrappedPlayerInteractEvent) {
        val event = wrappedEvent.event
        val endPoint = runBlocking { NetworkManager.getNode(pos) } // pos is already loaded, so this doesn't block
        if (endPoint is NetworkEndPoint) {
            val mode = itemStack.wrenchMode
            val face = event.blockFace
            
            event.isCancelled = true
            wrappedEvent.actionPerformed = true
            
            if (
                ProtectionManager.canUseBlock(player, itemStack, pos) &&
                ProtectionManager.canUseBlock(player, itemStack, pos.advance(face))
            ) {
                cycleEndPointConfig(player, endPoint, mode, face)
            }
        }
    }
    
    private fun cycleEndPointConfig(player: Player, endPoint: NetworkEndPoint, netType: NetworkType<*>, face: BlockFace) {
        NetworkManager.queue(endPoint.pos.chunkPos) { state ->
            val conType = when (netType) {
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
    
    private fun runPostCyclingActions(
        player: Player,
        endPoint: NetworkEndPoint,
        netType: NetworkType<*>, face: BlockFace,
        conType: NetworkConnectionType
    ) {
        player.swingMainHand()
        player.sendActionBar(Component.translatable(
            "item.logistics.wrench.use",
            Component.translatable("item.logistics.wrench.face.${face.name.lowercase()}"),
            BlockUtils.getName(endPoint.pos.block),
            Component.translatable("item.logistics.wrench.connection.${conType.name.lowercase()}"),
            Component.translatable("item.logistics.wrench.network.${netType.id.toString(".")}"),
        ))
    }
    
    private suspend fun cycleConnectionConfig(
        state: NetworkState,
        endPoint: NetworkEndPoint, energyHolder: EnergyHolder,
        type: NetworkType<*>, face: BlockFace
    ): NetworkConnectionType {
        if (face in energyHolder.blockedFaces)
            return NetworkConnectionType.NONE
        
        val currentType = energyHolder.connectionConfig[face]!!
        val newType = energyHolder.allowedConnectionType.supertypes.after(currentType)
        energyHolder.connectionConfig[face] = newType
        
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
        
        val currentType = holder.connectionConfig[face]!!
        val container = holder.containerConfig[face]!!
        val newType = holder.containers[container]!!.supertypes.after(currentType)
        holder.connectionConfig[face] = newType
        
        if (newType != currentType) {
            state.getNetwork(endPoint, type, face)?.markDirty()
        }
        
        return newType
    }
    
}