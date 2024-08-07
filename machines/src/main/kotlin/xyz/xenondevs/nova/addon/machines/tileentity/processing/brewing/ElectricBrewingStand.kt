package xyz.xenondevs.nova.addon.machines.tileentity.processing.brewing

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.RecipeChoice
import org.bukkit.potion.PotionData
import org.bukkit.potion.PotionEffectType
import org.bukkit.potion.PotionType
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.invui.gui.ScrollGui
import xyz.xenondevs.invui.inventory.event.ItemPostUpdateEvent
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.inventory.event.UpdateReason
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.invui.item.builder.ItemBuilder
import xyz.xenondevs.invui.item.builder.PotionBuilder
import xyz.xenondevs.invui.item.builder.addLoreLines
import xyz.xenondevs.invui.item.builder.setDisplayName
import xyz.xenondevs.invui.item.impl.AbstractItem
import xyz.xenondevs.nova.addon.machines.gui.BrewProgressItem
import xyz.xenondevs.nova.addon.machines.recipe.ElectricBrewingStandRecipe
import xyz.xenondevs.nova.addon.machines.registry.Blocks.ELECTRIC_BREWING_STAND
import xyz.xenondevs.nova.addon.machines.registry.GuiItems
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.RecipeTypes
import xyz.xenondevs.nova.addon.machines.util.efficiencyDividedValue
import xyz.xenondevs.nova.addon.machines.util.maxIdleTime
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedFluidContainer
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.data.recipe.RecipeManager
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.FluidBar
import xyz.xenondevs.nova.ui.menu.addIngredient
import xyz.xenondevs.nova.ui.menu.item.ScrollDownItem
import xyz.xenondevs.nova.ui.menu.item.ScrollUpItem
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.data.MutableLazy
import xyz.xenondevs.nova.util.item.ItemUtils
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import java.awt.Color

private val ENERGY_CAPACITY = ELECTRIC_BREWING_STAND.config.entry<Long>("energy_capacity")
private val ENERGY_PER_TICK = ELECTRIC_BREWING_STAND.config.entry<Long>("energy_per_tick")
private val FLUID_CAPACITY = ELECTRIC_BREWING_STAND.config.entry<Long>("fluid_capacity")
private val BREW_TIME = ELECTRIC_BREWING_STAND.config.entry<Int>("brew_time")

private val IGNORE_UPDATE_REASON = object : UpdateReason {}

