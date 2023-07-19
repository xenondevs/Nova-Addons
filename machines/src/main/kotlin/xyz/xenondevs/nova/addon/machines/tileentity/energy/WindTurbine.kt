package xyz.xenondevs.nova.addon.machines.tileentity.energy

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.joml.Quaternionf
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.data.config.NovaConfig
import xyz.xenondevs.nova.data.config.configReloadable
import xyz.xenondevs.nova.data.resources.model.data.DisplayEntityBlockModelData
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.addon.machines.registry.Blocks.WIND_TURBINE
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.upgrade.Upgradable
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.OpenUpgradesItem
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.concurrent.CombinedBooleanFuture
import xyz.xenondevs.nova.util.item.isReplaceable
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.model.Model
import xyz.xenondevs.nova.world.model.MovableMultiModel
import xyz.xenondevs.nova.world.pos
import xyz.xenondevs.nova.addon.simpleupgrades.ProviderEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import java.util.concurrent.CompletableFuture
import kotlin.math.abs

private val MAX_ENERGY = configReloadable { NovaConfig[WIND_TURBINE].getLong("capacity") }
private val ENERGY_PER_TICK = configReloadable { NovaConfig[WIND_TURBINE].getLong("energy_per_tick") }
private val PLAY_ANIMATION by configReloadable { NovaConfig[WIND_TURBINE].getBoolean("animation") }

class WindTurbine(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState), Upgradable {
    
    override val upgradeHolder = getUpgradeHolder(UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    override val energyHolder = ProviderEnergyHolder(this, MAX_ENERGY, ENERGY_PER_TICK, upgradeHolder, UpgradeTypes.EFFICIENCY) {
        createExclusiveSideConfig(NetworkConnectionType.EXTRACT, BlockSide.FRONT, BlockSide.BOTTOM)
    }
    
    private val turbineModel = MovableMultiModel()
    private val altitude = (location.y + abs(world.minHeight)) / (world.maxHeight - 1 + abs(world.minHeight))
    private val rotationPerTick = altitude * 15
    private var energyPerTick = 0
    
    init {
        reload()
        spawnModels()
    }
    
    override fun reload() {
        super.reload()
        energyPerTick = (altitude * energyHolder.energyGeneration).toInt()
    }
    
    override fun handleRemoved(unload: Boolean) {
        super.handleRemoved(unload)
        turbineModel.close()
    }
    
    private fun spawnModels() {
        val location = location.add(0.5, 3.5, 0.5)
        
        turbineModel.add(Model(
            (block.model as DisplayEntityBlockModelData)[4].get(),
            location
        ))
        
        for (blade in 0..2) {
            turbineModel.add(Model(
                (block.model as DisplayEntityBlockModelData)[5].get(),
                location,
                rightRotation = Quaternionf().setAngleAxis(
                    (Math.PI * 2 / 3 * blade).toFloat(),
                    0f, 0f, 1f
                )
            ))
        }
    }
    
    override fun handleTick() {
        energyHolder.energy += energyPerTick
    }
    
    override fun handleAsyncTick() {
        if (PLAY_ANIMATION) {
            turbineModel.useMetadata {
                it.leftRotation = it.leftRotation.rotateAxis(
                    Math.toRadians(rotationPerTick).toFloat(),
                    0f, 0f, 1f
                )
            }
        }
    }
    
    companion object {
        
        fun canPlace(player: Player, item: ItemStack, location: Location): CompletableFuture<Boolean> {
            return CombinedBooleanFuture(loadMultiBlock(location.pos).map {
                if (!it.block.type.isReplaceable())
                    return CompletableFuture.completedFuture(false)
                
                ProtectionManager.canPlace(player, item, it.location)
            })
        }
        
        fun loadMultiBlock(pos: BlockPos): List<BlockPos> =
            listOf(
                pos.copy(y = pos.y + 1),
                pos.copy(y = pos.y + 2),
                pos.copy(y = pos.y + 3)
            )
        
    }
    
    @TileEntityMenuClass
    inner class WindTurbineMenu : GlobalTileEntityMenu() {
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| u # # e # # # |",
                "| # # # e # # # |",
                "| # # # e # # # |",
                "3 - - - - - - - 4")
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('e', EnergyBar(3, energyHolder))
            .build()
        
    }
    
}