package xyz.xenondevs.nova.addon.machines.gui

import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.ui.menu.item.ProgressItem

class EnergyProgressItem : ProgressItem(GuiItems.ENERGY_PROGRESS, 16)

class ProgressArrowItem : ProgressItem(GuiItems.ARROW_PROGRESS, 16)

class PressProgressItem : ProgressItem(GuiItems.PRESS_PROGRESS, 8)

class PulverizerProgressItem : ProgressItem(GuiItems.PULVERIZER_PROGRESS, 14)

class LeftRightFluidProgressItem : ProgressItem(GuiItems.FLUID_PROGRESS_LEFT_RIGHT, 16)

class RightLeftFluidProgressItem : ProgressItem(GuiItems.FLUID_PROGRESS_RIGHT_LEFT, 16)

class BrewProgressItem : ProgressItem(GuiItems.TP_BREW_PROGRESS, 16)