package xyz.xenondevs.nova.addon.logistics.tileentity

import org.bukkit.Axis
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Orientable
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockBreakEvent
import xyz.xenondevs.commons.collections.enumMap
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.immutable.provider
import xyz.xenondevs.nova.data.config.NovaConfig
import xyz.xenondevs.nova.data.config.configReloadable
import xyz.xenondevs.nova.data.resources.model.data.DisplayEntityBlockModelData
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.item.DefaultItems
import xyz.xenondevs.nova.addon.logistics.gui.cable.CableConfigGui
import xyz.xenondevs.nova.addon.logistics.registry.Blocks
import xyz.xenondevs.nova.tileentity.TileEntity
import xyz.xenondevs.nova.tileentity.network.DefaultNetworkTypes.ENERGY
import xyz.xenondevs.nova.tileentity.network.DefaultNetworkTypes.FLUID
import xyz.xenondevs.nova.tileentity.network.DefaultNetworkTypes.ITEMS
import xyz.xenondevs.nova.tileentity.network.Network
import xyz.xenondevs.nova.tileentity.network.NetworkEndPoint
import xyz.xenondevs.nova.tileentity.network.NetworkManager
import xyz.xenondevs.nova.tileentity.network.NetworkNode
import xyz.xenondevs.nova.tileentity.network.NetworkType
import xyz.xenondevs.nova.tileentity.network.energy.EnergyBridge
import xyz.xenondevs.nova.tileentity.network.fluid.FluidBridge
import xyz.xenondevs.nova.tileentity.network.fluid.holder.FluidHolder
import xyz.xenondevs.nova.tileentity.network.item.ItemBridge
import xyz.xenondevs.nova.tileentity.network.item.holder.ItemHolder
import xyz.xenondevs.nova.util.CUBE_FACES
import xyz.xenondevs.nova.util.MathUtils
import xyz.xenondevs.nova.util.advance
import xyz.xenondevs.nova.util.item.novaItem
import xyz.xenondevs.nova.util.rotationNorth
import xyz.xenondevs.nova.util.rotationValues
import xyz.xenondevs.nova.util.runTask
import xyz.xenondevs.nova.util.toIntArray
import xyz.xenondevs.nova.world.block.hitbox.Hitbox
import xyz.xenondevs.nova.world.block.hitbox.PhysicalHitbox
import xyz.xenondevs.nova.world.block.hitbox.VirtualHitbox
import xyz.xenondevs.nova.world.model.FixedMultiModel
import xyz.xenondevs.nova.world.model.Model
import xyz.xenondevs.nova.world.point.Point3D

private val SUPPORTED_NETWORK_TYPES = hashSetOf(ENERGY, ITEMS, FLUID)
private val ATTACHMENTS: IntArray = (64..79).toIntArray()

private val NetworkNode.itemHolder: ItemHolder?
    get() = if (this is NetworkEndPoint) (holders[ITEMS] as ItemHolder?) else null

private val NetworkNode.fluidHolder: FluidHolder?
    get() = if (this is NetworkEndPoint) holders[FLUID] as FluidHolder? else null

