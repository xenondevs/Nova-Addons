package xyz.xenondevs.nova.addon.machines.tileentity.processing.brewing

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.PotionContents.potionContents
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Color
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType
import org.bukkit.potion.PotionEffectType
import org.bukkit.potion.PotionType
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.MutableProvider
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.combinedProvider
import xyz.xenondevs.commons.provider.flatten
import xyz.xenondevs.commons.provider.mapEach
import xyz.xenondevs.invui.dsl.WindowDsl
import xyz.xenondevs.invui.dsl.item
import xyz.xenondevs.invui.dsl.itemProvider
import xyz.xenondevs.invui.dsl.scrollInventoriesGui
import xyz.xenondevs.invui.dsl.with
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.inventory.itemsProvider
import xyz.xenondevs.nova.addon.machines.gui.brewProgressItem
import xyz.xenondevs.nova.addon.machines.recipe.ElectricBrewingStandRecipe
import xyz.xenondevs.nova.addon.machines.registry.Blocks.ELECTRIC_BREWING_STAND
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.RecipeTypes
import xyz.xenondevs.nova.addon.machines.util.energyConsumption
import xyz.xenondevs.nova.addon.machines.util.maxIdleTime
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedFluidContainer
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.registry.RegistryEntry
import xyz.xenondevs.nova.registry.entries.ItemTypeEntries
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.ui.menu.fluidBar
import xyz.xenondevs.nova.ui.menu.item.SCROLL_ENABLING_VISUALIZER
import xyz.xenondevs.nova.ui.menu.item.installInventoryScrollSupport
import xyz.xenondevs.nova.ui.menu.item.scrollBar
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.nova.util.CubeFaceSet
import xyz.xenondevs.nova.util.playClickSound
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.world.item.DefaultGuiItems
import xyz.xenondevs.nova.world.item.itemTypeEntry
import xyz.xenondevs.nova.world.item.name
import xyz.xenondevs.nova.world.item.recipe.RecipeManager

private val BLOCKED_FACES = CubeFaceSet(north = true, east = true, south = true, west = true)

private val ENERGY_CAPACITY = ELECTRIC_BREWING_STAND.config.entry<Long>("energy_capacity")
private val ENERGY_PER_TICK = ELECTRIC_BREWING_STAND.config.entry<Long>("energy_per_tick")
private val FLUID_CAPACITY = ELECTRIC_BREWING_STAND.config.entry<Long>("fluid_capacity")
private val BREW_TIME = ELECTRIC_BREWING_STAND.config.entry<Int>("brew_time")

