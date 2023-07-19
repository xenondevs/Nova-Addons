package xyz.xenondevs.nova.addon.simpleupgrades

import org.bukkit.block.BlockFace
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.TileEntity
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.energy.holder.BufferEnergyHolder
import xyz.xenondevs.nova.tileentity.network.energy.holder.ConsumerEnergyHolder
import xyz.xenondevs.nova.tileentity.network.energy.holder.ProviderEnergyHolder
import xyz.xenondevs.nova.tileentity.network.fluid.FluidType
import xyz.xenondevs.nova.tileentity.upgrade.UpgradeHolder
import xyz.xenondevs.nova.tileentity.upgrade.UpgradeType
import java.util.*

fun ConsumerEnergyHolder(
    endPoint: NetworkedTileEntity,
    defaultMaxEnergy: Provider<Long>,
    defaultEnergyConsumption: Provider<Long>? = null,
    defaultSpecialEnergyConsumption: Provider<Long>? = null,
    upgradeHolder: UpgradeHolder,
    lazyDefaultConfig: () -> EnumMap<BlockFace, NetworkConnectionType>
) = ConsumerEnergyHolder(
    endPoint,
    defaultMaxEnergy,
    defaultEnergyConsumption,
    defaultSpecialEnergyConsumption,
    upgradeHolder,
    UpgradeTypes.SPEED,
    UpgradeTypes.EFFICIENCY,
    UpgradeTypes.ENERGY,
    lazyDefaultConfig
)

fun ProviderEnergyHolder(
    endPoint: NetworkedTileEntity,
    defaultMaxEnergy: Provider<Long>,
    defaultEnergyProduction: Provider<Long>,
    upgradeHolder: UpgradeHolder,
    generationUpgradeType: UpgradeType<Double>,
    lazyDefaultConfig: () -> EnumMap<BlockFace, NetworkConnectionType>
) = ProviderEnergyHolder(
    endPoint,
    defaultMaxEnergy,
    defaultEnergyProduction,
    upgradeHolder,
    generationUpgradeType,
    UpgradeTypes.ENERGY,
    lazyDefaultConfig
)

fun ProviderEnergyHolder(
    endPoint: NetworkedTileEntity,
    defaultMaxEnergy: Provider<Long>,
    upgradeHolder: UpgradeHolder,
    lazyDefaultConfig: () -> EnumMap<BlockFace, NetworkConnectionType>
) = ProviderEnergyHolder(
    endPoint,
    defaultMaxEnergy,
    null,
    upgradeHolder,
    UpgradeTypes.SPEED, // irrelevant as defaultEnergyGeneration is null
    UpgradeTypes.ENERGY,
    lazyDefaultConfig
)

fun BufferEnergyHolder(
    endPoint: NetworkedTileEntity,
    defaultMaxEnergy: Provider<Long>,
    infiniteEnergy: Boolean,
    upgradeHolder: UpgradeHolder,
    lazyDefaultConfig: () -> EnumMap<BlockFace, NetworkConnectionType>
) = BufferEnergyHolder(
    endPoint,
    defaultMaxEnergy,
    infiniteEnergy,
    upgradeHolder,
    UpgradeTypes.ENERGY,
    lazyDefaultConfig
)

fun TileEntity.getFluidContainer(
    name: String,
    types: Set<FluidType>,
    capacity: Provider<Long>,
    defaultAmount: Long = 0,
    updateHandler: (() -> Unit)? = null,
    upgradeHolder: UpgradeHolder,
    global: Boolean = true
) = getFluidContainer(
    name,
    types,
    capacity,
    defaultAmount,
    updateHandler,
    upgradeHolder,
    UpgradeTypes.FLUID,
    global
)