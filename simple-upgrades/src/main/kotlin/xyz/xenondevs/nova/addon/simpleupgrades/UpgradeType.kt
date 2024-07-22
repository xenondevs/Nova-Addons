package xyz.xenondevs.nova.addon.simpleupgrades

import net.minecraft.resources.ResourceLocation
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.immutable.map
import xyz.xenondevs.commons.provider.immutable.orElse
import xyz.xenondevs.commons.provider.immutable.requireNotNull
import xyz.xenondevs.commons.reflection.createType
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypeRegistry
import xyz.xenondevs.nova.data.config.ConfigProvider
import xyz.xenondevs.nova.data.config.Configs
import xyz.xenondevs.nova.item.NovaItem
import kotlin.reflect.KType

/**
 * An upgrade type.
 *
 * @param id The [ResourceLocation] of this upgrade type.
 * @param item The [NovaItem] that represents this upgrade type.
 * @param icon The [NovaItem] that represents the icon of this upgrade type.
 * @param valueType The type of the upgrade values.
 */
class UpgradeType<T> internal constructor(
    val id: ResourceLocation,
    val item: NovaItem,
    val icon: NovaItem,
    valueType: KType
) {
    
    private val listValueType = createType(List::class, valueType)
    private val globalConfig = Configs["${id.namespace}:upgrade_values"]
    private val valueListProviders = HashMap<ConfigProvider, Provider<List<T>>>()
    private val valueProviders = HashMap<ConfigProvider, HashMap<Int, Provider<T>>>()
    
    /**
     * Gets the upgrade value for the given [level] configured in [config].
     * If the given [config] does not configure upgrade values, the global config will be used instead.
     */
    fun getValue(config: ConfigProvider, level: Int): T =
        getValueProvider(config, level).get()
    
    /**
     * Gets a provider for the upgrade value for the given [level] configured in [config].
     * If the given [config] does not configure upgrade values, the global config will be used instead.
     */
    fun getValueProvider(config: ConfigProvider, level: Int): Provider<T> =
        valueProviders
            .getOrPut(config, ::HashMap)
            .getOrPut(level) { getValueListProvider(config).map { list -> list[level.coerceIn(0..list.lastIndex)] } }
    
    /**
     * Gets the list of upgrade values configured in [config].
     * If the given [config] does not configure upgrade values, the global config will be used instead.
     */
    fun getValueList(config: ConfigProvider): List<T> =
        getValueListProvider(config).get()
    
    /**
     * Gets a provider for the list of upgrade values configured in [config].
     * If the given [config] does not configure upgrade values, the global config will be used instead.
     */
    fun getValueListProvider(config: ConfigProvider): Provider<List<T>> =
        valueListProviders.getOrPut(config) {
            config.optionalEntry<List<T>>(listValueType, "upgrade_values", id.path)
                .orElse(globalConfig.optionalEntry(listValueType, id.path))
                .requireNotNull("No upgrade values present for $id")
        }
    
    override fun toString(): String {
        return id.toString()
    }
    
    companion object {
        
        /**
         * Gets the [UpgradeType] for the given [item] or null if [item] is not an upgrade item.
         */
        @Suppress("UNCHECKED_CAST")
        fun <T> of(item: NovaItem): UpgradeType<T>? =
            UpgradeTypeRegistry.REGISTRY.firstOrNull { it.item == item } as? UpgradeType<T>
        
    }
    
}