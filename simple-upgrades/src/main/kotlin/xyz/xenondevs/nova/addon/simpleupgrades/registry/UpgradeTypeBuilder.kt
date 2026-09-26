package xyz.xenondevs.nova.addon.simpleupgrades.registry

import net.kyori.adventure.key.Key.key
import org.bukkit.inventory.ItemType
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.nova.addon.simpleupgrades.UpgradeType
import xyz.xenondevs.nova.registry.Registrar
import xyz.xenondevs.nova.registry.RegistryElementBuilder
import xyz.xenondevs.nova.registry.RegistryEntry
import xyz.xenondevs.nova.registry.RegistryEntrySet
import xyz.xenondevs.nova.registry.RegistryLoader
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Registers a new [UpgradeType] of [name], configured by [upgradeType].
 */
inline fun <reified T : Any> Registrar.upgradeType(name: String, noinline upgradeType: UpgradeTypeBuilder<T>.() -> Unit): RegistryEntry.Nova<UpgradeType<T>> =
    upgradeType(name, typeOf<T>(), upgradeType)

@Suppress("UNCHECKED_CAST")
@PublishedApi
internal fun <T : Any> Registrar.upgradeType(name: String, valueType: KType, upgradeType: UpgradeTypeBuilder<T>.() -> Unit): RegistryEntry.Nova<UpgradeType<T>> =
    RegistryLoader.enqueueNova(UpgradeTypes.upgradeTypeRegistry, key(namespace(), name), { UpgradeTypeBuilderImpl(it, valueType) }, upgradeType) as RegistryEntry.Nova<UpgradeType<T>>

/**
 * Builder for [UpgradeType].
 */
sealed interface UpgradeTypeBuilder<T : Any> {
    
    /**
     * The gameplay item representing this item type.
     */
    fun item(item: RegistryEntry.Paper<ItemType>)
    
    /**
     * The placeholder gui item displayed in the upgrades gui if there is no upgrade 
     * of this type installed yet.
     */
    fun placeholder(item: RegistryEntry.Paper<ItemType>)
    
    /**
     * The default value for this upgrade type.
     * Used when trying to retrieve the upgrade value of an unsupported upgrade type.
     */
    fun defaultValue(value: T)
    
}

private class UpgradeTypeBuilderImpl<T : Any>(
    override val entry: RegistryEntry.Nova<UpgradeType<*>>,
    private val valueType: KType
) : UpgradeTypeBuilder<T>, RegistryElementBuilder.Nova<UpgradeType<*>> {
    
    private var item: RegistryEntry.Paper<ItemType>? = null
    private var placeholder: RegistryEntry.Paper<ItemType>? = null
    private var defaultValue: T? = null
    
    override val tags: Provider<Set<RegistryEntrySet.Nova.Tag<UpgradeType<*>>>>
        field = mutableProvider<Set<RegistryEntrySet.Nova.Tag<UpgradeType<*>>>>(emptySet())
    
    override fun item(item: RegistryEntry.Paper<ItemType>) {
        this.item = item
    }
    
    override fun placeholder(item: RegistryEntry.Paper<ItemType>) {
        this.placeholder = item
    }
    
    override fun defaultValue(value: T) {
        this.defaultValue = value
    }
    
    @Suppress("UNCHECKED_CAST")
    override fun build() = UpgradeType(
        entry as RegistryEntry.Nova<UpgradeType<T>>,
        item ?: throw IllegalStateException("UpgradeType must have an item"),
        placeholder ?: throw IllegalStateException("UpgradeType must have a placeholder item"),
        defaultValue ?: throw IllegalStateException("UpgradeType must have a default value"),
        valueType
    )
    
    override fun tags(vararg tags: RegistryEntrySet.Nova.Tag<UpgradeType<*>>) {
        this.tags.set(this.tags.get() + tags)
    }
    
}