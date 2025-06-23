@file:Suppress("unused")

package xyz.xenondevs.nova.addon.logistics.registry

import xyz.xenondevs.nova.addon.logistics.Logistics.item
import xyz.xenondevs.nova.addon.logistics.Logistics.registerItem
import xyz.xenondevs.nova.addon.logistics.item.ItemFilterBehavior
import xyz.xenondevs.nova.addon.logistics.item.StorageUnitItemBehavior
import xyz.xenondevs.nova.addon.logistics.item.WrenchBehavior
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.world.block.NovaBlock

@Init(stage = InitStage.PRE_PACK)
object Items {
    
    val BASIC_CABLE = cable(Blocks.BASIC_CABLE, "basic")
    val ADVANCED_CABLE = cable(Blocks.ADVANCED_CABLE, "advanced")
    val ELITE_CABLE = cable(Blocks.ELITE_CABLE, "elite")
    val ULTIMATE_CABLE = cable(Blocks.ULTIMATE_CABLE, "ultimate")
    val CREATIVE_CABLE = cable(Blocks.CREATIVE_CABLE, "creative")
    
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
    
    val WRENCH = item("wrench") {
        behaviors(WrenchBehavior)
        maxStackSize(1)
    }
    
    private fun cable(block: NovaBlock, tier: String) = item(block) {
        modelDefinition { model = buildModel { getModel("item/cable/$tier") } }
    }
    
}