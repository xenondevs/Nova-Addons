package xyz.xenondevs.nova.addon.machines.tileentity.processing.brewing

import net.kyori.adventure.key.Key
import org.bukkit.inventory.ItemType
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.provider
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.invui.item.ItemWrapper
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.world.item.guiItemProvider

val Provider<PotionBottleType>.itemType: Provider<ItemType>
    get() = map(PotionBottleType::itemType)

val Provider<PotionBottleType>.typeKey: Provider<Key>
    get() = map(PotionBottleType::typeKey)

enum class PotionBottleType(
    val itemType: ItemType,
    val placeholder: Provider<ItemProvider>
) {
    
    NORMAL(ItemType.POTION, GuiItems.POTION_PLACEHOLDER.guiItemProvider),
    SPLASH(ItemType.SPLASH_POTION, GuiItems.SPLASH_POTION_PLACEHOLDER.guiItemProvider),
    LINGERING(ItemType.LINGERING_POTION, GuiItems.LINGERING_POTION_PLACEHOLDER.guiItemProvider);
    
    val item = provider(ItemWrapper(itemType.createItemStack()))
    val typeKey = itemType.key
    
}