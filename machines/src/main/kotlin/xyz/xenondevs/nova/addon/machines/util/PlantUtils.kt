package xyz.xenondevs.nova.addon.machines.util

import org.bukkit.block.Block
import org.bukkit.block.BlockType
import org.bukkit.block.data.Ageable
import org.bukkit.block.data.type.CaveVinesPlant
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType
import xyz.xenondevs.nova.addon.machines.registry.BlockTags
import xyz.xenondevs.nova.addon.machines.registry.ItemTags
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.BlockBreak
import xyz.xenondevs.nova.integration.customitems.CustomBlockType
import xyz.xenondevs.nova.integration.customitems.CustomItemServiceManager
import xyz.xenondevs.nova.integration.customitems.CustomItemType
import xyz.xenondevs.nova.registry.RegistryEntrySet
import xyz.xenondevs.nova.registry.tags.BlockTypeTags
import xyz.xenondevs.nova.registry.tags.ItemTypeTags
import xyz.xenondevs.nova.util.BlockUtils
import xyz.xenondevs.nova.util.below
import xyz.xenondevs.nova.util.novaSoundGroup
import xyz.xenondevs.nova.world.block.blockType
import xyz.xenondevs.nova.world.item.itemType
import kotlin.random.Random
import org.bukkit.block.data.type.MangrovePropagule as MangrovePropaguleData

fun BlockType.isTillable(): Boolean {
    return this == BlockType.GRASS_BLOCK
        || this == BlockType.DIRT
        || this == BlockType.DIRT_PATH
}

fun BlockType.isLeaveLike(): Boolean {
    return this in BlockTypeTags.LEAVES || this in BlockTypeTags.WART_BLOCKS
}

private sealed interface HarvestAction {
    
    fun isHarvestable(block: Block): Boolean
    
    fun getDrops(ctx: Context<BlockBreak>): List<ItemStack> {
        return BlockUtils.getDrops(ctx)
    }
    
    fun harvest(ctx: Context<BlockBreak>) {
        BlockUtils.breakBlock(ctx)
    }
    
    object Simple : HarvestAction {
        override fun isHarvestable(block: Block) = true
    }
    
    object FullyAged : HarvestAction {
        
        override fun isHarvestable(block: Block): Boolean {
            val blockData = block.blockData
            return blockData is Ageable && blockData.age >= blockData.maximumAge
        }
        
    }
    
    object SameTypeBelow : HarvestAction {
        
        override fun isHarvestable(block: Block): Boolean {
            return block.blockType == block.below.blockType
        }
        
    }
    
    object SweetBerries : HarvestAction {
        
        override fun isHarvestable(block: Block): Boolean {
            val blockData = block.blockData
            return blockData is Ageable && blockData.age >= blockData.maximumAge
        }
        
        override fun getDrops(ctx: Context<BlockBreak>): List<ItemStack> {
            val block = ctx[BlockBreak.BLOCK]
            if (isHarvestable(block)) {
                return listOf(ItemType.SWEET_BERRIES.createItemStack(Random.nextInt(1, 4)))
            }
            
            return emptyList()
        }
        
        override fun harvest(ctx: Context<BlockBreak>) {
            val block = ctx[BlockBreak.BLOCK]
            if (isHarvestable(block)) {
                val data = block.blockData as Ageable
                data.age = 1
                block.blockData = data
            }
        }
        
    }
    
    object CaveVines : HarvestAction {
        
        override fun isHarvestable(block: Block): Boolean {
            val blockData = block.blockData
            return blockData is CaveVinesPlant && blockData.hasBerries()
        }
        
        override fun getDrops(ctx: Context<BlockBreak>): List<ItemStack> {
            val block = ctx[BlockBreak.BLOCK]
            if (isHarvestable(block)) {
                return listOf(ItemType.GLOW_BERRIES.createItemStack())
            }
            
            return emptyList()
        }
        
        override fun harvest(ctx: Context<BlockBreak>) {
            val block = ctx[BlockBreak.BLOCK]
            if (isHarvestable(block)) {
                val data = block.blockData as CaveVinesPlant
                data.isBerries = false
                block.blockData = data
            }
        }
        
    }
    
    object MangrovePropagule : HarvestAction {
        
        override fun isHarvestable(block: Block): Boolean {
            val blockData = block.blockData
            return blockData is MangrovePropaguleData && blockData.isHanging
        }
        
    }
    
}

object PlantUtils {
    
