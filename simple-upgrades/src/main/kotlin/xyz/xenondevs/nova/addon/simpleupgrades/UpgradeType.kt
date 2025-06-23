package xyz.xenondevs.nova.addon.simpleupgrades

import net.minecraft.resources.ResourceLocation
import org.spongepowered.configurate.ConfigurationNode
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.orElse
import xyz.xenondevs.commons.provider.requireNotNull
import xyz.xenondevs.commons.reflection.createType
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypeRegistry
import xyz.xenondevs.nova.config.Configs
import xyz.xenondevs.nova.config.optionalEntry
import xyz.xenondevs.nova.world.item.NovaItem
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
    private val valueListProviders = HashMap<Provider<ConfigurationNode>, Provider<List<T>>>()
    private val valueProviders = HashMap<Provider<ConfigurationNode>, HashMap<Int, Provider<T>>>()
    
    /**
     * Gets the upgrade value for the given [level] configured in [config].
     * If the given [config] does not configure upgrade values, the global config will be used instead.
     */
    fun getValue(config: Provider<ConfigurationNode>, level: Int): T =
        getValueProvider(config, level).get()
    
    /**
     * Gets a provider for the upgrade value for the given [level] configured in [config].
     * If the given [config] does not configure upgrade values, the global config will be used instead.
     */
    fun getValueProvider(config: Provider<ConfigurationNode>, level: Int): Provider<T> =
        valueProviders
            .getOrPut(config, ::HashMap)
            .getOrPut(level) { getValueListProvider(config).map { list -> list[level.coerceIn(0..list.lastIndex)] } }
    
    /**
     * Gets the list of upgrade values configured in [config].
     * If the given [config] does not configure upgrade values, the global config will be used instead.
     */
    fun getValueList(config: Provider<ConfigurationNode>): List<T> =
        getValueListProvider(config).get()
    
    /**
     * Gets a provider for the list of upgrade values configured in [config].
     * If the given [config] does not configure upgrade values, the global config will be used instead.
     */
    fun getValueListProvider(config: Provider<ConfigurationNode>): Provider<List<T>> =
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