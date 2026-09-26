package xyz.xenondevs.nova.addon.machines.tileentity.processing.brewing

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.PotionContents.potionContents
import net.kyori.adventure.text.Component
import org.bukkit.Color
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemType
import org.bukkit.potion.PotionEffectType
import org.bukkit.potion.PotionType
import xyz.xenondevs.commons.provider.MutableProvider
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.combinedProvider
import xyz.xenondevs.commons.provider.dsl.by
import xyz.xenondevs.commons.provider.mapEach
import xyz.xenondevs.commons.provider.mapNonNull
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.commons.provider.plus
import xyz.xenondevs.commons.provider.provider
import xyz.xenondevs.commons.tuple.Tuple3
import xyz.xenondevs.invui.dsl.ScrollGuiDsl
import xyz.xenondevs.invui.dsl.WindowDsl
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.dsl.item
import xyz.xenondevs.invui.dsl.itemProvider
import xyz.xenondevs.invui.dsl.scrollGuisGui
import xyz.xenondevs.invui.dsl.scrollItemsGui
import xyz.xenondevs.invui.dsl.window
import xyz.xenondevs.invui.item.ItemWrapper
import xyz.xenondevs.invui.window.Window
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.tileentity.processing.brewing.ElectricBrewingStand.Companion.AVAILABLE_POTION_EFFECTS
import xyz.xenondevs.nova.registry.RegistryEntry
import xyz.xenondevs.nova.registry.entries.ItemTypeEntries
import xyz.xenondevs.nova.ui.menu.by
import xyz.xenondevs.nova.ui.menu.item.SCROLLABLE_BASE
import xyz.xenondevs.nova.ui.menu.item.SCROLL_ENABLING_VISUALIZER_EMPTIES
import xyz.xenondevs.nova.ui.menu.item.backItem
import xyz.xenondevs.nova.ui.menu.item.displayNumberItem
import xyz.xenondevs.nova.ui.menu.item.installItemScrollSupport
import xyz.xenondevs.nova.ui.menu.item.scrollBar
import xyz.xenondevs.nova.ui.menu.item.scrollableItemProvider
import xyz.xenondevs.nova.ui.menu.item.scrollerItem
import xyz.xenondevs.nova.util.playClickSound
import xyz.xenondevs.nova.util.playItemPickupSound
import xyz.xenondevs.nova.world.block.tileentity.TileEntity
import xyz.xenondevs.nova.world.item.DefaultGuiItems
import xyz.xenondevs.nova.world.item.guiItemProvider

