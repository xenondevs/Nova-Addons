package xyz.xenondevs.nova.addon.logistics.tileentity

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.cbf.entry
import xyz.xenondevs.commons.collections.enumMap
import xyz.xenondevs.commons.collections.next
import xyz.xenondevs.commons.collections.nextOrNull
import xyz.xenondevs.commons.collections.previousOrNull
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.commons.provider.orElseLazily
import xyz.xenondevs.commons.provider.provider
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.ItemBuilder
import xyz.xenondevs.invui.item.setItemProvider
import xyz.xenondevs.nova.addon.logistics.registry.Blocks
import xyz.xenondevs.nova.addon.logistics.registry.GuiItems
import xyz.xenondevs.nova.addon.logistics.registry.GuiTextures
import xyz.xenondevs.nova.addon.logistics.util.LongRingBuffer
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.Canvas
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.CUBE_FACES
import xyz.xenondevs.nova.util.playClickSound
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType
import xyz.xenondevs.nova.world.block.tileentity.network.type.energy.holder.EnergyHolder
import xyz.xenondevs.nova.world.item.DefaultGuiItems
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import kotlin.math.max

private class InfiniteEnergyHolder(compound: Provider<Compound>) : EnergyHolder {
    
    override val blockedFaces: Set<BlockFace>
        get() = emptySet()
    override val allowedConnectionType = NetworkConnectionType.EXTRACT
    override val connectionConfig: MutableMap<BlockFace, NetworkConnectionType>
        by compound.entry<MutableMap<BlockFace, NetworkConnectionType>>("connectionConfig")
            .orElseLazily { CUBE_FACES.associateWithTo(enumMap()) { NetworkConnectionType.EXTRACT } }
    
    override var energy = Long.MAX_VALUE
    override val maxEnergy = Long.MAX_VALUE
    
}

abstract class AbstractPowerCell(pos: BlockPos, state: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, state, data) {
    protected abstract val energyHolder: EnergyHolder
}

private const val PLOT_SIZE = 18 * 3

abstract class PowerCell(capacity: Provider<Long>, pos: BlockPos, state: NovaBlockState, data: Compound) : AbstractPowerCell(pos, state, data) {
    
    final override val energyHolder = storedEnergyHolder(capacity, NetworkConnectionType.BUFFER)
    
    private val entriesUntilRollover = enumMap<BarDuration, Int> { it.numOfPrevious }
    private val averagedEnergyValues = enumMap<BarDuration, LongRingBuffer> { LongRingBuffer(PLOT_SIZE) }
    private val averagedEnergyPlusValues = enumMap<BarDuration, LongRingBuffer> { LongRingBuffer(PLOT_SIZE) }
    private val averagedEnergyMinusValues = enumMap<BarDuration, LongRingBuffer> { LongRingBuffer(PLOT_SIZE) }
    
    override fun handleTick() {
        decrementRollover(BarDuration.TICK)
        
        menuContainer.forEachMenu<PowerCellMenu> { it.drawPlot() }
    }
    
    private fun decrementRollover(duration: BarDuration) {
        val n = entriesUntilRollover[duration]!! - 1
        if (n <= 0) {
            val prevDuration = duration.previousOrNull()
            val nextDuration = duration.nextOrNull()
            
            entriesUntilRollover[duration] = duration.numOfPrevious
            
            val averagedEnergyValues = averagedEnergyValues[duration]!!
            val averagedEnergyPlusValues = averagedEnergyPlusValues[duration]!!
            val averagedEnergyMinusValues = averagedEnergyMinusValues[duration]!!
            
            averagedEnergyValues += if (prevDuration == null)
                energyHolder.energy
            else this.averagedEnergyValues[prevDuration]!!.average()
            
            averagedEnergyPlusValues += if (prevDuration == null)
                energyHolder.energyPlus
            else this.averagedEnergyPlusValues[prevDuration]!!.average()
            
            averagedEnergyMinusValues += if (prevDuration == null)
                energyHolder.energyMinus
            else this.averagedEnergyMinusValues[prevDuration]!!.average()
            
            nextDuration?.let(::decrementRollover)
        } else {
            entriesUntilRollover[duration] = n
        }
    }
    
