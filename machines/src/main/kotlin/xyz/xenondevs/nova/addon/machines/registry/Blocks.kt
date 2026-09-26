package xyz.xenondevs.nova.addon.machines.registry

import org.bukkit.block.BlockType
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
import xyz.xenondevs.nova.registry.NovaBlockBuilder
import xyz.xenondevs.nova.registry.NovaTileEntityBlockBuilder
import xyz.xenondevs.nova.registry.entries.BlockTypeEntries
import xyz.xenondevs.nova.registry.entries.ItemTypeEntries
import xyz.xenondevs.nova.resources.builder.layout.block.BackingStateCategory
import xyz.xenondevs.nova.world.block.TileEntityConstructor
import xyz.xenondevs.nova.world.block.behavior.BlockDrops
import xyz.xenondevs.nova.world.block.behavior.Bucketable
import xyz.xenondevs.nova.world.block.behavior.Gravity
import xyz.xenondevs.nova.world.block.behavior.TileEntityDrops
import xyz.xenondevs.nova.world.block.behavior.TileEntityInteractive
import xyz.xenondevs.nova.world.block.sound.SoundGroup
import xyz.xenondevs.nova.world.block.state.property.DefaultBlockStateProperties
import xyz.xenondevs.nova.world.block.state.property.DefaultBlockStateProperties.FACING_HORIZONTAL
import xyz.xenondevs.nova.world.item.tool.VanillaToolCategories
import xyz.xenondevs.nova.world.item.tool.VanillaToolTiers

@Init(stage = InitStage.PRE_PACK)
object Blocks {
    
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
    val COBBLESTONE_GENERATOR = entityBackedMachine("cobblestone_generator", ::CobblestoneGenerator) {
        behaviors(Bucketable)
        stateProperties(BlockStateProperties.LAVA)
        lightEmission { if (getPropertyValueOrThrow(BlockStateProperties.LAVA)) 15 else 0 }
    }
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
    val SPRINKLER = interactiveTileEntity("sprinkler", ::Sprinkler) {
        sounds(SoundGroup.METAL)
        breakable(
            hardness = 0.5,
            toolCategories = setOf(VanillaToolCategories.PICKAXE),
            toolTier = VanillaToolTiers.WOOD,
            requiresToolForDrops = false,
            hitParticles = ItemTypeEntries.IRON_BLOCK,
            breakParticles = BlockTypeEntries.IRON_BLOCK
        )
        behaviors(Bucketable)
    }
    val SOLAR_PANEL = entityBackedMachine("solar_panel", ::SolarPanel)
    val LIGHTNING_EXCHANGER = interactiveTileEntity("lightning_exchanger", ::LightningExchanger) {
        metal()
    }
    val FURNACE_GENERATOR = activeMachine("furnace_generator", ::FurnaceGenerator) {
        lightEmission { if (getPropertyValueOrThrow(BlockStateProperties.ACTIVE)) 13 else 0 }
    }
    val LAVA_GENERATOR = activeMachine("lava_generator", ::LavaGenerator) {
        behaviors(Bucketable)
        lightEmission { if (getPropertyValueOrThrow(BlockStateProperties.ACTIVE)) 15 else 0 }
    }
    val INFINITE_WATER_SOURCE = interactiveTileEntity("infinite_water_source", ::InfiniteWaterSource) {
        sounds(SoundGroup.STONE)
        breakable(
            hardness = 0.8,
            toolCategories = setOf(VanillaToolCategories.PICKAXE),
            toolTier = VanillaToolTiers.WOOD,
            requiresToolForDrops = true,
            hitParticles = ItemTypeEntries.SANDSTONE,
            breakParticles = BlockTypeEntries.SANDSTONE
        )
        behaviors(Bucketable)
    }
    val CRYSTALLIZER = entityBackedMachine("crystallizer", ::Crystallizer)
    val AUTO_CRAFTER = stateBackedMachine("auto_crafter", ::AutoCrafter)
    val QUARRY = interactiveTileEntity("quarry", ::Quarry) {
        behaviors(Quarry)
        stone()
        stateProperties(FACING_HORIZONTAL)
        entityBacked { defaultModel.rotated() }
    }
    val WIND_TURBINE = interactiveTileEntity("wind_turbine", ::WindTurbine) {
        behaviors(WindTurbineBehavior)
        metal()
        stateProperties(FACING_HORIZONTAL)
        entityBacked { getModel("block/wind_turbine/base").rotated() }
    }
    val WIND_TURBINE_EXTRA = block("wind_turbine_extra") {
        behaviors(WindTurbineSectionBehavior)
        metal()
        stateProperties(BlockStateProperties.TURBINE_SECTION)
        modelLess { BlockType.BARRIER.createBlockData() }
    }
    
    // Normal blocks
    val STAR_DUST_BLOCK = nonInteractiveBlock("star_dust_block") {
        behaviors(Gravity())
        sounds(SoundGroup.SAND)
        breakable(
            hardness = 0.5,
            toolCategories = setOf(VanillaToolCategories.SHOVEL),
            toolTier = VanillaToolTiers.WOOD,
            requiresToolForDrops = false,
            hitParticles = ItemTypeEntries.PURPLE_CONCRETE_POWDER,
            breakParticles = BlockTypeEntries.PURPLE_CONCRETE_POWDER
        )
        behaviors(BlockDrops)
    }
    val BASIC_MACHINE_FRAME = machineFrame("basic")
    val ADVANCED_MACHINE_FRAME = machineFrame("advanced")
    val ELITE_MACHINE_FRAME = machineFrame("elite")
    val ULTIMATE_MACHINE_FRAME = machineFrame("ultimate")
    val CREATIVE_MACHINE_FRAME = machineFrame("creative")
    
