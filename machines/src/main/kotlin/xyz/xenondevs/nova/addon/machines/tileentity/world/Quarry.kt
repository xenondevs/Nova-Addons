package xyz.xenondevs.nova.addon.machines.tileentity.world

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.core.particles.ParticleTypes
import org.bukkit.Axis
import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.joml.Vector3f
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.invui.item.builder.addLoreLines
import xyz.xenondevs.invui.item.builder.setDisplayName
import xyz.xenondevs.invui.item.impl.AbstractItem
import xyz.xenondevs.nmsutils.particle.block
import xyz.xenondevs.nmsutils.particle.particle
import xyz.xenondevs.nova.addon.machines.registry.Blocks.QUARRY
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.addon.simpleupgrades.ConsumerEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.api.NovaEventFactory
import xyz.xenondevs.nova.data.config.GlobalValues
import xyz.xenondevs.nova.data.config.entry
import xyz.xenondevs.nova.data.world.block.state.NovaTileEntityState
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.item.DefaultGuiItems
import xyz.xenondevs.nova.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.tileentity.network.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.item.holder.NovaItemHolder
import xyz.xenondevs.nova.tileentity.upgrade.Upgradable
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.OpenUpgradesItem
import xyz.xenondevs.nova.ui.config.side.OpenSideConfigItem
import xyz.xenondevs.nova.ui.config.side.SideConfigMenu
import xyz.xenondevs.nova.ui.item.AddNumberItem
import xyz.xenondevs.nova.ui.item.RemoveNumberItem
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.LocationUtils
import xyz.xenondevs.nova.util.blockLocation
import xyz.xenondevs.nova.util.breakTexture
import xyz.xenondevs.nova.util.center
import xyz.xenondevs.nova.util.concurrent.CombinedBooleanFuture
import xyz.xenondevs.nova.util.getAllDrops
import xyz.xenondevs.nova.util.getFullCuboid
import xyz.xenondevs.nova.util.getNextBlockBelow
import xyz.xenondevs.nova.util.getRectangle
import xyz.xenondevs.nova.util.getStraightLine
import xyz.xenondevs.nova.util.hardness
import xyz.xenondevs.nova.util.item.ToolUtils
import xyz.xenondevs.nova.util.positionEquals
import xyz.xenondevs.nova.util.remove
import xyz.xenondevs.nova.util.sendTo
import xyz.xenondevs.nova.util.serverTick
import xyz.xenondevs.nova.util.setBreakStage
import xyz.xenondevs.nova.world.block.BlockManager
import xyz.xenondevs.nova.world.block.context.BlockBreakContext
import xyz.xenondevs.nova.world.model.Model
import xyz.xenondevs.nova.world.model.MovableMultiModel
import xyz.xenondevs.nova.world.model.MultiModel
import xyz.xenondevs.nova.world.pos
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.properties.Delegates

private val SCAFFOLDING_STACKS = Items.SCAFFOLDING.let { item -> (1..item.model.dataArray.lastIndex).map { item.clientsideProviders[it].get() } }
private val FULL_HORIZONTAL = SCAFFOLDING_STACKS[0]
private val FULL_VERTICAL = SCAFFOLDING_STACKS[1]
private val CORNER_DOWN = SCAFFOLDING_STACKS[2]
private val SMALL_HORIZONTAL = SCAFFOLDING_STACKS[3]
private val FULL_SLIM_VERTICAL = SCAFFOLDING_STACKS[4]
private val SLIM_VERTICAL_DOWN = SCAFFOLDING_STACKS[5]
private val DRILL = Items.NETHERITE_DRILL.clientsideProvider.get()

private val MIN_SIZE by QUARRY.config.entry<Int>("min_size")
private val MAX_SIZE by QUARRY.config.entry<Int>("max_size")
private val MIN_DEPTH by QUARRY.config.entry<Int>("min_depth")
private val MAX_DEPTH by QUARRY.config.entry<Int>("max_depth")
private val DEFAULT_SIZE_X by QUARRY.config.entry<Int>("default_size_x")
private val DEFAULT_SIZE_Z by QUARRY.config.entry<Int>("default_size_z")
private val DEFAULT_SIZE_Y by QUARRY.config.entry<Int>("default_size_y")

private val MOVE_SPEED by QUARRY.config.entry<Double>("move_speed")
private val DRILL_SPEED_MULTIPLIER by QUARRY.config.entry<Double>("drill_speed_multiplier")
private val DRILL_SPEED_CLAMP by QUARRY.config.entry<Double>("drill_speed_clamp")