context(tileEntity: TileEntity, windowDsl: WindowDsl)
fun potionConfiguratorWindow(
    bottleType: MutableProvider<PotionBottleType>,
    color: MutableProvider<Color>,
    effects: MutableProvider<List<PotionEffectProvider>>
): Window {
    val selectedEffect = mutableProvider<PotionEffectProvider?>(null)
    val (red, green, blue) = color.decompose(
        { Tuple3(it.red, it.green, it.blue) },
        { r, g, b -> Color.fromRGB(r, g, b) }
    )
    
    lateinit var configuratorWindow: Provider<Window>
    lateinit var potionPickerWindow: Provider<Window>
    
    configuratorWindow = provider {
        window(windowDsl.viewer) {
            title by GuiTextures.CONFIGURE_POTION
            upperGui by scrollGuisGui(
                "< . . . . . . . .",
                "1 2 3 . x x x x |",
                "r g b . x x x x |",
                "R G B . x x x x |"
            ) {
                '<' by backItem(windowDsl.window, DefaultGuiItems.TP_SMALL_ARROW_LEFT_ON.guiItemProvider)
                '1' by potionTypeItem(bottleType, PotionBottleType.NORMAL, color)
                '2' by potionTypeItem(bottleType, PotionBottleType.SPLASH, color)
                '3' by potionTypeItem(bottleType, PotionBottleType.LINGERING, color)
                'r' by changeColorItem(red, ItemType.RED_DYE)
                'g' by changeColorItem(green, ItemType.GREEN_DYE)
                'b' by changeColorItem(blue, ItemType.BLUE_DYE)
                'R' by displayNumberItem(red)
                'G' by displayNumberItem(green)
                'B' by displayNumberItem(blue)
                
                '|' by scrollBar()
                content by effects
                    .mapEach { effect ->
                        gui("- p d a ") {
                            '-' by removeEffectItem(effect, effects)
                            'p' by effectPickerItem(bottleType, effect, selectedEffect, potionPickerWindow)
                            'd' by modifierItem(
                                effect.durationLevel,
                                effect.amplifierLevel,
                                effect.maxDurationLevel,
                                "menu.machines.potion_configurator.duration",
                                ItemTypeEntries.REDSTONE,
                                GuiItems.REDSTONE_PLACEHOLDER
                            )
                            'a' by modifierItem(
                                effect.amplifierLevel,
                                effect.durationLevel,
                                effect.maxAmplifierLevel,
                                "menu.machines.potion_configurator.amplifier",
                                ItemTypeEntries.GLOWSTONE_DUST,
                                GuiItems.GLOWSTONE_DUST_PLACEHOLDER
                            )
                        }
                    } +
                    gui("+ . . .") {
                        '+' by addEffectItem(effects)
                        '.' by item {
                            itemProvider by SCROLL_ENABLING_VISUALIZER_EMPTIES(null)!!
                            installItemScrollSupport()
                        }
                    }
            }
        }.also(tileEntity.menu::register)
    }
    
    potionPickerWindow = provider {
        window(windowDsl.viewer) {
            title by GuiTextures.POTION_PICKER
            upperGui by scrollItemsGui(
                "< . . . . . . . .",
                "x x x x x x x x |",
                "x x x x x x x x |",
                "x x x x x x x x |",
            ) {
                '<' by backItem(configuratorWindow, DefaultGuiItems.TP_SMALL_ARROW_LEFT_ON.guiItemProvider)
                '|' by scrollBar(offset = 2)
                content by effects
                    .flatMap { combinedProvider(it.map(PotionEffectProvider::type)) }
                    .map { effectTypes ->
                        AVAILABLE_POTION_EFFECTS.keys
                            .filter { type -> type !in effectTypes }
                            .map { effectType -> effectTypeItem(effectType, bottleType, selectedEffect, configuratorWindow) }
                    }
            }
        }.also(tileEntity.menu::register)
    }
    
    return configuratorWindow.get()
}

private fun potionTypeItem(
    currentBottleType: MutableProvider<PotionBottleType>,
    targetBottleType: PotionBottleType,
    color: Provider<Color>
) = item {
    itemProvider by itemProvider {
        base by currentBottleType
            .flatMap { current ->
                if (current == targetBottleType) targetBottleType.item else targetBottleType.placeholder
            }
            .map { it.get() }
        customName by Component.translatable(when (targetBottleType) {
            PotionBottleType.NORMAL -> "menu.machines.potion_configurator.potion_type.normal"
            PotionBottleType.SPLASH -> "menu.machines.potion_configurator.potion_type.splash"
            PotionBottleType.LINGERING -> "menu.machines.potion_configurator.potion_type.lingering"
        })
        data[DataComponentTypes.POTION_CONTENTS] by color.map { c ->
            potionContents().customColor(c).build()
        }
        hiddenComponents by DataComponentTypes.POTION_CONTENTS
    }
    onClick {
        player.playItemPickupSound()
        currentBottleType.set(targetBottleType)
    }
}

context(_: WindowDsl, _: ScrollGuiDsl<*>)
private fun removeEffectItem(
    effect: PotionEffectProvider,
    effects: MutableProvider<List<PotionEffectProvider>>
) = item {
    itemProvider by itemProvider(DefaultGuiItems.TP_MINUS_BTN_ON) {
        name by Component.translatable("menu.machines.potion_configurator.remove_effect")
    }.map { scrollableItemProvider(it) }
    onClick {
        player.playClickSound()
        effects.set(effects.get() - effect)
    }
    installItemScrollSupport()
}

