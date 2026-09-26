@file:OptIn(ExperimentalReactiveApi::class)

package xyz.xenondevs.nova.addon.machines.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.combinedProvider
import xyz.xenondevs.invui.ExperimentalReactiveApi
import xyz.xenondevs.nova.ui.menu.verticalBar
import xyz.xenondevs.nova.world.item.DefaultGuiItems

fun idleBar(
    translationKey: String,
    timePassed: Provider<Int>,
    maxIdleTime: Provider<Int>
) = verticalBar(
    combinedProvider(timePassed, maxIdleTime) { a, b ->
        (a.toDouble() / b.toDouble()).coerceIn(0.0, 1.0)
    },
    DefaultGuiItems.TP_BAR_GREEN,
    {
        name by combinedProvider(timePassed, maxIdleTime) { timePassed, maxIdleTime ->
            Component.translatable(translationKey, NamedTextColor.GRAY, Component.text(maxIdleTime - timePassed))
        }
    }
)

fun progressBar(
    translationKey: String,
    progress: Provider<Double>
) = verticalBar(
    progress,
    DefaultGuiItems.TP_BAR_GREEN,
    { name by Component.translatable(translationKey, NamedTextColor.GRAY) }
)