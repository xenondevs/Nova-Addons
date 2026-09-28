package xyz.xenondevs.nova.addon.logistics.tileentity

import com.google.common.collect.Table
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.joml.Math
import org.joml.Matrix4d
import org.joml.Quaternionf
import org.joml.Vector3d
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.firstInstanceOfOrNull
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.combinedProvider
import xyz.xenondevs.commons.provider.provider
import xyz.xenondevs.nova.addon.logistics.gui.cable.CableConfigMenu
import xyz.xenondevs.nova.addon.logistics.registry.BlockStateProperties
import xyz.xenondevs.nova.addon.logistics.registry.Blocks
import xyz.xenondevs.nova.addon.logistics.registry.Items
import xyz.xenondevs.nova.addon.logistics.registry.Models
import xyz.xenondevs.nova.addon.logistics.util.MathUtils
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.BlockBreak
import xyz.xenondevs.nova.context.intention.BlockPlace
import xyz.xenondevs.nova.packetentity.PacketItemDisplay
import xyz.xenondevs.nova.packetentity.packetItemDisplay
import xyz.xenondevs.nova.packetentity.updateMetadata
import xyz.xenondevs.nova.registry.RegistryEntry
import xyz.xenondevs.nova.registry.registryEntrySetOf
import xyz.xenondevs.nova.util.CUBE_FACES
import xyz.xenondevs.nova.util.CubeFaceMap
import xyz.xenondevs.nova.util.CubeFaceSet
import xyz.xenondevs.nova.util.LocationUtils
import xyz.xenondevs.nova.util.add
import xyz.xenondevs.nova.util.forEachNonNull
import xyz.xenondevs.nova.util.item.setCustomModelDataFloat
import xyz.xenondevs.nova.util.pitch
import xyz.xenondevs.nova.util.runTask
import xyz.xenondevs.nova.util.yaw
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.world.block.hitbox.Hitbox
import xyz.xenondevs.nova.world.block.hitbox.VirtualHitbox
import xyz.xenondevs.nova.world.block.tileentity.TileEntity
import xyz.xenondevs.nova.world.block.tileentity.network.NetworkManager
import xyz.xenondevs.nova.world.block.tileentity.network.node.NetworkEndPoint
import xyz.xenondevs.nova.world.block.tileentity.network.node.NetworkNode
import xyz.xenondevs.nova.world.block.tileentity.network.type.DefaultNetworkTypes.ENERGY
import xyz.xenondevs.nova.world.block.tileentity.network.type.DefaultNetworkTypes.FLUID
import xyz.xenondevs.nova.world.block.tileentity.network.type.DefaultNetworkTypes.ITEM
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkType
import xyz.xenondevs.nova.world.block.tileentity.network.type.energy.EnergyBridge
import xyz.xenondevs.nova.world.block.tileentity.network.type.energy.EnergyNetwork
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidBridge
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidNetwork
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.holder.FluidHolder
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.ItemBridge
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.ItemNetwork
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.holder.ItemHolder
import xyz.xenondevs.nova.world.chunkPos
import xyz.xenondevs.nova.world.format.NetworkState
import xyz.xenondevs.nova.world.item.guiItemProvider
import xyz.xenondevs.nova.world.item.itemTypeEntry
import xyz.xenondevs.nova.world.player.swingMainHandEventless
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val SUPPORTED_NETWORK_TYPES by registryEntrySetOf(ENERGY, ITEM, FLUID)

private val BASIC_ENERGY_TRANSFER_RATE = energyTransferRate(Blocks.BASIC_CABLE)
private val BASIC_ITEM_TRANSFER_RATE = itemTransferRate(Blocks.BASIC_CABLE)
private val BASIC_FLUID_TRANSFER_RATE = fluidTransferRate(Blocks.BASIC_CABLE)

private val ADVANCED_ENERGY_TRANSFER_RATE = energyTransferRate(Blocks.ADVANCED_CABLE)
private val ADVANCED_ITEM_TRANSFER_RATE = itemTransferRate(Blocks.ADVANCED_CABLE)
private val ADVANCED_FLUID_TRANSFER_RATE = fluidTransferRate(Blocks.ADVANCED_CABLE)

private val ELITE_ENERGY_TRANSFER_RATE = energyTransferRate(Blocks.ELITE_CABLE)
private val ELITE_ITEM_TRANSFER_RATE = itemTransferRate(Blocks.ELITE_CABLE)
private val ELITE_FLUID_TRANSFER_RATE = fluidTransferRate(Blocks.ELITE_CABLE)

