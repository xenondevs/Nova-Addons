package xyz.xenondevs.nova.addon.machines.recipe.group.hardcoded

import net.kyori.adventure.key.Key
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.nova.addon.machines.Machines
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.addon.machines.registry.RecipeTypes
import xyz.xenondevs.nova.addon.machines.tileentity.processing.CobblestoneGenerator
import xyz.xenondevs.nova.addon.machines.tileentity.processing.Freezer
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitFun
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.util.Key
import xyz.xenondevs.nova.world.item.recipe.NovaRecipe
import xyz.xenondevs.nova.world.item.recipe.RecipeRegistry
import xyz.xenondevs.nova.world.item.recipe.SingleResultRecipe

@Init(stage = InitStage.POST_WORLD)
object HardcodedRecipes {
    
    private val recipes: List<NovaRecipe> = listOf(
        StarCollectorRecipe,
        CobblestoneGeneratorRecipe(Key(Machines, "cobblestone_generator.cobblestone"), CobblestoneGenerator.Mode.COBBLESTONE),
        CobblestoneGeneratorRecipe(Key(Machines, "cobblestone_generator.stone"), CobblestoneGenerator.Mode.STONE),
        CobblestoneGeneratorRecipe(Key(Machines, "cobblestone_generator.obsidian"), CobblestoneGenerator.Mode.OBSIDIAN),
        FreezerRecipe(Key(Machines, "freezer.ice"), Freezer.Mode.ICE),
        FreezerRecipe(Key(Machines, "freezer.packed_ice"), Freezer.Mode.PACKED_ICE),
        FreezerRecipe(Key(Machines, "freezer.blue_ice"), Freezer.Mode.BLUE_ICE),
    )
    
    @InitFun
    fun register() {
        RecipeRegistry.addFakeRecipes(recipes)
        RecipeRegistry.addCreationInfo(mapOf(
            "machines:star_shards" to "item_info.machines.star_shards",
            "machines:infinite_water_source" to "item_info.machines.infinite_water_source"
        ))
    }
    
}

object StarCollectorRecipe : NovaRecipe, SingleResultRecipe {
    override val id = Key(Machines, "star_collector.star_dust")
    override val type = RecipeTypes.STAR_COLLECTOR
    override val result = Items.STAR_DUST.createItemStack()
}

class CobblestoneGeneratorRecipe(
    override val id: Key,
    val mode: CobblestoneGenerator.Mode,
    override val result: ItemStack = mode.product
) : NovaRecipe, SingleResultRecipe {
    override val type = RecipeTypes.COBBLESTONE_GENERATOR
}

class FreezerRecipe(
    override val id: Key,
    val mode: Freezer.Mode,
    override val result: ItemStack = mode.product
) : NovaRecipe, SingleResultRecipe {
    override val type = RecipeTypes.FREEZER
}