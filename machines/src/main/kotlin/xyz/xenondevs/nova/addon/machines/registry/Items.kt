@file:Suppress("unused")

package xyz.xenondevs.nova.addon.machines.registry

import xyz.xenondevs.nova.addon.machines.Machines
import xyz.xenondevs.nova.addon.machines.item.MobCatcherBehavior
import xyz.xenondevs.nova.addon.registry.ItemRegistry
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.item.behavior.Damageable
import xyz.xenondevs.nova.item.behavior.Enchantable
import xyz.xenondevs.nova.item.behavior.Extinguishing
import xyz.xenondevs.nova.item.behavior.Flattening
import xyz.xenondevs.nova.item.behavior.Stripping
import xyz.xenondevs.nova.item.behavior.Tilling
import xyz.xenondevs.nova.item.behavior.Tool
import xyz.xenondevs.nova.item.behavior.Wearable
import xyz.xenondevs.nova.player.equipment.ArmorType

@Init
object Items : ItemRegistry by Machines.registry {
    
    val MOB_CATCHER = registerItem("mob_catcher", MobCatcherBehavior)
    
    // Tools
    val STAR_SWORD = registerItem("star_sword", Tool, Damageable, Enchantable)
    val STAR_SHOVEL = registerItem("star_shovel", Tool, Damageable, Enchantable, Flattening, Extinguishing)
    val STAR_PICKAXE = registerItem("star_pickaxe", Tool, Damageable, Enchantable)
    val STAR_AXE = registerItem("star_axe", Tool, Damageable, Enchantable, Stripping)
    val STAR_HOE = registerItem("star_hoe", Tool, Damageable, Enchantable, Tilling)
    
    // Armor
    val STAR_HELMET = registerItem("star_helmet", Wearable(ArmorType.HELMET, Sounds.ARMOR_EQUIP_STAR), Damageable)
    val STAR_CHESTPLATE = registerItem("star_chestplate", Wearable(ArmorType.CHESTPLATE, Sounds.ARMOR_EQUIP_STAR), Damageable)
    val STAR_LEGGINGS = registerItem("star_leggings", Wearable(ArmorType.LEGGINGS, Sounds.ARMOR_EQUIP_STAR), Damageable)
    val STAR_BOOTS = registerItem("star_boots", Wearable(ArmorType.BOOTS, Sounds.ARMOR_EQUIP_STAR), Damageable)
    
    // Plates
    val IRON_PLATE = registerItem("iron_plate")
    val GOLD_PLATE = registerItem("gold_plate")
    val DIAMOND_PLATE = registerItem("diamond_plate")
    val NETHERITE_PLATE = registerItem("netherite_plate")
    val EMERALD_PLATE = registerItem("emerald_plate")
    val REDSTONE_PLATE = registerItem("redstone_plate")
    val LAPIS_PLATE = registerItem("lapis_plate")
    val COPPER_PLATE = registerItem("copper_plate")
    
    // Gears
    val IRON_GEAR = registerItem("iron_gear")
    val GOLD_GEAR = registerItem("gold_gear")
    val DIAMOND_GEAR = registerItem("diamond_gear")
    val NETHERITE_GEAR = registerItem("netherite_gear")
    val EMERALD_GEAR = registerItem("emerald_gear")
    val REDSTONE_GEAR = registerItem("redstone_gear")
    val LAPIS_GEAR = registerItem("lapis_gear")
    val COPPER_GEAR = registerItem("copper_gear")
    
    // Dusts
    val IRON_DUST = registerItem("iron_dust")
    val GOLD_DUST = registerItem("gold_dust")
    val DIAMOND_DUST = registerItem("diamond_dust")
    val NETHERITE_DUST = registerItem("netherite_dust")
    val EMERALD_DUST = registerItem("emerald_dust")
    val LAPIS_DUST = registerItem("lapis_dust")
    val COAL_DUST = registerItem("coal_dust")
    val COPPER_DUST = registerItem("copper_dust")
    val STAR_DUST = registerItem("star_dust")
    