open class Cable(
    energyTransferRateDelegate: Provider<Long>,
    itemTransferRateDelegate: Provider<Int>,
    fluidTransferRateDelegate: Provider<Long>,
    blockState: NovaTileEntityState
) : TileEntity(blockState), EnergyBridge, ItemBridge, FluidBridge {
    
    override val energyTransferRate by energyTransferRateDelegate
    override val itemTransferRate by itemTransferRateDelegate
    override val fluidTransferRate by fluidTransferRateDelegate
    
    override var isNetworkInitialized = false
    override val supportedNetworkTypes = SUPPORTED_NETWORK_TYPES
    override val networks = HashMap<NetworkType, Network>()
    override val bridgeFaces by storedValue("bridgeFaces") { CUBE_FACES.toHashSet() }
    override val connectedNodes = HashMap<NetworkType, MutableMap<BlockFace, NetworkNode>>()
    override val typeId: String
        get() = block.id.toString()
    
    private val configGuis = enumMap<BlockFace, CableConfigGui>()
    
    private val hitboxes = ArrayList<Hitbox<*, *>>()
    private val multiModel = FixedMultiModel()
    private var modelId by storedValue("modelId") { 0 }
    private var attachments: ArrayList<Pair<BlockFace, Int>> by storedValue("attachments-0.2.4", ::ArrayList)
    
    init {
        if (attachments.isNotEmpty()) {
            updateAttachmentModels()
            createAttachmentHitboxes()
        }
        createCableHitboxes()
        
        if (modelId != 0)
            blockState.modelProvider.update(modelId)
    }
    
    override fun saveData() {
        super.saveData()
        serializeNetworks()
        serializeConnectedNodes()
    }
    
    override fun handleNetworkUpdate() {
        // assume that we have NetworkManager.LOCK
        if (isValid) {
            calculateCableModelId()
            calculateAttachmentModelIds()
            blockState.modelProvider.update(modelId)
            updateAttachmentModels()
            
            configGuis.forEach { (face, gui) ->
                val neighbor = connectedNodes[ITEMS]?.get(face)
                
                fun closeAndRemove() {
                    runTask { gui.closeForAllViewers() }
                    configGuis.remove(face)
                }
                
                if (neighbor is NetworkEndPoint) {
                    val itemHolder = neighbor.holders[ITEMS] as ItemHolder
                    if (itemHolder == gui.itemHolder) {
                        gui.updateValues(true)
                    } else closeAndRemove()
                } else closeAndRemove()
            }
            
            // !! Needs to be run on the server thread (updating blocks)
            // !! Also needs to be synchronized with NetworkManager as connectedNodes are retrieved
            NetworkManager.queueSync { updateHitbox() }
        }
    }
    
    override fun handleInitialized(first: Boolean) {
        if (first) NetworkManager.queueAsync { it.addBridge(this) }
    }
    
    override fun handleRemoved(unload: Boolean) {
        super.handleRemoved(unload)
        multiModel.close()
        hitboxes.forEach { it.remove() }
        if (!unload) {
            NetworkManager.queueAsync { it.removeBridge(this) }
            configGuis.values.forEach(CableConfigGui::closeForAllViewers)
        }
    }
    
    private fun calculateCableModelId() {
        val connectedFaces = connectedNodes.values.flatMapTo(HashSet()) { it.keys }
        val booleans = CUBE_FACES.map { connectedFaces.contains(it) }.reversed().toBooleanArray()
        modelId = MathUtils.convertBooleanArrayToInt(booleans)
    }
    
    private fun calculateAttachmentModelIds() {
        require(networks.isNotEmpty()) { "No network is initialized" }
        attachments.clear()
        
        CUBE_FACES.forEach { face ->
            val oppositeFace = face.oppositeFace
            val itemHolder = connectedNodes[ITEMS]?.get(face)?.itemHolder
            val fluidHolder = connectedNodes[FLUID]?.get(face)?.fluidHolder
            
            if (itemHolder == null && fluidHolder == null) return@forEach
            
            val array = booleanArrayOf(
                itemHolder?.isExtract(oppositeFace) ?: false,
                itemHolder?.isInsert(oppositeFace) ?: false,
                fluidHolder?.isExtract(oppositeFace) ?: false,
                fluidHolder?.isInsert(oppositeFace) ?: false
            )
            
            attachments += face to MathUtils.convertBooleanArrayToInt(array)
        }
    }
    
    private fun updateAttachmentModels() {
        val models = HashSet<Model>()
        attachments.forEach { (face, id) ->
            models += Model(
                (block.model as DisplayEntityBlockModelData)[ATTACHMENTS[id]].get(),
                pos.location.add(.5, .5, .5),
                leftRotation = face.rotationNorth // attachment models face south, display entities make north side of models face south, therefore attachments face north by default TODO: make attachment models face north
            )
        }
        multiModel.replaceModels(models)
    }
    
    private fun updateHitbox() {
        if (!isValid) return
        
        updateVirtualHitboxes()
        updateBlockHitbox()
    }
    
    private fun updateVirtualHitboxes() {
        hitboxes.forEach { it.remove() }
        hitboxes.clear()
        
        createCableHitboxes()
        createAttachmentHitboxes()
    }
    
    private fun createCableHitboxes() {
        CUBE_FACES.forEach { blockFace ->
            val pointA = Point3D(0.3, 0.3, 0.0)
            val pointB = Point3D(0.7, 0.7, 0.5)
            
            val origin = Point3D(0.5, 0.5, 0.5)
            
            val rotationValues = blockFace.rotationValues
            pointA.rotateAroundXAxis(rotationValues.first, origin)
            pointA.rotateAroundYAxis(rotationValues.second, origin)
            pointB.rotateAroundXAxis(rotationValues.first, origin)
            pointB.rotateAroundYAxis(rotationValues.second, origin)
            
            val sortedPoints = Point3D.sort(pointA, pointB)
            val from = location.clone().add(sortedPoints.first.x, sortedPoints.first.y, sortedPoints.first.z)
            val to = location.clone().add(sortedPoints.second.x, sortedPoints.second.y, sortedPoints.second.z)
            
            hitboxes += VirtualHitbox(from, to).apply {
                setQualifier { it.item?.novaItem == DefaultItems.WRENCH }
                addRightClickHandler { player, _, _ -> handleCableWrenchHit(player, blockFace) }
                register()
            }
        }
    }
    
    private fun createAttachmentHitboxes() {
        attachments.forEach { (face, _) ->
            val hitboxCenter = location
                .advance(face, .75)
                .add(.5, .125, .5)
            
            hitboxes += PhysicalHitbox(hitboxCenter, .75, .75).apply {
                addRightClickHandler { player, _, _ -> handleAttachmentHit(player, face) }
                register()
            }
        }
    }
    
    private fun updateBlockHitbox() {
        val block = location.block
        
        val neighborFaces = connectedNodes.flatMapTo(HashSet()) { it.value.keys }
        val axis = when {
            neighborFaces.contains(BlockFace.EAST) && neighborFaces.contains(BlockFace.WEST) -> Axis.X
            neighborFaces.contains(BlockFace.NORTH) && neighborFaces.contains(BlockFace.SOUTH) -> Axis.Z
            neighborFaces.contains(BlockFace.UP) && neighborFaces.contains(BlockFace.DOWN) -> Axis.Y
            else -> null
        }
        
        if (axis != null) {
            block.setType(Material.CHAIN, false)
            val blockData = block.blockData as Orientable
            blockData.axis = axis
            block.setBlockData(blockData, false)
        } else {
            block.setType(Material.STRUCTURE_VOID, false)
        }
    }
    
    private fun handleAttachmentHit(player: Player, face: BlockFace) {
        NetworkManager.queueSync {
            val endPoint = getConnectedNode(face) as? NetworkEndPoint
                ?: return@queueSync
            
            configGuis.getOrPut(face) { CableConfigGui(endPoint, endPoint.itemHolder, endPoint.fluidHolder, face.oppositeFace) }.openWindow(player)
        }
    }
    
    private fun handleCableWrenchHit(player: Player, face: BlockFace) {
        if (player.isSneaking) {
            Bukkit.getPluginManager().callEvent(BlockBreakEvent(location.block, player))
        } else {
            NetworkManager.queueAsync {
                if (connectedNodes.values.any { node -> node.containsKey(face) }) {
                    it.removeBridge(this, false)
                    bridgeFaces.remove(face)
                    it.addBridge(this)
                } else if (!bridgeFaces.contains(face)) {
                    it.removeBridge(this, false)
                    bridgeFaces.add(face)
                    it.addBridge(this)
                }
            }
        }
    }
    
    override fun handleTick() = Unit
    
}

