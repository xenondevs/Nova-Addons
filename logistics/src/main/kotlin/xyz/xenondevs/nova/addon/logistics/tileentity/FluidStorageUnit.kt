package xyz.xenondevs.nova.addon.logistics.tileentity

import org.bukkit.block.Block
import org.bukkit.entity.Display
import org.bukkit.inventory.ItemType
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.flatten
import xyz.xenondevs.commons.provider.provider
import xyz.xenondevs.invui.dsl.by
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.inventory.event.UpdateReason
import xyz.xenondevs.invui.inventory.get
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.invui.item.ItemWrapper
import xyz.xenondevs.nova.addon.logistics.registry.Blocks.FLUID_STORAGE_UNIT
import xyz.xenondevs.nova.addon.logistics.registry.BlockStateProperties
import xyz.xenondevs.nova.addon.logistics.registry.GuiTextures
import xyz.xenondevs.nova.addon.logistics.registry.Models
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.packetentity.packetItemDisplay
import xyz.xenondevs.nova.registry.entries.ItemTypeEntries
import xyz.xenondevs.nova.registry.registryEntrySetOf
import xyz.xenondevs.nova.ui.menu.itemProvider
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.nova.util.item.setCustomModelDataFloat
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.*
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.world.item.guiItemProvider
import xyz.xenondevs.nova.world.item.itemTypeEntry

private val MAX_CAPACITY = FLUID_STORAGE_UNIT.config.entry<Long>("max_capacity")
private val BUCKETS = registryEntrySetOf(FluidType.entries.map(FluidType::bucketType) + ItemTypeEntries.BUCKET)

class FluidStorageUnit(pos: Block, state: NovaBlockState, compound: Compound) : NetworkedTileEntity(pos, state, compound) {
    
    private val fluidTank = storedFluidContainer("fluid", setOf(FluidType.LAVA, FluidType.WATER), MAX_CAPACITY, true, ::updateLavaState)
    private val inputInventory = storedInventory("input", 1, ::onlyAllowBuckets)
    private val outputInventory = storedInventory("output", 1, ::onlyAllowExtract)
    private val fluidHolder = storedFluidHolder(fluidTank to BUFFER)
    private val itemHolder = storedItemHolder(inputInventory to INSERT, outputInventory to EXTRACT)
    
    private val fluidLevel = packetItemDisplay {
        val fluidModel = fluidTank.typeProvider.flatMap {
            when (it) {
                FluidType.LAVA -> Models.TANK_LAVA_LEVELS.guiItemProvider
                FluidType.WATER -> Models.TANK_WATER_LEVELS.guiItemProvider
                else -> provider(ItemProvider.EMPTY)
            }
        }
        
        location by pos.location.toCenterLocation()
        metadata {
            brightnessOverride by fluidTank.typeProvider.map {
                if (it == FluidType.LAVA) Display.Brightness(15, 15) else null
            }
            
            itemStack by fluidModel.map {
                it.get().apply { setCustomModelDataFloat(0, 10f) }
            }
        }
    }
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.STORAGE_UNIT) {
        upperGui by gui(
            "s . i . d . o . .",
        ) {
            's' by openSideConfigItem(
                mapOf(
                    itemHolder.getNetworkedInventory(inputInventory) to "inventory.nova.input",
                    itemHolder.getNetworkedInventory(outputInventory) to "inventory.nova.output"
                ),
                mapOf(fluidTank to "container.nova.fluid_tank")
            )
            'i' by inputInventory
            'o' by outputInventory
            
            'd' by itemProvider(
                fluidTank.typeProvider.map { fluidType ->
                    fluidType?.bucketType?.guiItemProvider
                        ?: provider(ItemWrapper(ItemType.BARRIER.createItemStack()))
                }.flatten()
            ) {
                name by fluidTank.amountProvider.map { amount -> "<green>$amount <gray>mB" }
            }
        }
    }
    
    private fun onlyAllowBuckets(event: ItemPreUpdateEvent) {
        event.isCancelled = !event.isRemove && event.newItem?.itemTypeEntry !in BUCKETS
    }
    
    private fun onlyAllowExtract(event: ItemPreUpdateEvent) {
        event.isCancelled = !event.isRemove
    }
    
    override fun handleTick() {
        val inputType = inputInventory[0]?.itemTypeEntry
            ?: return
        if (inputType == ItemTypeEntries.BUCKET) {
            // fill from unit
            val fluidType = fluidTank.type ?: return
            val result = fluidType.bucket
            if (fluidTank.amount >= 1000L && outputInventory.canHold(result)) {
                fluidTank.takeFluid(1000L)
                inputInventory.addItemAmount(UpdateReason.SUPPRESSED, 0, -1)
                outputInventory.addItem(UpdateReason.SUPPRESSED, result)
            }
        } else {
            // empty into unit
            val fluidType = FluidType.entries.firstOrNull { it.bucketType == inputType }
                ?: return
            val result = ItemType.BUCKET.createItemStack()
            if (fluidTank.accepts(fluidType, 1000L) && outputInventory.canHold(result)) {
                fluidTank.addFluid(fluidType, 1000L)
                inputInventory.addItemAmount(UpdateReason.SUPPRESSED, 0, -1)
                outputInventory.addItem(UpdateReason.SUPPRESSED, result)
            }
        }
    }
    
    override fun handleEnable() {
        super.handleEnable()
        updateLavaState()
        
        fluidLevel.spawn()
    }
    
    override fun handleDisable() {
        super.handleDisable()
        
        fluidLevel.despawn()
    }
    
    private fun updateLavaState() {
        val hasLava = fluidTank.type == FluidType.LAVA && !fluidTank.isEmpty()
        if (blockState.getOrThrow(BlockStateProperties.LAVA) != hasLava)
            updateBlockState(blockState.apply { this[BlockStateProperties.LAVA] = hasLava })
    }
    
}
