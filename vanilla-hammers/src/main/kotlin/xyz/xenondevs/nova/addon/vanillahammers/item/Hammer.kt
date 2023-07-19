package xyz.xenondevs.nova.addon.vanillahammers.item

import org.bukkit.Axis
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.nova.item.NovaItem
import xyz.xenondevs.nova.item.behavior.ItemBehavior
import xyz.xenondevs.nova.item.behavior.ItemBehaviorFactory
import xyz.xenondevs.nova.util.BlockFaceUtils
import xyz.xenondevs.nova.util.advance
import xyz.xenondevs.nova.util.axis
import xyz.xenondevs.nova.util.breakNaturally
import xyz.xenondevs.nova.util.destroyProgress
import xyz.xenondevs.nova.util.hardness
import xyz.xenondevs.nova.util.runTaskTimer
import xyz.xenondevs.nova.util.setBreakStage
import xyz.xenondevs.nova.world.block.context.BlockBreakContext
import xyz.xenondevs.nova.world.block.event.BlockBreakActionEvent
import xyz.xenondevs.nova.world.block.event.BlockBreakActionEvent.Action
import xyz.xenondevs.nova.world.pos
import xyz.xenondevs.nova.addon.vanillahammers.item.options.HammerOptions
import kotlin.math.abs
import kotlin.random.Random

class Hammer(private val options: HammerOptions) : ItemBehavior() {
    
    override fun handleBlockBreakAction(player: Player, itemStack: ItemStack, event: BlockBreakActionEvent) {
        when (event.action) {
            Action.START -> {
                cancelHammerWorkers(player)
                
                if (player.isSneaking)
                    return
                
                val face = BlockFaceUtils.determineBlockFaceLookingAt(player.eyeLocation) ?: BlockFace.NORTH
                startHammerWorkers(player, selectBlocks(event.block, face))
            }
            
            Action.FINISH -> finishHammerWorkers(player)
            Action.CANCEL -> cancelHammerWorkers(player)
        }
    }
    
    private fun selectBlocks(middle: Block, face: BlockFace): List<Block> {
        val blocks = ArrayList<Block>()
        
        val axisA = nextAxis(face.axis)
        val axisB = nextAxis(axisA)
        
        val range = options.range
        val depth = options.depth
        for (x in -range..range) {
            for (y in -range..range) {
                for (d in 0 until depth) {
                    // don't include middle block
                    if (x == 0 && y == 0 && d == 0)
                        continue
                    
                    val block = middle.location
                        .advance(face.oppositeFace, d.toDouble())
                        .advance(axisA, x.toDouble())
                        .advance(axisB, y.toDouble())
                        .block
                    
                    // don't include blocks whose hardness difference is outside the specified tolerance
                    val hardnessDifference = abs(block.hardness - middle.hardness)
                    if (hardnessDifference > options.hardnessTolerance)
                        continue
                    
                    blocks += block
                }
            }
        }
        
        return blocks
    }
    
    private fun nextAxis(axis: Axis): Axis = when (axis) {
        Axis.X -> Axis.Y
        Axis.Y -> Axis.Z
        Axis.Z -> Axis.X
    }
    
    companion object : ItemBehaviorFactory<Hammer>() {
        
        override fun create(item: NovaItem): Hammer {
            return Hammer(HammerOptions(item))
        }
        
        private val hammerWorkers = HashMap<Player, Map<Block, Int>>()
        
        init {
            runTaskTimer(0, 1) {
                hammerWorkers.forEach { (player, workers) ->
                    val progress = player.destroyProgress
                        ?: return@forEach
                    
                    workers.forEach { (block, breakerId) ->
                        block.setBreakStage(breakerId, (progress * 10).toInt())
                    }
                }
            }
        }
        
        private fun startHammerWorkers(player: Player, blocks: List<Block>) {
            hammerWorkers[player] = blocks.associateWithTo(HashMap()) { Random.nextInt() }
        }
        
        private fun finishHammerWorkers(player: Player) {
            hammerWorkers.remove(player)?.forEach { (block, breakerId) ->
                block.setBreakStage(breakerId, -1)
                
                val ctx = BlockBreakContext(block.pos, player, player.location, null, player.inventory.itemInMainHand)
                block.breakNaturally(ctx)
            }
        }
        
        private fun cancelHammerWorkers(player: Player) {
            hammerWorkers.remove(player)?.forEach { (block, breakerId) -> block.setBreakStage(breakerId, -1) }
        }
        
    }
    
}