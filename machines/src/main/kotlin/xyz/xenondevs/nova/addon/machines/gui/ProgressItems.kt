package xyz.xenondevs.nova.addon.machines.gui

import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.combinedProvider
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.ui.menu.item.progressItem
import xyz.xenondevs.nova.world.item.guiItemProvider

fun <C : Number, T : Number> progressItem(
    itemProvider: Provider<ItemProvider>,
    current: Provider<C>,
    total: Provider<T>,
    customModelDataIndex: Int = 0
) = progressItem(
    itemProvider,
    combinedProvider(current, total) { current, total ->
        val totalDouble = total.toDouble()
        if (totalDouble != 0.0) current.toDouble() / totalDouble else 0.0
    },
    customModelDataIndex
)

fun <C : Number, T : Number> energyProgressItem(current: Provider<C>, total: Provider<T>) =
    progressItem(GuiItems.ENERGY_PROGRESS.guiItemProvider, current, total)

fun energyProgressItem(progress: Provider<Double>) =
    progressItem(GuiItems.ENERGY_PROGRESS.guiItemProvider, progress)

fun progressArrowItem(progress: Provider<Double>) =
    progressItem(GuiItems.ARROW_PROGRESS.guiItemProvider, progress)

fun pressProgressItem(progress: Provider<Double>) =
    progressItem(GuiItems.PRESS_PROGRESS.guiItemProvider, progress)

fun pulverizerProgressItem(progress: Provider<Double>) =
    progressItem(GuiItems.PULVERIZER_PROGRESS.guiItemProvider, progress)

fun leftRightFluidProgressItem(progress: Provider<Double>) =
    progressItem(GuiItems.FLUID_PROGRESS_LEFT_RIGHT.guiItemProvider, progress)

fun rightLeftFluidProgressItem(progress: Provider<Double>) =
    progressItem(GuiItems.FLUID_PROGRESS_RIGHT_LEFT.guiItemProvider, progress)

fun brewProgressItem(current: Provider<Int>, total: Provider<Int>) =
    progressItem(GuiItems.BREW_PROGRESS.guiItemProvider, current, total)
