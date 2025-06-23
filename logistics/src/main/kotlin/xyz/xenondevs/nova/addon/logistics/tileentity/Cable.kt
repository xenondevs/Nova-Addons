package xyz.xenondevs.nova.addon.logistics.tileentity

import com.google.common.collect.Table
import org.bukkit.Location
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.joml.Math
import org.joml.Matrix4d
import org.joml.Quaternionf
import org.joml.Vector3d
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.enumMap
import xyz.xenondevs.commons.collections.firstInstanceOfOrNull
import xyz.xenondevs.commons.collections.toEnumSet
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
import xyz.xenondevs.nova.context.intention.DefaultContextIntentions.BlockBreak
import xyz.xenondevs.nova.context.intention.DefaultContextIntentions.BlockPlace
import xyz.xenondevs.nova.context.param.DefaultContextParamTypes
import xyz.xenondevs.nova.util.BlockUtils
import xyz.xenondevs.nova.util.CUBE_FACES
import xyz.xenondevs.nova.util.LocationUtils
import xyz.xenondevs.nova.util.add
import xyz.xenondevs.nova.util.item.novaItem
import xyz.xenondevs.nova.util.pitch
import xyz.xenondevs.nova.util.runTask
import xyz.xenondevs.nova.util.yaw
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.NovaBlock
import xyz.xenondevs.nova.world.block.hitbox.Hitbox
import xyz.xenondevs.nova.world.block.hitbox.VirtualHitbox
import xyz.xenondevs.nova.world.block.state.NovaBlockState
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
import xyz.xenondevs.nova.world.format.NetworkState
import xyz.xenondevs.nova.world.model.FixedMultiModel
import xyz.xenondevs.nova.world.model.Model
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val SUPPORTED_NETWORK_TYPES = hashSetOf(ENERGY, ITEM, FLUID)

private val NetworkNode.itemHolder: ItemHolder?
    get() = (this as? NetworkEndPoint)?.holders?.firstInstanceOfOrNull<ItemHolder>()

private val NetworkNode.fluidHolder: FluidHolder?
    get() = (this as? NetworkEndPoint)?.holders?.firstInstanceOfOrNull<FluidHolder>()

