package xyz.xenondevs.nova.addon.machines.util

import org.bukkit.craftbukkit.entity.CraftLivingEntity
import org.bukkit.entity.LivingEntity
import java.lang.invoke.MethodHandles
import net.minecraft.world.entity.LivingEntity as MojangLivingEntity

private val LIVING_ENTITY_SET_LAST_HURT_BY_PLAYER_MEMORY_TIME = MethodHandles
    .privateLookupIn(MojangLivingEntity::class.java, MethodHandles.lookup())
    .findSetter(MojangLivingEntity::class.java, "lastHurtByPlayerMemoryTime", Int::class.java)

/**
 * Sets the lastHurtByPlayerMemoryTime to mark the entity as hurt by a player,
 * which allows experience orbs to spawn on death.
 */
fun LivingEntity.markAsHurtByPlayer() {
    // sets the lastHurtByMemoryTimeValue to 100, (internals always set this to the magic value 100)
    LIVING_ENTITY_SET_LAST_HURT_BY_PLAYER_MEMORY_TIME.invoke(
        (this as CraftLivingEntity).handle,
        100
    )
}