package xyz.xenondevs.nova.addon.machines.registry

import net.minecraft.world.level.block.Blocks
import org.bukkit.Material
import xyz.xenondevs.nova.addon.machines.Machines.block
import xyz.xenondevs.nova.addon.machines.Machines.tileEntity
import xyz.xenondevs.nova.addon.machines.block.StarShardsOre
import xyz.xenondevs.nova.addon.machines.block.WindTurbineBehavior
import xyz.xenondevs.nova.addon.machines.block.WindTurbineSectionBehavior
import xyz.xenondevs.nova.addon.machines.tileentity.agriculture.AutoFisher
import xyz.xenondevs.nova.addon.machines.tileentity.agriculture.Fertilizer
import xyz.xenondevs.nova.addon.machines.tileentity.agriculture.Harvester
import xyz.xenondevs.nova.addon.machines.tileentity.agriculture.Planter
import xyz.xenondevs.nova.addon.machines.tileentity.agriculture.TreeFactory
import xyz.xenondevs.nova.addon.machines.tileentity.energy.Charger
import xyz.xenondevs.nova.addon.machines.tileentity.energy.FurnaceGenerator
import xyz.xenondevs.nova.addon.machines.tileentity.energy.LavaGenerator
import xyz.xenondevs.nova.addon.machines.tileentity.energy.LightningExchanger
import xyz.xenondevs.nova.addon.machines.tileentity.energy.SolarPanel
import xyz.xenondevs.nova.addon.machines.tileentity.energy.WindTurbine
import xyz.xenondevs.nova.addon.machines.tileentity.energy.WirelessCharger
import xyz.xenondevs.nova.addon.machines.tileentity.mob.Breeder
import xyz.xenondevs.nova.addon.machines.tileentity.mob.MobDuplicator
import xyz.xenondevs.nova.addon.machines.tileentity.mob.MobKiller
import xyz.xenondevs.nova.addon.machines.tileentity.processing.AutoCrafter
import xyz.xenondevs.nova.addon.machines.tileentity.processing.CobblestoneGenerator
import xyz.xenondevs.nova.addon.machines.tileentity.processing.Crystallizer
import xyz.xenondevs.nova.addon.machines.tileentity.processing.ElectricFurnace
import xyz.xenondevs.nova.addon.machines.tileentity.processing.FluidInfuser
import xyz.xenondevs.nova.addon.machines.tileentity.processing.Freezer
import xyz.xenondevs.nova.addon.machines.tileentity.processing.MechanicalPress
import xyz.xenondevs.nova.addon.machines.tileentity.processing.Pulverizer
import xyz.xenondevs.nova.addon.machines.tileentity.processing.brewing.ElectricBrewingStand
import xyz.xenondevs.nova.addon.machines.tileentity.world.BlockBreaker
import xyz.xenondevs.nova.addon.machines.tileentity.world.BlockPlacer
import xyz.xenondevs.nova.addon.machines.tileentity.world.ChunkLoader
import xyz.xenondevs.nova.addon.machines.tileentity.world.InfiniteWaterSource
import xyz.xenondevs.nova.addon.machines.tileentity.world.Pump
import xyz.xenondevs.nova.addon.machines.tileentity.world.Quarry
import xyz.xenondevs.nova.addon.machines.tileentity.world.Sprinkler
import xyz.xenondevs.nova.addon.machines.tileentity.world.StarCollector
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.resources.builder.layout.block.BackingStateCategory
import xyz.xenondevs.nova.world.block.NovaBlock
import xyz.xenondevs.nova.world.block.NovaBlockBuilder
import xyz.xenondevs.nova.world.block.NovaTileEntityBlock
import xyz.xenondevs.nova.world.block.NovaTileEntityBlockBuilder
import xyz.xenondevs.nova.world.block.TileEntityConstructor
import xyz.xenondevs.nova.world.block.behavior.BlockDrops
import xyz.xenondevs.nova.world.block.behavior.BlockSounds
import xyz.xenondevs.nova.world.block.behavior.Breakable
import xyz.xenondevs.nova.world.block.behavior.Bucketable
import xyz.xenondevs.nova.world.block.behavior.TileEntityDrops
import xyz.xenondevs.nova.world.block.behavior.TileEntityInteractive
import xyz.xenondevs.nova.world.block.behavior.TileEntityLimited
import xyz.xenondevs.nova.world.block.behavior.Waterloggable
import xyz.xenondevs.nova.world.block.sound.SoundGroup
import xyz.xenondevs.nova.world.block.state.property.DefaultScopedBlockStateProperties
import xyz.xenondevs.nova.world.block.state.property.DefaultScopedBlockStateProperties.FACING_HORIZONTAL
import xyz.xenondevs.nova.world.item.tool.VanillaToolCategories
import xyz.xenondevs.nova.world.item.tool.VanillaToolTiers