private val ULTIMATE_ENERGY_TRANSFER_RATE = energyTransferRate(Blocks.ULTIMATE_CABLE)
private val ULTIMATE_ITEM_TRANSFER_RATE = itemTransferRate(Blocks.ULTIMATE_CABLE)
private val ULTIMATE_FLUID_TRANSFER_RATE = fluidTransferRate(Blocks.ULTIMATE_CABLE)

private val CREATIVE_ENERGY_TRANSFER_RATE = provider(Long.MAX_VALUE)
private val CREATIVE_ITEM_TRANSFER_RATE = provider(Int.MAX_VALUE)
private val CREATIVE_FLUID_TRANSFER_RATE = provider(Long.MAX_VALUE)

private val NetworkNode.itemHolder: ItemHolder?
    get() = (this as? NetworkEndPoint)?.holders?.firstInstanceOfOrNull<ItemHolder>()

private val NetworkNode.fluidHolder: FluidHolder?
    get() = (this as? NetworkEndPoint)?.holders?.firstInstanceOfOrNull<FluidHolder>()

abstract class AbstractCable(
    private val unfacaded: RegistryEntry.Paper<BlockType>,
    energyTransferRateDelegate: Provider<Long>,
    itemTransferRateDelegate: Provider<Int>,
    fluidTransferRateDelegate: Provider<Long>,
    pos: Block,
    state: NovaBlockState,
    data: Compound
) : TileEntity(pos, state, data), EnergyBridge, ItemBridge, FluidBridge {
    
    @Volatile
    final override var isValid = false
    
    final override val energyTransferRate by energyTransferRateDelegate
    final override val itemTransferRate by itemTransferRateDelegate
    final override val fluidTransferRate by fluidTransferRateDelegate
    final override val linkedNodes: Set<NetworkNode> = emptySet()
    final override val typeId get() = unfacaded.key
    
    override fun handleEnable() {
        super.handleEnable()
        isValid = true
    }
    
    override fun handleDisable() {
        super.handleDisable()
        isValid = false
    }
    
    override fun handlePlace(ctx: Context<BlockPlace>) {
        super.handlePlace(ctx)
        NetworkManager.queueAddBridge(this, SUPPORTED_NETWORK_TYPES, CubeFaceSet.ALL)
        isValid = true
    }
    
    override fun handleBreak(ctx: Context<BlockBreak>) {
        super.handleBreak(ctx)
        NetworkManager.queueRemoveBridge(this)
        isValid = false
    }
    
    override fun getDrops(includeSelf: Boolean): List<ItemStack> {
        if (!includeSelf)
            return []
        return [unfacaded.get().itemType.createItemStack()]
    }
    
    override fun handleTick() = Unit
    
}

