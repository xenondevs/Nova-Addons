@file:Suppress("unused")

package xyz.xenondevs.nova.addon.logistics.registry

import net.kyori.adventure.text.Component
import org.bukkit.block.BlockType
import org.joml.Matrix4f
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.provider
import xyz.xenondevs.nova.addon.logistics.Logistics.item
import xyz.xenondevs.nova.addon.logistics.Logistics.registerItem
import xyz.xenondevs.nova.addon.logistics.item.CableFacadeItemBehavior
import xyz.xenondevs.nova.addon.logistics.item.FluidTankItemBehavior
import xyz.xenondevs.nova.addon.logistics.item.ItemFilterBehavior
import xyz.xenondevs.nova.addon.logistics.item.StorageUnitItemBehavior
import xyz.xenondevs.nova.addon.logistics.item.WrenchBehavior
import xyz.xenondevs.nova.addon.logistics.tileentity.FLUID_TANK_ADVANCED_CAPACITY
import xyz.xenondevs.nova.addon.logistics.tileentity.FLUID_TANK_BASIC_CAPACITY
import xyz.xenondevs.nova.addon.logistics.tileentity.FLUID_TANK_ELITE_CAPACITY
import xyz.xenondevs.nova.addon.logistics.tileentity.FLUID_TANK_ULTIMATE_CAPACITY
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.registry.RegistryEntry
import xyz.xenondevs.nova.resources.ResourcePath
import xyz.xenondevs.nova.resources.ResourceType
import xyz.xenondevs.nova.resources.builder.layout.item.SelectItemModelProperty
import xyz.xenondevs.nova.resources.builder.task.ItemModelContent

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
    
    val BASIC_FLUID_TANK = tank(Blocks.BASIC_FLUID_TANK, "basic", FLUID_TANK_BASIC_CAPACITY)
    val ADVANCED_FLUID_TANK = tank(Blocks.ADVANCED_FLUID_TANK, "advanced", FLUID_TANK_ADVANCED_CAPACITY)
    val ELITE_FLUID_TANK = tank(Blocks.ELITE_FLUID_TANK, "elite", FLUID_TANK_ELITE_CAPACITY)
    val ULTIMATE_FLUID_TANK = tank(Blocks.ULTIMATE_FLUID_TANK, "ultimate", FLUID_TANK_ULTIMATE_CAPACITY)
    val CREATIVE_FLUID_TANK = tank(Blocks.CREATIVE_FLUID_TANK, "creative", provider(Long.MAX_VALUE))
    
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
    
    val CABLE_FACADES = FacadeType.entries.associateWith { type ->
        val tn = type.name.lowercase()
        item("${tn}_cable_facade") {
            behaviors(CableFacadeItemBehavior(type))
            name(Component.translatable("item.logistics.cable_facade", Component.translatable("block.minecraft.${tn}")))
            modelDefinition {
                val modelToEmbed = resourcePackBuilder.getBuildData<ItemModelContent>()
                    .getVanilla(ResourcePath.of(ResourceType.ItemModelDefinition, tn))!!
                    .model
                
                model = composite {
                    models += buildModel { getModel("item/cable_facade") }
                    models += composite {
                        models += modelToEmbed
                        transformation = Matrix4f()
                            .scaleLocal(0.5f)
                            .translateLocal(4f / 16f, 4f / 16f, 4f / 16f)
                    }
                }
            }
        }
    }
    
    private fun cable(block: RegistryEntry.Paper<BlockType>, tier: String) = item(block) {
        modelDefinition { model = buildModel { getModel("item/cable/$tier") } }
    }
    
    private fun tank(block: RegistryEntry.Paper<BlockType>, tier: String, capacity: Provider<Long>) = item(block) {
        behaviors(FluidTankItemBehavior(capacity))
        modelDefinition {
            model = composite {
                models += { getModel("block/fluid_tank/$tier") }
                models += select(SelectItemModelProperty.CustomModelData(0)) {
                    fallback = empty()
                    case["water"] = fluidLevelModel("fluid_tank/water", water = true)
                    case["lava"] = fluidLevelModel("fluid_tank/lava")
                }
            }
        }
    }
    
}