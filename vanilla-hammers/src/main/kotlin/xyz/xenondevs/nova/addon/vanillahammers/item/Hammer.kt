package xyz.xenondevs.nova.addon.vanillahammers.item

import org.bukkit.Axis
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.nova.addon.vanillahammers.registry.Enchantments
import xyz.xenondevs.nova.config.ConfigProvider
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.BlockBreak
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitFun
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.registry.RegistryEntry
import xyz.xenondevs.nova.util.BlockFaceUtils
import xyz.xenondevs.nova.util.BlockUtils
import xyz.xenondevs.nova.util.advance
import xyz.xenondevs.nova.util.axis
import xyz.xenondevs.nova.util.destroyProgress
import xyz.xenondevs.nova.util.runTaskTimer
import xyz.xenondevs.nova.util.setBreakStage
import xyz.xenondevs.nova.world.block.blockType
import xyz.xenondevs.nova.world.block.event.BlockBreakActionEvent
import xyz.xenondevs.nova.world.block.event.BlockBreakActionEvent.Action
import xyz.xenondevs.nova.world.item.behavior.ItemBehavior
import xyz.xenondevs.nova.world.item.behavior.ItemBehaviorFactory
import kotlin.math.abs
import kotlin.random.Random

class Hammer(
    range: Provider<Int>,
    depth: Provider<Int>,
    hardnessTolerance: Provider<Double>,
    slowdownPerBlock: Provider<Double>
) : ItemBehavior {
    
    private val range by range
    private val depth by depth
    private val hardnessTolerance by hardnessTolerance
    private val slowdownPerBlock by slowdownPerBlock
    
    override fun handleBlockBreakAction(player: Player, itemStack: ItemStack, event: BlockBreakActionEvent) {
        when (event.action) {
            Action.START -> {
                cancelHammerWorkers(player)
                
                if (player.isSneaking)
                    return
                
                val face = BlockFaceUtils.determineBlockFaceLookingAt(player.eyeLocation) ?: BlockFace.NORTH
                startHammerWorkers(player, selectBlocks(player, itemStack, event.block, face, itemStack.containsEnchantment(Enchantments.CURSE_OF_GIGANTISM)))
            }
            
            Action.FINISH -> finishHammerWorkers(player)
            Action.CANCEL -> cancelHammerWorkers(player)
        }
    }
    
    private fun selectBlocks(player: Player, itemStack: ItemStack, middle: Block, face: BlockFace, cursed: Boolean): List<Block> {
        if (!ProtectionManager.canBreak(player, itemStack, middle))
            return emptyList()
        
        val blocks = ArrayList<Block>()
        
        val axisA = nextAxis(face.axis!!)
        val axisB = nextAxis(axisA)
        
        var range = range
        val depth = depth
        if (cursed) range *= 2
        for (x in -range..range) {
            for (y in -range..range) {
                for (d in 0..<depth) {
                    // don't include middle block
                    if (x == 0 && y == 0 && d == 0)
                        continue
                    
                    val block = middle.location
                        .advance(face.oppositeFace, d.toDouble())
                        .advance(axisA, x.toDouble())
                        .advance(axisB, y.toDouble())
                        .block
                    
                    // don't include blocks whose hardness difference is outside the specified tolerance
                    val hardnessDifference = abs(block.blockType.hardness - middle.blockType.hardness)
                    if (hardnessDifference > hardnessTolerance)
                        continue
                    if (!ProtectionManager.canBreak(player, itemStack, block))
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
    
    override fun modifyBlockDamage(player: Player, itemStack: ItemStack, block: Block, damage: Double): Double {
        val blockCount = hammerWorkers[player]?.size ?: 1
        val slowdown = 1 + blockCount * slowdownPerBlock
        if (slowdown <= 1)
            return damage
        return damage / slowdown
    }
    
    @Init(stage = InitStage.POST_WORLD)
    companion object : ItemBehaviorFactory<Hammer> {
        
        override fun create(entry: RegistryEntry.Paper<ItemType>, config: ConfigProvider) = Hammer(
            config.entry<Int>(1, "range"),
            config.entry<Int>(1, "depth"),
            config.entry<Double>(0.0, "hardness_tolerance"),
            config.entry<Double>(0.2, "slowdown_per_block")
        )
        
        private val hammerWorkers = HashMap<Player, Map<Block, Int>>()
        
        @InitFun
        private fun startHammerTask() {
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
            if (blocks.isEmpty())
                return
            hammerWorkers[player] = blocks.associateWithTo(HashMap()) { Random.nextInt() }
        }
        
        private fun finishHammerWorkers(player: Player) {
            hammerWorkers.remove(player)?.forEach { (block, breakerId) ->
                block.setBreakStage(breakerId, -1)
                
                BlockUtils.breakBlockNaturally(
                    Context.intention(BlockBreak)
                        .param(BlockBreak.BLOCK, block)
                        .param(BlockBreak.SOURCE_PLAYER, player)
                        .param(BlockBreak.TOOL_ITEM_STACK, player.inventory.itemInMainHand)
                        .build()
                )
            }
        }
        
        private fun cancelHammerWorkers(player: Player) {
            hammerWorkers.remove(player)?.forEach { (block, breakerId) -> block.setBreakStage(breakerId, -1) }
        }
        
    }
    
}