    private val HARVEST_ACTIONS: Map<BlockType, HarvestAction> = buildMap {
        put(BlockType.WHEAT, HarvestAction.FullyAged)
        put(BlockType.BEETROOTS, HarvestAction.FullyAged)
        put(BlockType.POTATOES, HarvestAction.FullyAged)
        put(BlockType.CARROTS, HarvestAction.FullyAged)
        
        put(BlockType.CACTUS, HarvestAction.SameTypeBelow)
        put(BlockType.SUGAR_CANE, HarvestAction.SameTypeBelow)
        
        put(BlockType.SWEET_BERRY_BUSH, HarvestAction.SweetBerries)
        
        put(BlockType.MANGROVE_PROPAGULE, HarvestAction.MangrovePropagule)
    }
    
    fun isSeed(item: ItemStack): Boolean =
        CustomItemServiceManager.getItemType(item) == CustomItemType.SEED
            || item.itemType in ItemTags.PLANTABLE_SEEDS
    
    fun canBePlaced(seed: ItemStack, block: Block): Boolean {
        val placeOn = block.below
        if (CustomItemServiceManager.getItemType(seed) == CustomItemType.SEED)
            return placeOn.blockType in BlockTypeTags.SUPPORTS_CROPS

        val supportingSoils = getSupportingSoils(seed.itemType) ?: return false
        return placeOn.blockType in supportingSoils
    }
    
    fun requiresFarmland(seed: ItemStack): Boolean {
        if (CustomItemServiceManager.getItemType(seed) == CustomItemType.SEED)
            return true

        val supportingSoils = getSupportingSoils(seed.itemType) ?: return false
        return BlockType.FARMLAND in supportingSoils
    }
    
    fun placeSeed(seed: ItemStack, block: Block, playEffects: Boolean) {
        if (CustomItemServiceManager.placeBlock(seed, block.location, playEffects))
            return
        
        block.blockType = seed.itemType.blockType
        
        if (playEffects) block.world.playSound(
            block.location,
            block.novaSoundGroup.placeSound,
            1f,
            Random.nextDouble(0.8, 0.95).toFloat()
        )
    }
    
    fun isHarvestable(block: Block): Boolean {
        return getHarvestAction(block.blockType)?.isHarvestable(block) == true
    }
    
    fun harvest(ctx: Context<BlockBreak>) {
        val block = ctx[BlockBreak.BLOCK]
        getHarvestAction(block.blockType)?.harvest(ctx)
    }
    
    fun getHarvestDrops(ctx: Context<BlockBreak>): List<ItemStack> {
        val block = ctx[BlockBreak.BLOCK]
        
        val customBlockType = CustomItemServiceManager.getBlockType(block)
        if (customBlockType == CustomBlockType.NORMAL)
            return emptyList()
        
        val drops: List<ItemStack>?
        if (customBlockType != CustomBlockType.CROP) {
            drops = getHarvestAction(block.blockType)?.getDrops(ctx)
        } else {
            drops = CustomItemServiceManager.getDrops(block, null)
        }
        
        return drops ?: emptyList()
    }
    
    fun isTreeAttachment(blockType: BlockType): Boolean =
        blockType in BlockTags.TREE_ATTACHMENTS
    
    private fun getHarvestAction(blockType: BlockType): HarvestAction? =
        HARVEST_ACTIONS[blockType]
            ?: HarvestAction.CaveVines.takeIf { blockType in BlockTypeTags.CAVE_VINES }
            ?: HarvestAction.Simple.takeIf { blockType in BlockTags.SIMPLE_HARVESTABLES }

    private fun getSupportingSoils(itemType: ItemType): RegistryEntrySet.Paper.Tag<BlockType>? = when {
        itemType == ItemType.MANGROVE_PROPAGULE -> BlockTypeTags.SUPPORTS_MANGROVE_PROPAGULE
        itemType == ItemType.AZALEA || itemType == ItemType.FLOWERING_AZALEA -> BlockTypeTags.SUPPORTS_AZALEA
        itemType in ItemTypeTags.VILLAGER_PLANTABLE_SEEDS -> BlockTypeTags.SUPPORTS_CROPS
        itemType == ItemType.PUMPKIN_SEEDS || itemType == ItemType.MELON_SEEDS -> BlockTypeTags.SUPPORTS_STEM_CROPS
        itemType in ItemTypeTags.SAPLINGS || itemType == ItemType.SWEET_BERRIES -> BlockTypeTags.SUPPORTS_VEGETATION
        itemType == ItemType.CRIMSON_FUNGUS -> BlockTypeTags.SUPPORTS_CRIMSON_FUNGUS
        itemType == ItemType.WARPED_FUNGUS -> BlockTypeTags.SUPPORTS_WARPED_FUNGUS
        itemType == ItemType.NETHER_WART -> BlockTypeTags.SUPPORTS_NETHER_WART
        else -> null
    }
    
}
