package xyz.xenondevs.nova.addon.logistics.registry

import xyz.xenondevs.nova.addon.registry.ItemRegistry
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.addon.logistics.Logistics

@Init
object GuiMaterials : ItemRegistry by Logistics.registry {
    
    val ITEM_FILTER_PLACEHOLDER = registerUnnamedHiddenItem("gui_item_filter_placeholder")
    val TRASH_CAN_PLACEHOLDER = registerUnnamedHiddenItem("gui_trash_can_placeholder")
    val NBT_BTN_OFF = registerUnnamedHiddenItem("gui_nbt_btn_off")
    val NBT_BTN_ON = registerUnnamedHiddenItem("gui_nbt_btn_on")
    val WHITELIST_BTN = registerUnnamedHiddenItem("gui_whitelist_btn")
    val BLACKLIST_BTN = registerUnnamedHiddenItem("gui_blacklist_btn")
    
}