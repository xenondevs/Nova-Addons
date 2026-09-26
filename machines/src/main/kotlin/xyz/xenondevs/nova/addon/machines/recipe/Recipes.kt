package xyz.xenondevs.nova.addon.machines.recipe

import net.kyori.adventure.key.Key
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.RecipeChoice
import org.bukkit.potion.PotionEffectType
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.RecipeTypes
import xyz.xenondevs.nova.util.data.getInputStacks
import xyz.xenondevs.nova.world.item.itemTypeEntry
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.world.item.guiItemProvider
import xyz.xenondevs.nova.world.item.recipe.ConversionNovaRecipe
import xyz.xenondevs.nova.world.item.recipe.MultiInputChoiceRecipe
import xyz.xenondevs.nova.world.item.recipe.NovaRecipe

class PulverizerRecipe(
    id: Key,
    input: RecipeChoice,
    result: ItemStack,
    time: Int,
) : ConversionNovaRecipe(id, input, result, time) {
    override val type = RecipeTypes.PULVERIZER
}

class PlatePressRecipe(
    id: Key,
    input: RecipeChoice,
    result: ItemStack,
    time: Int
) : ConversionNovaRecipe(id, input, result, time) {
    override val type = RecipeTypes.PLATE_PRESS
}

class GearPressRecipe(
    id: Key,
    input: RecipeChoice,
    result: ItemStack,
    time: Int
) : ConversionNovaRecipe(id, input, result, time) {
    override val type = RecipeTypes.GEAR_PRESS
}

class FluidInfuserRecipe(
    override val id: Key,
    val mode: InfuserMode,
    val fluidType: FluidType,
    val fluidAmount: Long,
    input: RecipeChoice,
    result: ItemStack,
    time: Int
) : ConversionNovaRecipe(id, input, result, time) {
    override val type = RecipeTypes.FLUID_INFUSER
    
    enum class InfuserMode(
        val btnItemProvider: Provider<ItemProvider>,
        val progressItemProvider: Provider<ItemProvider>
    ) {
        
        INSERT(
            GuiItems.FLUID_LEFT_RIGHT_BTN.guiItemProvider,
            GuiItems.FLUID_PROGRESS_LEFT_RIGHT.guiItemProvider
        ),
        
        EXTRACT(
            GuiItems.FLUID_RIGHT_LEFT_BTN.guiItemProvider,
            GuiItems.FLUID_PROGRESS_RIGHT_LEFT.guiItemProvider
        )
        
    }
    
}

class ElectricBrewingStandRecipe(
    override val id: Key,
    override val inputs: List<RecipeChoice>,
    val result: PotionEffectType,
    val defaultTime: Int,
    val redstoneMultiplier: Double,
    val glowstoneMultiplier: Double,
    val maxDurationLevel: Int,
    val maxAmplifierLevel: Int
) : NovaRecipe, MultiInputChoiceRecipe {
    
    val inputs2 = inputs.map { it.getInputStacks().single().itemTypeEntry }
    
    override val type = RecipeTypes.ELECTRIC_BREWING_STAND
}

class CrystallizerRecipe(
    id: Key,
    input: RecipeChoice,
    result: ItemStack,
    time: Int
) : ConversionNovaRecipe(id, input, result, time) {
    override val type = RecipeTypes.CRYSTALLIZER
}