    // Crafting components
    val STAR_SHARDS = registerItem("star_shards")
    val STAR_CRYSTAL = registerItem("star_crystal")
    val NETHERITE_DRILL = registerItem("netherite_drill")
    val SCAFFOLDING = registerItem("scaffolding")
    val SOLAR_CELL = registerItem("solar_cell")
    
    // Blocks
    val AUTO_FISHER = registerItem(Blocks.AUTO_FISHER)
    val FERTILIZER = registerItem(Blocks.FERTILIZER)
    val HARVESTER = registerItem(Blocks.HARVESTER)
    val PLANTER = registerItem(Blocks.PLANTER)
    val TREE_FACTORY = registerItem(Blocks.TREE_FACTORY)
    val CHARGER = registerItem(Blocks.CHARGER)
    val WIRELESS_CHARGER = registerItem(Blocks.WIRELESS_CHARGER)
    val BREEDER = registerItem(Blocks.BREEDER)
    val MOB_DUPLICATOR = registerItem(Blocks.MOB_DUPLICATOR)
    val MOB_KILLER = registerItem(Blocks.MOB_KILLER)
    val COBBLESTONE_GENERATOR = registerItem(Blocks.COBBLESTONE_GENERATOR)
    val ELECTRIC_FURNACE = registerItem(Blocks.ELECTRIC_FURNACE)
    val MECHANICAL_PRESS = registerItem(Blocks.MECHANICAL_PRESS)
    val PULVERIZER = registerItem(Blocks.PULVERIZER)
    val BLOCK_BREAKER = registerItem(Blocks.BLOCK_BREAKER)
    val BLOCK_PLACER = registerItem(Blocks.BLOCK_PLACER)
    val STAR_COLLECTOR = registerItem(Blocks.STAR_COLLECTOR)
    val CHUNK_LOADER = registerItem(Blocks.CHUNK_LOADER)
    val QUARRY = registerItem(Blocks.QUARRY)
    val ELECTRIC_BREWING_STAND = registerItem(Blocks.ELECTRIC_BREWING_STAND)
    val PUMP = registerItem(Blocks.PUMP)
    val FREEZER = registerItem(Blocks.FREEZER)
    val FLUID_INFUSER = registerItem(Blocks.FLUID_INFUSER)
    val SPRINKLER = registerItem(Blocks.SPRINKLER)
    val SOLAR_PANEL = registerItem(Blocks.SOLAR_PANEL)
    val LIGHTNING_EXCHANGER = registerItem(Blocks.LIGHTNING_EXCHANGER)
    val WIND_TURBINE = registerItem(Blocks.WIND_TURBINE)
    val FURNACE_GENERATOR = registerItem(Blocks.FURNACE_GENERATOR)
    val LAVA_GENERATOR = registerItem(Blocks.LAVA_GENERATOR)
    val INFINITE_WATER_SOURCE = registerItem(Blocks.INFINITE_WATER_SOURCE)
    val CRYSTALLIZER = registerItem(Blocks.CRYSTALLIZER)
    val STAR_DUST_BLOCK = registerItem(Blocks.STAR_DUST_BLOCK)
    val BASIC_MACHINE_FRAME = registerItem(Blocks.BASIC_MACHINE_FRAME)
    val ADVANCED_MACHINE_FRAME = registerItem(Blocks.ADVANCED_MACHINE_FRAME)
    val ELITE_MACHINE_FRAME = registerItem(Blocks.ELITE_MACHINE_FRAME)
    val ULTIMATE_MACHINE_FRAME = registerItem(Blocks.ULTIMATE_MACHINE_FRAME)
    val CREATIVE_MACHINE_FRAME = registerItem(Blocks.CREATIVE_MACHINE_FRAME)
    val STAR_SHARDS_ORE = registerItem(Blocks.STAR_SHARDS_ORE)
    val DEEPSLATE_STAR_SHARDS_ORE = registerItem(Blocks.DEEPSLATE_STAR_SHARDS_ORE)
    
}