package xyz.xenondevs.nova.addon.machines.registry

import xyz.xenondevs.nova.addon.machines.Machines
import xyz.xenondevs.nova.addon.registry.ItemRegistry
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.world.item.NovaItem

@Init(stage = InitStage.PRE_PACK)
object GuiItems : ItemRegistry by Machines.registry {
    
    val GEAR_BTN_OFF = guiItem("btn/gear_off", "menu.machines.mechanical_press.press_gears")
    val GEAR_BTN_ON = guiItem("btn/gear_on", "menu.machines.mechanical_press.press_gears")
    val PLATE_BTN_OFF = guiItem("btn/plate_off", "menu.machines.mechanical_press.press_plates")
    val PLATE_BTN_ON = guiItem("btn/plate_on", "menu.machines.mechanical_press.press_plates")
    val NBT_BTN_OFF = guiItem("btn/nbt_off", "menu.machines.mob_duplicator.nbt.off")
    val NBT_BTN_ON = guiItem("btn/nbt_on", "menu.machines.mob_duplicator.nbt.on")
    val COBBLESTONE_MODE_BTN = guiItem("btn/cobblestone", "menu.machines.cobblestone_generator.mode.cobblestone")
    val STONE_MODE_BTN = guiItem("btn/stone", "menu.machines.cobblestone_generator.mode.stone")
    val OBSIDIAN_MODE_BTN = guiItem("btn/obsidian", "menu.machines.cobblestone_generator.mode.obsidian")
    val PUMP_MODE_BTN = guiItem("btn/pump_pump", "menu.machines.pump.pump_mode")
    val PUMP_REPLACE_MODE_BTN = guiItem("btn/pump_replace", "menu.machines.pump.replace_mode")
    val ICE_MODE_BTN = guiItem("btn/ice", "menu.machines.freezer.mode.ice")
    val PACKED_ICE_MODE_BTN = guiItem("btn/packed_ice", "menu.machines.freezer.mode.packed_ice")
    val BLUE_ICE_MODE_BTN = guiItem("btn/blue_ice", "menu.machines.freezer.mode.blue_ice")
    val HOE_BTN_ON = guiItem("btn/hoe_on", "menu.machines.planter.autotill.on")
    val HOE_BTN_OFF = guiItem("btn/hoe_off", "menu.machines.planter.autotill.off")
    val FLUID_LEFT_RIGHT_BTN = guiItem("btn/fluid_left_right", "menu.machines.fluid_infuser.mode.insert")
    val FLUID_RIGHT_LEFT_BTN = guiItem("btn/fluid_right_left", "menu.machines.fluid_infuser.mode.extract")
    val INVENTORY_BTN = guiItem("btn/inventory", "menu.machines.auto_crafter.inventory")
    
    val TP_COLOR_PICKER = guiItem("color_picker")
    val TP_GREEN_PLUS = tpGuiItem("green_plus")
    val TP_RED_MINUS = tpGuiItem("red_minus")
    
    val AXE_PLACEHOLDER = tpGuiItem("placeholder/axe", null)
    val HOE_PLACEHOLDER = tpGuiItem("placeholder/hoe", null)
    val SHEARS_PLACEHOLDER = tpGuiItem("placeholder/shears", null)
    val BOTTLE_PLACEHOLDER = tpGuiItem("placeholder/bottle", null)
    val FISHING_ROD_PLACEHOLDER = tpGuiItem("placeholder/fishing_rod", null)
    val MOB_CATCHER_PLACEHOLDER = tpGuiItem("placeholder/mob_catcher", null)
    val SAPLING_PLACEHOLDER = tpGuiItem("placeholder/sapling", null)
    
    val ARROW_PROGRESS = progressItem("progress/arrow", 17)
    val ENERGY_PROGRESS = progressItem("progress/energy", 17)
    val PULVERIZER_PROGRESS = progressItem("progress/pulverizer", 15)
    val PRESS_PROGRESS = progressItem("progress/press", 9)
    val TP_BREW_PROGRESS = progressItem("progress/brew", 17)
    val FLUID_PROGRESS_LEFT_RIGHT = progressItem("progress/fluid/left_right",17)
    val FLUID_PROGRESS_RIGHT_LEFT = progressItem("progress/fluid/right_left", 17)
    val TP_FLUID_PROGRESS_LEFT_RIGHT = tpProgressItem("progress/fluid/left_right", 17)
    val TP_FLUID_PROGRESS_RIGHT_LEFT = tpProgressItem("progress/fluid/right_left", 17)
    
    private fun guiItem(name: String, localizedName: String? = ""): NovaItem = item("gui/opaque/$name") {
        if (localizedName == null) name(null) else localizedName(localizedName)
        hidden(true)
        
        modelDefinition {
            model = buildModel {
                createGuiModel(background = true, stretched = false, "item/gui/$name")
            }
        }
    }
    
    private fun tpGuiItem(name: String, localizedName: String? = ""): NovaItem = item("gui/transparent/$name") {
        if (localizedName == null) name(null) else localizedName(localizedName)
        hidden(true)
        
        modelDefinition {
            model = buildModel {
                createGuiModel(background = false, stretched = false, "item/gui/$name")
            }
        }
    }
    
    private fun progressItem(name: String, count: Int) = item("gui/opaque/$name") {
        localizedName("")
        hidden(true)
        
        modelDefinition {
            model = rangedModels(count) {
                createGuiModel(background = true, stretched = false, "item/gui/$name/$it")
            }
        }
    }
    
    private fun tpProgressItem(name: String, count: Int) = item("gui/transparent/$name") {
        localizedName("")
        hidden(true)
        
        modelDefinition {
            model = rangedModels(count) {
                createGuiModel(background = false, stretched = false, "item/gui/$name/$it")
            }
        }
    }
    
}