class ElectricBrewingStand(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.FLUID)
    private val fluidTank = storedFluidContainer("tank", setOf(FluidType.WATER), FLUID_CAPACITY, upgradeHolder)
    private val ingredientsInventory = storedInventory("ingredients", 27, null).apply { setVisualizer(SCROLL_ENABLING_VISUALIZER) }
    private val outputInventory = storedInventory("output", 3, ::handleOutputPreUpdate)
    
    private val energyHolder = storedEnergyHolder(ENERGY_CAPACITY, upgradeHolder, INSERT, BLOCKED_FACES)
    private val itemHolder = storedItemHolder(ingredientsInventory to INSERT, outputInventory to EXTRACT, blockedFaces = BLOCKED_FACES)
    private val fluidHolder = storedFluidHolder(fluidTank to INSERT, blockedFaces = BLOCKED_FACES)
    
    private val energyPerTick by energyConsumption(ENERGY_PER_TICK, upgradeHolder)
    private val _maxBrewTime = maxIdleTime(BREW_TIME, upgradeHolder)
    private val maxBrewTime by _maxBrewTime
    
    private val _color = storedValue("potionColor") { Color.fromRGB(0, 0, 0) }
    private val _bottleType = storedValue<PotionBottleType>("potionType") { PotionBottleType.NORMAL }
    private val _potionEffects = storedValue<List<PotionEffectProvider?>>("potionEffects", ::emptyList)
        .map({ effects -> effects.filterNotNull() }, { effects -> effects })
    
    private val _targetPotion = combinedProvider(
        _bottleType,
        _color,
        _potionEffects.mapEach { it.result }.map(::combinedProvider).flatten()
    ) { bottleType, color, potionEffects ->
        bottleType.itemType.createItemStack().apply {
            setData(
                DataComponentTypes.POTION_CONTENTS,
                potionContents()
                    .potion(PotionType.WATER)
                    .customColor(color)
                    .apply {
                        for (effect in potionEffects) {
                            if (effect != null)
                                addCustomEffect(effect.effect)
                        }
                    }.build()
            )
        }
    }
    private val targetPotion by _targetPotion
    
    private val _requiredItems = combinedProvider(
        _bottleType,
        _potionEffects.mapEach { it.result }.flatMap(::combinedProvider),
        ::computeRequiredItems
    )
    private val requiredItems by _requiredItems
    private val _requiredItemsStatus = combinedProvider(
        ingredientsInventory.itemsProvider,
        _requiredItems,
        ::computeStatus
    )
    private val hasRequiredIngredients by _requiredItemsStatus.map { it.all { (_, available) -> available } }
    
    private var nextPotion by storedValue<ItemStack>("nextPotion")
    private val _timePassed = storedValue<Int>("timePassed") { 0 }
    private var timePassed by _timePassed
    
    override val menu: TileEntityMenu = TileEntityMenu.cachedWindow(GuiTextures.ELECTRIC_BREWING_STAND) {
        upperGui by scrollInventoriesGui(
            "x x x | . i . U s",
            "x x x | . p . . .",
            "x x x | . . . f e",
            "^ . ^ . . . . f e",
            "o . o . . . . f e",
            ". o . . . . . f e"
        ) {
            '|' by scrollBar(offset = 2)
            's' by openSideConfigItem(
                mapOf(
                    itemHolder.getNetworkedInventory(ingredientsInventory) to "inventory.machines.ingredients",
                    itemHolder.getNetworkedInventory(outputInventory) to "inventory.nova.output",
                ),
                mapOf(fluidTank to "container.nova.fluid_tank")
            )
            'U' by openUpgradesItem(upgradeHolder)
            'e' by energyBar(energyHolder, DefaultGuiItems.TP_BAR_RED)
            'f' by fluidBar(fluidHolder, fluidTank)
            'i' by ingredientsDisplayItem(_requiredItemsStatus)
            'p' by configuredPotionDisplayItem(_bottleType, _potionEffects, _color, _targetPotion)
            'o' by (outputInventory with GuiItems.BOTTLE_PLACEHOLDER)
            '^' by brewProgressItem(_timePassed, _maxBrewTime)
            content by listOf(ingredientsInventory)
            installInventoryScrollSupport()
        }
    }
    
    private fun handleOutputPreUpdate(event: ItemPreUpdateEvent) {
        if (event.updateReason == SELF_UPDATE_REASON)
            return
        
        event.isCancelled = !event.isRemove
    }
    
    override fun handleTick() {
        val brewingPotion = nextPotion
        if (brewingPotion != null) {
            if (timePassed < maxBrewTime) {
                if (energyHolder.energy < energyPerTick)
                    return
                
                energyHolder.energy -= energyPerTick
                timePassed++
            }
            
            if (timePassed >= maxBrewTime && outputInventory.canHold(List(3) { brewingPotion })) {
                repeat(3) { outputInventory.addItem(SELF_UPDATE_REASON, brewingPotion) }
                nextPotion = null
                timePassed = 0
            }
        } else if (hasRequiredIngredients && fluidTank.amount >= 1000) {
            val potion = targetPotion
            if (!outputInventory.canHold(List(3) { potion }))
                return
            
            fluidTank.takeFluid(1000L)
            for ((item, amount) in requiredItems) {
                ingredientsInventory.removeFirst(SELF_UPDATE_REASON, amount) { it.itemTypeEntry == item }
            }
            nextPotion = potion
        }
    }
    
    // These values need to be accessed from outside the class
    companion object {
        
        val AVAILABLE_POTION_EFFECTS: Map<PotionEffectType, ElectricBrewingStandRecipe> by lazy {
            RecipeManager.novaRecipes[RecipeTypes.ELECTRIC_BREWING_STAND]?.values
                ?.filterIsInstance<ElectricBrewingStandRecipe>()
                ?.associateBy { it.result }
                ?: emptyMap()
        }
        
        val ALLOW_DURATION_AMPLIFIER_MIXING by ELECTRIC_BREWING_STAND.config.entry<Boolean>("duration_amplifier_mixing")
        
    }
    
}

