package xyz.xenondevs.nova.addon.machines.tileentity.mob

import net.minecraft.world.InteractionHand
import org.bukkit.Tag
import org.bukkit.entity.Animals
import org.bukkit.entity.Player
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.enumSetOf
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.gui.IdleBar
import xyz.xenondevs.nova.addon.machines.registry.Blocks.BREEDER
import xyz.xenondevs.nova.addon.machines.util.maxIdleTime
import xyz.xenondevs.nova.addon.machines.util.speedMultipliedValue
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedRegion
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.EntityUtils
import xyz.xenondevs.nova.util.nmsEntity
import xyz.xenondevs.nova.util.unwrap
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.region.Region
import xyz.xenondevs.nova.world.region.VisualRegion
import kotlin.math.min

private val BLOCKED_SIDES = enumSetOf(BlockSide.FRONT)

private val MAX_ENERGY = BREEDER.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = BREEDER.config.entry<Long>("energy_per_tick")
private val ENERGY_PER_BREED = BREEDER.config.entry<Long>("energy_per_breed")
private val IDLE_TIME = BREEDER.config.entry<Int>("idle_time")
private val BREED_LIMIT by BREEDER.config.entry<Int>("breed_limit")
private val MIN_RANGE = BREEDER.config.entry<Int>("range", "min")
private val MAX_RANGE = BREEDER.config.entry<Int>("range", "max")
private val DEFAULT_RANGE by BREEDER.config.entry<Int>("range", "default")
private val FEED_BABIES by BREEDER.config.entry<Boolean>("feed_babies")

private val FOOD_MATERIALS = setOf(
    Tag.ITEMS_PIGLIN_FOOD, Tag.ITEMS_FOX_FOOD, Tag.ITEMS_COW_FOOD, Tag.ITEMS_GOAT_FOOD, Tag.ITEMS_SHEEP_FOOD,
    Tag.ITEMS_WOLF_FOOD, Tag.ITEMS_CAT_FOOD, Tag.ITEMS_HORSE_FOOD, Tag.ITEMS_CAMEL_FOOD, Tag.ITEMS_ARMADILLO_FOOD,
    Tag.ITEMS_BEE_FOOD, Tag.ITEMS_CHICKEN_FOOD, Tag.ITEMS_FROG_FOOD, Tag.ITEMS_HOGLIN_FOOD, Tag.ITEMS_LLAMA_FOOD, Tag.ITEMS_OCELOT_FOOD,
    Tag.ITEMS_PANDA_FOOD, Tag.ITEMS_PIG_FOOD, Tag.ITEMS_RABBIT_FOOD, Tag.ITEMS_STRIDER_FOOD, Tag.ITEMS_TURTLE_FOOD, Tag.ITEMS_PARROT_FOOD,
    Tag.ITEMS_PARROT_POISONOUS_FOOD, Tag.ITEMS_AXOLOTL_FOOD
).flatMapTo(HashSet()) { it.values }

class Breeder(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val inventory = storedInventory("inventory", 9, ::handleInventoryUpdate)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.RANGE)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(inventory to INSERT, blockedSides = BLOCKED_SIDES)
    private val fakePlayer = EntityUtils.createFakePlayer(pos.location)
    
    private val region = storedRegion("region.default", MIN_RANGE, MAX_RANGE, DEFAULT_RANGE, upgradeHolder) {
        val size = 1 + it * 2
        Region.inFrontOf(this, size, size, 4, -1)
    }
    
    private val energyPerTick by speedMultipliedValue(ENERGY_PER_TICK, upgradeHolder)
    private val energyPerBreed by speedMultipliedValue(ENERGY_PER_BREED, upgradeHolder)
    private val maxIdleTimeProvider = maxIdleTime(IDLE_TIME, upgradeHolder)
    private val mxIdleTime by maxIdleTimeProvider
    
    private val idleTimeProvider = mutableProvider(0)
    private var idleTime by idleTimeProvider
    
    override fun handleDisable() {
        super.handleDisable()
        VisualRegion.removeRegion(uuid)
    }
    
    override fun handleTick() {
        if (energyHolder.energy >= energyPerTick) {
            energyHolder.energy -= energyPerTick
            
            if (idleTime++ >= mxIdleTime) {
                idleTime = 0
                
                val breedableEntities = pos.location.world
                    .getNearbyEntities(region.toBoundingBox())
                    .filterIsInstance<Animals>()
                
                // TODO: protection check?
                
                var breedsLeft = min((energyHolder.energy / energyPerBreed).toInt(), BREED_LIMIT)
                for (animal in breedableEntities) {
                    val success = interact(animal)
                    
                    if (success) {
                        breedsLeft--
                        energyHolder.energy -= energyPerBreed
                        if (breedsLeft == 0) break
                    }
                }
            }
        }
    }
    
    private fun interact(animal: Animals): Boolean {
        for ((index, item) in inventory.items.withIndex()) {
            if (item == null) continue
            
            if (!FEED_BABIES && !animal.isAdult)
                continue
            
            fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, item.unwrap())
            val result = animal.nmsEntity.interact(fakePlayer, InteractionHand.MAIN_HAND)
            if (result.consumesAction()) {
                inventory.addItemAmount(SELF_UPDATE_REASON, index, -1)
                return true
            }
        }
        
        return false
    }
    
    private fun handleInventoryUpdate(event: ItemPreUpdateEvent) {
        if (event.updateReason != SELF_UPDATE_REASON && !event.isRemove && event.newItem!!.type !in FOOD_MATERIALS)
            event.isCancelled = true
    }
    
    @TileEntityMenuClass
    inner class BreederMenu(player: Player) : IndividualTileEntityMenu(player) {
        
        private val sideConfigGui = SideConfigMenu(
            this@Breeder,
            mapOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default"),
            ::openWindow
        )
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s p i i i b e |",
                "| r n i i i b e |",
                "| u m i i i b e |",
                "3 - - - - - - - 4")
            .addIngredient('i', inventory)
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('r', region.createVisualizeRegionItem(player))
            .addIngredient('p', region.increaseSizeItem)
            .addIngredient('m', region.decreaseSizeItem)
            .addIngredient('n', region.displaySizeItem)
            .addIngredient('e', EnergyBar(3, energyHolder))
            .addIngredient('b', IdleBar(3, "menu.machines.breeder.idle", idleTimeProvider, maxIdleTimeProvider))
            .build()
        
    }
    
}