private val BASIC_ENERGY_RATE = configReloadable { NovaConfig[Blocks.BASIC_CABLE].getLong("energy_transfer_rate") }
private val BASIC_ITEM_RATE = configReloadable { NovaConfig[Blocks.BASIC_CABLE].getInt("item_transfer_rate") }
private val BASIC_FLUID_RATE = configReloadable { NovaConfig[Blocks.BASIC_CABLE].getLong("fluid_transfer_rate") }

private val ADVANCED_ENERGY_RATE = configReloadable { NovaConfig[Blocks.ADVANCED_CABLE].getLong("energy_transfer_rate") }
private val ADVANCED_ITEM_RATE = configReloadable { NovaConfig[Blocks.ADVANCED_CABLE].getInt("item_transfer_rate") }
private val ADVANCED_FLUID_RATE = configReloadable { NovaConfig[Blocks.ADVANCED_CABLE].getLong("fluid_transfer_rate") }

private val ELITE_ENERGY_RATE = configReloadable { NovaConfig[Blocks.ELITE_CABLE].getLong("energy_transfer_rate") }
private val ELITE_ITEM_RATE = configReloadable { NovaConfig[Blocks.ELITE_CABLE].getInt("item_transfer_rate") }
private val ELITE_FLUID_RATE = configReloadable { NovaConfig[Blocks.ELITE_CABLE].getLong("fluid_transfer_rate") }

private val ULTIMATE_ENERGY_RATE = configReloadable { NovaConfig[Blocks.ULTIMATE_CABLE].getLong("energy_transfer_rate") }
private val ULTIMATE_ITEM_RATE = configReloadable { NovaConfig[Blocks.ULTIMATE_CABLE].getInt("item_transfer_rate") }
private val ULTIMATE_FLUID_RATE = configReloadable { NovaConfig[Blocks.ULTIMATE_CABLE].getLong("fluid_transfer_rate") }

class BasicCable(blockState: NovaTileEntityState) : Cable(
    BASIC_ENERGY_RATE,
    BASIC_ITEM_RATE,
    BASIC_FLUID_RATE,
    blockState
)

class AdvancedCable(blockState: NovaTileEntityState) : Cable(
    ADVANCED_ENERGY_RATE,
    ADVANCED_ITEM_RATE,
    ADVANCED_FLUID_RATE,
    blockState
)

class EliteCable(blockState: NovaTileEntityState) : Cable(
    ELITE_ENERGY_RATE,
    ELITE_ITEM_RATE,
    ELITE_FLUID_RATE,
    blockState
)

class UltimateCable(blockState: NovaTileEntityState) : Cable(
    ULTIMATE_ENERGY_RATE,
    ULTIMATE_ITEM_RATE,
    ULTIMATE_FLUID_RATE,
    blockState
)

class CreativeCable(blockState: NovaTileEntityState) : Cable(
    provider(Long.MAX_VALUE),
    provider(Int.MAX_VALUE),
    provider(Long.MAX_VALUE),
    blockState
)
