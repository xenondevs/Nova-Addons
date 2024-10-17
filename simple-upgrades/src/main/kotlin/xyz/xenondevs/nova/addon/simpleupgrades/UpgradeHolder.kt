package xyz.xenondevs.nova.addon.simpleupgrades

import org.bukkit.inventory.ItemStack
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.combinedProvider
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.nova.addon.simpleupgrades.gui.UpgradesGui
import xyz.xenondevs.nova.world.block.tileentity.TileEntity
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.math.min

/**
 * Contains and manages upgrades for a [TileEntity].
 *
 * Prefer creating an [UpgradeHolder] using the [TileEntity.storedUpgradeHolder] extension function
 * instead of directly calling the [UpgradeHolder] constructor.
 */
class UpgradeHolder(
    internal val tileEntity: TileEntity,
    internal val allowed: Set<UpgradeType<*>>,
    upgrades: Provider<MutableMap<UpgradeType<*>, Int>>
) {
    
    private val config = tileEntity.block.config
    private val upgradeCountProviders = allowed.associateWithTo(HashMap()) { type ->
        val countProvider = mutableProvider { upgrades.get()[type] ?: 0 }
        countProvider.subscribe { upgrades.get()[type] = it }
        countProvider
    }
    private val valueProviders: Map<UpgradeType<*>, Provider<*>> = allowed.associateWithTo(HashMap()) { type ->
        combinedProvider(
            type.getValueListProvider(config), upgradeCountProviders[type]!!
        ) { valueList, level ->
            valueList[level.coerceIn(0..valueList.lastIndex)]
        }
    }
    internal val gui = lazy { UpgradesGui(this) { tileEntity.menuContainer.openWindow(it) } }
    
    /**
     * Gets the upgrade value of the given [type] based on the amount of upgrades this [UpgradeHolder] contains.
     */
    fun <T> getValue(type: UpgradeType<T>): T = type.getValue(config, upgradeCountProviders[type]?.get() ?: 0)
    
    /**
     * Gets a provider for the upgrade value of the given [type].
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getValueProvider(type: UpgradeType<T>): Provider<T> = valueProviders[type] as Provider<T>
    
    /**
     * Gets the amount of upgrades that this [UpgradeHolder] contains for the given [type].
     */
    fun getLevel(type: UpgradeType<*>): Int = upgradeCountProviders[type]?.get() ?: 0
    
    /**
     * Checks whether this [UpgradeHolder] contains any upgrades of the given [type].
     */
    fun hasUpgrade(type: UpgradeType<*>): Boolean = getLevel(type) > 0
    
    /**
     * Gets the maximum amount of upgrades that this [UpgradeHolder] can contain for the given [type].
     */
    fun getLimit(type: UpgradeType<*>): Int = min(type.getValueList(config).size - 1, 999)
    
    /**
     * Gets the [ItemStack] representation of all upgrades that this [UpgradeHolder] contains.
     */
    fun getUpgradeItems(): List<ItemStack> = upgradeCountProviders.asSequence()
        .map { (type, provider) -> type to provider.get() }
        .filter { (_, amount) -> amount > 0 }
        .map { (type, amount) -> type.item.createItemStack(amount) }
        .toList()
    
    /**
     * Tries adding the given amount of upgrades and returns the amount of upgrades that wasn't added.
     */
    fun addUpgrade(type: UpgradeType<*>, amount: Int): Int {
        if (type !in allowed || amount == 0)
            return amount
        
        val limit = getLimit(type)
        
        val currentProvider = upgradeCountProviders[type]!!
        val current = currentProvider.get()
        if (limit - current < amount) {
            currentProvider.set(limit)
            handleUpgradeUpdates()
            return amount - (limit - current)
        } else {
            currentProvider.set(current + amount)
            handleUpgradeUpdates()
            return 0
        }
    }
    
    /**
     * Removes one or all upgrades of the given type and returns the [ItemStack] of removed upgrades.
     */
    fun removeUpgrade(type: UpgradeType<*>, all: Boolean): ItemStack? {
        if (type !in allowed)
            return null
        
        val amountProvider = upgradeCountProviders[type]!!
        val amount = amountProvider.get()
        if (amount <= 0)
            return null
        
        if (all) {
            amountProvider.set(0)
        } else {
            amountProvider.set(amount - 1)
        }
        
        handleUpgradeUpdates()
        return type.item.createItemStack(if (all) amount else 1)
    }
    
    private fun handleUpgradeUpdates() {
        if (gui.isInitialized())
            gui.value.updateUpgrades()
    }
    
}