@Init(stage = InitStage.PRE_PACK)
object Blocks {
    
    private val SAND = Breakable(0.5, setOf(VanillaToolCategories.SHOVEL), VanillaToolTiers.WOOD, false, Material.PURPLE_CONCRETE_POWDER)
    private val SANDSTONE = Breakable(0.8, setOf(VanillaToolCategories.PICKAXE), VanillaToolTiers.WOOD, true, Material.SANDSTONE)
    private val STONE = Breakable(3.0, setOf(VanillaToolCategories.PICKAXE), VanillaToolTiers.WOOD, true, Material.NETHERITE_BLOCK)
    private val LIGHT_METAL = Breakable(0.5, setOf(VanillaToolCategories.PICKAXE), VanillaToolTiers.WOOD, false, Material.IRON_BLOCK)
    private val STONE_ORE = Breakable(3.0, setOf(VanillaToolCategories.PICKAXE), VanillaToolTiers.STONE, true, Material.STONE)
    private val DEEPSLATE_ORE = Breakable(3.0, setOf(VanillaToolCategories.PICKAXE), VanillaToolTiers.STONE, true, Material.DEEPSLATE)
    private val METAL = Breakable(5.0, setOf(VanillaToolCategories.PICKAXE), VanillaToolTiers.WOOD, true, Material.IRON_BLOCK)
    private val MACHINE_FRAME = Breakable(2.0, setOf(VanillaToolCategories.PICKAXE), VanillaToolTiers.WOOD, true, Material.STONE)
    
    // TileEntities
    val AUTO_FISHER = stateBackedMachine("auto_fisher", ::AutoFisher)
    val FERTILIZER = stateBackedMachine("fertilizer", ::Fertilizer)
    val HARVESTER = stateBackedMachine("harvester", ::Harvester)
    val PLANTER = stateBackedMachine("planter", ::Planter)
    val TREE_FACTORY = entityBackedMachine("tree_factory", ::TreeFactory)
    val CHARGER = stateBackedMachine("charger", ::Charger)
    val WIRELESS_CHARGER = stateBackedMachine("wireless_charger", ::WirelessCharger)
    val BREEDER = stateBackedMachine("breeder", ::Breeder)
    val MOB_DUPLICATOR = stateBackedMachine("mob_duplicator", ::MobDuplicator)
    val MOB_KILLER = stateBackedMachine("mob_killer", ::MobKiller)
    val COBBLESTONE_GENERATOR = entityBackedMachine("cobblestone_generator", ::CobblestoneGenerator) { behaviors(Bucketable) }
    val ELECTRIC_FURNACE = activeMachine("electric_furnace", ::ElectricFurnace)
    val MECHANICAL_PRESS = stateBackedMachine("mechanical_press", ::MechanicalPress)
    val PULVERIZER = stateBackedMachine("pulverizer", ::Pulverizer)
    val BLOCK_BREAKER = stateBackedMachine("block_breaker", ::BlockBreaker)
    val BLOCK_PLACER = stateBackedMachine("block_placer", ::BlockPlacer)
    val STAR_COLLECTOR = entityBackedMachine("star_collector", ::StarCollector)
    val CHUNK_LOADER = stateBackedMachine("chunk_loader", ::ChunkLoader)
    val ELECTRIC_BREWING_STAND = entityBackedMachine("electric_brewing_stand", ::ElectricBrewingStand) { behaviors(Bucketable) }
    val PUMP = entityBackedMachine("pump", ::Pump)
    val FREEZER = stateBackedMachine("freezer", ::Freezer) { behaviors(Bucketable) }
    val FLUID_INFUSER = stateBackedMachine("fluid_infuser", ::FluidInfuser) { behaviors(Bucketable) }
    val SPRINKLER = interactiveTileEntity("sprinkler", ::Sprinkler) { behaviors(LIGHT_METAL, BlockSounds(SoundGroup.METAL), Bucketable) }
    val SOLAR_PANEL = entityBackedMachine("solar_panel", ::SolarPanel)
    val LIGHTNING_EXCHANGER = interactiveTileEntity("lightning_exchanger", ::LightningExchanger) { behaviors(METAL, BlockSounds(SoundGroup.METAL)) }
    val FURNACE_GENERATOR = activeMachine("furnace_generator", ::FurnaceGenerator)
    val LAVA_GENERATOR = activeMachine("lava_generator", ::LavaGenerator) { behaviors(Bucketable) }
    val INFINITE_WATER_SOURCE = interactiveTileEntity("infinite_water_source", ::InfiniteWaterSource) { behaviors(SANDSTONE, BlockSounds(SoundGroup.STONE), Bucketable) }
    val CRYSTALLIZER = entityBackedMachine("crystallizer", ::Crystallizer)
    val AUTO_CRAFTER = stateBackedMachine("auto_crafter", ::AutoCrafter)
    val QUARRY = interactiveTileEntity("quarry", ::Quarry) {
        behaviors(Quarry, STONE, BlockSounds(SoundGroup.STONE))
        stateProperties(FACING_HORIZONTAL)
        entityBacked { defaultModel.rotated() }
    }
    val WIND_TURBINE = interactiveTileEntity("wind_turbine", ::WindTurbine) {
        behaviors(WindTurbineBehavior, METAL, BlockSounds(SoundGroup.METAL))
        stateProperties(FACING_HORIZONTAL)
        entityBacked { getModel("block/wind_turbine/base").rotated() }
    }
    val WIND_TURBINE_EXTRA = block("wind_turbine_extra") {
        behaviors(WindTurbineSectionBehavior, METAL, BlockSounds(SoundGroup.METAL))
        stateProperties(ScopedBlockStateProperties.TURBINE_SECTION)
        modelLess { Blocks.BARRIER.defaultBlockState() }
    }
    
