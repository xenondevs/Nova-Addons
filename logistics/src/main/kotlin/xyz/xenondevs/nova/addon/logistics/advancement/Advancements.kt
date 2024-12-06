package xyz.xenondevs.nova.addon.logistics.advancement

import net.kyori.adventure.text.Component
import net.minecraft.advancements.AdvancementType
import net.minecraft.advancements.DisplayInfo
import net.minecraft.advancements.critereon.PlayerTrigger
import net.minecraft.resources.ResourceLocation
import xyz.xenondevs.nova.addon.logistics.Logistics
import xyz.xenondevs.nova.addon.logistics.registry.Items
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitFun
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.util.advancement.AdvancementLoader
import xyz.xenondevs.nova.util.advancement.advancement
import xyz.xenondevs.nova.util.advancement.obtainNovaItemAdvancement
import xyz.xenondevs.nova.util.component.adventure.toNMSComponent
import xyz.xenondevs.nova.util.unwrap
import java.util.*

@Init(stage = InitStage.POST_WORLD)
object Advancements {
    
    private val ROOT = advancement(Logistics, "root") {
        display(DisplayInfo(
            Items.ULTIMATE_CABLE.clientsideProvider.get().unwrap(),
            Component.translatable("advancement.logistics.root.title").toNMSComponent(),
            Component.empty().toNMSComponent(),
            Optional.of(ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/tuff.png")),
            AdvancementType.TASK,
            false, false, false
        ))
        
        addCriterion("tick", PlayerTrigger.TriggerInstance.tick())
    }
    
    //<editor-fold desc="Cables" defaultstate="collapsed">
    private val BASIC_CABLE = obtainNovaItemAdvancement(Logistics, ROOT, Items.BASIC_CABLE)
    private val ADVANCED_CABLE = obtainNovaItemAdvancement(Logistics, BASIC_CABLE, Items.ADVANCED_CABLE)
    private val ELITE_CABLE = obtainNovaItemAdvancement(Logistics, ADVANCED_CABLE, Items.ELITE_CABLE)
    private val ULTIMATE_CABLE = obtainNovaItemAdvancement(Logistics, ELITE_CABLE, Items.ULTIMATE_CABLE)
    //</editor-fold>
    
    //<editor-fold desc="Power Cells" defaultstate="collapsed">
    private val BASIC_POWER_CELL = obtainNovaItemAdvancement(Logistics, ROOT, Items.BASIC_POWER_CELL)
    private val ADVANCED_POWER_CELL = obtainNovaItemAdvancement(Logistics, BASIC_POWER_CELL, Items.ADVANCED_POWER_CELL)
    private val ELITE_POWER_CELL = obtainNovaItemAdvancement(Logistics, ADVANCED_POWER_CELL, Items.ELITE_POWER_CELL)
    private val ULTIMATE_POWER_CELL = obtainNovaItemAdvancement(Logistics, ELITE_POWER_CELL, Items.ULTIMATE_POWER_CELL)
    //</editor-fold>
    
    //<editor-fold desc="Fluid Storage" defaultstate="collapsed">
    private val BASIC_FLUID_TANK = obtainNovaItemAdvancement(Logistics, ROOT, Items.BASIC_FLUID_TANK)
    private val ADVANCED_FLUID_TANK = obtainNovaItemAdvancement(Logistics, BASIC_FLUID_TANK, Items.ADVANCED_FLUID_TANK)
    private val ELITE_FLUID_TANK = obtainNovaItemAdvancement(Logistics, ADVANCED_FLUID_TANK, Items.ELITE_FLUID_TANK)
    private val ULTIMATE_FLUID_TANK = obtainNovaItemAdvancement(Logistics, ELITE_FLUID_TANK, Items.ULTIMATE_FLUID_TANK)
    private val FLUID_STORAGE_UNIT = obtainNovaItemAdvancement(Logistics, ULTIMATE_FLUID_TANK, Items.FLUID_STORAGE_UNIT)
    //</editor-fold>
    
    //<editor-fold desc="Items" defaultstate="collapsed">
    private val TRASH_CAN = obtainNovaItemAdvancement(Logistics, ROOT, Items.TRASH_CAN)
    private val VACUUM_CHEST = obtainNovaItemAdvancement(Logistics, TRASH_CAN, Items.VACUUM_CHEST)
    private val STORAGE_UNIT = obtainNovaItemAdvancement(Logistics, VACUUM_CHEST, Items.STORAGE_UNIT)
    //</editor-fold>
    
    @InitFun
    private fun register() {
        AdvancementLoader.registerAdvancements(
            ROOT, BASIC_CABLE, ADVANCED_CABLE, ELITE_CABLE, ULTIMATE_CABLE, BASIC_POWER_CELL, ADVANCED_POWER_CELL,
            ELITE_POWER_CELL, ULTIMATE_POWER_CELL, BASIC_FLUID_TANK, ADVANCED_FLUID_TANK, ELITE_FLUID_TANK,
            ULTIMATE_FLUID_TANK, FLUID_STORAGE_UNIT, TRASH_CAN, VACUUM_CHEST, STORAGE_UNIT
        )
    }
    
}