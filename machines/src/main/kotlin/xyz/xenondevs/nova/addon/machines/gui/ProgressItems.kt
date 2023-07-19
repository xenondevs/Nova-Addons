package xyz.xenondevs.nova.addon.machines.gui

import xyz.xenondevs.nova.addon.machines.registry.GuiMaterials
import xyz.xenondevs.nova.ui.item.ProgressItem

class EnergyProgressItem : ProgressItem(GuiMaterials.ENERGY_PROGRESS, 16)

class ProgressArrowItem : ProgressItem(GuiMaterials.ARROW_PROGRESS, 16)

class PressProgressItem : ProgressItem(GuiMaterials.PRESS_PROGRESS, 8)

class PulverizerProgressItem : ProgressItem(GuiMaterials.PULVERIZER_PROGRESS, 14)

class LeftRightFluidProgressItem : ProgressItem(GuiMaterials.FLUID_PROGRESS_LEFT_RIGHT, 16)

class RightLeftFluidProgressItem : ProgressItem(GuiMaterials.FLUID_PROGRESS_RIGHT_LEFT, 16)

class BrewProgressItem : ProgressItem(GuiMaterials.TP_BREW_PROGRESS, 16)