open class Cable(
    energyTransferRateDelegate: Provider<Long>,
    itemTransferRateDelegate: Provider<Int>,
    fluidTransferRateDelegate: Provider<Long>,
    pos: BlockPos,
    state: NovaBlockState,
    data: Compound
) : TileEntity(pos, state, data), EnergyBridge, ItemBridge, FluidBridge {
    
    @Volatile
    override var isValid = false
    
    override val energyTransferRate by energyTransferRateDelegate
    override val itemTransferRate by itemTransferRateDelegate
    override val fluidTransferRate by fluidTransferRateDelegate
    override val linkedNodes: Set<NetworkNode> = emptySet()
    override val typeId get() = block.id
    
    private val configMenus = ConcurrentHashMap<BlockFace, CableConfigMenu>()
    
    private var hitboxes: Set<Hitbox<*, *>> = emptySet()
    private val multiModel = FixedMultiModel()
    
    override fun handleEnable() {
        super.handleEnable()
        
        // legacy conversion
        if (hasData("connectedNodes") || hasData("networks") || hasData("bridgeFaces")) {
            val bridgeFaces = retrieveDataOrNull("bridgeFaces") ?: CUBE_FACES
            NetworkManager.queueAddBridge(this, SUPPORTED_NETWORK_TYPES, bridgeFaces)
            
            removeData("connectedNodes")
            removeData("networks")
            removeData("bridgeFaces")
        }
        
        isValid = true
    }
    
    override fun handleDisable() {
        super.handleDisable()
        multiModel.clear()
        hitboxes.forEach { it.remove() }
        isValid = false
    }
    
    override fun handlePlace(ctx: Context<BlockPlace>) {
        super.handlePlace(ctx)
        NetworkManager.queueAddBridge(this, SUPPORTED_NETWORK_TYPES, CUBE_FACES)
        isValid = true
    }
    
    override fun handleBreak(ctx: Context<BlockBreak>) {
        super.handleBreak(ctx)
        NetworkManager.queueRemoveBridge(this)
        isValid = false
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
        return block.defaultBlockState.with(mapOf(
            BlockStateProperties.NORTH to (BlockFace.NORTH in connectedNodes.columnKeySet()),
            BlockStateProperties.EAST to (BlockFace.EAST in connectedNodes.columnKeySet()),
            BlockStateProperties.SOUTH to (BlockFace.SOUTH in connectedNodes.columnKeySet()),
            BlockStateProperties.WEST to (BlockFace.WEST in connectedNodes.columnKeySet()),
            BlockStateProperties.UP to (BlockFace.UP in connectedNodes.columnKeySet()),
            BlockStateProperties.DOWN to (BlockFace.DOWN in connectedNodes.columnKeySet())
        ))
    }
    
    private fun calculateAttachmentModelIds(connectedNodes: Table<NetworkType<*>, BlockFace, NetworkNode>): Map<BlockFace, Int> {
        val attachments = enumMap<BlockFace, Int>()
        
        for (face in CUBE_FACES) {
            val itemHolder = connectedNodes[ITEM, face]?.itemHolder
            val fluidHolder = connectedNodes[FLUID, face]?.fluidHolder
            
            if (itemHolder == null && fluidHolder == null)
                continue
            
            val oppositeFace = face.oppositeFace
            val array = booleanArrayOf(
                fluidHolder?.connectionConfig?.get(oppositeFace)?.insert ?: false,
                fluidHolder?.connectionConfig?.get(oppositeFace)?.extract ?: false,
                itemHolder?.connectionConfig?.get(oppositeFace)?.insert ?: false,
                itemHolder?.connectionConfig?.get(oppositeFace)?.extract ?: false,
            )
            
            attachments += face to MathUtils.encodeToInt(array)
        }
        
        return attachments
    }
    
    private fun createHitboxes(
        bridgeFaces: Set<BlockFace>,
        connectedNodes: Table<NetworkType<*>, BlockFace, NetworkNode>
    ): Set<Hitbox<*, *>> {
        val hitboxes = HashSet<Hitbox<*, *>>()
        for (face in CUBE_FACES) {
            if (face in connectedNodes.columnKeySet()) {
                hitboxes += createCableHitbox(face, false)
                hitboxes += createCableDestructionHitbox(face)
            } else if (face !in bridgeFaces) {
                hitboxes += createCableHitbox(face, true)
            }
            
            if (connectedNodes[ITEM, face] is NetworkEndPoint || connectedNodes[FLUID, face] is NetworkEndPoint)
                hitboxes += createAttachmentHitbox(face)
        }
        return hitboxes
    }
    
    private fun createCableHitbox(face: BlockFace, long: Boolean): VirtualHitbox {
        val pointA = Vector3d(0.4, 0.4, 0.5)
        val pointB = Vector3d(0.6, 0.6, if (long) 1.5 else 1.0)
        val (from, to) = createHitboxPoints(pointA, pointB, face)
        
        return VirtualHitbox(from, to).apply {
            setQualifier { player, _ -> player.inventory.itemInMainHand.novaItem == Items.WRENCH }
            addRightClickHandler { _, _ -> cycleBridgeFaces(face) }
        }
    }
    
    private fun createCableDestructionHitbox(face: BlockFace): VirtualHitbox {
        val pointA = Vector3d(0.4, 0.4, 0.5)
        val pointB = Vector3d(0.6, 0.6, 1.0)
        val (from, to) = createHitboxPoints(pointA, pointB, face)
        
        return VirtualHitbox(from, to).apply {
            addLeftClickHandler { player, _ ->
                val ctx = Context.intention(BlockBreak)
                    .param(DefaultContextParamTypes.BLOCK_POS, pos)
                    .param(DefaultContextParamTypes.SOURCE_PLAYER, player)
                    .build()
                BlockUtils.breakBlockNaturally(ctx)
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
            pos.location.add(a.mulPosition(transform)),
            pos.location.add(b.mulPosition(transform))
        )
    }
    
    private fun updateAttachmentModels(attachments: Map<BlockFace, Int>) {
        val models = HashSet<Model>()
        attachments.forEach { (face, id) ->
            models += Model(
                Models.CABLE_ATTACHMENT.createClientsideItemBuilder().addCustomModelData(id).get(),
                pos.location.add(.5, .5, .5),
                // attachment models face south, display entities make north side of models face south,
                // therefore attachments face north by default TODO: make attachment models face north
                leftRotation = Quaternionf()
                    .rotateY(Math.toRadians(180 - face.yaw))
                    .rotateX(Math.toRadians(-face.pitch))
            )
        }
        multiModel.replaceModels(models)
    }
    
    private fun updateHitboxes(hitboxes: Set<Hitbox<*, *>>) {
        this.hitboxes.forEach { it.remove() }
        this.hitboxes = hitboxes
        this.hitboxes.forEach { it.register() }
    }
    
    private fun openAttachmentWindow(player: Player, face: BlockFace) {
        if (configMenus.containsKey(face)) {
            configMenus[face]?.openWindow(player)
        } else {
            NetworkManager.queueRead(pos.chunkPos) { state ->
                val endPoint = state.getConnectedNode(this, face) as? NetworkEndPoint
                    ?: return@queueRead
                
                val gui = configMenus.computeIfAbsent(face) {
                    CableConfigMenu(
                        this@Cable,
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
        NetworkManager.queueRead(pos.chunkPos) { state ->
            val bridgeFaces = state.getBridgeFaces(this).toEnumSet()
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

class BasicCable(pos: BlockPos, state: NovaBlockState, data: Compound) : Cable(
    energyTransferRate(Blocks.BASIC_CABLE),
    itemTransferRate(Blocks.BASIC_CABLE),
    fluidTransferRate(Blocks.BASIC_CABLE),
    pos, state, data
)

class AdvancedCable(pos: BlockPos, state: NovaBlockState, data: Compound) : Cable(
    energyTransferRate(Blocks.ADVANCED_CABLE),
    itemTransferRate(Blocks.ADVANCED_CABLE),
    fluidTransferRate(Blocks.ADVANCED_CABLE),
    pos, state, data
)

class EliteCable(pos: BlockPos, state: NovaBlockState, data: Compound) : Cable(
    energyTransferRate(Blocks.ELITE_CABLE),
    itemTransferRate(Blocks.ELITE_CABLE),
    fluidTransferRate(Blocks.ELITE_CABLE),
    pos, state, data
)

class UltimateCable(pos: BlockPos, state: NovaBlockState, data: Compound) : Cable(
    energyTransferRate(Blocks.ULTIMATE_CABLE),
    itemTransferRate(Blocks.ULTIMATE_CABLE),
    fluidTransferRate(Blocks.ULTIMATE_CABLE),
    pos, state, data
)

class CreativeCable(pos: BlockPos, state: NovaBlockState, data: Compound) : Cable(
    provider(Long.MAX_VALUE),
    provider(Int.MAX_VALUE),
    provider(Long.MAX_VALUE),
    pos, state, data
)

private fun energyTransferRate(block: NovaBlock): Provider<Long> =
    combinedProvider(
        block.config.entry<Double>("energy_transfer_rate"),
        EnergyNetwork.TICK_DELAY_PROVIDER
    ).map { (transferRate, tickDelay) -> (transferRate * tickDelay).roundToLong() }

private fun itemTransferRate(block: NovaBlock): Provider<Int> =
    combinedProvider(
        block.config.entry<Double>("item_transfer_rate"),
        ItemNetwork.TICK_DELAY_PROVIDER
    ).map { (transferRate, tickDelay) -> (transferRate * tickDelay).roundToInt() }

private fun fluidTransferRate(block: NovaBlock): Provider<Long> =
    combinedProvider(
        block.config.entry<Double>("fluid_transfer_rate"),
        FluidNetwork.TICK_DELAY_PROVIDER
    ).map { (transferRate, tickDelay) -> (transferRate * tickDelay).roundToLong() }