package xyz.xenondevs.nova.addon.machines.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.builder.setDisplayName
import xyz.xenondevs.nova.item.DefaultGuiItems
import xyz.xenondevs.nova.ui.menu.VerticalBar
import xyz.xenondevs.nova.ui.menu.item.reactiveItem

class IdleBar(
    height: Int,
    private val translationKey: String,
    private val timePassed: Provider<Int>,
    private val maxIdleTime: Provider<Int>
) : VerticalBar(height) {
    
    override fun createBarItem(section: Int): Item =
        reactiveItem(timePassed, maxIdleTime) { timePassed, maxIdleTime ->
            createItemBuilder(
                DefaultGuiItems.BAR_GREEN,
                section,
                timePassed.toDouble() / maxIdleTime.toDouble()
            ).setDisplayName(Component.translatable(
                translationKey,
                NamedTextColor.GRAY,
                Component.text(maxIdleTime - timePassed)
            ))
        }
    
}

class ProgressBar(
    height: Int,
    private val translationKey: String,
    private val progress: Provider<Double>
) : VerticalBar(height) {
    
    override fun createBarItem(section: Int): Item =
        reactiveItem(progress) { progress ->
            createItemBuilder(DefaultGuiItems.BAR_GREEN, section, progress)
                .setDisplayName(Component.translatable(translationKey, NamedTextColor.GRAY))
        }
    
}