package xyz.xenondevs.nova.addon.simpleupgrades

import org.bukkit.inventory.ItemStack
import xyz.xenondevs.commons.provider.AbstractProvider
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.nova.addon.simpleupgrades.gui.UpgradesGui
import xyz.xenondevs.nova.config.ConfigProvider
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
    
    internal val upgrades: MutableMap<UpgradeType<*>, Int> by upgrades
    private val config: ConfigProvider = tileEntity.block.config
    private val valueProviders: Map<UpgradeType<*>, ModifierProvider<*>> = allowed.associateWithTo(HashMap()) { ModifierProvider(it) }
    internal val gui = lazy { UpgradesGui(this) { tileEntity.menuContainer.openWindow(it) } }
    
    /**
     * Gets the upgrade value of the given [type] based on the amount of upgrades this [UpgradeHolder] contains.
     */
    fun <T> getValue(type: UpgradeType<T>): T = type.getValue(config, upgrades[type] ?: 0)
    
    /**
     * Gets a provider for the upgrade value of the given [type].
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getValueProvider(type: UpgradeType<T>): Provider<T> = valueProviders[type] as Provider<T>
    
    /**
     * Gets the amount of upgrades that this [UpgradeHolder] contains for the given [type].
     */
    fun getLevel(type: UpgradeType<*>): Int = upgrades[type] ?: 0
    
    /**
     * Checks whether this [UpgradeHolder] contains any upgrades of the given [type].
     */
    fun hasUpgrade(type: UpgradeType<*>): Boolean = type in upgrades
    
    /**
     * Gets the maximum amount of upgrades that this [UpgradeHolder] can contain for the given [type].
     */
    fun getLimit(type: UpgradeType<*>): Int = min(type.getValueList(config).size - 1, 999)
    
    /**
     * Gets the [ItemStack] representation of all upgrades that this [UpgradeHolder] contains.
     */
    fun getUpgradeItems(): List<ItemStack> = upgrades.map { (type, amount) -> type.item.createItemStack(amount) }
    
    /**
     * Tries adding the given amount of upgrades and returns the amount of upgrades that wasn't added.
     */
    fun addUpgrade(type: UpgradeType<*>, amount: Int): Int {
        if (type !in allowed || amount == 0)
            return amount
        
        val limit = getLimit(type)
        
        val current = upgrades[type] ?: 0
        if (limit - current < amount) {
            upgrades[type] = limit
            handleUpgradeUpdates()
            return amount - (limit - current)
        } else {
            upgrades[type] = current + amount
            handleUpgradeUpdates()
            return 0
        }
    }
    
    /**
     * Removes one or all upgrades of the given type and returns the [ItemStack] of removed upgrades.
     */
    fun removeUpgrade(type: UpgradeType<*>, all: Boolean): ItemStack? {
        val amount = upgrades[type] ?: return null
        
        if (all) {
            upgrades.remove(type)
        } else {
            if (amount - 1 == 0) upgrades.remove(type)
            else upgrades[type] = amount - 1
        }
        
        handleUpgradeUpdates()
        return type.item.createItemStack(if (all) amount else 1)
    }
    
    private fun handleUpgradeUpdates() {
        valueProviders.values.forEach(Provider<*>::update)
        
        if (gui.isInitialized())
            gui.value.updateUpgrades()
    }
    
    private inner class ModifierProvider<T>(private val type: UpgradeType<T>) : AbstractProvider<T>() {
        
        private val parent = type.getValueListProvider(config)
        
        init {
            parent.addChild(this)
        }
        
        override fun loadValue(): T {
            return getValue(type)
        }
        
    }
    
}