private val MAX_ENERGY = QUARRY.config.entry<Long>("capacity")
private val BASE_ENERGY_CONSUMPTION by QUARRY.config.entry<Int>("base_energy_consumption")
private val ENERGY_PER_SQUARE_BLOCK by QUARRY.config.entry<Int>("energy_consumption_per_square_block")

class Quarry(blockState: NovaTileEntityState) : NetworkedTileEntity(blockState), Upgradable {
    
    private val inventory = getInventory("quarryInventory", 9)
    override val upgradeHolder = getUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.RANGE)
    override val energyHolder = ConsumerEnergyHolder(this, MAX_ENERGY, upgradeHolder = upgradeHolder) { createSideConfig(NetworkConnectionType.INSERT, BlockSide.FRONT, BlockSide.RIGHT, BlockSide.BACK) }
    override val itemHolder = NovaItemHolder(this, inventory to NetworkConnectionType.EXTRACT) { createSideConfig(NetworkConnectionType.EXTRACT, BlockSide.FRONT, BlockSide.RIGHT, BlockSide.BACK) }
    
    private val entityId = uuid.hashCode()
    
    private var sizeX = retrieveData("sizeX") { DEFAULT_SIZE_X }
    private var sizeZ = retrieveData("sizeZ") { DEFAULT_SIZE_Z }
    private var sizeY = retrieveData("sizeY") { DEFAULT_SIZE_Y }
    
    private var energyPerTick by Delegates.notNull<Int>()
    
    private val solidScaffolding = MovableMultiModel()
    private val armX = MovableMultiModel()
    private val armZ = MovableMultiModel()
    private val armY = MovableMultiModel()
    private val drill = MovableMultiModel()
    
    private var maxSize = 0
    private var drillSpeedMultiplier = 0.0
    private var moveSpeed = 0.0
    
    private val y = location.blockY
    private var minX = 0
    private var minZ = 0
    private var maxX = 0
    private var maxZ = 0
    private val minY: Int
        get() = max(world.minHeight, y - 1 - sizeY)
    
    private val minBreakX: Int
        get() = minX + 1
    private val minBreakY: Int
        get() = minY + 1
    private val minBreakZ: Int
        get() = minZ + 1
    private val maxBreakX: Int
        get() = maxX - 1
    private val maxBreakY: Int
        get() = y - 2
    private val maxBreakZ: Int
        get() = maxZ - 1
    
    private lateinit var lastPointerLocation: Location
    private lateinit var pointerLocation: Location
    private var pointerDestination: Location? = retrieveDataOrNull("pointerDestination")
    
    private var drillProgress = retrieveData("drillProgress") { 0.0 }
    private var drilling = retrieveData("drilling") { false }
    private var done = retrieveData("done") { false }
    
    private val energySufficiency: Double
        get() = min(1.0, energyHolder.energy.toDouble() / energyPerTick.toDouble())
    
    private val currentMoveSpeed: Double
        get() = moveSpeed * energySufficiency
    
    private val currentDrillSpeedMultiplier: Double
        get() = drillSpeedMultiplier * energySufficiency
    
    init {
        reload()
    }
    
    override fun handleInitialized(first: Boolean) {
        super.handleInitialized(first)
        
        updateBounds(first)
        pointerLocation = retrieveDataOrNull("pointerLocation") ?: Location(world, minX + 1.5, y - 2.0, minZ + 1.5)
        lastPointerLocation = retrieveDataOrNull("lastPointerLocation") ?: Location(world, 0.0, 0.0, 0.0)
        createScaffolding()
    }
    
    override fun reload() {
        super.reload()
        
        updateEnergyPerTick()
        
        maxSize = MAX_SIZE + upgradeHolder.getValue(UpgradeTypes.RANGE)
        drillSpeedMultiplier = DRILL_SPEED_MULTIPLIER * upgradeHolder.getValue(UpgradeTypes.SPEED)
        moveSpeed = MOVE_SPEED * upgradeHolder.getValue(UpgradeTypes.SPEED)
    }
    
    override fun handleRemoved(unload: Boolean) {
        super.handleRemoved(unload)
        solidScaffolding.close()
        armX.close()
        armZ.close()
        armY.close()
        drill.close()
    }
    
    private fun updateBounds(checkPermission: Boolean): Boolean {
        val positions = getMinMaxPositions(location, sizeX, sizeZ, getFace(BlockSide.BACK), getFace(BlockSide.RIGHT))
        minX = positions[0]
        minZ = positions[1]
        maxX = positions[2]
        maxZ = positions[3]
        
        updateEnergyPerTick()
        
        if (owner == null || (checkPermission && !canBreak(owner!!, location, positions).get())) {
            if (sizeX == MIN_SIZE && sizeZ == MIN_SIZE) {
                BlockManager.breakBlockState(BlockBreakContext(pos, this, location))
                return false
            } else resize(MIN_SIZE, MIN_SIZE)
        }
        
        return true
    }
    
    private fun resize(sizeX: Int, sizeZ: Int) {
        this.sizeX = sizeX
        this.sizeZ = sizeZ
        
        if (updateBounds(true)) {
            drilling = false
            drillProgress = 0.0
            done = false
            pointerDestination = null
            pointerLocation = Location(world, minX + 1.5, y - 2.0, minZ + 1.5)
            
            solidScaffolding.clear()
            armX.clear()
            armY.clear()
            armZ.clear()
            drill.clear()
            
            createScaffolding()
        }
    }
    
    private fun updateEnergyPerTick() {
        energyPerTick = ((BASE_ENERGY_CONSUMPTION + sizeX * sizeZ * ENERGY_PER_SQUARE_BLOCK)
            * upgradeHolder.getValue(UpgradeTypes.SPEED) / upgradeHolder.getValue(UpgradeTypes.EFFICIENCY)).toInt()
    }
    
    override fun saveData() {
        super.saveData()
        storeData("sizeX", sizeX)
        storeData("sizeZ", sizeZ)
        storeData("sizeY", sizeY)
        if (::pointerLocation.isInitialized) storeData("pointerLocation", pointerLocation)
        if (::lastPointerLocation.isInitialized) storeData("lastPointerLocation", lastPointerLocation)
        storeData("pointerDestination", pointerDestination)
        storeData("drillProgress", drillProgress)
        storeData("drilling", drilling)
        storeData("done", done)
    }
    
    override fun handleTick() {
        if (energyHolder.energy == 0L) return
        
        if (!done || serverTick % 300 == 0) {
            if (!drilling) {
                val pointerDestination = pointerDestination ?: selectNextDestination()
                if (pointerDestination != null) {
                    done = false
                    if (pointerLocation.distance(pointerDestination) > 0.2) {
                        moveToPointer(pointerDestination)
                    } else {
                        pointerLocation = pointerDestination.clone()
                        pointerDestination.y -= 1
                        drilling = true
                    }
                } else done = true
            } else drill()
            
            energyHolder.energy -= energyPerTick
        }
        
    }
    
    override fun handleAsyncTick() {
        if (!done && energyHolder.energy != 0L)
            updatePointer()
    }
    
    private fun moveToPointer(pointerDestination: Location) {
        val deltaX = pointerDestination.x - pointerLocation.x
        val deltaY = pointerDestination.y - pointerLocation.y
        val deltaZ = pointerDestination.z - pointerLocation.z
        
        var moveX = 0.0
        var moveY = 0.0
        var moveZ = 0.0
        
        val moveSpeed = currentMoveSpeed
        
        if (deltaY > 0) {
            moveY = deltaY.coerceIn(-moveSpeed, moveSpeed)
        } else {
            var distance = 0.0
            moveX = deltaX.coerceIn(-moveSpeed, moveSpeed)
            distance += moveX
            moveZ = deltaZ.coerceIn(-(moveSpeed - distance), moveSpeed - distance)
            distance += moveZ
            if (distance == 0.0) moveY = deltaY.coerceIn(-moveSpeed, moveSpeed)
        }
        
        pointerLocation.add(moveX, moveY, moveZ)
    }
    
    private fun drill() {
        val block = pointerDestination!!.block
        
        // calculate and add damage
        val damage = ToolUtils.calculateDamage(
            block.hardness,
            correctCategory = true,
            correctForDrops = true,
            toolMultiplier = currentDrillSpeedMultiplier,
            efficiency = 0,
            onGround = true,
            underWater = false,
            hasteLevel = 0,
            fatigueLevel = 0
        ).coerceAtMost(DRILL_SPEED_CLAMP)
        drillProgress = min(1.0, drillProgress + damage)
        
        // lower the drill
        pointerLocation.y = pointerDestination!!.y + 1 - drillProgress
        // particle effects
        spawnDrillParticles(block)
        
        if (drillProgress >= 1) { // is done drilling
            val ctx = BlockBreakContext(block.pos, this, location, BlockFace.UP)
            val drops = block.getAllDrops(ctx).toMutableList()
            NovaEventFactory.callTileEntityBlockBreakEvent(this, block, drops)
            
            if (!GlobalValues.DROP_EXCESS_ON_GROUND && !inventory.canHold(drops))
                return
            
            block.setBreakStage(entityId, -1)
            block.remove(ctx)
            
            drops.forEach { drop ->
                val leftover = inventory.addItem(null, drop)
                if (GlobalValues.DROP_EXCESS_ON_GROUND && leftover != 0) {
                    drop.amount = leftover
                    world.dropItemNaturally(block.location, drop)
                }
            }
            
            pointerDestination = null
            drillProgress = 0.0
            drilling = false
        } else {
            block.setBreakStage(entityId, (drillProgress * 9).roundToInt())
        }
    }
    
    private fun updatePointer(force: Boolean = false) {
        val pointerLocation = pointerLocation.clone()
        
        if (force || lastPointerLocation.z != pointerLocation.z)
            armX.use { it.teleport { z = pointerLocation.z } }
        if (force || lastPointerLocation.x != pointerLocation.x)
            armZ.use { it.teleport { x = pointerLocation.x } }
        if (force || lastPointerLocation.x != pointerLocation.x || lastPointerLocation.z != pointerLocation.z)
            armY.use { it.teleport { x = pointerLocation.x; z = pointerLocation.z } }
        
        if (force || lastPointerLocation.y != pointerLocation.y) {
            for (y in y - 1 downTo pointerLocation.blockY + 1) {
                val location = pointerLocation.clone()
                location.y = y.toDouble() + .5
                if (armY.itemDisplays.none { it.location == location })
                    armY.add(Model(FULL_SLIM_VERTICAL, location))
            }
            armY.removeIf { armorStand -> armorStand.location.blockY - 1 < pointerLocation.blockY }
        }
        
        drill.use {
            val location = pointerLocation.clone().apply {
                y += 1.5
                yaw = it.location.yaw.mod(360f)
                yaw += if (drilling)
                    25f * (2 - drillProgress.toFloat())
                else 10f
            }
            it.teleport(location)
        }
        
        lastPointerLocation = pointerLocation
    }
    
    private fun selectNextDestination(): Location? {
        var radius = -1
        val results = ArrayList<Location>()
        
        do {
            radius++
            
            val minX = max(pointerLocation.blockX - radius, minBreakX)
            val minZ = max(pointerLocation.blockZ - radius, minBreakZ)
            val maxX = min(pointerLocation.blockX + radius, maxBreakX)
            val maxZ = min(pointerLocation.blockZ + radius, maxBreakZ)
            
            for (x in minX..maxX) {
                for (z in minZ..maxZ) {
                    if (x != minX && x != maxX && z != minZ && z != maxZ) continue
                    
                    val topLoc = LocationUtils.getTopBlockBetween(world, x, z, maxBreakY, minBreakY)
                    if (topLoc != null
                        && topLoc.block.hardness >= 0
                        && ProtectionManager.canBreak(this, null, topLoc).get()
                    ) {
                        results += topLoc
                    }
                }
            }
            
        } while (
            (results.isEmpty() || radius <= 0) // only take results (if available) when radius > 0
            && !(minX == minBreakX && minZ == minBreakZ && maxX == maxBreakX && maxZ == maxBreakZ) // break loop when the region cannot expand
        )
        
        val destination = results
            .minByOrNull { prioritizedDistance(pointerLocation, it) }
            ?.add(0.5, 1.0, 0.5)
        pointerDestination = destination
        
        return destination
    }
    
    /**
     * Returns the square of a modified distance that discourages travelling downwards
     * and encourages travelling upwards.
     */
    private fun prioritizedDistance(location: Location, destination: Location): Double {
        val deltaX = destination.x - location.x
        val deltaZ = destination.z - location.z
        
        // encourage travelling up, discourage travelling down
        var deltaY = (destination.y - location.y)
        if (deltaY > 0) deltaY *= 0.05
        else if (deltaY < 0) deltaY *= 2
        
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ
    }
    
    private fun spawnDrillParticles(block: Block) {
        // block cracks
        particle(ParticleTypes.BLOCK, block.location.center().apply { y += 1 }) {
            block(block.breakTexture)
            offsetX(0.2f)
            offsetZ(0.2f)
            speed(0.5f)
        }.sendTo(getViewers())
        
        // smoke
        particle(ParticleTypes.SMOKE, pointerLocation.clone().apply { y -= 0.1 }) {
            amount(10)
            speed(0.02f)
        }.sendTo(getViewers())
    }
    
    private fun createScaffolding() {
        createScaffoldingOutlines()
        createScaffoldingCorners()
        createScaffoldingPillars()
        createScaffoldingArms()
        drill.add(Model(DRILL, pointerLocation, translation = Vector3f(0f, -1f, 0f))) // translation fixes light issues, as entity is never inside block
        updatePointer(true)
    }
    
    private fun createScaffoldingOutlines() {
        val min = Location(location.world, minX.toDouble(), location.y, minZ.toDouble())
        val max = Location(location.world, maxX.toDouble(), location.y, maxZ.toDouble())
        
        min.getRectangle(max, true).forEach { (axis, locations) ->
            locations.forEach { createHorizontalScaffolding(solidScaffolding, it, axis) }
        }
    }
    
    private fun createScaffoldingArms() {
        val baseLocation = pointerLocation.clone().also { it.y = y.toDouble() + .5; }
        
        val armXLocations = LocationUtils.getStraightLine(baseLocation, Axis.X, minX..maxX)
        armXLocations.withIndex().forEach { (index, location) ->
            location.x += 0.5
            if (index == 0 || index == armXLocations.size - 1) {
                createSmallHorizontalScaffolding(
                    armX,
                    location.apply { yaw = if (index == 0) 180f else 0f },
                    Axis.X
                )
            } else {
                createHorizontalScaffolding(armX, location, Axis.X, false)
            }
        }
        
        val armZLocations = LocationUtils.getStraightLine(baseLocation, Axis.Z, minZ..maxZ)
        armZLocations.withIndex().forEach { (index, location) ->
            location.z += 0.5
            if (index == 0 || index == armZLocations.size - 1) {
                createSmallHorizontalScaffolding(armZ,
                    location.apply { yaw = if (index == 0) 0f else 180f },
                    Axis.Z
                )
            } else {
                createHorizontalScaffolding(armZ, location, Axis.Z, false)
            }
        }
        
        armY.add(Model(SLIM_VERTICAL_DOWN, baseLocation.clone()))
    }
    
    private fun createScaffoldingPillars() {
        for (corner in getCornerLocations(location.y)) {
            corner.y -= 1
            
            val blockBelow = corner.getNextBlockBelow(countSelf = true, requiresSolid = true)
            if (blockBelow != null && blockBelow.positionEquals(corner)) continue
            
            corner
                .getStraightLine(Axis.Y, (blockBelow?.blockY ?: world.minHeight) + 1)
                .forEach { createVerticalScaffolding(solidScaffolding, it) }
        }
    }
    
    private fun createScaffoldingCorners() {
        val y = location.y
        
        val corners = getCornerLocations(y)
            .filterNot { it.blockLocation == location }
            .map { it.add(.5, .5, .5) }
        
        solidScaffolding.addAll(corners.map { Model(CORNER_DOWN, it) })
    }
    
    private fun getCornerLocations(y: Double) =
        listOf(
            Location(world, maxX.toDouble(), y, maxZ.toDouble(), 180f, 0f),
            Location(world, minX.toDouble(), y, maxZ.toDouble(), 270f, 0f),
            Location(world, maxX.toDouble(), y, minZ.toDouble(), 90f, 0f),
            Location(world, minX.toDouble(), y, minZ.toDouble(), 0f, 0f),
        )
    
    private fun createSmallHorizontalScaffolding(model: MultiModel, location: Location, axis: Axis) {
        location.yaw += if (axis == Axis.Z) 0f else 90f
        model.add(Model(SMALL_HORIZONTAL, location))
    }
    
    private fun createHorizontalScaffolding(model: MultiModel, location: Location, axis: Axis, center: Boolean = true) {
        location.yaw = if (axis == Axis.Z) 180f else -90f
        model.add(Model(FULL_HORIZONTAL, if (center) location.add(.5, .5, .5) else location))
    }
    
    private fun createVerticalScaffolding(model: MultiModel, location: Location) {
        model.add(Model(FULL_VERTICAL, location.add(.5, .5, .5)))
    }
    
    companion object {
        
        fun canPlace(player: Player, item: ItemStack, location: Location): CompletableFuture<Boolean> {
            val positions = getMinMaxPositions(
                location,
                MIN_SIZE, MIN_SIZE,
                BlockSide.BACK.getBlockFace(location.yaw),
                BlockSide.RIGHT.getBlockFace(location.yaw)
            )
            
            val minLoc = Location(location.world, positions[0].toDouble(), location.y, positions[1].toDouble())
            val maxLoc = Location(location.world, positions[2].toDouble(), location.y, positions[3].toDouble())
            return CombinedBooleanFuture(minLoc.getFullCuboid(maxLoc).map { ProtectionManager.canPlace(player, item, it) })
        }
        
        private fun canBreak(owner: OfflinePlayer?, location: Location, positions: IntArray): CompletableFuture<Boolean> {
            if (owner == null) return CompletableFuture.completedFuture(true)
            val minLoc = Location(location.world, positions[0].toDouble(), location.y, positions[1].toDouble())
            val maxLoc = Location(location.world, positions[2].toDouble(), location.y, positions[3].toDouble())
            return CombinedBooleanFuture(minLoc.getFullCuboid(maxLoc).map { ProtectionManager.canBreak(owner, null, it) })
        }
        
        private fun getMinMaxPositions(location: Location, sizeX: Int, sizeZ: Int, back: BlockFace, right: BlockFace): IntArray {
            val modX = back.modX.takeUnless { it == 0 } ?: right.modX
            val modZ = back.modZ.takeUnless { it == 0 } ?: right.modZ
            
            val distanceX = modX * (sizeX + 1)
            val distanceZ = modZ * (sizeZ + 1)
            
            val minX = min(location.blockX, location.blockX + distanceX)
            val minZ = min(location.blockZ, location.blockZ + distanceZ)
            val maxX = max(location.blockX, location.blockX + distanceX)
            val maxZ = max(location.blockZ, location.blockZ + distanceZ)
            
            return intArrayOf(minX, minZ, maxX, maxZ)
        }
        
    }
    
    @TileEntityMenuClass
    inner class QuarryMenu : GlobalTileEntityMenu() {
        
        private val sideConfigGui = SideConfigMenu(
            this@Quarry,
            listOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default"),
            ::openWindow
        )
        
        private val sizeItems = ArrayList<Item>()
        private val depthItems = ArrayList<Item>()
        
        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| s u # # # # e |",
                "| # # # i i i e |",
                "| m n p i i i e |",
                "| M N P i i i e |",
                "3 - - - - - - - 4")
            .addIngredient('i', inventory)
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('m', RemoveNumberItem({ MIN_SIZE..maxSize }, { sizeX }, ::setSize).also(sizeItems::add))
            .addIngredient('n', SizeDisplayItem { sizeX }.also(sizeItems::add))
            .addIngredient('p', AddNumberItem({ MIN_SIZE..maxSize }, { sizeX }, ::setSize).also(sizeItems::add))
            .addIngredient('M', RemoveNumberItem({ MIN_DEPTH..MAX_DEPTH }, { sizeY }, ::setDepth).also(depthItems::add))
            .addIngredient('N', DepthDisplayItem { sizeY }.also(depthItems::add))
            .addIngredient('P', AddNumberItem({ MIN_DEPTH..MAX_DEPTH }, { sizeY }, ::setDepth).also(depthItems::add))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('e', EnergyBar(4, energyHolder))
            .build()
        
        private fun setSize(size: Int) {
            resize(size, size)
            sizeItems.forEach(Item::notifyWindows)
        }
        
        private fun setDepth(depth: Int) {
            sizeY = depth
            done = false
            depthItems.forEach(Item::notifyWindows)
        }
        
        private inner class SizeDisplayItem(private val getNumber: () -> Int) : AbstractItem() {
            
            override fun getItemProvider(): ItemProvider {
                val number = getNumber()
                return DefaultGuiItems.NUMBER.model.createItemBuilder(getNumber())
                    .setDisplayName(Component.translatable("menu.machines.quarry.size", Component.text(number), Component.text(number)))
                    .addLoreLines(Component.translatable("menu.machines.quarry.size_tip", NamedTextColor.GRAY))
            }
            
            override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) = Unit
            
        }
        
        private inner class DepthDisplayItem(private val getNumber: () -> Int) : AbstractItem() {
            
            override fun getItemProvider(): ItemProvider {
                val number = getNumber()
                return DefaultGuiItems.NUMBER.model.createItemBuilder(getNumber())
                    .setDisplayName(Component.translatable("menu.machines.quarry.depth", Component.text(number)))
                    .addLoreLines(Component.translatable("menu.machines.quarry.depth_tip", NamedTextColor.GRAY))
            }
            
            override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) = Unit
            
        }
        
    }
    
}