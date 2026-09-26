package xyz.xenondevs.nova.addon.machines.tileentity.mob

import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.Vec3
import org.bukkit.entity.Animals
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.gui.idleBar
import xyz.xenondevs.nova.addon.machines.registry.Blocks.BREEDER
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.ItemTags
import xyz.xenondevs.nova.addon.machines.util.energyConsumption
import xyz.xenondevs.nova.addon.machines.util.maxIdleTime
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedRegion
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.nova.util.BlockSideSet
import xyz.xenondevs.nova.util.EntityUtils
import xyz.xenondevs.nova.util.nmsEntity
import xyz.xenondevs.nova.util.unwrap
import org.bukkit.block.Block
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.item.itemType
import xyz.xenondevs.nova.world.region.Region
import xyz.xenondevs.nova.world.region.VisualRegion
import kotlin.math.min

private val BLOCKED_SIDES = BlockSideSet(front = true)

private val MAX_ENERGY = BREEDER.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = BREEDER.config.entry<Long>("energy_per_tick")
private val ENERGY_PER_BREED = BREEDER.config.entry<Long>("energy_per_breed")
private val IDLE_TIME = BREEDER.config.entry<Int>("idle_time")
private val BREED_LIMIT by BREEDER.config.entry<Int>("breed_limit")
private val MIN_RANGE = BREEDER.config.entry<Int>("range", "min")
private val MAX_RANGE = BREEDER.config.entry<Int>("range", "max")
private val DEFAULT_RANGE by BREEDER.config.entry<Int>("range", "default")
private val FEED_BABIES by BREEDER.config.entry<Boolean>("feed_babies")

class Breeder(pos: Block, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {
    
    private val inventory = storedInventory("inventory", 9, ::handleInventoryUpdate)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.RANGE)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(inventory to INSERT, blockedSides = BLOCKED_SIDES)
    private val fakePlayer = EntityUtils.createFakePlayer(block.location)
    
    private val region = storedRegion("region.default", MIN_RANGE, MAX_RANGE, DEFAULT_RANGE, upgradeHolder) {
        val size = 1 + it * 2
        Region.inFrontOf(this, size, size, 4, -1)
    }
    
    private val energyPerTick by energyConsumption(ENERGY_PER_TICK, upgradeHolder)
    private val energyPerBreed by energyConsumption(ENERGY_PER_BREED, upgradeHolder)
    private val maxIdleTimeProvider = maxIdleTime(IDLE_TIME, upgradeHolder)
    private val mxIdleTime by maxIdleTimeProvider
    
    private val idleTimeProvider = mutableProvider(0)
    private var idleTime by idleTimeProvider
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.BREEDER) {
        upperGui by gui(
            "s p . i i i . b e",
            "r n . i i i . b e",
            "u m . i i i . b e",
        ) {
            'i' by inventory
            's' by openSideConfigItem(mapOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default"))
            'u' by openUpgradesItem(upgradeHolder)
            'r' by region.visualizeRegionItem
            'p' by region.increaseSizeItem
            'm' by region.decreaseSizeItem
            'n' by region.displaySizeItem
            'e' by energyBar(energyHolder)
            'b' by idleBar("menu.machines.breeder.idle", idleTimeProvider, maxIdleTimeProvider)
        }
    }
    
    override fun handleDisable() {
        super.handleDisable()
        VisualRegion.removeRegion(uuid)
    }
    
    override fun handleTick() {
        if (energyHolder.energy >= energyPerTick) {
            energyHolder.energy -= energyPerTick
            
            if (idleTime++ >= mxIdleTime) {
                idleTime = 0
                
                val breedableEntities = block.location.world
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
            val result = animal.nmsEntity.interact(fakePlayer, InteractionHand.MAIN_HAND, Vec3.ZERO)
            if (result.consumesAction()) {
                inventory.addItemAmount(SELF_UPDATE_REASON, index, -1)
                return true
            }
        }
        
        return false
    }
    
    private fun handleInventoryUpdate(event: ItemPreUpdateEvent) {
        if (event.updateReason != SELF_UPDATE_REASON
            && !event.isRemove
            && event.newItem!!.itemType !in ItemTags.ANIMAL_FOOD
        ) {
            event.isCancelled = true
        }
    }
    
}
