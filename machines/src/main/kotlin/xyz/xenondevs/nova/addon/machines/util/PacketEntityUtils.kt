package xyz.xenondevs.nova.addon.machines.util

import org.bukkit.Location
import org.bukkit.inventory.ItemType
import org.joml.Quaternionf
import org.joml.Vector3f
import xyz.xenondevs.nova.packetentity.PacketItemDisplay
import xyz.xenondevs.nova.packetentity.packetItemDisplay
import xyz.xenondevs.nova.registry.RegistryEntry
import xyz.xenondevs.nova.world.item.guiItemProvider

internal fun MutableList<PacketItemDisplay>.addDisplay(
    model: RegistryEntry.Paper<ItemType>,
    location: Location,
    translation: Vector3f = Vector3f(),
    leftRotation: Quaternionf = Quaternionf(),
    rightRotation: Quaternionf = Quaternionf()
) {
    val display = packetItemDisplay {
        this.location by location.clone()
        metadata {
            itemStack by model.guiItemProvider.map { it.get() }
            this.translation by translation
            this.leftRotation by leftRotation
            this.rightRotation by rightRotation
        }
    }
    add(display)
    display.spawn()
}
