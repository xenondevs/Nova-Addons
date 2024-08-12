package xyz.xenondevs.nova.addon.machines.tileentity.processing.brewing

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionData
import org.bukkit.potion.PotionEffectType
import org.bukkit.potion.PotionType
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.gui.ScrollGui
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.invui.item.ItemWrapper
import xyz.xenondevs.invui.item.builder.ItemBuilder
import xyz.xenondevs.invui.item.builder.PotionBuilder
import xyz.xenondevs.invui.item.builder.addLoreLines
import xyz.xenondevs.invui.item.builder.setDisplayName
import xyz.xenondevs.invui.item.impl.AbstractItem
import xyz.xenondevs.invui.item.impl.CycleItem
import xyz.xenondevs.invui.item.impl.SimpleItem
import xyz.xenondevs.invui.window.Window
import xyz.xenondevs.invui.window.type.context.setTitle
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.tileentity.processing.brewing.ElectricBrewingStand.Companion.ALLOW_DURATION_AMPLIFIER_MIXING
import xyz.xenondevs.nova.addon.machines.tileentity.processing.brewing.ElectricBrewingStand.Companion.AVAILABLE_POTION_EFFECTS
import xyz.xenondevs.nova.ui.menu.ColorPickerWindow
import xyz.xenondevs.nova.ui.menu.ColorPreviewItem
import xyz.xenondevs.nova.ui.menu.OpenColorPickerWindowItem
import xyz.xenondevs.nova.ui.menu.item.BackItem
import xyz.xenondevs.nova.ui.menu.item.ScrollDownItem
import xyz.xenondevs.nova.ui.menu.item.ScrollUpItem
import xyz.xenondevs.nova.ui.overlay.guitexture.DefaultGuiTextures
import xyz.xenondevs.nova.util.playClickSound
import xyz.xenondevs.nova.util.playItemPickupSound
import xyz.xenondevs.nova.world.item.DefaultGuiItems
import java.awt.Color
import kotlin.math.min