abstract class UnfacadedCable(
    unfacaded: RegistryEntry.Paper<BlockType>,
    energyTransferRateDelegate: Provider<Long>,
    itemTransferRateDelegate: Provider<Int>,
    fluidTransferRateDelegate: Provider<Long>,
    pos: Block,
    state: NovaBlockState,
    data: Compound
) : AbstractCable(
    unfacaded,
    energyTransferRateDelegate,
    itemTransferRateDelegate,
    fluidTransferRateDelegate,
    pos,
    state,
    data
) {
    
    private val configMenus = ConcurrentHashMap<BlockFace, CableConfigMenu>()
    private var hitboxes: Set<Hitbox<*, *>> = emptySet()
    private var attachmentDisplays: CubeFaceMap<PacketItemDisplay?> = CubeFaceMap.NULL
    
    override fun handleDisable() {
        super.handleDisable()
        attachmentDisplays.forEachNonNull(PacketItemDisplay::despawn)
        attachmentDisplays = CubeFaceMap.NULL
        hitboxes.forEach { it.remove() }
    }
    
    override suspend fun handleNetworkLoaded(state: NetworkState) {
        val connectedNodes = state.getConnectedNodes(this)
        val attachments = calculateAttachmentModelIds(connectedNodes)
        val hitboxes = createHitboxes(state.getBridgeFaces(this), connectedNodes)
        
        runTask {
            if (isEnabled) {
                updateAttachmentModels(attachments)
                updateHitboxes(hitboxes)
            }
        }
    }
    
    override suspend fun handleNetworkUpdate(state: NetworkState) {
        val connectedNodes = state.getConnectedNodes(this)
        
        // update block state, attachment model, and hitboxes
        val newBlockState = calculateCableBlockState(connectedNodes)
        val attachments = calculateAttachmentModelIds(connectedNodes)
        val hitboxes = createHitboxes(state.getBridgeFaces(this), connectedNodes)
        runTask {
            if (isEnabled) {
                updateBlockState(newBlockState)
                updateAttachmentModels(attachments)
                updateHitboxes(hitboxes)
            }
        }
        
        // update config menus or close them if necessary
        for ((face, gui) in configMenus) {
            fun closeAndRemove() {
                runTask { gui.closeForAllViewers() }
                configMenus.remove(face)
            }
            
            val neighbor = connectedNodes[ITEM, face]
            if (neighbor is NetworkEndPoint) {
                val itemHolder = neighbor.holders.firstInstanceOfOrNull<ItemHolder>()
                val fluidHolder = neighbor.holders.firstInstanceOfOrNull<FluidHolder>()
                
                if (gui.itemHolder == itemHolder && gui.fluidHolder == fluidHolder) {
                    gui.updateValues()
                    runTask { gui.updateGui() }
                } else closeAndRemove()
            } else closeAndRemove()
        }
    }
    
    private fun calculateCableBlockState(connectedNodes: Table<NetworkType<*>, BlockFace, NetworkNode>): NovaBlockState {
        return blockState.apply {
            set(BlockStateProperties.NORTH, BlockFace.NORTH in connectedNodes.columnKeySet())
            set(BlockStateProperties.EAST, BlockFace.EAST in connectedNodes.columnKeySet())
            set(BlockStateProperties.SOUTH, BlockFace.SOUTH in connectedNodes.columnKeySet())
            set(BlockStateProperties.WEST, BlockFace.WEST in connectedNodes.columnKeySet())
            set(BlockStateProperties.UP, BlockFace.UP in connectedNodes.columnKeySet())
            set(BlockStateProperties.DOWN, BlockFace.DOWN in connectedNodes.columnKeySet())
        }
    }
    
    private fun calculateAttachmentModelIds(connectedNodes: Table<NetworkType<*>, BlockFace, NetworkNode>) = CubeFaceMap { face ->
        val itemHolder = connectedNodes[ITEM.get(), face]?.itemHolder
        val fluidHolder = connectedNodes[FLUID.get(), face]?.fluidHolder
        
        if (itemHolder == null && fluidHolder == null)
            return@CubeFaceMap null
        
        val oppositeFace = face.oppositeFace
        val array = booleanArrayOf(
            fluidHolder?.connectionConfig?.get(oppositeFace)?.insert ?: false,
            fluidHolder?.connectionConfig?.get(oppositeFace)?.extract ?: false,
            itemHolder?.connectionConfig?.get(oppositeFace)?.insert ?: false,
            itemHolder?.connectionConfig?.get(oppositeFace)?.extract ?: false,
        )
        
        return@CubeFaceMap MathUtils.encodeToInt(array)
    }
    
    private fun createHitboxes(
        bridgeFaces: CubeFaceSet,
        connectedNodes: Table<NetworkType<*>, BlockFace, NetworkNode>
    ): Set<Hitbox<*, *>> {
        val hitboxes = HashSet<Hitbox<*, *>>()
        for (face in CUBE_FACES) {
            if (face in connectedNodes.columnKeySet()) {
                hitboxes += createCableHitbox(face, false)
            } else if (face !in bridgeFaces) {
                hitboxes += createCableHitbox(face, true)
            }
            
            if (connectedNodes[ITEM.get(), face] is NetworkEndPoint || connectedNodes[FLUID.get(), face] is NetworkEndPoint)
                hitboxes += createAttachmentHitbox(face)
        }
        return hitboxes
    }
    
    private fun createCableHitbox(face: BlockFace, long: Boolean): VirtualHitbox {
        val pointA = Vector3d(0.4, 0.4, 0.5)
        val pointB = Vector3d(0.6, 0.6, if (long) 1.5 else 1.0)
        val (from, to) = createHitboxPoints(pointA, pointB, face)
        
        return VirtualHitbox(from, to).apply {
            setQualifier { player, _ -> player.inventory.itemInMainHand.itemTypeEntry == Items.WRENCH }
            addRightClickHandler { player, _ ->
                cycleBridgeFaces(face)
                player.swingMainHandEventless()
            }
        }
    }
    
    private fun createAttachmentHitbox(face: BlockFace): VirtualHitbox {
        val pointA = Vector3d(0.125, 0.125, 0.875)
        val pointB = Vector3d(0.875, 0.875, 1.0)
        val (from, to) = createHitboxPoints(pointA, pointB, face)
        
        return VirtualHitbox(from, to).apply {
            addRightClickHandler { player, _ -> openAttachmentWindow(player, face) }
        }
    }
    
    private fun createHitboxPoints(a: Vector3d, b: Vector3d, face: BlockFace): Pair<Location, Location> {
        val origin = Vector3d(0.5, 0.5, 0.5)
        
        val transform = Matrix4d()
            .translate(origin)
            .rotateX(Math.toRadians(face.pitch.toDouble()))
            .rotateY(-Math.toRadians(face.yaw.toDouble()))
            .translate(origin.negate())
        
        return LocationUtils.sort(
            block.location.add(a.mulPosition(transform)),
            block.location.add(b.mulPosition(transform))
        )
    }
    
    private fun updateAttachmentModels(attachments: CubeFaceMap<Int?>) {
        attachments.forEach { face, id ->
            val display = attachmentDisplays[face]
            
            if (id == null) {
                display?.despawn()
                attachmentDisplays = attachmentDisplays.with(face, null)
            } else if (display == null) {
                val newDisplay = createAttachmentDisplay(face, id).apply(PacketItemDisplay::spawn)
                attachmentDisplays = attachmentDisplays.with(face, newDisplay)
            } else {
                listOf(display).updateMetadata {
                    itemStack = createAttachmentItem(id)
                }
            }
        }
    }
    
    private fun createAttachmentDisplay(face: BlockFace, id: Int) = packetItemDisplay {
        location by block.location.add(.5, .5, .5)
        metadata {
            itemStack by createAttachmentItem(id)
            // attachment models face south, display entities make north side of models face south,
            // therefore attachments face north by default TODO: make attachment models face north
            leftRotation by attachmentRotation(face)
        }
    }
    
    private fun createAttachmentItem(id: Int): ItemStack =
        Models.CABLE_ATTACHMENT.guiItemProvider.get().get().apply {
            setCustomModelDataFloat(0, id.toFloat())
        }
    
    private fun attachmentRotation(face: BlockFace) =
        Quaternionf()
            .rotateY(Math.toRadians(180 - face.yaw))
            .rotateX(Math.toRadians(-face.pitch))
    
    private fun updateHitboxes(hitboxes: Set<Hitbox<*, *>>) {
        this.hitboxes.forEach { it.remove() }
        this.hitboxes = hitboxes
        this.hitboxes.forEach { it.register() }
    }
    
    private fun openAttachmentWindow(player: Player, face: BlockFace) {
        if (configMenus.containsKey(face)) {
            configMenus[face]?.openWindow(player)
        } else {
            NetworkManager.queueRead(block.chunkPos) { state ->
                val endPoint = state.getConnectedNode(this, face) as? NetworkEndPoint
                    ?: return@queueRead
                
                val gui = configMenus.computeIfAbsent(face) {
                    CableConfigMenu(
                        this@UnfacadedCable,
                        endPoint,
                        endPoint.itemHolder,
                        endPoint.fluidHolder,
                        face.oppositeFace
                    )
                }
                runTask { gui.openWindow(player) }
            }
        }
    }
    
    private fun cycleBridgeFaces(face: BlockFace) {
        NetworkManager.queueRead(block.chunkPos) { state ->
            var bridgeFaces = state.getBridgeFaces(this)
            if (face in bridgeFaces) {
                bridgeFaces -= face
            } else {
                bridgeFaces += face
            }
            
            // this approach is not ideal, but alternatively we'd have to
            // do network splitting & recalculation here
            NetworkManager.queueRemoveBridge(this)
            NetworkManager.queueAddBridge(this, SUPPORTED_NETWORK_TYPES, bridgeFaces)
        }
    }
    
    override fun handleTick() = Unit
    
}

