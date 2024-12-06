package xyz.xenondevs.nova.addon.machines.tileentity.processing.brewing

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.PotionContents
import io.papermc.paper.datacomponent.item.PotionContents.potionContents
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffectType
import org.bukkit.potion.PotionType
import xyz.xenondevs.commons.collections.after
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.invui.ExperimentalReactiveApi
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.gui.ScrollGui
import xyz.xenondevs.invui.item.AbstractItem
import xyz.xenondevs.invui.item.Click
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.ItemBuilder
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.invui.item.ItemWrapper
import xyz.xenondevs.invui.item.setItemProvider
import xyz.xenondevs.invui.window.Window
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

val POTION_TYPES = listOf(Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION)

class PotionConfiguratorWindow(
    effects: List<PotionEffectBuilder>,
    type: Material,
    color: Color,
    private val updatePotionData: (Material, List<PotionEffectBuilder>, Color) -> Unit,
    openPrevious: (Player) -> Unit
) {
    
    private val effects: MutableMap<PotionEffectBuilder, PotionTypeGui> = effects.associateWithTo(LinkedHashMap(), ::PotionTypeGui)
    private val potionMaterial = mutableProvider(type)
    
    private val potionTypeItem = Item.builder()
        .setItemProvider(this.potionMaterial) { type ->
            when (type) {
                Material.POTION -> ItemBuilder(type).setName(Component.translatable("menu.machines.potion_configurator.potion_type.normal"))
                Material.SPLASH_POTION -> ItemBuilder(type).setName(Component.translatable("menu.machines.potion_configurator.potion_type.splash"))
                Material.LINGERING_POTION -> ItemBuilder(type).setName(Component.translatable("menu.machines.potion_configurator.potion_type.lingering"))
                else -> throw UnsupportedOperationException()
            }
        }.addClickHandler { _, click ->
            click.player.playItemPickupSound()
            this.potionMaterial.set(POTION_TYPES.after(this.potionMaterial.get()))
        }
        .build()
    
    private val colorPickerWindow = ColorPickerWindow(
        PotionColorPreviewItem(
            ItemBuilder(Material.POTION)
                .setName(Component.translatable("menu.machines.color_picker.current_color"))
        ), color, ::openConfigurator)
    
    private val gui = ScrollGui.guis()
        .setStructure(
            "< c t . . . . . s",
            "x x x x x x x x u",
            "x x x x x x x x .",
            "x x x x x x x x .",
            "x x x x x x x x .",
            "x x x x x x x x d")
        .addIngredient('u', ScrollUpItem(DefaultGuiItems.TP_ARROW_UP_ON.clientsideProvider, DefaultGuiItems.TP_ARROW_UP_OFF.clientsideProvider))
        .addIngredient('d', ScrollDownItem(DefaultGuiItems.TP_ARROW_DOWN_ON.clientsideProvider, DefaultGuiItems.TP_ARROW_DOWN_OFF.clientsideProvider))
        .addIngredient('<', BackItem(DefaultGuiItems.TP_ARROW_LEFT_ON.clientsideProvider, openPrevious))
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
            .addIngredient('+', Item.builder()
                .setItemProvider(
                    GuiItems.TP_GREEN_PLUS
                        .createClientsideItemBuilder()
                        .setName(Component.translatable("menu.machines.potion_configurator.add_effect"))
                ).addClickHandler { _, click ->
                    click.player.playClickSound()
                    addEffect()
                })
            .build()
    }
    
    fun openConfigurator(player: Player) {
        Window.single { w ->
            w.setViewer(player)
            w.setTitle(GuiTextures.CONFIGURE_POTION.getTitle("menu.machines.electric_brewing_stand.configure_potion"))
            w.setGui(gui)
            w.addCloseHandler { updatePotionData(potionMaterial.get(), this.effects.keys.filter { it.type != null }, colorPickerWindow.color) }
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
            .addIngredient('-', Item.builder()
                .setItemProvider(GuiItems.TP_RED_MINUS.createClientsideItemBuilder().setName(Component.translatable("menu.machines.potion_configurator.remove_effect")))
                .addClickHandler { _, click ->
                    click.player.playClickSound()
                    removeEffect(effect)
                }
            )
            .build()
        
        private inner class OpenPotionPickerItem : AbstractItem() {
            
            override fun getItemProvider(player: Player): ItemProvider {
                return if (effect.type != null) {
                    ItemBuilder(Material.POTION)
                        .setName(Component.translatable("menu.machines.potion_configurator.effect"))
                        .set(
                            DataComponentTypes.POTION_CONTENTS,
                            potionContents()
                                .potion(PotionType.WATER)
                                .addCustomEffect(effect.build())
                        )
                } else ItemBuilder(Material.GLASS_BOTTLE)
                    .setName(Component.translatable("menu.machines.potion_configurator.undefined_effect"))
            }
            
            override fun handleClick(clickType: ClickType, player: Player, click: Click) {
                PickPotionWindow(effect).openPicker(player)
            }
            
        }
        
        private inner class DurationModifierItem : AbstractItem() {
            
            override fun getItemProvider(player: Player): ItemProvider {
                if (effect.type == null) return ItemWrapper(ItemStack(Material.AIR))
                
                val durationLevel = effect.durationLevel + 1
                val maxDurationLevel = effect.maxDurationLevel + 1
                
                return DefaultGuiItems.NUMBER.createClientsideItemBuilder().addCustomModelData(durationLevel)
                    .setName(Component.translatable(
                        "menu.machines.potion_configurator.duration",
                        Component.text(durationLevel), Component.text(maxDurationLevel)
                    )).addLoreLines(
                        Component.translatable("menu.machines.potion_configurator.left_inc", NamedTextColor.GRAY),
                        Component.translatable("menu.machines.potion_configurator.right_dec", NamedTextColor.GRAY)
                    )
            }
            
            override fun handleClick(clickType: ClickType, player: Player, click: Click) {
                if (effect.type == null)
                    return
                
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
            
            override fun getItemProvider(player: Player): ItemProvider {
                if (effect.type == null) return ItemWrapper(ItemStack(Material.AIR))
                
                val amplifierLevel = effect.amplifierLevel + 1
                val maxAmplifierLevel = effect.maxAmplifierLevel + 1
                
                return DefaultGuiItems.NUMBER.createClientsideItemBuilder().addCustomModelData(amplifierLevel)
                    .setName(Component.translatable(
                        "menu.machines.potion_configurator.amplifier",
                        Component.text(amplifierLevel),
                        Component.text(maxAmplifierLevel)
                    )).addLoreLines(
                        Component.translatable("menu.machines.potion_configurator.left_inc", NamedTextColor.GRAY),
                        Component.translatable("menu.machines.potion_configurator.right_dec", NamedTextColor.GRAY)
                    )
            }
            
            override fun handleClick(clickType: ClickType, player: Player, click: Click) {
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
            override fun getItemProvider(player: Player): ItemProvider {
                val potionType = PotionType.getByEffect(type)
                    ?: return ItemBuilder(Material.POTION).setName(type.name)
                
                return ItemBuilder(potionMaterial.get()).set(
                    DataComponentTypes.POTION_CONTENTS,
                    potionContents().potion(potionType)
                )
            }
            
            override fun handleClick(clickType: ClickType, player: Player, click: Click) {
                effect.type = type
                openConfigurator(player)
                player.playItemPickupSound()
            }
            
        }
        
    }
    
}

class PotionColorPreviewItem(builder: ItemBuilder, color: Color = Color(0, 0, 0)) : ColorPreviewItem(color) {
    
    private val builder = builder.clone()
    
    override fun getItemProvider(player: Player): ItemBuilder =
        builder.set(
            DataComponentTypes.POTION_CONTENTS,
            potionContents().customColor(org.bukkit.Color.fromRGB(color.rgb))
        )
    
}