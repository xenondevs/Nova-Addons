package xyz.xenondevs.nova.addon.machines.util

import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.combinedProvider
import xyz.xenondevs.nova.addon.simpleupgrades.UpgradeHolder
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import kotlin.math.roundToInt
import kotlin.math.roundToLong

fun energyConsumption(
    base: Provider<Long>,
    upgradeHolder: UpgradeHolder
): Provider<Long> = combinedProvider(
    base,
    upgradeHolder.getValueProvider(UpgradeTypes.SPEED),
    upgradeHolder.getValueProvider(UpgradeTypes.EFFICIENCY)
) { base, speed, efficiency -> (base * speed / efficiency).roundToLong() }

fun efficiencyDividedValue(
    value: Provider<Long>,
    upgradeHolder: UpgradeHolder
): Provider<Long> = combinedProvider(
    value,
    upgradeHolder.getValueProvider(UpgradeTypes.EFFICIENCY)
) { baseValue, efficiency -> (baseValue / efficiency).roundToLong() }

fun efficiencyMultipliedValue(
    value: Provider<Long>,
    upgradeHolder: UpgradeHolder
): Provider<Long> = combinedProvider(
    value,
    upgradeHolder.getValueProvider(UpgradeTypes.EFFICIENCY)
) { baseValue, efficiency -> (baseValue * efficiency).roundToLong() }

@JvmName("speedMultipliedValueDouble")
fun speedMultipliedValue(
    value: Provider<Double>,
    upgradeHolder: UpgradeHolder
): Provider<Double> = combinedProvider(
    value,
    upgradeHolder.getValueProvider(UpgradeTypes.SPEED)
) { baseValue, speed -> (baseValue * speed) }

@JvmName("speedMultipliedValueLong")
fun speedMultipliedValue(
    value: Provider<Long>,
    upgradeHolder: UpgradeHolder
): Provider<Long> = combinedProvider(
    value,
    upgradeHolder.getValueProvider(UpgradeTypes.SPEED)
) { baseValue, speed -> (baseValue * speed).roundToLong() }

fun speedMultipliedValue(
    value: Provider<Int>,
    upgradeHolder: UpgradeHolder
): Provider<Int> = combinedProvider(
    value,
    upgradeHolder.getValueProvider(UpgradeTypes.SPEED)
) { baseValue, speed -> (baseValue * speed).roundToInt() }

fun rangeAffectedValue(
    value: Provider<Int>,
    upgradeHolder: UpgradeHolder
): Provider<Int> = combinedProvider(
    value,
    upgradeHolder.getValueProvider(UpgradeTypes.RANGE)
) { baseValue, range -> baseValue + range }

fun maxIdleTime(
    maxIdleTime: Provider<Int>,
    upgradeHolder: UpgradeHolder
): Provider<Int> = combinedProvider(
    maxIdleTime,
    upgradeHolder.getValueProvider(UpgradeTypes.SPEED)
) { idleTime, speed -> (idleTime / speed).roundToInt() }
