package xyz.xenondevs.nova.addon.machines.registry

import xyz.xenondevs.nova.addon.machines.Machines
import xyz.xenondevs.nova.addon.machines.recipe.CrystallizerRecipe
import xyz.xenondevs.nova.addon.machines.recipe.CrystallizerRecipeDeserializer
import xyz.xenondevs.nova.addon.machines.recipe.ElectricBrewingStandRecipe
import xyz.xenondevs.nova.addon.machines.recipe.ElectricBrewingStandRecipeDeserializer
import xyz.xenondevs.nova.addon.machines.recipe.FluidInfuserRecipe
import xyz.xenondevs.nova.addon.machines.recipe.FluidInfuserRecipeDeserializer
import xyz.xenondevs.nova.addon.machines.recipe.GearPressRecipe
import xyz.xenondevs.nova.addon.machines.recipe.GearPressRecipeDeserializer
import xyz.xenondevs.nova.addon.machines.recipe.PlatePressRecipe
import xyz.xenondevs.nova.addon.machines.recipe.PlatePressRecipeDeserializer
import xyz.xenondevs.nova.addon.machines.recipe.PulverizerRecipe
import xyz.xenondevs.nova.addon.machines.recipe.PulverizerRecipeDeserializer
import xyz.xenondevs.nova.addon.machines.recipe.group.CrystallizerRecipeGroup
import xyz.xenondevs.nova.addon.machines.recipe.group.ElectricBrewingStandRecipeGroup
import xyz.xenondevs.nova.addon.machines.recipe.group.FluidInfuserRecipeGroup
import xyz.xenondevs.nova.addon.machines.recipe.group.PressingRecipeGroup
import xyz.xenondevs.nova.addon.machines.recipe.group.PulverizingRecipeGroup
import xyz.xenondevs.nova.addon.machines.recipe.group.hardcoded.CobblestoneGeneratorRecipe
import xyz.xenondevs.nova.addon.machines.recipe.group.hardcoded.CobblestoneGeneratorRecipeGroup
import xyz.xenondevs.nova.addon.machines.recipe.group.hardcoded.FreezerRecipe
import xyz.xenondevs.nova.addon.machines.recipe.group.hardcoded.FreezerRecipeGroup
import xyz.xenondevs.nova.addon.machines.recipe.group.hardcoded.StarCollectorRecipe
import xyz.xenondevs.nova.addon.machines.recipe.group.hardcoded.StarCollectorRecipeGroup
import xyz.xenondevs.nova.addon.registry.RecipeTypeRegistry
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage

@Init(stage = InitStage.POST_WORLD)
object RecipeTypes : RecipeTypeRegistry by Machines.registry {
    
    val PULVERIZER = registerRecipeType("pulverizer", PulverizerRecipe::class, PulverizingRecipeGroup, PulverizerRecipeDeserializer)
    val GEAR_PRESS = registerRecipeType("press/gear", GearPressRecipe::class, PressingRecipeGroup, GearPressRecipeDeserializer)
    val PLATE_PRESS = registerRecipeType("press/plate", PlatePressRecipe::class, PressingRecipeGroup, PlatePressRecipeDeserializer)
    val FLUID_INFUSER = registerRecipeType("fluid_infuser", FluidInfuserRecipe::class, FluidInfuserRecipeGroup, FluidInfuserRecipeDeserializer)
    val ELECTRIC_BREWING_STAND = registerRecipeType("electric_brewing_stand", ElectricBrewingStandRecipe::class, ElectricBrewingStandRecipeGroup, ElectricBrewingStandRecipeDeserializer)
    val CRYSTALLIZER = registerRecipeType("crystallizer", CrystallizerRecipe::class, CrystallizerRecipeGroup, CrystallizerRecipeDeserializer)
    val STAR_COLLECTOR = registerRecipeType("star_collector", StarCollectorRecipe::class, StarCollectorRecipeGroup, null)
    val COBBLESTONE_GENERATOR = registerRecipeType("cobblestone_generator", CobblestoneGeneratorRecipe::class, CobblestoneGeneratorRecipeGroup, null)
    val FREEZER = registerRecipeType("freezer", FreezerRecipe::class, FreezerRecipeGroup, null)
    
}