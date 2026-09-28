package xyz.xenondevs.nova.addon.giganticchests

import net.kyori.adventure.key.Key.key
import org.bukkit.OfflinePlayer
import org.bukkit.block.Block
import org.bukkit.block.BlockType
import org.bukkit.entity.Entity
import org.bukkit.util.Vector
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.nova.addon.giganticchests.ContextParamTypes.SKIP_MULTIBLOCK_FORMING_FROM_PART_PLACE
import xyz.xenondevs.nova.addon.giganticchests.ContextParamTypes.SKIP_MULTIBLOCK_RESET_FROM_TE_BREAK
import xyz.xenondevs.nova.config.CONFIGS
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.BlockBreak
import xyz.xenondevs.nova.context.intention.BlockInteract
import xyz.xenondevs.nova.context.intention.BlockPlace
import xyz.xenondevs.nova.util.BlockFaceUtils
import xyz.xenondevs.nova.util.BlockStateMatcher
import xyz.xenondevs.nova.util.BlockUtils
import xyz.xenondevs.nova.util.calculateYaw
import xyz.xenondevs.nova.world.InteractionResult
import xyz.xenondevs.nova.world.block.BlockUpdateFlags
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.behavior.BlockBehavior
import xyz.xenondevs.nova.world.block.novaTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntity
import java.util.*

private val MAX_CUBE_SIZE: Int by CONFIGS[key(GiganticChests, "config")].entry("max_size")

object GiganticChestPartBlock : BlockBehavior {
    
    private val BLOCK_STATE_MATCHER by Blocks.GIGANTIC_CHEST_LIKE.map {
        BlockStateMatcher(it.map(BlockType::createBlockData))
    }
    
    override fun handlePlace(block: Block, state: NovaBlockState, ctx: Context<BlockPlace>) {
        if (ctx[SKIP_MULTIBLOCK_FORMING_FROM_PART_PLACE])
            return
        
        val (min, size) = findCube(block) ?: return
        placeMultiBlock(min, size, ctx[BlockPlace.SOURCE_DIRECTION], ctx[BlockPlace.SOURCE_ENTITY], ctx[BlockPlace.SOURCE_TILE_ENTITY], ctx[BlockPlace.RESPONSIBLE_PLAYER])
    }
    
    override fun use(block: Block, state: NovaBlockState, ctx: Context<BlockInteract>): InteractionResult {
        val (min, size) = findCube(block)
            ?: return InteractionResult.Pass
        if (placeMultiBlock(min, size, ctx[BlockInteract.SOURCE_DIRECTION], ctx[BlockInteract.SOURCE_ENTITY], ctx[BlockInteract.SOURCE_TILE_ENTITY], ctx[BlockInteract.RESPONSIBLE_PLAYER])) {
            return InteractionResult.Success(swing = true)
        } else {
            return InteractionResult.Pass
        }
    }
    
    private fun findCube(pos: Block): Pair<Block, Int>? {
        val maxSize = MAX_CUBE_SIZE
        if (maxSize < 2)
            return null
        
        val world = pos.world
        val r = maxSize - 1
        val d = 2 * maxSize - 1
        val originX = pos.x - r
        val originY = pos.y - r
        val originZ = pos.z - r
        val matches = BLOCK_STATE_MATCHER.match(world, originX, originY, originZ, d, d, d)
        
        // try sizes top-down; the first fully-valid cube containing pos wins.
        // pos sits at local coord (r,r,r), so any cube containing it has its min
        // corner at (r-dx, r-dy, r-dz) for dx,dy,dz in [0, s).
        for (s in maxSize downTo 2) {
            for (dx in 0..<s) for (dy in 0..<s) for (dz in 0..<s) {
                val lx = r - dx
                val ly = r - dy
                val lz = r - dz
                if (matches.matchesAll(lx, ly, lz, s, s, s))
                    return world.getBlockAt(originX + lx, originY + ly, originZ + lz) to s
            }
        }
        
        return null
    }
    
    private fun placeMultiBlock(
        min: Block,
        size: Int,
        srcDirection: Vector?,
        srcEntity: Entity?,
        srcTileEntity: TileEntity?,
        responsiblePlayer: OfflinePlayer?
    ): Boolean {
        val uuid = UUID.randomUUID()
        val partCount = size * size * size
        
        val positions = List(partCount) { part ->
            val xOff = part % size
            val zOff = (part / size) % size
            val yOff = (part / (size * size))
            val block = min.getRelative(xOff, yOff, zOff)
            val te = block.novaTileEntity
            block to te
        }
        
        // don't place if it would override an existing gigantic chest of the same or larger size
        if (positions.any { (_, te) -> te is GiganticChestTileEntity && te.size >= size })
            return false
        
        // store facing in tile entity data instead of block state to:
        // - only compute facing once for all affected blocks
        // - match over fewer block states in findCube
        val facing = srcDirection
            ?.calculateYaw()
            ?.let { BlockFaceUtils.toCartesianFace(it) }
            ?.oppositeFace
        for ((part, pair) in positions.withIndex()) {
            val (block, te) = pair
            val currentInv = (te as? GiganticContainerPart)?.inventory
            val data = Compound().apply {
                set("compositeUuid", uuid)
                set("primary", part == 0)
                set("size", size)
                set("partIndex", part)
                set("inventory", currentInv)
                if (part == 0) {
                    set("facing", facing)
                }
            }
            
            // remove block first because a simply replace would not update existing GiganticChestTileEntity instances of smaller sizes
            BlockUtils.breakBlock(
                Context.intention(BlockBreak)
                    .param(BlockBreak.BLOCK, block)
                    .param(BlockBreak.BLOCK_BREAK_EFFECTS, false)
                    .param(BlockBreak.BLOCK_UPDATE_FLAGS, BlockUpdateFlags.BUKKIT_NO_PHYSICS)
                    .param(SKIP_MULTIBLOCK_RESET_FROM_TE_BREAK, true) // we're already resetting
                    .build()
            )
            
            BlockUtils.placeBlock(
                Context.intention(BlockPlace)
                    .param(BlockPlace.BLOCK, block)
                    .param(BlockPlace.BLOCK_TYPE, Blocks.GIGANTIC_CHEST.get())
                    .param(BlockPlace.TILE_ENTITY_DATA_NOVA, data)
                    .param(BlockPlace.RESPONSIBLE_PLAYER, responsiblePlayer)
                    .param(BlockPlace.SOURCE_ENTITY, srcEntity)
                    .param(BlockPlace.SOURCE_TILE_ENTITY, srcTileEntity)
                    .param(BlockPlace.BLOCK_PLACE_EFFECTS, false)
                    .param(BlockPlace.BLOCK_UPDATE_FLAGS, BlockUpdateFlags.BUKKIT_NO_PHYSICS)
                    .build()
            )
        }
        
        return true
    }
    
}