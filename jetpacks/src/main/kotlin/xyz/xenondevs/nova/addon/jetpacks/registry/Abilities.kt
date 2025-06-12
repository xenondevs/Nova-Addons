package xyz.xenondevs.nova.addon.jetpacks.registry

import xyz.xenondevs.nova.addon.jetpacks.Jetpacks.registerAbilityType
import xyz.xenondevs.nova.addon.jetpacks.ability.JetpackFlyAbility
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage

private val BASIC_FLY_SPEED = Items.BASIC_JETPACK.config.entry<Float>("fly_speed")
private val BASIC_ENERGY_PER_TICK = Items.BASIC_JETPACK.config.entry<Long>("energy_per_tick")
private val ADVANCED_FLY_SPEED = Items.ADVANCED_JETPACK.config.entry<Float>("fly_speed")
private val ADVANCED_ENERGY_PER_TICK = Items.ADVANCED_JETPACK.config.entry<Long>("energy_per_tick")
private val ELITE_FLY_SPEED = Items.ELITE_JETPACK.config.entry<Float>("fly_speed")
private val ELITE_ENERGY_PER_TICK = Items.ELITE_JETPACK.config.entry<Long>("energy_per_tick")
private val ULTIMATE_FLY_SPEED = Items.ULTIMATE_JETPACK.config.entry<Float>("fly_speed")
private val ULTIMATE_ENERGY_PER_TICK = Items.ULTIMATE_JETPACK.config.entry<Long>("energy_per_tick")

@Init(stage = InitStage.POST_PACK_PRE_WORLD)
object Abilities {
    
    val BASIC_JETPACK_FLY = registerAbilityType("basic_jetpack_fly") { JetpackFlyAbility(it, BASIC_FLY_SPEED, BASIC_ENERGY_PER_TICK) }
    val ADVANCED_JETPACK_FLY = registerAbilityType("advanced_jetpack_fly") { JetpackFlyAbility(it, ADVANCED_FLY_SPEED, ADVANCED_ENERGY_PER_TICK) }
    val ELITE_JETPACK_FLY = registerAbilityType("elite_jetpack_fly") { JetpackFlyAbility(it, ELITE_FLY_SPEED, ELITE_ENERGY_PER_TICK) }
    val ULTIMATE_JETPACK_FLY = registerAbilityType("ultimate_jetpack_fly") { JetpackFlyAbility(it, ULTIMATE_FLY_SPEED, ULTIMATE_ENERGY_PER_TICK) }
    
}