    // Normal blocks
    val STAR_DUST_BLOCK = nonInteractiveBlock("star_dust_block") { behaviors(SAND, BlockSounds(SoundGroup.SAND), BlockDrops) }
    val BASIC_MACHINE_FRAME = machineFrame("basic")
    val ADVANCED_MACHINE_FRAME = machineFrame("advanced")
    val ELITE_MACHINE_FRAME = machineFrame("elite")
    val ULTIMATE_MACHINE_FRAME = machineFrame("ultimate")
    val CREATIVE_MACHINE_FRAME = machineFrame("creative")
    
    // Ores
    val STAR_SHARDS_ORE = nonInteractiveBlock("star_shards_ore") { behaviors(StarShardsOre, STONE_ORE, BlockSounds(SoundGroup.STONE)) }
    val DEEPSLATE_STAR_SHARDS_ORE = nonInteractiveBlock("deepslate_star_shards_ore") { behaviors(StarShardsOre, DEEPSLATE_ORE, BlockSounds(SoundGroup.DEEPSLATE)) }
    
    private fun activeMachine(
        name: String,
        ctor: TileEntityConstructor,
        init: NovaTileEntityBlockBuilder.() -> Unit = {}
    ): NovaTileEntityBlock = interactiveTileEntity(name, ctor) {
        init()
        behaviors(STONE, BlockSounds(SoundGroup.STONE))
        stateProperties(FACING_HORIZONTAL, ScopedBlockStateProperties.ACTIVE)
        stateBacked(BackingStateCategory.NOTE_BLOCK, BackingStateCategory.MUSHROOM_BLOCK) {
            val active = getPropertyValueOrThrow(BlockStateProperties.ACTIVE)
            getModel("block/" + name + "_" + if (active) "on" else "off").rotated()
        }
    }
    
    private fun stateBackedMachine(
        name: String,
        ctor: TileEntityConstructor,
        init: NovaTileEntityBlockBuilder.() -> Unit = {}
    ): NovaTileEntityBlock = interactiveTileEntity(name, ctor) {
        init()
        behaviors(STONE, BlockSounds(SoundGroup.STONE))
        stateProperties(FACING_HORIZONTAL)
        stateBacked(BackingStateCategory.NOTE_BLOCK, BackingStateCategory.MUSHROOM_BLOCK) {
            defaultModel.rotated()
        }
    }
    
    private fun entityBackedMachine(
        name: String,
        ctor: TileEntityConstructor,
        init: NovaTileEntityBlockBuilder.() -> Unit = {}
    ): NovaTileEntityBlock = interactiveTileEntity(name, ctor) {
        init()
        behaviors(STONE, BlockSounds(SoundGroup.STONE))
        stateProperties(FACING_HORIZONTAL)
        entityBacked { defaultModel.rotated() }
    }
    
    private fun interactiveTileEntity(
        name: String,
        ctor: TileEntityConstructor,
        init: NovaTileEntityBlockBuilder.() -> Unit
    ): NovaTileEntityBlock = tileEntity(name, ctor) {
        init()
        behaviors(TileEntityLimited, TileEntityDrops, TileEntityInteractive)
    }
    
    private fun machineFrame(tier: String): NovaBlock =
        block("${tier}_machine_frame") {
            stateProperties(DefaultScopedBlockStateProperties.WATERLOGGED)
            behaviors(MACHINE_FRAME, BlockSounds(SoundGroup.METAL), BlockDrops, Waterloggable)
            stateBacked(BackingStateCategory.LEAVES) {
                getModel("block/machine_frame/$tier")
            }
        }
    
    private fun nonInteractiveBlock(
        name: String,
        block: NovaBlockBuilder.() -> Unit
    ): NovaBlock = block(name) {
        block()
        stateBacked(BackingStateCategory.MUSHROOM_BLOCK, BackingStateCategory.NOTE_BLOCK)
    }
    
}