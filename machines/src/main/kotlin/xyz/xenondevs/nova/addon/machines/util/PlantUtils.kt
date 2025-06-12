package xyz.xenondevs.nova.addon.machines.util

import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.block.Block
import org.bukkit.block.data.Ageable
import org.bukkit.block.data.type.CaveVinesPlant
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.DefaultContextIntentions.BlockBreak
import xyz.xenondevs.nova.context.param.DefaultContextParamTypes
import xyz.xenondevs.nova.integration.customitems.CustomBlockType
import xyz.xenondevs.nova.integration.customitems.CustomItemServiceManager
import xyz.xenondevs.nova.integration.customitems.CustomItemType
import xyz.xenondevs.nova.util.BlockUtils
import xyz.xenondevs.nova.util.below
import xyz.xenondevs.nova.util.item.soundGroup
import xyz.xenondevs.nova.util.novaBlock
import kotlin.random.Random
import org.bukkit.block.data.type.MangrovePropagule as MangrovePropaguleData

fun Material.isTillable(): Boolean {
    return this == Material.GRASS_BLOCK
        || this == Material.DIRT
        || this == Material.DIRT_PATH
}

fun Material.isLeaveLike(): Boolean {
    return Tag.LEAVES.isTagged(this) || Tag.WART_BLOCKS.isTagged(this)
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
            return block.type == block.below.type
        }
        
    }
    
    object SweetBerries : HarvestAction {
        
        override fun isHarvestable(block: Block): Boolean {
            val blockData = block.blockData
            return blockData is Ageable && blockData.age >= blockData.maximumAge
        }
        
        override fun getDrops(ctx: Context<BlockBreak>): List<ItemStack> {
            val block = ctx[DefaultContextParamTypes.BLOCK_POS]!!.block
            if (isHarvestable(block)) {
                return listOf(ItemStack.of(Material.SWEET_BERRIES, Random.nextInt(1, 4)))
            }
            
            return emptyList()
        }
        
        override fun harvest(ctx: Context<BlockBreak>) {
            val block = ctx[DefaultContextParamTypes.BLOCK_POS]!!.block
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
            val block = ctx[DefaultContextParamTypes.BLOCK_POS]!!.block
            if (isHarvestable(block)) {
                return listOf(ItemStack.of(Material.GLOW_BERRIES))
            }
            
            return emptyList()
        }
        
        override fun harvest(ctx: Context<BlockBreak>) {
            val block = ctx[DefaultContextParamTypes.BLOCK_POS]!!.block
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
    
    private val SEED_SOIL_BLOCKS: Map<Material, Set<Material>> = buildMap {
        val farmland = hashSetOf(Material.FARMLAND)
        val defaultDirt = hashSetOf(Material.FARMLAND, Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT,
            Material.ROOTED_DIRT, Material.PODZOL, Material.MYCELIUM)
        
        put(Material.WHEAT_SEEDS, farmland)
        put(Material.BEETROOT_SEEDS, farmland)
        put(Material.POTATO, farmland)
        put(Material.CARROT, farmland)
        put(Material.PUMPKIN_SEEDS, farmland)
        put(Material.MELON_SEEDS, farmland)
        put(Material.SWEET_BERRIES, defaultDirt)
        put(Material.OAK_SAPLING, defaultDirt)
        put(Material.SPRUCE_SAPLING, defaultDirt)
        put(Material.BIRCH_SAPLING, defaultDirt)
        put(Material.JUNGLE_SAPLING, defaultDirt)
        put(Material.ACACIA_SAPLING, defaultDirt)
        put(Material.DARK_OAK_SAPLING, defaultDirt)
        put(Material.CRIMSON_FUNGUS, hashSetOf(Material.CRIMSON_NYLIUM))
        put(Material.WARPED_FUNGUS, hashSetOf(Material.WARPED_NYLIUM))
        put(Material.NETHER_WART, hashSetOf(Material.SOUL_SAND))
    }
    
    private val SEED_GROWTH_BLOCKS: Map<Material, Material> = buildMap {
        put(Material.WHEAT_SEEDS, Material.WHEAT)
        put(Material.BEETROOT_SEEDS, Material.BEETROOTS)
        put(Material.POTATO, Material.POTATOES)
        put(Material.CARROT, Material.CARROTS)
        put(Material.SWEET_BERRIES, Material.SWEET_BERRY_BUSH)
        put(Material.PUMPKIN_SEEDS, Material.PUMPKIN_STEM)
        put(Material.MELON_SEEDS, Material.MELON_STEM)
    }
    
    private val HARVESTABLE_PLANTS: Map<Material, HarvestAction> = buildMap {
        put(Material.SHORT_GRASS, HarvestAction.Simple)
        put(Material.TALL_GRASS, HarvestAction.Simple)
        put(Material.BEE_NEST, HarvestAction.Simple)
        put(Material.PUMPKIN, HarvestAction.Simple)
        put(Material.MELON, HarvestAction.Simple)
        put(Material.SHROOMLIGHT, HarvestAction.Simple)
        put(Material.WEEPING_VINES, HarvestAction.Simple)
        put(Material.WEEPING_VINES_PLANT, HarvestAction.Simple)
        put(Material.MUSHROOM_STEM, HarvestAction.Simple)
        put(Material.RED_MUSHROOM_BLOCK, HarvestAction.Simple)
        put(Material.BROWN_MUSHROOM_BLOCK, HarvestAction.Simple)
        put(Material.VINE, HarvestAction.Simple)
        put(Material.MANGROVE_ROOTS, HarvestAction.Simple)
        put(Material.MUDDY_MANGROVE_ROOTS, HarvestAction.Simple)
        put(Material.MOSS_CARPET, HarvestAction.Simple)
        put(Material.PALE_MOSS_CARPET, HarvestAction.Simple)
        put(Material.PALE_HANGING_MOSS, HarvestAction.Simple)
        put(Material.CREAKING_HEART, HarvestAction.Simple)
        
        put(Material.WHEAT, HarvestAction.FullyAged)
        put(Material.BEETROOTS, HarvestAction.FullyAged)
        put(Material.POTATOES, HarvestAction.FullyAged)
        put(Material.CARROTS, HarvestAction.FullyAged)
        
        put(Material.CACTUS, HarvestAction.SameTypeBelow)
        put(Material.SUGAR_CANE, HarvestAction.SameTypeBelow)
        
        put(Material.SWEET_BERRY_BUSH, HarvestAction.SweetBerries)
        
        put(Material.CAVE_VINES, HarvestAction.CaveVines)
        put(Material.CAVE_VINES_PLANT, HarvestAction.CaveVines)
        
        put(Material.MANGROVE_PROPAGULE, HarvestAction.MangrovePropagule)
        
        fun putTags(vararg tags: Tag<Material>) {
            tags.asSequence()
                .flatMap { it.values }
                .filter { it !in this }
                .forEach { put(it, HarvestAction.Simple) }
        }
        
        putTags(Tag.LEAVES, Tag.LOGS, Tag.FLOWERS, Tag.WART_BLOCKS)
    }
    
    private val TREE_ATTACHMENTS: Set<Material> = buildSet {
        add(Material.BEE_NEST)
        add(Material.SHROOMLIGHT)
        add(Material.WEEPING_VINES)
        add(Material.WEEPING_VINES_PLANT)
        add(Material.MANGROVE_PROPAGULE)
        add(Material.VINE)
        add(Material.MOSS_CARPET)
        add(Material.PALE_MOSS_CARPET)
        add(Material.PALE_HANGING_MOSS)
    }
    
    fun isSeed(item: ItemStack): Boolean =
        CustomItemServiceManager.getItemType(item) == CustomItemType.SEED
            || item.type in SEED_SOIL_BLOCKS
    
    fun canBePlaced(seed: ItemStack, block: Block): Boolean {
        val placeOn = block.below
        return (CustomItemServiceManager.getItemType(seed) == CustomItemType.SEED && placeOn.type == Material.FARMLAND)
            || SEED_SOIL_BLOCKS[seed.type]?.contains(placeOn.type) == true
    }
    
    fun requiresFarmland(seed: ItemStack): Boolean =
        CustomItemServiceManager.getItemType(seed) == CustomItemType.SEED
            || SEED_SOIL_BLOCKS[seed.type]?.contains(Material.FARMLAND) == true
    
    fun placeSeed(seed: ItemStack, block: Block, playEffects: Boolean) {
        if (CustomItemServiceManager.placeBlock(seed, block.location, playEffects))
            return
        
        val newType = SEED_GROWTH_BLOCKS[seed.type] ?: seed.type
        block.type = newType
        
        if (playEffects) block.world.playSound(
            block.location,
            newType.soundGroup.placeSound,
            1f,
            Random.nextDouble(0.8, 0.95).toFloat()
        )
    }
    
    fun isHarvestable(block: Block): Boolean {
        // nova blocks using harvestable blocks as backing states are not harvestable,
        // unless they are Nova's leave replacements
        val novaBlock = block.novaBlock
        if (novaBlock != null && (novaBlock.id.namespace() != "nova" || !Tag.LEAVES.isTagged(block.type)))
            return false
        
        return HARVESTABLE_PLANTS[block.type]?.isHarvestable(block) == true
    }
    
    fun harvest(ctx: Context<BlockBreak>) {
        val block = ctx[DefaultContextParamTypes.BLOCK_POS]!!.block
        HARVESTABLE_PLANTS[block.type]?.harvest(ctx)
    }
    
    fun getHarvestDrops(ctx: Context<BlockBreak>): List<ItemStack> {
        val block = ctx[DefaultContextParamTypes.BLOCK_POS]!!.block
        
        val customBlockType = CustomItemServiceManager.getBlockType(block)
        if (customBlockType == CustomBlockType.NORMAL)
            return emptyList()
        
        val drops: List<ItemStack>?
        if (customBlockType != CustomBlockType.CROP) {
            drops = HARVESTABLE_PLANTS[block.type]?.getDrops(ctx)
        } else {
            drops = CustomItemServiceManager.getDrops(block, null)
        }
        
        return drops ?: emptyList()
    }
    
    fun isTreeAttachment(material: Material): Boolean =
        material in TREE_ATTACHMENTS
    
}
