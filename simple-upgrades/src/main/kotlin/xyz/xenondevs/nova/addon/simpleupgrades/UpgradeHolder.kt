package xyz.xenondevs.nova.addon.simpleupgrades

import org.bukkit.inventory.ItemStack
import xyz.xenondevs.commons.provider.MutableProvider
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.getCoerced
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.commons.provider.provider
import xyz.xenondevs.invui.inventory.VirtualInventory
import xyz.xenondevs.invui.inventory.event.UpdateReason
import xyz.xenondevs.nova.registry.RegistryEntry
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.world.block.tileentity.TileEntity
import xyz.xenondevs.nova.world.item.createItemStack
import xyz.xenondevs.nova.world.item.itemTypeEntry
import kotlin.math.min

/**
 * Contains and manages upgrades for a [TileEntity].
 *
 * Prefer creating an [UpgradeHolder] using the [TileEntity.storedUpgradeHolder] extension function
 * instead of directly calling the [UpgradeHolder] constructor.
 */
class UpgradeHolder(
    val tileEntity: TileEntity,
    val allowed: Set<RegistryEntry.Nova<UpgradeType<*>>>,
    upgrades: MutableProvider<Map<RegistryEntry.Nova<UpgradeType<*>>, Int>>
) {
    
    private val upgradeCounts: Map<RegistryEntry.Nova<UpgradeType<*>>, MutableProvider<Int>>
    private val valueLists: Map<RegistryEntry.Nova<UpgradeType<*>>, Provider<List<*>>>
    private val values: Map<RegistryEntry.Nova<UpgradeType<*>>, Provider<*>>
    private val limits: Map<RegistryEntry.Nova<UpgradeType<*>>, Provider<Int>>
    
    /**
     * The [Inventories][VirtualInventory] representing the upgrade slots of all allowed upgrade types.
     */
    val inventories: Map<RegistryEntry.Nova<UpgradeType<*>>, VirtualInventory>
        
    init {
        val config = tileEntity.blockType.config
        
        upgradeCounts = allowed.asSequence()
            .zip(
                upgrades.decompose(
                    allowed.size,
                    { map -> allowed.map { map[it] ?: 0 } },
                    { values ->
                        allowed.asSequence()
                            .zip(values.asSequence())
                            .filter { (_, value) -> value > 0 }
                            .toMap()
                    }
                ).asSequence()
            )
            .toMap()
        
        valueLists = allowed.associateWith { type -> type.flatMap { it.getValueList(config) } }
        values = allowed.associateWith { type -> valueLists[type]!!.getCoerced(upgradeCounts[type]!!) }
        limits = allowed.associateWith { type -> valueLists[type]!!.map { min(it.size - 1, 99) } }
        
        inventories = allowed.associateWith { type ->
            val inv = VirtualInventory(1)
            
            // init inventory
            inv.setMaxStackSize(0, getLimit(type))
            inv.setItem(UpdateReason.SUPPRESSED, 0, type.get().item.createItemStack(getLevel(type)))
            
            // prevent putting in items that don't match the upgrade type's item
            inv.addPreUpdateHandler { e ->
                e.isCancelled = (e.isAdd || e.isSwap) && e.newItem?.itemTypeEntry != type.get().item
            }
            // update upgrade count based on inventory changes
            inv.addPostUpdateHandler {
                getLevelProvider(type).set(inv.getItemAmount(0))
            }
            
            // update maxStackSize, item type on reload
            getLimitProvider(type).subscribe { limit ->
                inv.setMaxStackSize(0, limit)
            }
            type.subscribe { type ->
                inv.setItem(UpdateReason.SUPPRESSED, 0, type.item.createItemStack(inv.getItemAmount(0)))
            }
            
            // update item in inventory when level changes
            getLevelProvider(type).subscribe { level ->
                inv.setItem(UpdateReason.SUPPRESSED, 0, type.get().item.createItemStack(level))
            }
            
            inv
        }
    }
    
    /**
     * Gets a provider for the upgrade value list of the given [type],
     * or of an empty list if the given [type] is not allowed in this [UpgradeHolder].
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getValueListProvider(type: RegistryEntry.Nova<UpgradeType<T>>): Provider<List<T>> =
        valueLists[type] as Provider<List<T>>? ?: provider(emptyList())
    
    /**
     * Gets a provider for the current upgrade value of the given [type], 
     * or of the default value of the given [type] if it is not allowed in this [UpgradeHolder].
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getValueProvider(type: RegistryEntry.Nova<UpgradeType<T>>): Provider<T> =
        values[type] as Provider<T>? ?: type.map { it.defaultValue }
    
    /**
     * Gets a provider for the amount of upgrades of the given [type] that this [UpgradeHolder] contains,
     * or of 0 if the given [type] is not allowed in this [UpgradeHolder].
     */
    fun getLevelProvider(type: RegistryEntry.Nova<UpgradeType<*>>): MutableProvider<Int> =
        upgradeCounts[type] ?: mutableProvider(0)
    
    /**
     * Gets a provider for the maximum amount of upgrades of the given [type] that this [UpgradeHolder] can contain,
     * or of 0 if the given [type] is not allowed in this [UpgradeHolder].
     */
    fun getLimitProvider(type: RegistryEntry.Nova<UpgradeType<*>>): Provider<Int> =
        limits[type] ?: provider(0)
    
    /**
     * Gets the current upgrade value of the given [type] based on the amount of upgrades this [UpgradeHolder] contains,
     * or the current default value of the given [type] if it is not allowed in this [UpgradeHolder].
     */
    fun <T : Any> getValue(type: RegistryEntry.Nova<UpgradeType<T>>): T =
        getValueProvider(type).get()
    
    /**
     * Gets the current amount of upgrades that this [UpgradeHolder] contains for the given [type],
     * or 0 if the given [type] is not allowed in this [UpgradeHolder].
     */
    fun getLevel(type: RegistryEntry.Nova<UpgradeType<*>>): Int =
        getLevelProvider(type).get()
    
    /**
     * Checks whether this [UpgradeHolder] currently contains any upgrades of the given [type].
     * Returns `false` if the given [type] is not allowed in this [UpgradeHolder].
     */
    fun hasUpgrade(type: RegistryEntry.Nova<UpgradeType<*>>): Boolean =
        getLevel(type) > 0
    
    /**
     * Gets the current maximum amount of upgrades that this [UpgradeHolder] can contain for the given [type],
     * or 0 if the given [type] is not allowed in this [UpgradeHolder].
     */
    fun getLimit(type: RegistryEntry.Nova<UpgradeType<*>>): Int =
        getLimitProvider(type).get()
    
    /**
     * Gets the [ItemStack] representation of all upgrades that this [UpgradeHolder] contains.
     */
    fun getUpgradeItems(): List<ItemStack> = upgradeCounts.asSequence()
        .map { (type, provider) -> type to provider.get() }
        .filter { (_, amount) -> amount > 0 }
        .map { (type, amount) -> type.get().item.createItemStack(amount) }
        .toList()
    
}