private fun computeRequiredItems(bottleType: PotionBottleType, potionEffects: List<PotionEffectWithExtraInfo?>): Map<RegistryEntry.Paper<ItemType>, Int> = buildMap {
    fun requireItem(type: RegistryEntry.Paper<ItemType>, amount: Int) =
        compute(type) { _, currentAmount -> (currentAmount ?: 0) + amount }
    
    // Potion type items
    requireItem(ItemTypeEntries.GLASS_BOTTLE, 3)
    if (bottleType == PotionBottleType.SPLASH) {
        requireItem(ItemTypeEntries.GUNPOWDER, 1)
    } else if (bottleType == PotionBottleType.LINGERING) {
        requireItem(ItemTypeEntries.GUNPOWDER, 1)
        requireItem(ItemTypeEntries.DRAGON_BREATH, 1)
    }
    
    // Potion modifier items
    var redstone = 0
    var glowstone = 0
    
    // Potion ingredients
    for (effect in potionEffects) {
        if (effect == null)
            continue
        val recipe = ElectricBrewingStand.AVAILABLE_POTION_EFFECTS[effect.type]
            ?: continue
        
        for (input in recipe.inputs2) {
            requireItem(input, 1)
        }
        
        redstone += effect.durationLevel
        glowstone += effect.amplifierLevel
    }
    
    // Potion modifier items
    if (redstone > 0)
        requireItem(ItemTypeEntries.REDSTONE, redstone)
    if (glowstone > 0)
        requireItem(ItemTypeEntries.GLOWSTONE_DUST, glowstone)
}

private fun computeStatus(availableItems: List<ItemStack?>, requiredItems: Map<RegistryEntry.Paper<ItemType>, Int>): Map<Pair<RegistryEntry.Paper<ItemType>, Int>, Boolean> {
    if (requiredItems.isEmpty())
        return emptyMap()
    
    return buildMap {
        for ((requiredType, requiredAmount) in requiredItems) {
            var count = 0
            for (item in availableItems) {
                if (item != null && item.itemTypeEntry == requiredType)
                    count += item.amount
            }
            put(requiredType to requiredAmount, count >= requiredAmount)
        }
    }
}

context(_: WindowDsl, _: TileEntity)
private fun configuredPotionDisplayItem(
    bottleType: MutableProvider<PotionBottleType>,
    potionEffects: MutableProvider<List<PotionEffectProvider>>,
    color: MutableProvider<Color>,
    target: Provider<ItemStack>
) = item {
    itemProvider by itemProvider(target) {
        customName by Component.translatable("menu.machines.electric_brewing_stand.configure_potion")
    }
    val window by lazy { potionConfiguratorWindow(bottleType, color, potionEffects) }
    onClick {
        window.open()
        player.playClickSound()
    }
}

private fun ingredientsDisplayItem(
    status: Provider<Map<Pair<RegistryEntry.Paper<ItemType>, Int>, Boolean>>
) = item {
    itemProvider by itemProvider(ItemType.KNOWLEDGE_BOOK) {
        name by status.map { status ->
            val hasAll = status.all { it.value }
            Component.translatable(
                "menu.machines.electric_brewing_stand.ingredients",
                if (hasAll) NamedTextColor.GREEN else NamedTextColor.RED
            )
        }
        lore by status.flatMap { status ->
            status.entries
                .sortedByDescending { (p, _) -> p.second }
                .map { (itemAndAmount, hasItem) ->
                    val (item, amount) = itemAndAmount
                    item.map { it.name }.map { name ->
                        Component.text()
                            .color(if (hasItem) NamedTextColor.GREEN else NamedTextColor.RED)
                            .append(Component.text(if (hasItem) "✔ " else "❌ "))
                            .append(Component.text("${amount}x "))
                            .append(name)
                            .build()
                    }
                }
                .let(::combinedProvider)
        }
    }
}