    @TileEntityMenuClass
    inner class PowerCellMenu(player: Player) : IndividualTileEntityMenu(player, GuiTextures.POWER_CELL) {
        
        private val sideConfigMenu = SideConfigMenu(this@PowerCell, ::openWindow)
        
        private val _duration = mutableProvider(BarDuration.SECOND)
        private var duration by _duration
        private var plotMode = PlotMode.ENERGY_DELTA
        private val graph = BufferedImage(PLOT_SIZE, PLOT_SIZE, BufferedImage.TYPE_INT_ARGB)
        private val graphics = graph.createGraphics()
        private val canvas = object : Canvas(graph) {
            
            override fun modifyItemBuilder(x: Int, y: Int, viewer: Player, itemBuilder: ItemBuilder) {
                itemBuilder.setName(Component.translatable("menu.logistics.power_cell.graph_mode.${plotMode.name.lowercase()}", NamedTextColor.GRAY))
                itemBuilder.addLoreLines(Component.translatable("menu.logistics.power_cell.graph_bar_duration", NamedTextColor.GRAY, Component.text(duration.desc)))
            }
            
        }
        
        override val gui: Gui = Gui.builder()
            .setStructure(
                ". . . . . . . . .",
                "s . m . c c c . e",
                ". . + . c c c . e",
                ". . - . c c c . e",
                ". . . . . . . . .")
            .addIngredient('#', Item.simple(ItemStack(Material.AIR)))
            .addIngredient('s', OpenSideConfigItem(sideConfigMenu))
            .addIngredient('e', EnergyBar(3, energyHolder, DefaultGuiItems.TP_BAR_RED))
            .addIngredient('c', canvas)
            .addIngredient('m', Item.builder()
                .setItemProvider { (if (plotMode == PlotMode.ENERGY) GuiItems.PLOT_MODE_ENERGY else GuiItems.PLOT_MODE_ENERGY_DELTA).clientsideProvider }
                .addClickHandler { _, click -> 
                    if (click.clickType == ClickType.LEFT) {
                        plotMode = plotMode.next()
                        click.player.playClickSound()
                    }
                }
                .updateOnClick()
            ).addIngredient('-', Item.builder()
                .setItemProvider(_duration) { duration ->
                    if (duration.nextOrNull() != null)
                        GuiItems.PLOT_SHRINK_HORIZONTALLY_ON.clientsideProvider
                    else GuiItems.PLOT_SHRINK_HORIZONTALLY_OFF.clientsideProvider
                }
                .addClickHandler { _, click ->
                    val nextDuration = duration.nextOrNull()
                    if (click.clickType == ClickType.LEFT && nextDuration != null) {
                        duration = nextDuration
                        click.player.playClickSound()
                    }
                }
            ).addIngredient('+', Item.builder()
                .setItemProvider(_duration) { duration ->
                    if (duration.previousOrNull() != null)
                        GuiItems.PLOT_ENLARGE_HORIZONTALLY_ON.clientsideProvider
                    else GuiItems.PLOT_ENLARGE_HORIZONTALLY_OFF.clientsideProvider
                }
                .addClickHandler { _, click ->
                    val prevDuration = duration.previousOrNull()
                    if (click.clickType == ClickType.LEFT && prevDuration != null) {
                        duration = prevDuration
                        click.player.playClickSound()
                    }
                }
            ).build()
        
        init {
            drawPlot()
        }
        
        fun drawPlot() {
            graphics.color = Color.BLACK
            graphics.fillRect(0, 0, PLOT_SIZE, PLOT_SIZE)
            
            when (plotMode) {
                PlotMode.ENERGY -> {
                    val values = averagedEnergyValues[duration]!!
                    val plotHeight = values.max()
                    
                    graphics.color = Color.BLUE
                    graphics.drawBars(values, plotHeight)
                }
                
                PlotMode.ENERGY_DELTA -> {
                    val averagedEnergyPlusValues = averagedEnergyPlusValues[duration]!!
                    val averagedEnergyMinusValues = averagedEnergyMinusValues[duration]!!
                    val plotHeight = max(averagedEnergyPlusValues.max(), averagedEnergyMinusValues.max())
                    
                    graphics.color = Color.GREEN
                    graphics.drawBars(averagedEnergyPlusValues, plotHeight)
                    graphics.color = Color.RED
                    graphics.drawBars(averagedEnergyMinusValues, plotHeight)
                }
            }
            
            canvas.notifyWindows()
        }
        
        private fun Graphics2D.drawBars(buffer: LongRingBuffer, plotHeight: Long) {
            buffer.forEach { index, value ->
                val barHeight = ((value.toDouble() / plotHeight.toDouble()) * PLOT_SIZE).toInt()
                fillRect(index, PLOT_SIZE - barHeight, 1, barHeight)
            }
        }
        
    }
    
