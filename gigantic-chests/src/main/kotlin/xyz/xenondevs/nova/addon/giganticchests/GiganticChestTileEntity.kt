package xyz.xenondevs.nova.addon.giganticchests

import com.google.common.collect.MapMaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.inventory.ItemStack
import org.joml.Math
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.primitives.AABBd
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.dsl.by
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.invui.dsl.scrollInventoriesGui
import xyz.xenondevs.invui.inventory.CompositeInventory
import xyz.xenondevs.invui.inventory.Inventory
import xyz.xenondevs.invui.inventory.VirtualInventory
import xyz.xenondevs.nova.addon.giganticchests.ContextParamTypes.SKIP_MULTIBLOCK_FORMING_FROM_PART_PLACE
import xyz.xenondevs.nova.addon.giganticchests.ContextParamTypes.SKIP_MULTIBLOCK_RESET_FROM_TE_BREAK
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.BlockBreak
import xyz.xenondevs.nova.context.intention.BlockInteract
import xyz.xenondevs.nova.context.intention.BlockPlace
import xyz.xenondevs.nova.packetentity.PacketItemDisplay
import xyz.xenondevs.nova.packetentity.packetItemDisplay
import xyz.xenondevs.nova.ui.menu.by
import xyz.xenondevs.nova.ui.menu.item.SCROLL_ENABLING_VISUALIZER
import xyz.xenondevs.nova.ui.menu.item.installInventoryScrollSupport
import xyz.xenondevs.nova.ui.menu.item.scrollBar
import xyz.xenondevs.nova.util.BlockUtils
import xyz.xenondevs.nova.util.advance
import xyz.xenondevs.nova.util.yaw
import xyz.xenondevs.nova.world.InteractionResult
import xyz.xenondevs.nova.world.block.BlockUpdateFlags
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.novaTileEntity
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.node.NetworkNode
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.inventory.NetworkedInventory
import xyz.xenondevs.nova.world.item.itemProvider
import java.util.*
import java.util.concurrent.ConcurrentMap
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

private const val PARTIAL_INVENTORY_SIZE = 8
private const val LID_SPEED = 0.1f
private const val LID_KEEP_OPEN_MS = 250

interface GiganticContainerPart {
    
    val inventory: VirtualInventory
    
}

class GiganticChestPartTileEntity(
    block: Block,
    blockState: NovaBlockState,
    data: Compound
) : TileEntity(block, blockState, data), GiganticContainerPart {
    
    override val inventory = storedInventory("inventory", PARTIAL_INVENTORY_SIZE)
    
}