class PotionConfiguratorWindow(
    effects: List<PotionEffectBuilder>,
    private var type: PotionBuilder.PotionType,
    color: Color,
    private val updatePotionData: (PotionBuilder.PotionType, List<PotionEffectBuilder>, Color) -> Unit,
    openPrevious: (Player) -> Unit
) {
    
    private val effects: MutableMap<PotionEffectBuilder, PotionTypeGui> = effects.associateWithTo(LinkedHashMap(), ::PotionTypeGui)
    
    private val potionTypeItem = CycleItem.withStateChangeHandler(
        { p, i -> p.playItemPickupSound(); type = PotionBuilder.PotionType.entries[i] },
        type.ordinal,
        PotionBuilder(PotionBuilder.PotionType.NORMAL).setDisplayName(Component.translatable("menu.machines.potion_configurator.potion_type.normal")),
        PotionBuilder(PotionBuilder.PotionType.SPLASH).setDisplayName(Component.translatable("menu.machines.potion_configurator.potion_type.splash")),
        PotionBuilder(PotionBuilder.PotionType.LINGERING).setDisplayName(Component.translatable("menu.machines.potion_configurator.potion_type.lingering"))
    )
    
    private val colorPickerWindow = ColorPickerWindow(
        PotionColorPreviewItem(
            PotionBuilder(PotionBuilder.PotionType.NORMAL)
                .setDisplayName(Component.translatable("menu.machines.color_picker.current_color"))
        ), color, ::openConfigurator)
    
    private val gui = ScrollGui.guis()
        .setStructure(
            "< c t . . . . . s",
            "x x x x x x x x u",
            "x x x x x x x x .",
            "x x x x x x x x .",
            "x x x x x x x x .",
            "x x x x x x x x d")
        .addIngredient('u', ScrollUpItem(DefaultGuiItems.TP_ARROW_UP_ON.model.clientsideProvider, DefaultGuiItems.TP_ARROW_UP_OFF.model.clientsideProvider))
        .addIngredient('d', ScrollDownItem(DefaultGuiItems.TP_ARROW_DOWN_ON.model.clientsideProvider, DefaultGuiItems.TP_ARROW_DOWN_OFF.model.clientsideProvider))
        .addIngredient('<', BackItem(DefaultGuiItems.TP_ARROW_LEFT_ON.model.clientsideProvider, openPrevious))
        .addIngredient('c', OpenColorPickerWindowItem(colorPickerWindow))
        .addIngredient('t', potionTypeItem)
        .build()
    
    init {
        updateEffectGuis()
    }
    
    private fun removeEffect(effect: PotionEffectBuilder) {
        effects -= effect
        updateEffectGuis()
    }
    
    private fun addEffect() {
        val builder = PotionEffectBuilder()
        val gui = PotionTypeGui(builder)
        effects[builder] = gui
        updateEffectGuis()
    }
    
    private fun updateEffectGuis() {
        val guis = effects.values.mapTo(ArrayList()) { it.gui }
        guis += createAddEffectGui()
        gui.setContent(guis)
    }
    
    private fun createAddEffectGui(): Gui {
        return Gui.normal()
            .setStructure("+ . . . . . . .")
            .addIngredient('+', SimpleItem(
                GuiItems.TP_GREEN_PLUS.model.createClientsideItemBuilder(
                    Component.translatable("menu.machines.potion_configurator.add_effect")
                )
            ) { it.player.playClickSound(); addEffect() })
            .build()
    }
    
    fun openConfigurator(player: Player) {
        Window.single { w ->
            w.setViewer(player)
            w.setTitle(GuiTextures.CONFIGURE_POTION.getTitle("menu.machines.electric_brewing_stand.configure_potion"))
            w.setGui(gui)
            w.addCloseHandler { updatePotionData(type, this.effects.keys.filter { it.type != null }, colorPickerWindow.color) }
        }.open()
    }
    
    private inner class PotionTypeGui(private val effect: PotionEffectBuilder) {
        
        private val durationModifierItem = DurationModifierItem()
        private val amplifierModifierItem = AmplifierModifierItem()
        private val potionPickerItem = OpenPotionPickerItem()
        
        val gui = Gui.normal()
            .setStructure("- . p . d . a .")
            .addIngredient('p', potionPickerItem)
            .addIngredient('d', durationModifierItem)
            .addIngredient('a', amplifierModifierItem)
            .addIngredient('-', SimpleItem(
                GuiItems.TP_RED_MINUS.model.createClientsideItemBuilder(
                    Component.translatable("menu.machines.potion_configurator.remove_effect")
                )
            ) { it.player.playClickSound(); removeEffect(effect) })
            .build()
        
        private inner class OpenPotionPickerItem : AbstractItem() {
            
            override fun getItemProvider(): ItemProvider {
                return if (effect.type != null) {
                    PotionBuilder(PotionBuilder.PotionType.NORMAL)
                        .setBasePotionData(PotionData(PotionType.WATER, false, false))
                        .setDisplayName(Component.translatable("menu.machines.potion_configurator.effect"))
                        .addEffect(effect.build())
                } else ItemBuilder(Material.GLASS_BOTTLE)
                    .setDisplayName(Component.translatable("menu.machines.potion_configurator.undefined_effect"))
            }
            
            override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) {
                PickPotionWindow(effect).openPicker(player)
            }
            
        }
        
        private inner class DurationModifierItem : AbstractItem() {
            
            override fun getItemProvider(): ItemProvider {
                if (effect.type == null) return ItemWrapper(ItemStack(Material.AIR))
                
                val durationLevel = effect.durationLevel + 1
                val maxDurationLevel = effect.maxDurationLevel + 1
                
                return DefaultGuiItems.NUMBER.model.createClientsideItemBuilder(modelId = min(999, durationLevel))
                    .setDisplayName(Component.translatable(
                        "menu.machines.potion_configurator.duration",
                        Component.text(durationLevel), Component.text(maxDurationLevel)
                    )).addLoreLines(
                        Component.translatable("menu.machines.potion_configurator.left_inc", NamedTextColor.GRAY),
                        Component.translatable("menu.machines.potion_configurator.right_dec", NamedTextColor.GRAY)
                    )
            }
            
            override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) {
                if (effect.type == null) return
                
                if (clickType.isLeftClick) {
                    if (effect.durationLevel < effect.maxDurationLevel) {
                        effect.durationLevel++
                        player.playItemPickupSound()
                        notifyWindows()
                        potionPickerItem.notifyWindows()
                        
                        if (!ALLOW_DURATION_AMPLIFIER_MIXING) {
                            effect.amplifierLevel = 0
                            amplifierModifierItem.notifyWindows()
                        }
                    }
                } else if (clickType.isRightClick) {
                    if (effect.durationLevel > 0) {
                        effect.durationLevel--
                        player.playItemPickupSound()
                        notifyWindows()
                        potionPickerItem.notifyWindows()
                    }
                }
            }
        }
        
        private inner class AmplifierModifierItem : AbstractItem() {
            
            override fun getItemProvider(): ItemProvider {
                if (effect.type == null) return ItemWrapper(ItemStack(Material.AIR))
                
                val amplifierLevel = effect.amplifierLevel + 1
                val maxAmplifierLevel = effect.maxAmplifierLevel + 1
                
                return DefaultGuiItems.NUMBER.model.createClientsideItemBuilder(modelId = min(999, amplifierLevel))
                    .setDisplayName(Component.translatable(
                        "menu.machines.potion_configurator.amplifier",
                        Component.text(amplifierLevel),
                        Component.text(maxAmplifierLevel)
                    )).addLoreLines(
                        Component.translatable("menu.machines.potion_configurator.left_inc", NamedTextColor.GRAY),
                        Component.translatable("menu.machines.potion_configurator.right_dec", NamedTextColor.GRAY)
                    )
            }
            
            override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) {
                if (effect.type == null)
                    return
                
                if (clickType.isLeftClick) {
                    if (effect.amplifierLevel < effect.maxAmplifierLevel) {
                        effect.amplifierLevel++
                        player.playItemPickupSound()
                        notifyWindows()
                        potionPickerItem.notifyWindows()
                        
                        if (!ALLOW_DURATION_AMPLIFIER_MIXING) {
                            effect.durationLevel = 0
                            durationModifierItem.notifyWindows()
                        }
                    }
                } else if (clickType.isRightClick) {
                    if (effect.amplifierLevel > 0) {
                        effect.amplifierLevel--
                        player.playItemPickupSound()
                        notifyWindows()
                        potionPickerItem.notifyWindows()
                    }
                }
            }
            
        }
        
    }
    
    private inner class PickPotionWindow(private val effect: PotionEffectBuilder) {
        
        private val potionItems = AVAILABLE_POTION_EFFECTS.keys
            .filter { availableEffect -> effects.keys.none { builder -> builder.type == availableEffect } }
            .map(::ChooseEffectTypeItem)
        
        private val gui = ScrollGui.items()
            .setStructure(
                "< - - - - - - - 2",
                "| x x x x x x x u",
                "| x x x x x x x |",
                "| x x x x x x x |",
                "| x x x x x x x d",
                "3 - - - - - - - 4")
            .addIngredient('<', BackItem { openConfigurator(it) })
            .setContent(potionItems)
            .build()
        
        fun openPicker(player: Player) {
            Window.single {
                it.setViewer(player)
                it.setTitle(DefaultGuiTextures.EMPTY_GUI.getTitle("menu.machines.electric_brewing_stand.pick_effect"))
                it.setGui(gui)
            }.open()
        }
        
        private inner class ChooseEffectTypeItem(private val type: PotionEffectType) : AbstractItem() {
            
            @Suppress("DEPRECATION")
            override fun getItemProvider(): ItemProvider {
                val type = PotionType.getByEffect(type)
                    ?: return ItemBuilder(Material.POTION).setDisplayName(type.name)
                
                return PotionBuilder(PotionBuilder.PotionType.NORMAL)
                    .setBasePotionData(PotionData(type, false, false))
            }
            
            override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) {
                effect.type = type
                openConfigurator(player)
                player.playItemPickupSound()
            }
            
        }
        
    }
    
}

class PotionColorPreviewItem(builder: PotionBuilder, color: Color = Color(0, 0, 0)) : ColorPreviewItem(color) {
    
    private val builder: PotionBuilder = builder.clone()
    
    override fun getItemProvider(): PotionBuilder =
        builder.setColor(color)
    
}