    // Ores
    val STAR_SHARDS_ORE = nonInteractiveBlock("star_shards_ore") {
        sounds(SoundGroup.STONE)
        breakable(
            hardness = 3.0,
            toolCategories = setOf(VanillaToolCategories.PICKAXE),
            toolTier = VanillaToolTiers.STONE,
            requiresToolForDrops = true,
            hitParticles = ItemTypeEntries.STONE,
            breakParticles = BlockTypeEntries.STONE
        )
        behaviors(StarShardsOre)
    }
    val DEEPSLATE_STAR_SHARDS_ORE = nonInteractiveBlock("deepslate_star_shards_ore") {
        sounds(SoundGroup.DEEPSLATE)
        breakable(
            hardness = 3.0,
            toolCategories = setOf(VanillaToolCategories.PICKAXE),
            toolTier = VanillaToolTiers.STONE,
            requiresToolForDrops = true,
            hitParticles = ItemTypeEntries.DEEPSLATE,
            breakParticles = BlockTypeEntries.DEEPSLATE
        )
        behaviors(StarShardsOre)
    }
    
    private fun activeMachine(
        name: String,
        ctor: TileEntityConstructor,
        init: NovaTileEntityBlockBuilder.() -> Unit = {}
    ) = interactiveTileEntity(name, ctor) {
        init()
        stone()
        stateProperties(FACING_HORIZONTAL, BlockStateProperties.ACTIVE)
        stateBacked(BackingStateCategory.NOTE_BLOCK, BackingStateCategory.MUSHROOM_BLOCK) {
            val active = getPropertyValueOrThrow(BlockStateProperties.ACTIVE)
            getModel("block/" + name + "_" + if (active) "on" else "off").rotated()
        }
    }
    
    private fun stateBackedMachine(
        name: String,
        ctor: TileEntityConstructor,
        init: NovaTileEntityBlockBuilder.() -> Unit = {}
    ) = interactiveTileEntity(name, ctor) {
        init()
        stone()
        stateProperties(FACING_HORIZONTAL)
        stateBacked(BackingStateCategory.NOTE_BLOCK, BackingStateCategory.MUSHROOM_BLOCK) {
            defaultModel.rotated()
        }
    }
    
    private fun entityBackedMachine(
        name: String,
        ctor: TileEntityConstructor,
        init: NovaTileEntityBlockBuilder.() -> Unit = {}
    ) = interactiveTileEntity(name, ctor) {
        init()
        stone()
        stateProperties(FACING_HORIZONTAL)
        entityBacked { defaultModel.rotated() }
    }
    
    private fun interactiveTileEntity(
        name: String,
        ctor: TileEntityConstructor,
        init: NovaTileEntityBlockBuilder.() -> Unit
    ) = tileEntity(name, ctor) {
        init()
        behaviors(TileEntityDrops, TileEntityInteractive)
    }
    
    private fun machineFrame(tier: String) =
        block("${tier}_machine_frame") {
            stateProperties(DefaultBlockStateProperties.WATERLOGGED)
            sounds(SoundGroup.METAL)
            breakable(
                hardness = 2.0,
                toolCategories = setOf(VanillaToolCategories.PICKAXE),
                toolTier = VanillaToolTiers.WOOD,
                requiresToolForDrops = true,
                hitParticles = ItemTypeEntries.STONE,
                breakParticles = BlockTypeEntries.STONE
            )
            behaviors(BlockDrops)
            stateBacked(BackingStateCategory.LEAVES) {
                getModel("block/machine_frame/$tier")
            }
        }
    
    private fun nonInteractiveBlock(
        name: String,
        block: NovaBlockBuilder.() -> Unit
    ) = block(name) {
        block()
        stateBacked(BackingStateCategory.MUSHROOM_BLOCK, BackingStateCategory.NOTE_BLOCK)
    }
    
    private fun NovaBlockBuilder.stone() {
        sounds(SoundGroup.STONE)
        breakable(
            hardness = 3.0,
            toolCategories = setOf(VanillaToolCategories.PICKAXE),
            toolTier = VanillaToolTiers.WOOD,
            requiresToolForDrops = true,
            hitParticles = ItemTypeEntries.NETHERITE_BLOCK,
            breakParticles = BlockTypeEntries.NETHERITE_BLOCK
        )
    }
    
    private fun NovaBlockBuilder.metal() {
        sounds(SoundGroup.METAL)
        breakable(
            hardness = 5.0,
            toolCategories = setOf(VanillaToolCategories.PICKAXE),
            toolTier = VanillaToolTiers.WOOD,
            requiresToolForDrops = true,
            hitParticles = ItemTypeEntries.IRON_BLOCK,
            breakParticles = BlockTypeEntries.IRON_BLOCK
        )
    }
    
}