class GiganticChestTileEntity(
    block: Block,
    blockState: NovaBlockState,
    data: Compound
) : NetworkedTileEntity(block, blockState, data), GiganticContainerPart {
    
    private val compositeUuid: UUID = retrieveDataOrNull("compositeUuid")
        ?: throw IllegalStateException("Missing 'compositeUuid'")
    private val isPrimary: Boolean = retrieveDataOrNull("primary")
        ?: throw IllegalStateException("Missing 'primary'")
    val size: Int = retrieveDataOrNull("size")
        ?: throw IllegalStateException("Missing 'size'")
    private val partIndex: Int = retrieveDataOrNull("partIndex")
        ?: throw IllegalStateException("Missing 'partIndex'")
    override val inventory = storedInventory("inventory", PARTIAL_INVENTORY_SIZE)
        .apply { setVisualizer(SCROLL_ENABLING_VISUALIZER) }
    private val partCount = size * size * size
    private val totalInventory = getInventory(compositeUuid, partCount)
    private val chestBottomDisplay: PacketItemDisplay?
    
    private val chestLidDisplay: PacketItemDisplay?
    private val primaryBlock: Block
    
    override var menu = TileEntityMenu.none()
    private var viewerCount = 0
    
    private val itemHolder = storedItemHolder(totalInventory to NetworkConnectionType.BUFFER)
    override var linkedNodes: Set<NetworkNode> = emptySet()
    
    init {
        if (isPrimary) {
            val facing = retrieveDataOrNull("facing") ?: BlockFace.NORTH
            val yaw = facing.yaw
            val center = block.location.add(size / 2.0, size / 2.0, size / 2.0)
                .also { it.yaw = yaw }
            chestBottomDisplay = packetItemDisplay {
                location by center
                metadata {
                    itemStack by Models.CHEST_BOTTOM.itemProvider
                    scale by Vector3f(size.toFloat(), size.toFloat(), size.toFloat())
                }
            }
            val hinge = block.location
                .add(size / 2.0, 9.0 / 14.0 * size, size / 2.0)
                .advance(facing.oppositeFace, size / 2.0 - 0.0001)
                .also { it.yaw = yaw }
            chestLidDisplay = packetItemDisplay {
                location by hinge
                metadata {
                    itemStack by Models.CHEST_LID.itemProvider
                    scale by Vector3f(size.toFloat(), size.toFloat(), size.toFloat())
                }
            }
            primaryBlock = block
            
            menu = TileEntityMenu.cachedWindow(
                texture = GuiTextures.GIGANTIC_CHEST,
                bounds = AABBd(
                    block.x.toDouble(), block.y.toDouble(), block.z.toDouble(),
                    block.x.toDouble() + size, block.y.toDouble() + size, block.z.toDouble() + size
                )
            ) {
                upperGui by scrollInventoriesGui(
                    "x x x x x x x x |",
                    "x x x x x x x x |",
                    "x x x x x x x x |",
                    "x x x x x x x x |",
                    "x x x x x x x x |",
                    "x x x x x x x x |",
                ) {
                    '|' by scrollBar(offset = 2)
                    content by totalInventory.compositeProvider
                    installInventoryScrollSupport()
                }
                
                onOpen {
                    if (++viewerCount == 1)
                        center.world.playSound(center, Sound.BLOCK_CHEST_OPEN, 0.5f, (Random.nextFloat() * 0.1F + 0.9F) / size)
                }
                onClose {
                    if (--viewerCount == 0)
                        center.world.playSound(center, Sound.BLOCK_CHEST_CLOSE, 0.5f, (Random.nextFloat() * 0.1F + 0.9F) / size)
                }
            }
        } else {
            chestBottomDisplay = null
            chestLidDisplay = null
            
            val xOff = partIndex % size
            val zOff = (partIndex / size) % size
            val yOff = (partIndex / (size * size))
            primaryBlock = block.getRelative(-xOff, -yOff, -zOff)
        }
    }
    
    override fun requestsLocalNetwork(face: BlockFace): Boolean =
        itemHolder.connectionConfig[face] != NetworkConnectionType.BUFFER
    
    override fun use(ctx: Context<BlockInteract>): InteractionResult {
        if (isPrimary) {
            return super.use(ctx)
        } else {
            return primaryBlock.novaTileEntity?.use(ctx) ?: InteractionResult.Pass
        }
    }
    
    override fun handleEnable() {
        super.handleEnable()
        registerPart(this)
        
        chestBottomDisplay?.spawn()
        chestLidDisplay?.spawn()
    }
    
    override fun handleEnableTicking() {
        super.handleEnableTicking()
        
        if (!isPrimary)
            return
        
        coroutineSupervisor?.let(::CoroutineScope)?.launch {
            var openness = 0f
            var lastViewed = 0L
            while (isActive) {
                delay(50.milliseconds)
                
                // keep fully opened lids open for a short amount of time after closing
                if (viewerCount > 0)
                    lastViewed = System.currentTimeMillis()
                if (viewerCount == 0 && openness == 1f && (System.currentTimeMillis()) - lastViewed < LID_KEEP_OPEN_MS)
                    continue
                
                val predictedOpenness: Float
                if (viewerCount > 0 && openness < 1f) {
                    openness = (openness + LID_SPEED).coerceAtMost(1f)
                    predictedOpenness = (openness + LID_SPEED).coerceAtMost(1f)
                } else if (viewerCount <= 0 && openness > 0f) {
                    openness = (openness - LID_SPEED).coerceAtLeast(0f)
                    predictedOpenness = (openness - LID_SPEED).coerceAtLeast(0f)
                } else {
                    continue
                }
                
                var eased = 1 - predictedOpenness
                eased = 1 - eased * eased * eased
                chestLidDisplay!!.metadata.apply {
                    leftRotation = Quaternionf().rotateX(-eased * Math.PI_f / 2)
                    transformationInterpolationDuration = 2
                    transformationInterpolationStartDeltaTicks = -1
                }
            }
        }
    }
    
    override fun handleDisable() {
        super.handleDisable()
        unregisterPart(this)
        
        chestBottomDisplay?.despawn()
        chestLidDisplay?.despawn()
    }
    
    override fun handleBreak(ctx: Context<BlockBreak>) {
        super.handleBreak(ctx)
        
        if (ctx[SKIP_MULTIBLOCK_RESET_FROM_TE_BREAK])
            return
        
        for (x in 0..<size) for (y in 0..<size) for (z in 0..<size) {
            val otherBlock = primaryBlock.getRelative(x, y, z)
            if (otherBlock == block)
                continue
            val otherChest = otherBlock.novaTileEntity as? GiganticChestTileEntity
                ?: continue
            val data = Compound().apply { set("inventory", otherChest.inventory) }
            
            BlockUtils.breakBlock(
                Context.intention(BlockBreak)
                    .param(BlockBreak.BLOCK, otherBlock)
                    .param(BlockBreak.BLOCK_BREAK_EFFECTS, false)
                    .param(BlockBreak.BLOCK_UPDATE_FLAGS, BlockUpdateFlags.BUKKIT_NO_PHYSICS)
                    .param(SKIP_MULTIBLOCK_RESET_FROM_TE_BREAK, true)
                    .build()
            )
            
            BlockUtils.placeBlock(
                Context.intention(BlockPlace)
                    .param(BlockPlace.BLOCK, otherBlock)
                    .param(BlockPlace.BLOCK_TYPE, Blocks.GIGANTIC_CHEST_PART.get())
                    .param(BlockPlace.TILE_ENTITY_DATA_NOVA, data)
                    .param(BlockPlace.RESPONSIBLE_PLAYER, owner)
                    .param(BlockPlace.BLOCK_PLACE_EFFECTS, false)
                    .param(BlockPlace.BLOCK_UPDATE_FLAGS, BlockUpdateFlags.BUKKIT_NO_PHYSICS)
                    .param(SKIP_MULTIBLOCK_FORMING_FROM_PART_PLACE, true)
                    .build()
            )
        }
    }
    
    companion object {
        
        private val chests: MutableMap<UUID, Array<GiganticChestTileEntity?>> = HashMap()
        private val inventories: ConcurrentMap<UUID, NetworkedGiganticChestInventory> = MapMaker().weakValues().makeMap()
        
        fun getInventory(uuid: UUID, partCount: Int): NetworkedGiganticChestInventory =
            inventories.computeIfAbsent(uuid) { NetworkedGiganticChestInventory(uuid, partCount) }
        
        fun registerPart(part: GiganticChestTileEntity) {
            val uuid = part.compositeUuid
            val partCount = part.partCount
            
            val parts = chests.computeIfAbsent(uuid) { arrayOfNulls(partCount) }
            parts[part.partIndex] = part
            getInventory(uuid, partCount).updateParts(parts)
            
            updateLinked(parts)
        }
        
        fun unregisterPart(part: GiganticChestTileEntity) {
            val uuid = part.compositeUuid
            
            val parts = chests[uuid]
                ?: return
            parts[part.partIndex] = null
            if (parts.all { it == null })
                chests -= uuid
            getInventory(uuid, part.partCount).updateParts(parts)
            
            updateLinked(parts)
        }
        
        private fun updateLinked(parts: Array<GiganticChestTileEntity?>) {
            val linked = parts.filterNotNullTo(HashSet())
            for (p in parts) {
                if (p == null)
                    continue
                p.linkedNodes = linked
            }
        }
        
    }
    
}

