package xyz.xenondevs.nova.addon.machines.advancement

import net.kyori.adventure.text.Component
import net.minecraft.advancements.AdvancementType
import net.minecraft.advancements.DisplayInfo
import net.minecraft.advancements.critereon.PlayerTrigger
import net.minecraft.core.ClientAsset
import net.minecraft.resources.ResourceLocation
import xyz.xenondevs.nova.addon.machines.Machines
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitFun
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.util.advancement.AdvancementLoader
import xyz.xenondevs.nova.util.advancement.advancement
import xyz.xenondevs.nova.util.advancement.obtainNovaItemAdvancement
import xyz.xenondevs.nova.util.advancement.obtainNovaItemsAdvancement
import xyz.xenondevs.nova.util.component.adventure.toNMSComponent
import xyz.xenondevs.nova.util.unwrap
import java.util.*

private val ROOT = advancement(Machines, "root") {
    display(DisplayInfo(
        Items.QUARRY.clientsideProvider.get().unwrap(),
        Component.translatable("advancement.machines.root.title").toNMSComponent(),
        Component.empty().toNMSComponent(),
        Optional.of(ClientAsset(ResourceLocation.withDefaultNamespace("block/tuff"))),
        AdvancementType.TASK,
        false, false, false
    ))
    
    addCriterion("tick", PlayerTrigger.TriggerInstance.tick())
}

//<editor-fold desc="Power Generation" defaultstate="collapsed">
private val FURNACE_GENERATOR = obtainNovaItemAdvancement(Machines, ROOT, Items.FURNACE_GENERATOR)
private val LAVA_GENERATOR = obtainNovaItemAdvancement(Machines, FURNACE_GENERATOR, Items.LAVA_GENERATOR)
private val SOLAR_PANEL = obtainNovaItemAdvancement(Machines, LAVA_GENERATOR, Items.SOLAR_PANEL)
private val WIND_TURBINE = obtainNovaItemAdvancement(Machines, SOLAR_PANEL, Items.WIND_TURBINE)
private val LIGHTNING_EXCHANGER = obtainNovaItemAdvancement(Machines, WIND_TURBINE, Items.LIGHTNING_EXCHANGER)
//</editor-fold>

//<editor-fold desc="Farming" defaultstate="collapsed">
private val PLANTER = obtainNovaItemAdvancement(Machines, ROOT, Items.PLANTER)
private val SPRINKLER = obtainNovaItemAdvancement(Machines, PLANTER, Items.SPRINKLER)
private val FERTILIZER = obtainNovaItemAdvancement(Machines, SPRINKLER, Items.FERTILIZER)
private val HARVESTER = obtainNovaItemAdvancement(Machines, FERTILIZER, Items.HARVESTER)
private val TREE_FACTORY = obtainNovaItemAdvancement(Machines, HARVESTER, Items.TREE_FACTORY)
//</editor-fold>

//<editor-fold desc="Mobs" defaultstate="collapsed">
private val MOB_CATCHER = obtainNovaItemAdvancement(Machines, ROOT, Items.MOB_CATCHER)
private val BREEDER = obtainNovaItemAdvancement(Machines, MOB_CATCHER, Items.BREEDER)
private val MOB_KILLER = obtainNovaItemAdvancement(Machines, BREEDER, Items.MOB_KILLER)
private val MOB_DUPLICATOR = obtainNovaItemAdvancement(Machines, MOB_KILLER, Items.MOB_DUPLICATOR)
//</editor-fold>

//<editor-fold desc="Blocks" defaultstate="collapsed">
private val BLOCK_PLACER = obtainNovaItemAdvancement(Machines, ROOT, Items.BLOCK_PLACER)
private val BLOCK_BREAKER = obtainNovaItemAdvancement(Machines, BLOCK_PLACER, Items.BLOCK_BREAKER)
private val QUARRY = obtainNovaItemAdvancement(Machines, BLOCK_BREAKER, Items.QUARRY)
//</editor-fold>

//<editor-fold desc="Star Shards" defaultstate="collapsed">
private val STAR_SHARDS = obtainNovaItemAdvancement(Machines, ROOT, Items.STAR_SHARDS)
private val STAR_COLLECTOR = obtainNovaItemAdvancement(Machines, STAR_SHARDS, Items.STAR_COLLECTOR)
private val CRYSTALLIZER = obtainNovaItemAdvancement(Machines, STAR_COLLECTOR, Items.CRYSTALLIZER)
private val STAR_TOOLS = obtainNovaItemsAdvancement(Machines, "all_star_tools", CRYSTALLIZER, listOf(
    Items.STAR_SWORD, Items.STAR_AXE, Items.STAR_PICKAXE, Items.STAR_HOE, Items.STAR_SHOVEL
), true)
//</editor-fold>

