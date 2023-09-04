package xyz.xenondevs.nova.addon.logistics.tileentity

import net.minecraft.util.Brightness
import net.minecraft.world.item.ItemDisplayContext
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.invui.item.builder.ItemBuilder
import xyz.xenondevs.invui.item.impl.AbstractItem
import xyz.xenondevs.nova.addon.logistics.registry.Blocks.FLUID_STORAGE_UNIT
import xyz.xenondevs.nova.addon.logistics.registry.Items
import xyz.xenondevs.nova.data.config.Reloadable
import xyz.xenondevs.nova.data.config.entry
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.fluid.FluidType
import xyz.xenondevs.nova.tileentity.network.fluid.holder.NovaFluidHolder
import xyz.xenondevs.nova.ui.FluidBar
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu
import xyz.xenondevs.nova.util.nmsCopy
import xyz.xenondevs.nova.world.fakeentity.impl.FakeItemDisplay

private val MAX_CAPACITY = FLUID_STORAGE_UNIT.config.entry<Long>("max_capacity")

class FluidStorageUnit(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState), Reloadable {
    
    private val fluidTank = getFluidContainer("fluid", setOf(FluidType.LAVA, FluidType.WATER), MAX_CAPACITY, 0, ::handleFluidUpdate)
    private val fluidLevel = FakeItemDisplay(location.add(.5, .5, .5)) { _, data -> data.itemDisplay = ItemDisplayContext.HEAD }
    override val fluidHolder = NovaFluidHolder(this, fluidTank to NetworkConnectionType.BUFFER) { createSideConfig(NetworkConnectionType.BUFFER) }
    
    init {
        handleFluidUpdate()
    }
    
    private fun handleFluidUpdate() {
        val stack = if (fluidTank.hasFluid()) {
            when (fluidTank.type) {
                FluidType.LAVA -> Items.TANK_LAVA_LEVELS
                FluidType.WATER -> Items.TANK_WATER_LEVELS
                else -> throw IllegalStateException()
            }.clientsideProviders[10].get()
        } else null
        
        fluidLevel.updateEntityData(true) {
            brightness = if (fluidTank.type == FluidType.LAVA) Brightness.FULL_BRIGHT else null
            itemStack = stack.nmsCopy
        }
    }
    
    override fun handleRemoved(unload: Boolean) {
        super.handleRemoved(unload)
        fluidLevel.remove()
    }
    
    @TileEntityMenuClass
    inner class FluidStorageUnitMenu : GlobalTileEntityMenu() {
        
        private val SideConfigMenu = SideConfigMenu(
            this@FluidStorageUnit,
            fluidContainerNames = listOf(fluidTank to "container.nova.fluid_tank"),
            openPrevious = ::openWindow
        )
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s # # # # # f |",
                "| # # # d # # f |",
                "| # # # # # # f |",
                "3 - - - - - - - 4")
            .addIngredient('s', OpenSideConfigItem(SideConfigMenu))
            .addIngredient('d', FluidStorageUnitDisplay())
            .addIngredient('f', FluidBar(3, fluidHolder, fluidTank))
            .build()
        
        private inner class FluidStorageUnitDisplay : AbstractItem() {
            
            init {
                fluidTank.updateHandlers += { notifyWindows() }
            }
            
            override fun getItemProvider(): ItemProvider {
                val type = fluidTank.type?.bucket
                    ?: return ItemBuilder(Material.BARRIER).setDisplayName("§r")
                val amount = fluidTank.amount
                return ItemBuilder(type).setDisplayName("§a$amount §7mB").setAmount(1)
            }
            
            override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) = Unit
            
        }
    }
    
}