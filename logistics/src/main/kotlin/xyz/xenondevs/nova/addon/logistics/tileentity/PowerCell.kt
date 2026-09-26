package xyz.xenondevs.nova.addon.logistics.tileentity

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.cbf.entry
import xyz.xenondevs.commons.collections.enumMap
import xyz.xenondevs.commons.collections.next
import xyz.xenondevs.commons.collections.nextOrNull
import xyz.xenondevs.commons.collections.previousOrNull
import xyz.xenondevs.commons.provider.MutableProvider
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.commons.provider.orElse
import xyz.xenondevs.commons.provider.provider
import xyz.xenondevs.invui.dsl.by
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.dsl.item
import xyz.xenondevs.invui.item.ItemBuilder
import xyz.xenondevs.nova.addon.logistics.registry.Blocks
import xyz.xenondevs.nova.addon.logistics.registry.GuiItems
import xyz.xenondevs.nova.addon.logistics.registry.GuiTextures
import xyz.xenondevs.nova.addon.logistics.util.LongRingBuffer
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.Canvas
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.nova.util.CubeFaceMap
import xyz.xenondevs.nova.util.CubeFaceSet
import xyz.xenondevs.nova.util.playClickSound
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType
import xyz.xenondevs.nova.world.block.tileentity.network.type.energy.holder.EnergyHolder
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import kotlin.math.max

private val ALL_EXTRACT = CubeFaceMap(NetworkConnectionType.EXTRACT)

private class InfiniteEnergyHolder(compound: Provider<Compound>) : EnergyHolder {
    
    override val blockedFaces: CubeFaceSet
        get() = CubeFaceSet.NONE
    override val allowedConnectionType = NetworkConnectionType.EXTRACT
    override var connectionConfig: CubeFaceMap<NetworkConnectionType>
        by compound.entry<CubeFaceMap<NetworkConnectionType>>("connectionConfig")
            .orElse(ALL_EXTRACT)
    
    override var energy = Long.MAX_VALUE
    override val maxEnergy = Long.MAX_VALUE
    
}

abstract class AbstractPowerCell(pos: Block, state: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, state, data) {
    protected abstract val energyHolder: EnergyHolder
}

private const val PLOT_SIZE = 18 * 3

abstract class PowerCell(capacity: Provider<Long>, pos: Block, state: NovaBlockState, data: Compound) : AbstractPowerCell(pos, state, data) {
    
    final override val energyHolder = storedEnergyHolder(capacity, NetworkConnectionType.BUFFER)
    
    private val entriesUntilRollover = enumMap<BarDuration, Int> { it.numOfPrevious }
    private val averagedEnergyValues = enumMap<BarDuration, LongRingBuffer> { LongRingBuffer(PLOT_SIZE) }
    private val averagedEnergyPlusValues = enumMap<BarDuration, LongRingBuffer> { LongRingBuffer(PLOT_SIZE) }
    private val averagedEnergyMinusValues = enumMap<BarDuration, LongRingBuffer> { LongRingBuffer(PLOT_SIZE) }
    private val activePlots = mutableSetOf<PowerCellPlot>()
    
    override fun handleTick() {
        decrementRollover(BarDuration.TICK)
        activePlots.forEach(PowerCellPlot::drawPlot)
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
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.POWER_CELL) {
        val plotMode = mutableProvider(PlotMode.ENERGY_DELTA)
        val duration = mutableProvider(BarDuration.SECOND)
        
        val plot = PowerCellPlot(plotMode, duration)
        plot.drawPlot()
        
        upperGui by gui(
            ". . . . . . . . .",
            "s . m . c c c . e",
            ". . + . c c c . e",
            ". . - . c c c . e",
            ". . . . . . . . ."
        ) {
            's' by openSideConfigItem()
            'e' by energyBar(energyHolder)
            'c' by plot.canvas
            'm' by switchPlotModeItem(plotMode)
            '-' by increaseDurationItem(duration)
            '+' by decreaseDurationItem(duration)
        }
        
        onOpen { activePlots += plot }
        onClose { activePlots -= plot }
    }
    