abstract class FacadedCable(
    unfacaded: RegistryEntry.Paper<BlockType>,
    energyTransferRateDelegate: Provider<Long>,
    itemTransferRateDelegate: Provider<Int>,
    fluidTransferRateDelegate: Provider<Long>,
    pos: Block,
    state: NovaBlockState,
    data: Compound
) : AbstractCable(
    unfacaded,
    energyTransferRateDelegate,
    itemTransferRateDelegate,
    fluidTransferRateDelegate,
    pos,
    state,
    data
) {
    
    override fun getDrops(includeSelf: Boolean): List<ItemStack> {
        if (!includeSelf)
            return []
        
        val facadeStack = Items.CABLE_FACADES[blockState[BlockStateProperties.FACADE]]!!.get().createItemStack()
        return super.getDrops(includeSelf) + facadeStack
    }
    
}

class BasicCable(block: Block, state: NovaBlockState, data: Compound) : UnfacadedCable(
    Blocks.BASIC_CABLE,
    BASIC_ENERGY_TRANSFER_RATE,
    BASIC_ITEM_TRANSFER_RATE,
    BASIC_FLUID_TRANSFER_RATE,
    block, state, data
)

class AdvancedCable(block: Block, state: NovaBlockState, data: Compound) : UnfacadedCable(
    Blocks.ADVANCED_CABLE,
    ADVANCED_ENERGY_TRANSFER_RATE,
    ADVANCED_ITEM_TRANSFER_RATE,
    ADVANCED_FLUID_TRANSFER_RATE,
    block, state, data
)

