package xyz.xenondevs.nova.addon.logistics.tileentity

import org.bukkit.Material
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.AbstractItem
import xyz.xenondevs.invui.item.Click
import xyz.xenondevs.invui.item.ItemBuilder
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.nova.addon.logistics.registry.Blocks.FLUID_STORAGE_UNIT
import xyz.xenondevs.nova.addon.logistics.registry.Models
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.FluidBar
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.world.fakeentity.impl.FakeItemDisplay

private val MAX_CAPACITY = FLUID_STORAGE_UNIT.config.entry<Long>("max_capacity")

class FluidStorageUnit(pos: BlockPos, state: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, state, data) {
    
    private val fluidTank = storedFluidContainer("fluid", setOf(FluidType.LAVA, FluidType.WATER), MAX_CAPACITY, true, ::handleFluidUpdate)
    private val fluidLevel = FakeItemDisplay(pos.location.add(.5, .5, .5), false)
    private val fluidHolder = storedFluidHolder(fluidTank to NetworkConnectionType.BUFFER)
    
    private fun handleFluidUpdate() {
        fluidLevel.updateEntityData(true) {
            brightness = if (fluidTank.type == FluidType.LAVA)
                Display.Brightness(15, 15)
            else null
            
            itemStack = if (!fluidTank.isEmpty()) {
                when (fluidTank.type) {
                    FluidType.LAVA -> Models.TANK_LAVA_LEVELS
                    FluidType.WATER -> Models.TANK_WATER_LEVELS
                    else -> throw IllegalStateException()
                }.createClientsideItemBuilder().addCustomModelData(10).get()
            } else null
        }
    }
    
    override fun handleEnable() {
        super.handleEnable()
        
        fluidLevel.register()
        handleFluidUpdate()
    }
    
    override fun handleDisable() {
        super.handleDisable()
        
        fluidLevel.remove()
    }
    
    @TileEntityMenuClass
    inner class FluidStorageUnitMenu : GlobalTileEntityMenu() {
        
        private val SideConfigMenu = SideConfigMenu(
            this@FluidStorageUnit,
            mapOf(fluidTank to "container.nova.fluid_tank"),
            ::openWindow
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
                fluidTank.addUpdateHandler { notifyWindows() }
            }
            
            override fun getItemProvider(player: Player): ItemProvider {
                val type = fluidTank.type?.bucket
                    ?: return ItemBuilder(Material.BARRIER).hideTooltip(true)
                val amount = fluidTank.amount
                return ItemBuilder(type).setName("<green>$amount <gray>mB").setAmount(1)
            }
            
            override fun handleClick(clickType: ClickType, player: Player, click: Click) = Unit
            
        }
    }
    
}