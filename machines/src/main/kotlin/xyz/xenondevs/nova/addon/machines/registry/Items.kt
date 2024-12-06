@file:Suppress("unused")

package xyz.xenondevs.nova.addon.machines.registry

import org.bukkit.inventory.EquipmentSlot
import xyz.xenondevs.nova.addon.machines.Machines
import xyz.xenondevs.nova.addon.machines.item.MobCatcherBehavior
import xyz.xenondevs.nova.addon.registry.ItemRegistry
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.world.item.behavior.Damageable
import xyz.xenondevs.nova.world.item.behavior.Enchantable
import xyz.xenondevs.nova.world.item.behavior.Equippable
import xyz.xenondevs.nova.world.item.behavior.Tool

@Init(stage = InitStage.PRE_PACK)
object Items : ItemRegistry by Machines.registry {
    
    val WIND_TURBINE = item(Blocks.WIND_TURBINE) { modelDefinition { model = buildModel { getModel("item/wind_turbine") } } }
    val SOLAR_PANEL = registerItem(Blocks.SOLAR_PANEL)
    val LIGHTNING_EXCHANGER = registerItem(Blocks.LIGHTNING_EXCHANGER)
    val FURNACE_GENERATOR = registerItem(Blocks.FURNACE_GENERATOR)
    val LAVA_GENERATOR = registerItem(Blocks.LAVA_GENERATOR)
    val MECHANICAL_PRESS = registerItem(Blocks.MECHANICAL_PRESS)
    val PULVERIZER = registerItem(Blocks.PULVERIZER)
    val ELECTRIC_FURNACE = registerItem(Blocks.ELECTRIC_FURNACE)
    val BLOCK_PLACER = registerItem(Blocks.BLOCK_PLACER)
    val BLOCK_BREAKER = registerItem(Blocks.BLOCK_BREAKER)
    val QUARRY = registerItem(Blocks.QUARRY)
    val CHARGER = registerItem(Blocks.CHARGER)
    val WIRELESS_CHARGER = registerItem(Blocks.WIRELESS_CHARGER)
    val AUTO_FISHER = registerItem(Blocks.AUTO_FISHER)
    val BREEDER = registerItem(Blocks.BREEDER)
    val MOB_KILLER = registerItem(Blocks.MOB_KILLER)
    val MOB_DUPLICATOR = registerItem(Blocks.MOB_DUPLICATOR)
    val PLANTER = registerItem(Blocks.PLANTER)
    val FERTILIZER = registerItem(Blocks.FERTILIZER)
    val HARVESTER = registerItem(Blocks.HARVESTER)
    val CHUNK_LOADER = registerItem(Blocks.CHUNK_LOADER)
    val COBBLESTONE_GENERATOR = registerItem(Blocks.COBBLESTONE_GENERATOR)
    val FLUID_INFUSER = registerItem(Blocks.FLUID_INFUSER)
    val FREEZER = registerItem(Blocks.FREEZER)
    val ELECTRIC_BREWING_STAND = registerItem(Blocks.ELECTRIC_BREWING_STAND)
    val SPRINKLER = registerItem(Blocks.SPRINKLER)
    val PUMP = registerItem(Blocks.PUMP)
    val STAR_COLLECTOR = registerItem(Blocks.STAR_COLLECTOR)
    val CRYSTALLIZER = registerItem(Blocks.CRYSTALLIZER)
    val AUTO_CRAFTER = registerItem(Blocks.AUTO_CRAFTER)
    val TREE_FACTORY = registerItem(Blocks.TREE_FACTORY)
    val INFINITE_WATER_SOURCE = registerItem(Blocks.INFINITE_WATER_SOURCE)
    val BASIC_MACHINE_FRAME = registerItem(Blocks.BASIC_MACHINE_FRAME)
    val ADVANCED_MACHINE_FRAME = registerItem(Blocks.ADVANCED_MACHINE_FRAME)
    val ELITE_MACHINE_FRAME = registerItem(Blocks.ELITE_MACHINE_FRAME)
    val ULTIMATE_MACHINE_FRAME = registerItem(Blocks.ULTIMATE_MACHINE_FRAME)
    val CREATIVE_MACHINE_FRAME = registerItem(Blocks.CREATIVE_MACHINE_FRAME)
    val STAR_DUST_BLOCK = registerItem(Blocks.STAR_DUST_BLOCK)
    val STAR_SHARDS_ORE = registerItem(Blocks.STAR_SHARDS_ORE)
    val DEEPSLATE_STAR_SHARDS_ORE = registerItem(Blocks.DEEPSLATE_STAR_SHARDS_ORE)
    
    // Crafting components
    val STAR_SHARDS = registerItem("star_shards")
    val STAR_CRYSTAL = registerItem("star_crystal")
    val NETHERITE_DRILL = item("netherite_drill") { modelDefinition { model = buildModel { getModel("block/netherite_drill") } } }
    val SCAFFOLDING = registerItem("scaffolding")
    val SOLAR_CELL = registerItem("solar_cell")
    
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
    
    // Tools
    val STAR_SWORD = item("star_sword") {
        behaviors(Tool(), Damageable(), Enchantable())
        maxStackSize(1)
    }
    val STAR_SHOVEL = item("star_shovel") {
        behaviors(Tool(), Damageable(), Enchantable())
        maxStackSize(1)
    }
    val STAR_PICKAXE = item("star_pickaxe") {
        behaviors(Tool(), Damageable(), Enchantable())
        maxStackSize(1)
    }
    val STAR_AXE = item("star_axe") {
        behaviors(Tool(), Damageable(), Enchantable())
        maxStackSize(1)
    }
    val STAR_HOE = item("star_hoe") {
        behaviors(Tool(), Damageable(), Enchantable())
        maxStackSize(1)
    }
    
    // Armor
    val STAR_HELMET = item("star_helmet") {
        behaviors(Equippable(Equipment.STAR, EquipmentSlot.HEAD, equipSound = Sounds.ARMOR_EQUIP_STAR), Damageable(), Enchantable())
        maxStackSize(1)
    }
    val STAR_CHESTPLATE = item("star_chestplate") {
        behaviors(Equippable(Equipment.STAR, EquipmentSlot.CHEST, equipSound = Sounds.ARMOR_EQUIP_STAR), Damageable(), Enchantable())
        maxStackSize(1)
    }
    val STAR_LEGGINGS = item("star_leggings") {
        behaviors(Equippable(Equipment.STAR, EquipmentSlot.LEGS, equipSound = Sounds.ARMOR_EQUIP_STAR), Damageable(), Enchantable())
        maxStackSize(1)
    }
    val STAR_BOOTS = item("star_boots") {
        behaviors(Equippable(Equipment.STAR, EquipmentSlot.FEET, equipSound = Sounds.ARMOR_EQUIP_STAR), Damageable(), Enchantable())
        maxStackSize(1)
    }
    
    val MOB_CATCHER = registerItem("mob_catcher", MobCatcherBehavior)
    
}