//<editor-fold desc="Fluids" defaultstate="collapsed">
private val PUMP = obtainNovaItemAdvancement(Machines, ROOT, Items.PUMP)
private val COBBLESTONE_GENERATOR = obtainNovaItemAdvancement(Machines, PUMP, Items.COBBLESTONE_GENERATOR)
private val FREEZER = obtainNovaItemAdvancement(Machines, COBBLESTONE_GENERATOR, Items.FREEZER)
private val FLUID_INFUSER = obtainNovaItemAdvancement(Machines, PUMP, Items.FLUID_INFUSER)
private val ELECTRIC_BREWING_STAND = obtainNovaItemAdvancement(Machines, FLUID_INFUSER, Items.ELECTRIC_BREWING_STAND)
//</editor-fold>

//<editor-fold desc="Pulverizing" defaultstate="collapsed">
private val PULVERIZER = obtainNovaItemAdvancement(Machines, ROOT, Items.PULVERIZER)
private val DUST = obtainNovaItemsAdvancement(Machines, "dust", PULVERIZER, listOf(
    Items.IRON_DUST, Items.GOLD_DUST, Items.DIAMOND_DUST,
    Items.NETHERITE_DUST, Items.EMERALD_DUST, Items.LAPIS_DUST,
    Items.COAL_DUST, Items.COPPER_DUST, Items.STAR_DUST
), false)
private val ALL_DUSTS = obtainNovaItemsAdvancement(Machines, "all_dusts", DUST, listOf(
    Items.DIAMOND_DUST, Items.IRON_DUST, Items.GOLD_DUST,
    Items.NETHERITE_DUST, Items.EMERALD_DUST, Items.LAPIS_DUST,
    Items.COAL_DUST, Items.COPPER_DUST, Items.STAR_DUST
), true)
//</editor-fold>

//<editor-fold desc="Mechanical Press" defaultstate="collapsed">
private val MECHANICAL_PRESS = obtainNovaItemAdvancement(Machines, ROOT, Items.MECHANICAL_PRESS)
private val GEAR = obtainNovaItemsAdvancement(Machines, "gears", MECHANICAL_PRESS, listOf(
    Items.IRON_GEAR, Items.GOLD_GEAR, Items.DIAMOND_GEAR,
    Items.NETHERITE_GEAR, Items.EMERALD_GEAR, Items.LAPIS_GEAR,
    Items.REDSTONE_GEAR, Items.COPPER_GEAR
), false)
private val ALL_GEARS = obtainNovaItemsAdvancement(Machines, "all_gears", GEAR, listOf(
    Items.DIAMOND_GEAR, Items.IRON_GEAR, Items.GOLD_GEAR,
    Items.NETHERITE_GEAR, Items.EMERALD_GEAR, Items.LAPIS_GEAR,
    Items.REDSTONE_GEAR, Items.COPPER_GEAR
), true)
private val PLATE = obtainNovaItemsAdvancement(Machines, "plates", MECHANICAL_PRESS, listOf(
    Items.IRON_PLATE, Items.GOLD_PLATE, Items.DIAMOND_PLATE,
    Items.NETHERITE_PLATE, Items.EMERALD_PLATE, Items.LAPIS_PLATE,
    Items.REDSTONE_PLATE, Items.COPPER_PLATE
), false)
private val ALL_PLATES = obtainNovaItemsAdvancement(Machines, "all_plates", PLATE, listOf(
    Items.DIAMOND_PLATE, Items.IRON_PLATE, Items.GOLD_PLATE,
    Items.NETHERITE_PLATE, Items.EMERALD_PLATE, Items.LAPIS_PLATE,
    Items.REDSTONE_PLATE, Items.COPPER_PLATE
), true)
//</editor-fold>

//<editor-fold desc="Charger" defaultstate="collapsed">
private val CHARGER = obtainNovaItemAdvancement(Machines, ROOT, Items.CHARGER)
private val WIRELESS_CHARGER = obtainNovaItemAdvancement(Machines, CHARGER, Items.WIRELESS_CHARGER)
//</editor-fold>

//<editor-fold desc="Miscellaneous" defaultstate="collapsed">
private val AUTO_FISHER = obtainNovaItemAdvancement(Machines, ROOT, Items.AUTO_FISHER)
//</editor-fold>

@Init(stage = InitStage.POST_WORLD)
object Advancements {
    
    @InitFun
    fun register() {
        AdvancementLoader.registerAdvancements(
            ROOT, FURNACE_GENERATOR, LAVA_GENERATOR, SOLAR_PANEL, WIND_TURBINE, LIGHTNING_EXCHANGER, PLANTER,
            SPRINKLER, FERTILIZER, HARVESTER, TREE_FACTORY, MOB_CATCHER, BREEDER, MOB_KILLER, MOB_DUPLICATOR,
            BLOCK_PLACER, BLOCK_BREAKER, QUARRY, STAR_SHARDS, STAR_COLLECTOR, CRYSTALLIZER, STAR_TOOLS, PUMP, COBBLESTONE_GENERATOR,
            FLUID_INFUSER, ELECTRIC_BREWING_STAND, PULVERIZER, DUST, ALL_DUSTS, GEAR, ALL_GEARS, PLATE, ALL_PLATES,
            MECHANICAL_PRESS, GEAR, ALL_GEARS, PLATE, ALL_PLATES, CHARGER, WIRELESS_CHARGER, AUTO_FISHER
        )
    }
    
}