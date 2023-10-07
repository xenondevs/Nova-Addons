@file:Suppress("unused")

package xyz.xenondevs.nova.addon.logistics.registry

import xyz.xenondevs.nova.addon.logistics.Logistics
import xyz.xenondevs.nova.addon.logistics.item.ItemFilterBehavior
import xyz.xenondevs.nova.addon.logistics.item.StorageUnitItemBehavior
import xyz.xenondevs.nova.addon.registry.ItemRegistry
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage

@Init(stage = InitStage.PRE_PACK)
object Items : ItemRegistry by Logistics.registry {
    
    val BASIC_CABLE = registerItem(Blocks.BASIC_CABLE)
    val ADVANCED_CABLE = registerItem(Blocks.ADVANCED_CABLE)
    val ELITE_CABLE = registerItem(Blocks.ELITE_CABLE)
    val ULTIMATE_CABLE = registerItem(Blocks.ULTIMATE_CABLE)
    val CREATIVE_CABLE = registerItem(Blocks.CREATIVE_CABLE)
    
    val BASIC_POWER_CELL = registerItem(Blocks.BASIC_POWER_CELL)
    val ADVANCED_POWER_CELL = registerItem(Blocks.ADVANCED_POWER_CELL)
    val ELITE_POWER_CELL = registerItem(Blocks.ELITE_POWER_CELL)
    val ULTIMATE_POWER_CELL = registerItem(Blocks.ULTIMATE_POWER_CELL)
    val CREATIVE_POWER_CELL = registerItem(Blocks.CREATIVE_POWER_CELL)
    
    val BASIC_FLUID_TANK = registerItem(Blocks.BASIC_FLUID_TANK)
    val ADVANCED_FLUID_TANK = registerItem(Blocks.ADVANCED_FLUID_TANK)
    val ELITE_FLUID_TANK = registerItem(Blocks.ELITE_FLUID_TANK)
    val ULTIMATE_FLUID_TANK = registerItem(Blocks.ULTIMATE_FLUID_TANK)
    val CREATIVE_FLUID_TANK = registerItem(Blocks.CREATIVE_FLUID_TANK)
    
    val STORAGE_UNIT = registerItem(Blocks.STORAGE_UNIT, StorageUnitItemBehavior)
    val FLUID_STORAGE_UNIT = registerItem(Blocks.FLUID_STORAGE_UNIT)
    val VACUUM_CHEST = registerItem(Blocks.VACUUM_CHEST)
    val TRASH_CAN = registerItem(Blocks.TRASH_CAN)
    
    val BASIC_ITEM_FILTER = registerItem("basic_item_filter", ItemFilterBehavior)
    val ADVANCED_ITEM_FILTER = registerItem("advanced_item_filter", ItemFilterBehavior)
    val ELITE_ITEM_FILTER = registerItem("elite_item_filter", ItemFilterBehavior)
    val ULTIMATE_ITEM_FILTER = registerItem("ultimate_item_filter", ItemFilterBehavior)
    
    val TANK_WATER_LEVELS = registerUnnamedItem("tank_water_levels")
    val TANK_LAVA_LEVELS = registerUnnamedItem("tank_lava_levels")
    
}