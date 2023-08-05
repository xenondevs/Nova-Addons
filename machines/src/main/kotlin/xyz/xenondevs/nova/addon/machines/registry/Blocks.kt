package xyz.xenondevs.nova.addon.machines.registry

import org.bukkit.Material
import xyz.xenondevs.nova.addon.machines.Machines
import xyz.xenondevs.nova.addon.machines.block.StarShardsOre
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
import xyz.xenondevs.nova.addon.registry.BlockRegistry
import xyz.xenondevs.nova.data.world.block.property.Directional
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.item.options.BlockOptions
import xyz.xenondevs.nova.item.tool.VanillaToolCategories
import xyz.xenondevs.nova.item.tool.VanillaToolTiers
import xyz.xenondevs.nova.world.block.sound.SoundGroup

@Init
object Blocks : BlockRegistry by Machines.registry {
    
    private val SAND = BlockOptions(0.5, VanillaToolCategories.SHOVEL, VanillaToolTiers.WOOD, false, SoundGroup.SAND, Material.PURPLE_CONCRETE_POWDER)
    private val SANDSTONE = BlockOptions(0.8, VanillaToolCategories.PICKAXE, VanillaToolTiers.WOOD, true, SoundGroup.STONE, Material.SANDSTONE)
    private val STONE = BlockOptions(3.0, VanillaToolCategories.PICKAXE, VanillaToolTiers.WOOD, true, SoundGroup.STONE, Material.NETHERITE_BLOCK)
    private val LIGHT_METAL = BlockOptions(0.5, VanillaToolCategories.PICKAXE, VanillaToolTiers.WOOD, false, SoundGroup.METAL, Material.IRON_BLOCK)
    private val STONE_ORE = BlockOptions(3.0, VanillaToolCategories.PICKAXE, VanillaToolTiers.STONE, true, SoundGroup.STONE, Material.STONE)
    private val DEEPSLATE_ORE = BlockOptions(3.0, VanillaToolCategories.PICKAXE, VanillaToolTiers.STONE, true, SoundGroup.DEEPSLATE, Material.DEEPSLATE)
    private val METAL = BlockOptions(5.0, VanillaToolCategories.PICKAXE, VanillaToolTiers.WOOD, true, SoundGroup.METAL, Material.IRON_BLOCK)
    private val MACHINE_FRAME = BlockOptions(2.0, VanillaToolCategories.PICKAXE, VanillaToolTiers.WOOD, true, SoundGroup.METAL, Material.STONE)
    
