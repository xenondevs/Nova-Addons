package xyz.xenondevs.nova.addon.logistics.registry

import xyz.xenondevs.nova.addon.logistics.Logistics.guiTexture
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.ui.overlay.guitexture.GuiTexture.TitlePosition.Alignment

@Init(stage = InitStage.PRE_PACK)
object GuiTextures {
    
    val ITEM_FILTER = guiTexture("item_filter") {}
    val CABLE_CONFIG_ITEM = guiTexture("cable_config_item") {}
    val CABLE_CONFIG_FLUID = guiTexture("cable_config_fluid") {}
    
    val CREATIVE_POWER_CELL = guiTexture("creative_power_cell") {
        title { dynamicLine(Alignment.CENTER) }
    }
    val FLUID_TANK = guiTexture("fluid_tank") {
        title { dynamicLine(Alignment.CENTER) }
    }
    val STORAGE_UNIT = guiTexture("storage_unit") {
        title { dynamicLine(Alignment.CENTER) }
    }
    val POWER_CELL = guiTexture("power_cell") {}
    val TRASH_CAN = guiTexture("trash_can") {
        title { dynamicLine(Alignment.CENTER) }
    }
    val VACUUM_CHEST = guiTexture("vacuum_chest") {
        title { dynamicLine(Alignment.CENTER) }
    }
    
}