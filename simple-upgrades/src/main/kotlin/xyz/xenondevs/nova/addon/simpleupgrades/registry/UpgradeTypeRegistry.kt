package xyz.xenondevs.nova.addon.simpleupgrades.registry

import xyz.xenondevs.nova.addon.Addon
import xyz.xenondevs.nova.addon.simpleupgrades.SimpleUpgrades
import xyz.xenondevs.nova.addon.simpleupgrades.UpgradeType
import xyz.xenondevs.nova.registry.NovaRegistryAccess
import xyz.xenondevs.nova.util.Identifier
import xyz.xenondevs.nova.util.set
import xyz.xenondevs.nova.world.item.NovaItem
import kotlin.reflect.KType
import kotlin.reflect.typeOf

object UpgradeTypeRegistry {
    
    val REGISTRY = NovaRegistryAccess.addRegistry<UpgradeType<*>>(SimpleUpgrades, "upgrade_type")
    
    internal inline fun <reified T> registerUpgradeType(name: String, item: NovaItem, icon: NovaItem): UpgradeType<T> {
        return registerUpgradeType(name, item, icon, typeOf<T>())
    }
    
    internal fun <T> registerUpgradeType(name: String, item: NovaItem, icon: NovaItem, valueType: KType): UpgradeType<T> =
        registerUpgradeType(SimpleUpgrades, name, item, icon, valueType)
    
    inline fun <reified T> registerUpgradeType(addon: Addon, name: String, item: NovaItem, icon: NovaItem): UpgradeType<T> {
        return registerUpgradeType(addon, name, item, icon, typeOf<T>())
    }
    
    fun <T> registerUpgradeType(addon: Addon, name: String, item: NovaItem, icon: NovaItem, valueType: KType): UpgradeType<T> {
        val id = Identifier(addon, name)
        val upgradeType = UpgradeType<T>(id, item, icon, valueType)
        
        REGISTRY[id] = upgradeType
        return upgradeType
    }
    
}