class EliteCable(block: Block, state: NovaBlockState, data: Compound) : UnfacadedCable(
    Blocks.ELITE_CABLE,
    ELITE_ENERGY_TRANSFER_RATE,
    ELITE_ITEM_TRANSFER_RATE,
    ELITE_FLUID_TRANSFER_RATE,
    block, state, data
)

class UltimateCable(block: Block, state: NovaBlockState, data: Compound) : UnfacadedCable(
    Blocks.ULTIMATE_CABLE,
    ULTIMATE_ENERGY_TRANSFER_RATE,
    ULTIMATE_ITEM_TRANSFER_RATE,
    ULTIMATE_FLUID_TRANSFER_RATE,
    block, state, data
)

class CreativeCable(block: Block, state: NovaBlockState, data: Compound) : UnfacadedCable(
    Blocks.CREATIVE_CABLE,
    CREATIVE_ENERGY_TRANSFER_RATE,
    CREATIVE_ITEM_TRANSFER_RATE,
    CREATIVE_FLUID_TRANSFER_RATE,
    block, state, data
)

class FacadedBasicCable(block: Block, state: NovaBlockState, data: Compound) : FacadedCable(
    Blocks.BASIC_CABLE,
    BASIC_ENERGY_TRANSFER_RATE,
    BASIC_ITEM_TRANSFER_RATE,
    BASIC_FLUID_TRANSFER_RATE,
    block, state, data
)

class FacadedAdvancedCable(block: Block, state: NovaBlockState, data: Compound) : FacadedCable(
    Blocks.ADVANCED_CABLE,
    ADVANCED_ENERGY_TRANSFER_RATE,
    ADVANCED_ITEM_TRANSFER_RATE,
    ADVANCED_FLUID_TRANSFER_RATE,
    block, state, data
)

class FacadedEliteCable(block: Block, state: NovaBlockState, data: Compound) : FacadedCable(
    Blocks.ELITE_CABLE,
    ELITE_ENERGY_TRANSFER_RATE,
    ELITE_ITEM_TRANSFER_RATE,
    ELITE_FLUID_TRANSFER_RATE,
    block, state, data
)

class FacadedUltimateCable(block: Block, state: NovaBlockState, data: Compound) : FacadedCable(
    Blocks.ULTIMATE_CABLE,
    ULTIMATE_ENERGY_TRANSFER_RATE,
    ULTIMATE_ITEM_TRANSFER_RATE,
    ULTIMATE_FLUID_TRANSFER_RATE,
    block, state, data
)

class FacadedCreativeCable(block: Block, state: NovaBlockState, data: Compound) : FacadedCable(
    Blocks.CREATIVE_CABLE,
    CREATIVE_ENERGY_TRANSFER_RATE,
    CREATIVE_ITEM_TRANSFER_RATE,
    CREATIVE_FLUID_TRANSFER_RATE,
    block, state, data
)

private fun energyTransferRate(block: RegistryEntry.Paper<BlockType>): Provider<Long> =
    combinedProvider(
        block.config.entry<Double>("energy_transfer_rate"),
        EnergyNetwork.TICK_DELAY_PROVIDER
    ).map { (transferRate, tickDelay) -> (transferRate * tickDelay).roundToLong() }

private fun itemTransferRate(block: RegistryEntry.Paper<BlockType>): Provider<Int> =
    combinedProvider(
        block.config.entry<Double>("item_transfer_rate"),
        ItemNetwork.TICK_DELAY_PROVIDER
    ).map { (transferRate, tickDelay) -> (transferRate * tickDelay).roundToInt() }

private fun fluidTransferRate(block: RegistryEntry.Paper<BlockType>): Provider<Long> =
    combinedProvider(
        block.config.entry<Double>("fluid_transfer_rate"),
        FluidNetwork.TICK_DELAY_PROVIDER
    ).map { (transferRate, tickDelay) -> (transferRate * tickDelay).roundToLong() }
