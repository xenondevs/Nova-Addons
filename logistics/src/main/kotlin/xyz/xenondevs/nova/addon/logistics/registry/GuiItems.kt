package xyz.xenondevs.nova.addon.logistics.registry

import xyz.xenondevs.nova.addon.logistics.Logistics
import xyz.xenondevs.nova.addon.registry.ItemRegistry
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.world.item.NovaItem

@Init(stage = InitStage.PRE_PACK)
object GuiItems : ItemRegistry by Logistics.registry {
    
    val ITEM_FILTER_PLACEHOLDER = tpGuiItem("gui/placeholder/item_filter", null)
    val TRASH_CAN_PLACEHOLDER = tpGuiItem("gui/placeholder/trash_can", null)
    val NBT_BTN_OFF = guiItem("gui/btn/nbt_off", "menu.logistics.item_filter.nbt.off")
    val NBT_BTN_ON = guiItem("gui/btn/nbt_on", "menu.logistics.item_filter.nbt.on")
    val WHITELIST_BTN = guiItem("gui/btn/whitelist", "menu.logistics.item_filter.whitelist")
    val BLACKLIST_BTN = guiItem("gui/btn/blacklist", "menu.logistics.item_filter.blacklist")
    
    private fun tpGuiItem(name: String, localizedName: String? = ""): NovaItem =
        item(name) {
            if (localizedName == null) name(null) else localizedName(localizedName)
            hidden(true)
            modelDefinition { model = buildModel { createGuiModel(background = false, stretched = false, "item/$name") } }
        }
    
    private fun guiItem(name: String, localizedName: String? = ""): NovaItem =
        item(name) {
            if (localizedName == null) name(null) else localizedName(localizedName)
            hidden(true)
            modelDefinition { model = buildModel { createGuiModel(background = true, stretched = false, "item/$name") } }
        }
    
}