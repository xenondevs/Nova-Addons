package xyz.xenondevs.nova.addon.simpleupgrades

import net.kyori.adventure.key.Key
import org.bukkit.inventory.ItemType
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.getCoerced
import xyz.xenondevs.commons.provider.provider
import xyz.xenondevs.commons.reflection.createType
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.config.CONFIGS
import xyz.xenondevs.nova.config.ConfigProvider
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.config.entryOrElse
import xyz.xenondevs.nova.registry.NovaRegistryElement
import xyz.xenondevs.nova.registry.RegistryEntry
import kotlin.reflect.KType

/**
 * An upgrade type.
 *
 * @param key The [Key] of this upgrade type.
 * @param item The [NovaItem] that represents this upgrade type.
 * @param icon The [NovaItem] that represents the icon of this upgrade type.
 * @param valueType The type of the upgrade values.
 */
class UpgradeType<T : Any> internal constructor(
    override val entry: RegistryEntry.Nova<UpgradeType<T>>,
    val item: RegistryEntry.Paper<ItemType>,
    val placeholder: RegistryEntry.Paper<ItemType>,
    val defaultValue: T,
    valueType: KType,
) : NovaRegistryElement<UpgradeType<T>> {
    
    private val listValueType = createType(List::class, valueType)
    private val globalValueList = CONFIGS["${key.namespace()}:upgrade_values"]
        .entry<List<T>>(listValueType, provider(emptyList()), listOf(key.value()))
    private val valueListProviders = HashMap<ConfigProvider, Provider<List<T>>>()
    private val valueProviders = HashMap<ConfigProvider, HashMap<Int, Provider<T>>>()
    
    /**
     * Gets a provider for the upgrade value for the given [level] configured in [config].
     * If the given [config] does not configure upgrade values, the global config will be used instead.
     */
    fun getValue(config: Provider<ConfigProvider>, level: Int): Provider<T> =
        config.flatMap { getValue(it, level) }
    
    /**
     * Gets a provider for the upgrade value for the given [level] configured in [config].
     * If the given [config] does not configure upgrade values, the global config will be used instead.
     */
    fun getValue(config: ConfigProvider, level: Int): Provider<T> =
        valueProviders
            .getOrPut(config, ::HashMap)
            .getOrPut(level) { getValueList(config).getCoerced(level) }
    
    /**
     * Gets a provider for the list of upgrade values configured in [config].
     * If the given [config] does not configure upgrade values, the global config will be used instead.
     */
    fun getValueList(config: Provider<ConfigProvider>): Provider<List<T>> =
        config.flatMap { getValueList(it) }
    
    /**
     * Gets a provider for the list of upgrade values configured in [config].
     * If the given [config] does not configure upgrade values, the global config will be used instead.
     */
    fun getValueList(config: ConfigProvider): Provider<List<T>> =
        valueListProviders.getOrPut(config) {
            config.entryOrElse(listValueType, globalValueList, "upgrade_values", key.value())
        }
    
    override fun toString(): String = key.toString()
    
    companion object {
        
        private val byItem: Map<RegistryEntry.Paper<ItemType>, RegistryEntry.Nova<UpgradeType<*>>>
            by UpgradeTypes.upgradeTypeRegistry.entrySet.map { types -> types.associate { type -> type.item to type.entry } }
        
        /**
         * Gets the [UpgradeType] for the given [item] or null if [item] is not an upgrade item.
         */
        fun of(item: RegistryEntry.Paper<ItemType>): RegistryEntry.Nova<UpgradeType<*>>? =
            byItem[item]
        
    }
    
}