    private inner class PowerCellPlot(
        plotMode: Provider<PlotMode>,
        duration: Provider<BarDuration>,
    ) {
        
        private val plotMode by plotMode
        private val duration by duration
        
        private val graph = BufferedImage(PLOT_SIZE, PLOT_SIZE, BufferedImage.TYPE_INT_ARGB)
        private val graphics = graph.createGraphics()
        
        val canvas = object : Canvas(graph) {
            override fun modifyItemBuilder(x: Int, y: Int, viewer: Player, itemBuilder: ItemBuilder) {
                itemBuilder.setName(
                    Component.translatable(
                        "menu.logistics.power_cell.graph_mode.${plotMode.get().name.lowercase()}",
                        NamedTextColor.GRAY
                    )
                )
                itemBuilder.addLoreLines(
                    Component.translatable(
                        "menu.logistics.power_cell.graph_bar_duration",
                        NamedTextColor.GRAY,
                        Component.text(duration.get().desc)
                    )
                )
            }
        }
        
        init {
            plotMode.observe { canvas.notifyWindows() }
            duration.observe { canvas.notifyWindows() }
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

private fun switchPlotModeItem(plotMode: MutableProvider<PlotMode>) = item {
    itemProvider by plotMode.flatMap {
        if (it == PlotMode.ENERGY)
            GuiItems.PLOT_MODE_ENERGY
        else GuiItems.PLOT_MODE_ENERGY_DELTA
    }
    onClick {
        if (clickType == ClickType.LEFT) {
            plotMode.set(plotMode.get().next())
            player.playClickSound()
        }
    }
}

private fun increaseDurationItem(duration: MutableProvider<BarDuration>) = item {
    itemProvider by duration.flatMap { duration ->
        if (duration.nextOrNull() != null)
            GuiItems.PLOT_SHRINK_HORIZONTALLY_ON
        else GuiItems.PLOT_SHRINK_HORIZONTALLY_OFF
    }
    onClick {
        val nextDuration = duration.get().nextOrNull()
        if (clickType == ClickType.LEFT && nextDuration != null) {
            duration.set(nextDuration)
            player.playClickSound()
        }
    }
}

private fun decreaseDurationItem(duration: MutableProvider<BarDuration>) = item {
    itemProvider by duration.flatMap { duration ->
        if (duration.previousOrNull() != null)
            GuiItems.PLOT_ENLARGE_HORIZONTALLY_ON
        else GuiItems.PLOT_ENLARGE_HORIZONTALLY_OFF
    }
    onClick {
        val prevDuration = duration.get().previousOrNull()
        if (clickType == ClickType.LEFT && prevDuration != null) {
            duration.set(prevDuration)
            player.playClickSound()
        }
    }
}

class CreativePowerCell(pos: Block, state: NovaBlockState, data: Compound) : AbstractPowerCell(pos, state, data) {
    
    override val energyHolder: EnergyHolder
    
    init {
        val energyHolder = InfiniteEnergyHolder(storedValue("energyHolder", ::Compound))
        holders += energyHolder
        this.energyHolder = energyHolder
    }
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.CREATIVE_POWER_CELL) {
        upperGui by gui(
            "s . . . e . . . .",
            ". . . . e . . . .",
            ". . . . e . . . .",
        ) {
            's' by openSideConfigItem()
            'e' by energyBar(provider(Long.MAX_VALUE), provider(Long.MAX_VALUE), provider(0L), provider(0L))
        }
    }
    
}

private val BASIC_CAPACITY = Blocks.BASIC_POWER_CELL.config.entry<Long>("capacity")
private val ADVANCED_CAPACITY = Blocks.ADVANCED_POWER_CELL.config.entry<Long>("capacity")
private val ELITE_CAPACITY = Blocks.ELITE_POWER_CELL.config.entry<Long>("capacity")
private val ULTIMATE_CAPACITY = Blocks.ULTIMATE_POWER_CELL.config.entry<Long>("capacity")

class BasicPowerCell(pos: Block, state: NovaBlockState, data: Compound) : PowerCell(BASIC_CAPACITY, pos, state, data)
class AdvancedPowerCell(pos: Block, state: NovaBlockState, data: Compound) : PowerCell(ADVANCED_CAPACITY, pos, state, data)
class ElitePowerCell(pos: Block, state: NovaBlockState, data: Compound) : PowerCell(ELITE_CAPACITY, pos, state, data)
class UltimatePowerCell(pos: Block, state: NovaBlockState, data: Compound) : PowerCell(ULTIMATE_CAPACITY, pos, state, data)
