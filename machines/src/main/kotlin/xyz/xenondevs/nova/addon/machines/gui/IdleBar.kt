@file:OptIn(ExperimentalReactiveApi::class)

package xyz.xenondevs.nova.addon.machines.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.invui.ExperimentalReactiveApi
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.setItemProvider
import xyz.xenondevs.nova.ui.menu.VerticalBar
import xyz.xenondevs.nova.world.item.DefaultGuiItems

class IdleBar(
    height: Int,
    private val translationKey: String,
    private val timePassed: Provider<Int>,
    private val maxIdleTime: Provider<Int>
) : VerticalBar(height) {
    
    override fun createBarItem(section: Int): Item =
        Item.builder()
            .setItemProvider(timePassed, maxIdleTime) { timePassed, maxIdleTime ->
                createItemBuilder(
                    DefaultGuiItems.BAR_GREEN,
                    section,
                    timePassed.toDouble() / maxIdleTime.toDouble()
                ).setName(Component.translatable(
                    translationKey,
                    NamedTextColor.GRAY,
                    Component.text(maxIdleTime - timePassed)
                ))
            }.build()
    
}

class ProgressBar(
    height: Int,
    private val translationKey: String,
    private val progress: Provider<Double>
) : VerticalBar(height) {
    
    override fun createBarItem(section: Int): Item =
        Item.builder().setItemProvider(progress) { progress ->
            createItemBuilder(DefaultGuiItems.BAR_GREEN, section, progress)
                .setName(Component.translatable(translationKey, NamedTextColor.GRAY))
        }.build()
    
}