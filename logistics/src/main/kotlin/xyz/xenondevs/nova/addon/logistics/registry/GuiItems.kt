package xyz.xenondevs.nova.addon.logistics.registry

import xyz.xenondevs.nova.addon.logistics.Logistics.item
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.world.item.NovaItem

@Init(stage = InitStage.PRE_PACK)
object GuiItems {
    
    val ITEM_FILTER_PLACEHOLDER = tpGuiItem("gui/placeholder/item_filter", null)
    val TRASH_CAN_PLACEHOLDER = tpGuiItem("gui/placeholder/trash_can", null)
    val NBT_BTN_OFF = guiItem("gui/btn/nbt_off", "menu.logistics.item_filter.nbt.off")
    val NBT_BTN_ON = guiItem("gui/btn/nbt_on", "menu.logistics.item_filter.nbt.on")
    val WHITELIST_BTN = guiItem("gui/btn/whitelist", "menu.logistics.item_filter.whitelist")
    val BLACKLIST_BTN = guiItem("gui/btn/blacklist", "menu.logistics.item_filter.blacklist")
    val PLOT_MODE_ENERGY = tpGuiItem("gui/btn/plot_mode_energy", "menu.logistics.power_cell.graph_mode.energy")
    val PLOT_MODE_ENERGY_DELTA = tpGuiItem("gui/btn/plot_mode_energy_delta", "menu.logistics.power_cell.graph_mode.energy_delta")
    val PLOT_ENLARGE_HORIZONTALLY_ON = tpGuiItem("gui/btn/enlarge_horizontally_on", "menu.logistics.power_cell.graph_enlarge")
    val PLOT_ENLARGE_HORIZONTALLY_OFF = tpGuiItem("gui/btn/enlarge_horizontally_off", null)
    val PLOT_SHRINK_HORIZONTALLY_ON = tpGuiItem("gui/btn/shrink_horizontally_on", "menu.logistics.power_cell.graph_shrink")
    val PLOT_SHRINK_HORIZONTALLY_OFF = tpGuiItem("gui/btn/shrink_horizontally_off", null)
    
    private fun tpGuiItem(name: String, localizedName: String? = null): NovaItem =
        item(name) {
            if (localizedName == null) name(null) else localizedName(localizedName)
            hidden(true)
            modelDefinition { model = buildModel { createGuiModel(background = false, stretched = false, "item/$name") } }
        }
    
    private fun guiItem(name: String, localizedName: String? = null): NovaItem =
        item(name) {
            if (localizedName == null) name(null) else localizedName(localizedName)
            hidden(true)
            modelDefinition { model = buildModel { createGuiModel(background = true, stretched = false, "item/$name") } }
        }
    
}