    private enum class PlotMode {
        ENERGY, ENERGY_DELTA
    }
    
    private enum class BarDuration(val numOfPrevious: Int, val desc: String) {
        TICK(-1, "1 tick"),
        SECOND(20, "1 s"),
        MINUTES_1(60, "1 min"),
        MINUTES_5(5, "5 min"),
        MINUTES_10(3, "15 min"),
        MINUTES_30(3, "30 min"),
        HOURS_1(2, "1 h"),
        HOURS_6(6, "6 h"),
        HOURS_12(2, "12 h"),
        HOURS_24(2, "24 h")
    }
    
}

class CreativePowerCell(pos: BlockPos, state: NovaBlockState, data: Compound) : AbstractPowerCell(pos, state, data) {
    
    override val energyHolder: EnergyHolder
    
    init {
        val energyHolder = InfiniteEnergyHolder(storedValue("energyHolder", ::Compound))
        holders += energyHolder
        this.energyHolder = energyHolder
    }
    
    @TileEntityMenuClass
    inner class PowerCellMenu : GlobalTileEntityMenu() {
        
        private val sideConfigMenu = SideConfigMenu(this@CreativePowerCell, ::openWindow)
        
        override val gui = Gui.builder()
            .setStructure(
                "1 - - - - - - - 2",
                "| s # # e # # # |",
                "| # # # e # # # |",
                "| # # # e # # # |",
                "3 - - - - - - - 4")
            .addIngredient('s', OpenSideConfigItem(sideConfigMenu))
            .addIngredient('e', EnergyBar(3, provider(Long.MAX_VALUE), provider(Long.MAX_VALUE), { 0L }, { 0L }))
            .build()
        
    }
    
}

private val BASIC_CAPACITY = Blocks.BASIC_POWER_CELL.config.entry<Long>("capacity")
private val ADVANCED_CAPACITY = Blocks.ADVANCED_POWER_CELL.config.entry<Long>("capacity")
private val ELITE_CAPACITY = Blocks.ELITE_POWER_CELL.config.entry<Long>("capacity")
private val ULTIMATE_CAPACITY = Blocks.ULTIMATE_POWER_CELL.config.entry<Long>("capacity")

class BasicPowerCell(pos: BlockPos, state: NovaBlockState, data: Compound) : PowerCell(BASIC_CAPACITY, pos, state, data)
class AdvancedPowerCell(pos: BlockPos, state: NovaBlockState, data: Compound) : PowerCell(ADVANCED_CAPACITY, pos, state, data)
class ElitePowerCell(pos: BlockPos, state: NovaBlockState, data: Compound) : PowerCell(ELITE_CAPACITY, pos, state, data)
class UltimatePowerCell(pos: BlockPos, state: NovaBlockState, data: Compound) : PowerCell(ULTIMATE_CAPACITY, pos, state, data)