class ElectricBrewingStand(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.FLUID)
    private val fluidTank = storedFluidContainer("tank", setOf(FluidType.WATER), FLUID_CAPACITY, upgradeHolder)
    private val ingredientsInventory = storedInventory("ingredients", 27, null, ::handleIngredientsInventoryAfterUpdate)
    private val outputInventory = storedInventory("output", 3, ::handleOutputPreUpdate) { checkBrewingPossibility() }
    
    private val energyHolder = storedEnergyHolder(ENERGY_CAPACITY, upgradeHolder, INSERT)
    private val itemHolder = storedItemHolder(ingredientsInventory to INSERT, outputInventory to EXTRACT)
    private val fluidHolder = storedFluidHolder(fluidTank to INSERT)
    
    private val energyPerTick by efficiencyDividedValue(ENERGY_PER_TICK, upgradeHolder)
    private val maxBrewTime by maxIdleTime(BREW_TIME, upgradeHolder)
    private var timePassed = 0
    
    private var color = retrieveData("potionColor") { Color(0, 0, 0) }
    private var potionType = retrieveData("potionType") { PotionBuilder.PotionType.NORMAL }
    private var potionEffects: List<PotionEffectBuilder> by MutableLazy {
        val potionEffects = ArrayList<PotionEffectBuilder>()
        retrieveDataOrNull<List<Compound>>("potionEffects")?.forEach { potionCompound ->
            val type = Registry.POTION_EFFECT_TYPE.get(potionCompound.get<NamespacedKey>("type")!!)
            val duration: Int = potionCompound["duration"]!!
            val amplifier: Int = potionCompound["amplifier"]!!
            potionEffects += PotionEffectBuilder(type, duration, amplifier)
        }
        potionEffects
    }
    private var requiredItems: List<ItemStack>? = null
    private var requiredItemsStatus: MutableMap<ItemStack, Boolean>? = null
    private var nextPotion: ItemStack? = null
    
    override fun handleEnable() {
        super.handleEnable()
        updatePotionData(potionType, potionEffects, color)
    }
    
    override fun saveData() {
        super.saveData()
        
        val list = potionEffects.map { effect ->
            Compound().also {
                it["type"] = effect.type!!.key
                it["duration"] = effect.durationLevel
                it["amplifier"] = effect.amplifierLevel
            }
        }
        
        storeData("potionEffects", list)
        storeData("potionType", potionType)
        storeData("potionColor", color)
    }
    
    
    private fun updatePotionData(type: PotionBuilder.PotionType, effects: List<PotionEffectBuilder>, color: Color) {
        this.potionEffects = effects.map(PotionEffectBuilder::clone)
        this.potionType = type
        this.color = color
        
        if (effects.isNotEmpty()) {
            val requiredItems = ArrayList<ItemStack>()
            
            // Potion type items
            requiredItems.add(ItemStack(Material.GLASS_BOTTLE, 3))
            if (type == PotionBuilder.PotionType.SPLASH) {
                requiredItems.add(ItemStack(Material.GUNPOWDER))
            } else if (type == PotionBuilder.PotionType.LINGERING) {
                requiredItems.add(ItemStack(Material.GUNPOWDER))
                requiredItems.add(ItemStack(Material.DRAGON_BREATH))
            }
            
            // Potion modifier items
            var redstone = 0
            var glowstone = 0
            
            // Potion ingredients
            potionEffects.forEach { effect ->
                effect.recipe.inputs.forEach { choice ->
                    require(choice is RecipeChoice.ExactChoice && choice.choices.size == 1)
                    val itemStack = choice.itemStack
                    val firstSimilar = requiredItems.firstOrNull { it.isSimilar(itemStack) }
                    
                    if (firstSimilar != null) firstSimilar.amount++
                    else requiredItems += itemStack
                }
                redstone += effect.durationLevel
                glowstone += effect.amplifierLevel
            }
            
            // Potion modifier items
            if (redstone > 0) requiredItems.add(ItemStack(Material.REDSTONE, redstone))
            if (glowstone > 0) requiredItems.add(ItemStack(Material.GLOWSTONE_DUST, glowstone))
            
            // Set required items
            this.requiredItems = requiredItems
        } else {
            requiredItems = null
            requiredItemsStatus = null
        }
        
        // Update
        updateAllRequiredStatus()
        checkBrewingPossibility()
        
        menuContainer.forEachMenu<ElectricBrewingStandMenu> {
            it.configurePotionItem.notifyWindows()
            it.ingredientsDisplay.notifyWindows()
        }
    }
    
    private fun handleOutputPreUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = !event.isRemove && event.updateReason != SELF_UPDATE_REASON
    }
    
    private fun handleIngredientsInventoryAfterUpdate(event: ItemPostUpdateEvent) {
        if (event.updateReason != IGNORE_UPDATE_REASON) {
            if (event.isAdd) updateFalseRequiredStatus()
            else updateAllRequiredStatus()
            
            checkBrewingPossibility()
        }
    }
    
    private fun updateFalseRequiredStatus() {
        if (requiredItems == null) return
        requiredItemsStatus!!
            .filter { !it.value }
            .forEach { (item, _) -> requiredItemsStatus!![item] = ingredientsInventory.containsSimilar(item) }
        
        menuContainer.forEachMenu<ElectricBrewingStandMenu> { it.ingredientsDisplay.notifyWindows() }
    }
    
    private fun updateAllRequiredStatus() {
        if (requiredItems == null) return
        requiredItemsStatus = requiredItems!!.associateWithTo(HashMap()) { ingredientsInventory.containsSimilar(it) }
        
        menuContainer.forEachMenu<ElectricBrewingStandMenu> { it.ingredientsDisplay.notifyWindows() }
    }
    
    private fun checkBrewingPossibility() {
        if (requiredItems != null && requiredItemsStatus != null && outputInventory.isEmpty && requiredItemsStatus!!.values.all { it }) {
            val builder = PotionBuilder(potionType)
            potionEffects.forEach { builder.addEffect(it.build()) }
            nextPotion = builder
                .setDisplayName(Component.translatable("item.minecraft.potion"))
                .setAmount(3)
                .setColor(color)
                .get()
        } else {
            nextPotion = null
            timePassed = 0
            
            menuContainer.forEachMenu<ElectricBrewingStandMenu> { it.progressItem.percentage = 0.0 }
        }
    }
    
    override fun handleTick() {
        if (nextPotion != null && energyHolder.energy >= energyPerTick && fluidTank.amount >= 1000) {
            energyHolder.energy -= energyPerTick
            
            if (++timePassed >= maxBrewTime) {
                outputInventory.addItem(SELF_UPDATE_REASON, nextPotion!!)
                
                nextPotion = null
                timePassed = 0
                fluidTank.takeFluid(1000)
                if (!requiredItems!!.all { ingredientsInventory.removeFirstSimilar(IGNORE_UPDATE_REASON, 1, it) == 0 })
                    throw IllegalStateException("Could not remove all ingredients from the ingredients inventory")
                updateAllRequiredStatus()
            }
            
            menuContainer.forEachMenu<ElectricBrewingStandMenu> {
                it.progressItem.percentage = timePassed.toDouble() / maxBrewTime.toDouble()
            }
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
    
    @TileEntityMenuClass
    inner class ElectricBrewingStandMenu : GlobalTileEntityMenu(GuiTextures.ELECTRIC_BREWING_STAND) {
        
        private val sideConfigGui = SideConfigMenu(
            this@ElectricBrewingStand,
            mapOf(
                itemHolder.getNetworkedInventory(ingredientsInventory) to "inventory.machines.ingredients",
                itemHolder.getNetworkedInventory(outputInventory) to "inventory.nova.output",
            ),
            mapOf(fluidTank to "container.nova.fluid_tank"),
            ::openWindow
        )
        
        val configurePotionItem = ConfigurePotionItem()
        val progressItem = BrewProgressItem()
        val ingredientsDisplay = IngredientsDisplay()
        
        override val gui = ScrollGui.inventories()
            .setStructure(
                ". x x x u i . U s",
                ". x x x . p . . .",
                ". x x x d . . f e",
                ". ^ . ^ . . . f e",
                ". o . o . . . f e",
                ". . o . . . . f e")
            .addContent(ingredientsInventory)
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('U', OpenUpgradesItem(upgradeHolder))
            .addIngredient('e', EnergyBar(4, energyHolder))
            .addIngredient('f', FluidBar(4, fluidHolder, fluidTank))
            .addIngredient('i', ingredientsDisplay)
            .addIngredient('p', configurePotionItem)
            .addIngredient('o', outputInventory, GuiItems.BOTTLE_PLACEHOLDER)
            .addIngredient('^', progressItem)
            .build()
        
        private val configuratorWindow = PotionConfiguratorWindow(
            potionEffects.map(PotionEffectBuilder::clone),
            potionType,
            color,
            ::updatePotionData,
            ::openWindow
        )
        
        inner class ConfigurePotionItem : AbstractItem() {
            
            override fun getItemProvider(): ItemProvider {
                val builder = PotionBuilder(potionType)
                    .setColor(color)
                    .setBasePotionData(PotionData(PotionType.WATER, false, false))
                    .setDisplayName(Component.translatable("menu.machines.electric_brewing_stand.configured_potion"))
                potionEffects.forEach { builder.addEffect(it.build()) }
                return builder
            }
            
            override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) {
                configuratorWindow.openConfigurator(player)
            }
            
        }
        
        inner class IngredientsDisplay : AbstractItem() {
            
            override fun getItemProvider(): ItemProvider {
                val hasAll = requiredItemsStatus?.all { it.value } ?: false
                val builder = ItemBuilder(Material.KNOWLEDGE_BOOK)
                    .setDisplayName(Component.translatable("menu.machines.electric_brewing_stand.ingredients", if (hasAll) NamedTextColor.GREEN else NamedTextColor.RED))
                requiredItems
                    ?.asSequence()
                    ?.sortedByDescending { it.amount }
                    ?.forEach {
                        val hasItem = requiredItemsStatus?.get(it) ?: false
                        val component = Component.text()
                            .color(if (hasItem) NamedTextColor.GREEN else NamedTextColor.RED)
                            .append(Component.text("${it.amount}x "))
                            .append(ItemUtils.getName(it))
                            .append(Component.text(": " + if (hasItem) "✓" else "❌"))
                            .build()
                        builder.addLoreLines(component)
                    }
                
                return builder
            }
            
            override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) = Unit
            
        }
        
    }
    
}
