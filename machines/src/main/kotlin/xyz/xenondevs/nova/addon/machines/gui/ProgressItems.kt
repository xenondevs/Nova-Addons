package xyz.xenondevs.nova.addon.machines.gui

import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.ui.menu.item.ProgressItem

class EnergyProgressItem : ProgressItem(GuiItems.ENERGY_PROGRESS)

class ProgressArrowItem : ProgressItem(GuiItems.ARROW_PROGRESS)

class PressProgressItem : ProgressItem(GuiItems.PRESS_PROGRESS)

class PulverizerProgressItem : ProgressItem(GuiItems.PULVERIZER_PROGRESS)

class LeftRightFluidProgressItem : ProgressItem(GuiItems.FLUID_PROGRESS_LEFT_RIGHT)

class RightLeftFluidProgressItem : ProgressItem(GuiItems.FLUID_PROGRESS_RIGHT_LEFT)

class BrewProgressItem : ProgressItem(GuiItems.TP_BREW_PROGRESS)