context(_: WindowDsl, _: ScrollGuiDsl<*>)
private fun effectPickerItem(
    bottleType: Provider<PotionBottleType>,
    effect: PotionEffectProvider,
    selectedEffect: MutableProvider<PotionEffectProvider?>,
    potionPickerWindow: Provider<Window>
) = item {
    itemProvider by itemProvider {
        base by SCROLLABLE_BASE
        data[DataComponentTypes.ITEM_MODEL] by effect.type
            .flatMap { if (it != null) bottleType.itemType else provider(ItemType.GLASS_BOTTLE) }
            .map { it.key }
        customName by effect.type.map {
            Component.translatable(
                if (it != null) "menu.machines.potion_configurator.effect"
                else "menu.machines.potion_configurator.pick_effect"
            )
        }
        data[DataComponentTypes.POTION_CONTENTS] by effect.result
            .mapNonNull { potionContents().potion(PotionType.WATER).addCustomEffect(it.effect).build() }
    }
    onClick {
        selectedEffect.set(effect)
        potionPickerWindow.get().open()
    }
    installItemScrollSupport()
}

context(_: WindowDsl, scrollGuiDsl: ScrollGuiDsl<*>)
private fun addEffectItem(effects: MutableProvider<List<PotionEffectProvider>>) = item {
    itemProvider by itemProvider(DefaultGuiItems.TP_PLUS_BTN_ON) {
        customName by Component.translatable("menu.machines.potion_configurator.add_effect")
    }.map { scrollableItemProvider(it) }
    onClick {
        var effects by effects
        var line by scrollGuiDsl.line
        val maxLine by scrollGuiDsl.maxLine
        if (effects.size >= 100)
            return@onClick
        effects += PotionEffectProvider()
        if (line >= maxLine)
            line++
        player.playClickSound()
    }
    installItemScrollSupport()
}

context(_: WindowDsl, _: ScrollGuiDsl<*>)
private fun modifierItem(
    level: MutableProvider<Int>,
    otherLevel: MutableProvider<Int>,
    maxLevel: Provider<Int>,
    translationKey: String,
    item: RegistryEntry.Paper<ItemType>,
    placeholder: RegistryEntry.Paper<ItemType>
) = item {
    itemProvider by itemProvider {
        type by level.flatMap { if (it > 0) item else placeholder }
        name by combinedProvider(level, maxLevel).map { (level, maxLevel) ->
            Component.translatable(translationKey, Component.text(level), Component.text(maxLevel))
        }
        amount by level.map { it.coerceIn(1..99) }
        data[DataComponentTypes.MAX_STACK_SIZE] by 99
    }.map { scrollableItemProvider(it) }
    onClick {
        var level by level
        var otherLevel by otherLevel
        val maxLevel by maxLevel
        val target = (level + when {
            clickType.isLeftClick -> 1
            clickType.isRightClick -> -1
            else -> 0
        }).coerceIn(0..maxLevel)
        
        if (target != level) {
            level = target
            if (!ElectricBrewingStand.ALLOW_DURATION_AMPLIFIER_MIXING)
                otherLevel = 0
            player.playItemPickupSound()
        }
    }
    installItemScrollSupport()
}

context(_: WindowDsl, _: ScrollGuiDsl<*>)
private fun effectTypeItem(
    effectType: PotionEffectType,
    bottleType: Provider<PotionBottleType>,
    currentEffect: MutableProvider<PotionEffectProvider?>,
    configuratorWindow: Provider<Window>
) = item {
    itemProvider by itemProvider {
        base by SCROLLABLE_BASE
        data[DataComponentTypes.ITEM_MODEL] by bottleType.typeKey
        name by Component.translatable("menu.machines.potion_configurator.effect")
        @Suppress("DEPRECATION")
        data[DataComponentTypes.POTION_CONTENTS] by PotionType.getByEffect(effectType)?.let { potionContents().potion(it).build() }
    }
    onClick {
        currentEffect.get()?.type?.set(effectType)
        configuratorWindow.get().open()
        player.playItemPickupSound()
    }
    installItemScrollSupport()
}

context(windowDsl: WindowDsl)
private fun changeColorItem(
    number: MutableProvider<Int>,
    type: ItemType
) = scrollerItem(
    windowDsl.window,
    number,
    provider(255),
    provider(ItemWrapper(type.createItemStack())),
    -10
) {
    var number by number
    var targetNumber = number + when (clickType) {
        ClickType.LEFT -> 1
        ClickType.SHIFT_LEFT -> 10
        ClickType.RIGHT -> -1
        ClickType.SHIFT_RIGHT -> -10
        else -> 0
    }
    targetNumber = targetNumber.coerceIn(0..255)
    if (targetNumber != number)
        number = targetNumber
}