    // TileEntities
    val AUTO_FISHER = tileEntity("auto_fisher", ::AutoFisher).blockOptions(STONE).properties(Directional.NORMAL).register()
    val FERTILIZER = tileEntity("fertilizer", ::Fertilizer).blockOptions(STONE).properties(Directional.NORMAL).register()
    val HARVESTER = tileEntity("harvester", ::Harvester).blockOptions(STONE).properties(Directional.NORMAL).register()
    val PLANTER = tileEntity("planter", ::Planter).blockOptions(STONE).properties(Directional.NORMAL).register()
    val TREE_FACTORY = tileEntity("tree_factory", ::TreeFactory).blockOptions(STONE).properties(Directional.NORMAL).register()
    val CHARGER = tileEntity("charger", ::Charger).blockOptions(STONE).properties(Directional.NORMAL).register()
    val WIRELESS_CHARGER = tileEntity("wireless_charger", ::WirelessCharger).blockOptions(STONE).properties(Directional.NORMAL).register()
    val BREEDER = tileEntity("breeder", ::Breeder).blockOptions(STONE).properties(Directional.NORMAL).register()
    val MOB_DUPLICATOR = tileEntity("mob_duplicator", ::MobDuplicator).blockOptions(STONE).properties(Directional.NORMAL).register()
    val MOB_KILLER = tileEntity("mob_killer", ::MobKiller).blockOptions(STONE).properties(Directional.NORMAL).register()
    val COBBLESTONE_GENERATOR = tileEntity("cobblestone_generator", ::CobblestoneGenerator).blockOptions(STONE).properties(Directional.NORMAL).register()
    val ELECTRIC_FURNACE = tileEntity("electric_furnace", ::ElectricFurnace).blockOptions(STONE).properties(Directional.NORMAL).register()
    val MECHANICAL_PRESS = tileEntity("mechanical_press", ::MechanicalPress).blockOptions(STONE).properties(Directional.NORMAL).register()
    val PULVERIZER = tileEntity("pulverizer", ::Pulverizer).blockOptions(STONE).properties(Directional.NORMAL).register()
    val BLOCK_BREAKER = tileEntity("block_breaker", ::BlockBreaker).blockOptions(STONE).properties(Directional.NORMAL).register()
    val BLOCK_PLACER = tileEntity("block_placer", ::BlockPlacer).blockOptions(STONE).properties(Directional.NORMAL).register()
    val STAR_COLLECTOR = tileEntity("star_collector", ::StarCollector).blockOptions(STONE).register()
    val CHUNK_LOADER = tileEntity("chunk_loader", ::ChunkLoader).blockOptions(STONE).properties(Directional.NORMAL).register()
    val QUARRY = tileEntity("quarry", ::Quarry).blockOptions(STONE).properties(Directional.NORMAL).placeCheck(Quarry::canPlace).register()
    val ELECTRIC_BREWING_STAND = tileEntity("electric_brewing_stand", ::ElectricBrewingStand).blockOptions(STONE).properties(Directional.NORMAL).register()
    val PUMP = tileEntity("pump", ::Pump).blockOptions(STONE).register()
    val FREEZER = tileEntity("freezer", ::Freezer).blockOptions(STONE).properties(Directional.NORMAL).register()
    val FLUID_INFUSER = tileEntity("fluid_infuser", ::FluidInfuser).blockOptions(STONE).properties(Directional.NORMAL).register()
    val SPRINKLER = tileEntity("sprinkler", ::Sprinkler).blockOptions(LIGHT_METAL).register()
    val SOLAR_PANEL = tileEntity("solar_panel", ::SolarPanel).blockOptions(STONE).properties(Directional.NORMAL).register()
    val LIGHTNING_EXCHANGER = tileEntity("lightning_exchanger", ::LightningExchanger).blockOptions(STONE).register()
    val WIND_TURBINE = tileEntity("wind_turbine", ::WindTurbine).blockOptions(METAL).properties(Directional.NORMAL).placeCheck(WindTurbine::canPlace).multiBlockLoader(WindTurbine::loadMultiBlock).register()
    val FURNACE_GENERATOR = tileEntity("furnace_generator", ::FurnaceGenerator).blockOptions(STONE).properties(Directional.NORMAL).register()
    val LAVA_GENERATOR = tileEntity("lava_generator", ::LavaGenerator).blockOptions(STONE).properties(Directional.NORMAL).register()
    val INFINITE_WATER_SOURCE = tileEntity("infinite_water_source", ::InfiniteWaterSource).blockOptions(SANDSTONE).register()
    val CRYSTALLIZER = tileEntity("crystallizer", ::Crystallizer).blockOptions(STONE).register()
    val AUTO_CRAFTER = tileEntity("auto_crafter", ::AutoCrafter).blockOptions(STONE).register()
    
    // Normal blocks
    val STAR_DUST_BLOCK = block("star_dust_block").blockOptions(SAND).register()
    val BASIC_MACHINE_FRAME = block("basic_machine_frame").blockOptions(MACHINE_FRAME).register()
    val ADVANCED_MACHINE_FRAME = block("advanced_machine_frame").blockOptions(MACHINE_FRAME).register()
    val ELITE_MACHINE_FRAME = block("elite_machine_frame").blockOptions(MACHINE_FRAME).register()
    val ULTIMATE_MACHINE_FRAME = block("ultimate_machine_frame").blockOptions(MACHINE_FRAME).register()
    val CREATIVE_MACHINE_FRAME = block("creative_machine_frame").blockOptions(MACHINE_FRAME).register()
    
    // Ores
    val STAR_SHARDS_ORE = block("star_shards_ore").blockOptions(STONE_ORE).behaviors(StarShardsOre).register()
    val DEEPSLATE_STAR_SHARDS_ORE = block("deepslate_star_shards_ore").blockOptions(DEEPSLATE_ORE).behaviors(StarShardsOre).register()
    
}