class NetworkedGiganticChestInventory(
    override val uuid: UUID,
    partCount: Int
) : NetworkedInventory {
    
    override val size = partCount * PARTIAL_INVENTORY_SIZE
    
    private var inventories: List<Inventory?> = List(partCount) { null }
    
    val compositeProvider = mutableProvider(CompositeInventory(inventories.filterNotNull()))
    private var composite by compositeProvider
    
    fun updateParts(parts: Array<GiganticChestTileEntity?>) {
        inventories = parts.map { it?.inventory }
        composite = CompositeInventory(inventories.filterNotNull())
    }
    
    override fun add(itemStack: ItemStack, amount: Int): Int {
        val itemStackWithAmount = itemStack.clone().also { it.amount = amount }
        return composite.addItem(null, itemStackWithAmount)
    }
    
    override fun canTake(slot: Int, amount: Int): Boolean {
        val (inv, slotInInv) = findInventory(slot)
            ?: return false
        return (inv.getUnsafeItem(slotInInv)?.amount ?: 0) >= amount
    }
    
    override fun take(slot: Int, amount: Int) {
        val (inv, slotInInv) = findInventory(slot)
            ?: return
        inv.addItemAmount(null, slotInInv, -amount)
    }
    
    override fun isFull(): Boolean {
        return composite.isFull
    }
    
    override fun isEmpty(): Boolean {
        return composite.isEmpty
    }
    
    override fun copyContents(destination: Array<ItemStack>) {
        for ((invIdx, inv) in inventories.withIndex()) {
            if (inv != null) {
                for ((slot, item) in inv.unsafeItems.withIndex()) {
                    destination[invIdx * PARTIAL_INVENTORY_SIZE + slot] = item?.clone() ?: ItemStack.empty()
                }
            } else {
                Arrays.fill(destination, invIdx * PARTIAL_INVENTORY_SIZE, (invIdx + 1) * PARTIAL_INVENTORY_SIZE, ItemStack.empty())
            }
        }
    }
    
    private fun findInventory(slot: Int): Pair<Inventory, Int>? {
        if (slot >= size)
            return null
        val part = slot / PARTIAL_INVENTORY_SIZE
        val slotInInv = slot % PARTIAL_INVENTORY_SIZE
        val inv = inventories.getOrNull(part)
            ?: return null
        return inv to slotInInv
    }
    
}