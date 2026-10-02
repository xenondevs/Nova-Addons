package xyz.xenondevs.nova.addon.machines.tileentity.world

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.CustomModelData.customModelData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.core.particles.ParticleTypes
import org.bukkit.Axis
import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.inventory.ItemStack
import org.joml.Quaternionf
import org.joml.Vector3f
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.provider.combinedProvider
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.commons.provider.provider
import xyz.xenondevs.invui.dsl.gui
import xyz.xenondevs.invui.dsl.item
import xyz.xenondevs.nova.addon.machines.registry.Blocks.QUARRY
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.Models
import xyz.xenondevs.nova.addon.machines.util.addDisplay
import xyz.xenondevs.nova.addon.machines.util.rangeAffectedValue
import xyz.xenondevs.nova.addon.machines.util.speedMultipliedValue
import xyz.xenondevs.nova.addon.simpleupgrades.openUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.api.NovaEventFactory
import xyz.xenondevs.nova.config.GlobalValues
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.BlockBreak
import xyz.xenondevs.nova.context.intention.BlockPlace
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.network.sendTo
import xyz.xenondevs.nova.packetentity.PacketItemDisplay
import xyz.xenondevs.nova.packetentity.clearAndDespawn
import xyz.xenondevs.nova.packetentity.removeAndDespawnIf
import xyz.xenondevs.nova.packetentity.teleport
import xyz.xenondevs.nova.packetentity.updateMetadata
import xyz.xenondevs.nova.world.block.config
import xyz.xenondevs.nova.ui.menu.energyBar
import xyz.xenondevs.nova.ui.menu.item.addNumberItem
import xyz.xenondevs.nova.ui.menu.item.removeNumberItem
import xyz.xenondevs.nova.ui.menu.itemProvider
import xyz.xenondevs.nova.ui.menu.sideconfig.openSideConfigItem
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.BlockSideSet
import xyz.xenondevs.nova.util.BlockUtils
import xyz.xenondevs.nova.util.LocationUtils
import xyz.xenondevs.nova.util.center
import xyz.xenondevs.nova.util.getNextBlockBelow
import xyz.xenondevs.nova.util.getRectangle
import xyz.xenondevs.nova.util.getStraightLine
import xyz.xenondevs.nova.util.item.ToolUtils
import xyz.xenondevs.nova.util.particle.block
import xyz.xenondevs.nova.util.particle.particle
import xyz.xenondevs.nova.util.positionEquals
import xyz.xenondevs.nova.util.serverTick
import xyz.xenondevs.nova.util.setBreakStage
import xyz.xenondevs.nova.util.toVector3f
import xyz.xenondevs.nova.world.block.behavior.BlockBehavior
import xyz.xenondevs.nova.world.block.NovaBlockState
import xyz.xenondevs.nova.world.block.blockType
import xyz.xenondevs.nova.world.block.state.property.DefaultBlockStateProperties
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.TileEntityMenu
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.item.DefaultGuiItems
import xyz.xenondevs.nova.world.item.guiItemProvider
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private val BLOCKED_SIDES = BlockSideSet(front = true)

private val MIN_SIZE by QUARRY.config.entry<Int>("min_size")
private val MAX_SIZE = QUARRY.config.entry<Int>("max_size")
private val MIN_DEPTH by QUARRY.config.entry<Int>("min_depth")
private val MAX_DEPTH by QUARRY.config.entry<Int>("max_depth")
private val DEFAULT_SIZE_X by QUARRY.config.entry<Int>("default_size_x")
private val DEFAULT_SIZE_Z by QUARRY.config.entry<Int>("default_size_z")
private val DEFAULT_SIZE_Y by QUARRY.config.entry<Int>("default_size_y")

private val MOVE_SPEED = QUARRY.config.entry<Double>("move_speed")
private val DRILL_SPEED_MULTIPLIER = QUARRY.config.entry<Double>("drill_speed_multiplier")
private val DRILL_SPEED_CLAMP by QUARRY.config.entry<Double>("drill_speed_clamp")

private val MAX_ENERGY = QUARRY.config.entry<Long>("capacity")
private val BASE_ENERGY_CONSUMPTION = QUARRY.config.entry<Int>("base_energy_consumption")
private val ENERGY_PER_SQUARE_BLOCK = QUARRY.config.entry<Int>("energy_consumption_per_square_block")

class Quarry(pos: Block, blockState: NovaBlockState, compound: Compound) : NetworkedTileEntity(pos, blockState, compound) {
    
    private val inventory = storedInventory("quarryInventory", 9)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.RANGE)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(inventory to EXTRACT, blockedSides = BLOCKED_SIDES)
    
    private var sizeXZProvider = storedValue("sizeX") { DEFAULT_SIZE_X }
    private var sizeXZ by sizeXZProvider
    private val sizeYProvider = storedValue("sizeY") { DEFAULT_SIZE_Y }
    private var sizeY by sizeYProvider
    private val menuSize = mutableProvider(sizeXZ)
    private val menuDepth = mutableProvider(sizeY)
    
    private val solidScaffolding = ArrayList<PacketItemDisplay>()
    private val armX = ArrayList<PacketItemDisplay>()
    private val armZ = ArrayList<PacketItemDisplay>()
    private val armY = ArrayList<PacketItemDisplay>()
    private val drill = ArrayList<PacketItemDisplay>()
    
    private val energyPerTick by combinedProvider(
        BASE_ENERGY_CONSUMPTION,
        sizeXZProvider,
        ENERGY_PER_SQUARE_BLOCK,
        upgradeHolder.getValueProvider(UpgradeTypes.SPEED),
        upgradeHolder.getValueProvider(UpgradeTypes.EFFICIENCY)
    ).map { (base, sizeXZ, perSqr, speed, eff) -> (base + sizeXZ * sizeXZ * perSqr * speed / eff).roundToInt() }
    private val maxSizeProvider = rangeAffectedValue(MAX_SIZE, upgradeHolder)
    private val maxSize by maxSizeProvider
    private val drillSpeedMultiplier by speedMultipliedValue(DRILL_SPEED_MULTIPLIER, upgradeHolder)
    private val moveSpeed by speedMultipliedValue(MOVE_SPEED, upgradeHolder)
    
    private var minX = 0
    private var minZ = 0
    private var maxX = 0
    private var maxZ = 0
    private val minY: Int
        get() = max(block.world.minHeight, block.y - 1 - sizeY)
    
    private val minBreakX: Int
        get() = minX + 1
    private val minBreakY: Int
        get() = minY + 1
    private val minBreakZ: Int
        get() = minZ + 1
    private val maxBreakX: Int
        get() = maxX - 1
    private val maxBreakY: Int
        get() = block.y - 2
    private val maxBreakZ: Int
        get() = maxZ - 1
    
    private var lastPointerLocation by storedValue("lastPointerLocation") { Location(block.world, 0.0, 0.0, 0.0) }
    private var pointerLocation by storedValue("pointerLocation") { Location(block.world, minX + 1.5, block.y - 2.0, minZ + 1.5) }
    private var pointerDestination: Location? by storedValue("pointerDestination")
    private var drillProgress by storedValue("drillProgress") { 0.0 }
    private var drilling by storedValue("drilling") { false }
    private var done by storedValue("done") { false }
    
    private val energySufficiency: Double
        get() = min(1.0, energyHolder.energy.toDouble() / energyPerTick.toDouble())
    private val currentMoveSpeed: Double
        get() = moveSpeed * energySufficiency
    private val currentDrillSpeedMultiplier: Double
        get() = drillSpeedMultiplier * energySufficiency
    private val sizeRange = maxSizeProvider.map { MIN_SIZE..it }
    private val depthRange = provider(MIN_DEPTH..MAX_DEPTH)
    
    override val menu = TileEntityMenu.cachedWindow(GuiTextures.GENERIC_3X3_WITH_BAR) {
        upperGui by gui(
            "s . u i i i . . e",
            "m n p i i i . . e",
            "M N P i i i . . e",
        ) {
            'i' by inventory
            's' by openSideConfigItem(mapOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default"))
            'm' by removeNumberItem(sizeRange, menuSize)
            'n' by item {
                itemProvider by itemProvider(DefaultGuiItems.TP_NUMBER.guiItemProvider) {
                    data[DataComponentTypes.CUSTOM_MODEL_DATA] by sizeXZProvider.map {
                        customModelData().addFloat(it.toFloat()).build()
                    }
                    name by sizeXZProvider.map { Component.translatable("menu.machines.quarry.size", Component.text(it), Component.text(it)) }
                    lore by listOf(Component.translatable("menu.machines.quarry.size_tip", NamedTextColor.GRAY))
                }
            }
            'p' by addNumberItem(sizeRange, menuSize)
            'M' by removeNumberItem(depthRange, menuDepth)
            'N' by item {
                itemProvider by itemProvider(DefaultGuiItems.TP_NUMBER.guiItemProvider) {
                    data[DataComponentTypes.CUSTOM_MODEL_DATA] by sizeYProvider.map {
                        customModelData().addFloat(it.toFloat()).build()
                    }
                    name by sizeYProvider.map { Component.translatable("menu.machines.quarry.depth", Component.text(it)) }
                    lore by listOf(Component.translatable("menu.machines.quarry.depth_tip", NamedTextColor.GRAY))
                }
            }
            'P' by addNumberItem(depthRange, menuDepth)
            'u' by openUpgradesItem(upgradeHolder)
            'e' by energyBar(energyHolder)
        }
    }
    
    init {
        maxSizeProvider.subscribe { resize(sizeXZ.coerceIn(MIN_SIZE, it)) }
        menuSize.subscribe(::resize)
        menuDepth.subscribe {
            sizeY = it
            done = false
        }
    }
    
    override fun handleEnable() {
        super.handleEnable()
        updateBounds(true)
        createScaffolding()
    }
    
    override fun handleDisable() {
        super.handleDisable()
        
        solidScaffolding.clearAndDespawn()
        armX.clearAndDespawn()
        armZ.clearAndDespawn()
        armY.clearAndDespawn()
        drill.clearAndDespawn()
        
        // reset break stage of current block
        pointerDestination?.block?.setBreakStage(uuid.hashCode(), -1)
    }
    
    private fun updateBounds(checkPermission: Boolean): Boolean {
        val facing = blockState.getOrThrow(DefaultBlockStateProperties.FACING_HORIZONTAL)
        val (minX, minZ, maxX, maxZ) = getMinMaxPositions(
            block,
            sizeXZ, sizeXZ,
            BlockSide.BACK.getBlockFace(facing), BlockSide.RIGHT.getBlockFace(facing)
        )
        this.minX = minX
        this.maxX = maxX
        this.minZ = minZ
        this.maxZ = maxZ
        
        if (owner == null || (checkPermission && runBlocking { !canBreak(owner!!, block, minX, maxX, minZ, maxZ) })) { // TODO: non-blocking
            if (sizeXZ == MIN_SIZE) {
                val ctx = Context.intention(BlockBreak)
                    .param(BlockBreak.BLOCK, block)
                    .build()
                BlockUtils.breakBlockNaturally(ctx)
                return false
            } else resize(MIN_SIZE)
        }
        
        return true
    }
    
    private fun resize(sizeXZ: Int) {
        if (this.sizeXZ == sizeXZ)
            return
        this.sizeXZ = sizeXZ
        if (menuSize.get() != sizeXZ) menuSize.set(sizeXZ)
        
        if (updateBounds(true)) {
            drilling = false
            drillProgress = 0.0
            done = false
            pointerDestination = null
            pointerLocation = Location(block.world, minX + 1.5, block.y - 2.0, minZ + 1.5)
            
            solidScaffolding.clearAndDespawn()
            armX.clearAndDespawn()
            armY.clearAndDespawn()
            armZ.clearAndDespawn()
            drill.clearAndDespawn()
            
            createScaffolding()
        }
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
    
    override fun handleEnableTicking() {
        CoroutineScope(coroutineSupervisor!!).launch {
            while (true) {
                if (!done && energyHolder.energy != 0L)
                    updatePointer()
                delay(50.milliseconds)
            }
        }
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
            block.blockType.hardness.toDouble(),
            correctForDrops = true,
            speed = currentDrillSpeedMultiplier
        ).coerceAtMost(DRILL_SPEED_CLAMP)
        drillProgress = min(1.0, drillProgress + damage)
        
        // lower the drill
        pointerLocation.y = pointerDestination!!.y + 1 - drillProgress
        // particle effects
        spawnDrillParticles(block)
        
        if (drillProgress >= 1) { // is done drilling
            val ctx = Context.intention(BlockBreak)
                .param(BlockBreak.BLOCK, block)
                .param(BlockBreak.SOURCE_TILE_ENTITY, this)
                .param(BlockBreak.BLOCK_DROPS, true)
                .build()
            val drops = BlockUtils.getDrops(ctx).toMutableList()
            NovaEventFactory.callTileEntityBlockBreakEvent(this, block, drops)
            
            if (!GlobalValues.DROP_EXCESS_ON_GROUND && !inventory.canHold(drops))
                return
            
            block.setBreakStage(uuid.hashCode(), -1)
            BlockUtils.breakBlock(ctx)
            
            drops.forEach { drop ->
                val leftover = inventory.addItem(null, drop)
                if (GlobalValues.DROP_EXCESS_ON_GROUND && leftover != 0) {
                    drop.amount = leftover
                    block.world.dropItemNaturally(block.location, drop)
                }
            }
            
            pointerDestination = null
            drillProgress = 0.0
            drilling = false
        } else {
            block.setBreakStage(uuid.hashCode(), (drillProgress * 9).roundToInt())
        }
    }
    
    private fun updatePointer(force: Boolean = false) {
        val pointerLocation = pointerLocation.clone()
        
        // move arm x
        if (force || lastPointerLocation.z != pointerLocation.z) {
            armX.updateMetadata { posRotInterpolationDuration = 1 }
            armX.teleport {
                z = pointerLocation.z
            }
        }
        
        // move arm z
        if (force || lastPointerLocation.x != pointerLocation.x) {
            armZ.updateMetadata { posRotInterpolationDuration = 1 }
            armZ.teleport {
                x = pointerLocation.x
            }
        }
        
        // extend / retract arm y
        var extended = false
        if (force || lastPointerLocation.y != pointerLocation.y) {
            // extend
            for (y in (block.y - 1) downTo (pointerLocation.blockY + 1)) {
                val armYLocation = Location(block.world, pointerLocation.x, y + 0.5, pointerLocation.z)
                if (armY.none { it.location.y == armYLocation.y }) {
                    armY.addDisplay(
                        Models.SCAFFOLDING_FULL_SLIM_VERTICAL,
                        armYLocation
                    )
                    extended = true
                }
            }
            
            // retract
            armY.removeAndDespawnIf { it.location.y < pointerLocation.y + 0.5 }
        }
        
        // move arm y
        if (force || extended || lastPointerLocation.x != pointerLocation.x || lastPointerLocation.z != pointerLocation.z) {
            armY.updateMetadata { posRotInterpolationDuration = 1 }
            armY.teleport {
                x = pointerLocation.x
                z = pointerLocation.z
            }
        }
        
        // move and rotate drill
        val rotAngle = if (drilling) 25 * (2 - drillProgress) else 0.0
        drill.teleport {
            x = pointerLocation.x
            y = pointerLocation.y + 0.5
            z = pointerLocation.z
        }
        drill.updateMetadata {
            posRotInterpolationDuration = 1
            transformationInterpolationDuration = 1
            transformationInterpolationStartDeltaTicks = 0
            leftRotation = leftRotation.rotateY(Math.toRadians(rotAngle).toFloat(), Quaternionf())
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
                    
                    val topLoc = LocationUtils.getTopBlockBetween(block.world, x, z, maxBreakY, minBreakY)
                    if (topLoc != null
                        && topLoc.block.blockType.hardness >= 0
                        && runBlocking { ProtectionManager.canBreak(this@Quarry, null, topLoc.block) } // TODO: non-blocking
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
            block(block.blockType)
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
    
    //<editor-fold desc="scaffolding creation">
    private fun createScaffolding() {
        createScaffoldingOutlines()
        createScaffoldingCorners()
        createScaffoldingPillars()
        createScaffoldingArms()
        drill.addDisplay(Models.NETHERITE_DRILL, pointerLocation.clone().add(0.0, 0.5, 0.0))
        
        updatePointer(true)
    }
    
    private fun createScaffoldingOutlines() {
        val min = Location(block.world, minX.toDouble(), block.y.toDouble(), minZ.toDouble())
        val max = Location(block.world, maxX.toDouble(), block.y.toDouble(), maxZ.toDouble())
        
        min.getRectangle(max, true).forEach { (axis, locations) ->
            locations.forEach { createHorizontalScaffolding(solidScaffolding, it, axis) }
        }
    }
    
    private fun createScaffoldingArms() {
        val baseLocation = block.location.add(0.0, 0.5, 0.0)
        
        val armXLocations = LocationUtils.getStraightLine(baseLocation, Axis.X, minX..maxX)
        armXLocations.withIndex().forEach { (index, location) ->
            location.x += 0.5
            if (index == 0 || index == armXLocations.size - 1) {
                createSmallHorizontalScaffolding(armX, location, if (index == 0) Math.PI.toFloat() else 0f, Axis.X)
            } else {
                createHorizontalScaffolding(armX, location, Axis.X, false)
            }
        }
        
        val armZLocations = LocationUtils.getStraightLine(baseLocation, Axis.Z, minZ..maxZ)
        armZLocations.withIndex().forEach { (index, location) ->
            location.z += 0.5
            if (index == 0 || index == armZLocations.size - 1) {
                createSmallHorizontalScaffolding(armZ, location, if (index == 0) Math.PI.toFloat() else 0f, Axis.Z)
            } else {
                createHorizontalScaffolding(armZ, location, Axis.Z, false)
            }
        }
        
        armY.addDisplay(
            Models.SCAFFOLDING_SLIM_VERTICAL_DOWN,
            baseLocation.clone()
        )
    }
    
    private fun createScaffoldingPillars() {
        for (corner in getCornerLocations()) {
            corner.y -= 1
            
            val blockBelow = corner.getNextBlockBelow(countSelf = true, requiresSolid = true)
            if (blockBelow != null && blockBelow.positionEquals(corner)) continue
            
            corner
                .getStraightLine(Axis.Y, (blockBelow?.blockY ?: block.world.minHeight) + 1)
                .forEach { createVerticalScaffolding(solidScaffolding, it) }
        }
    }
    
    private fun createScaffoldingCorners() {
        val corners = getCornerLocations()
            .filterNot { it.block == block }
            .map { it.add(.5, .5, .5) }
        
        corners.forEach { solidScaffolding.addDisplay(Models.SCAFFOLDING_CORNER_DOWN, it) }
    }
    
    private fun getCornerLocations(): List<Location> =
        listOf(
            Location(block.world, maxX.toDouble(), block.y.toDouble(), maxZ.toDouble(), 180f, 0f),
            Location(block.world, minX.toDouble(), block.y.toDouble(), maxZ.toDouble(), 270f, 0f),
            Location(block.world, maxX.toDouble(), block.y.toDouble(), minZ.toDouble(), 90f, 0f),
            Location(block.world, minX.toDouble(), block.y.toDouble(), minZ.toDouble(), 0f, 0f),
        )
    
    private fun createSmallHorizontalScaffolding(model: MutableList<PacketItemDisplay>, location: Location, extraRot: Float, axis: Axis) {
        model.addDisplay(
            Models.SCAFFOLDING_SMALL_HORIZONTAL,
            location,
            leftRotation = Quaternionf().rotateY((if (axis == Axis.Z) Math.PI else Math.PI / -2).toFloat() + extraRot)
        )
    }
    
    private fun createHorizontalScaffolding(model: MutableList<PacketItemDisplay>, location: Location, axis: Axis, center: Boolean = true) {
        model.addDisplay(
            Models.SCAFFOLDING_FULL_HORIZONTAL,
            if (center) location.clone().add(0.5, 0.5, 0.5) else location,
            leftRotation = Quaternionf().rotateY((if (axis == Axis.Z) Math.PI else Math.PI / -2).toFloat())
        )
    }
    
    private fun createVerticalScaffolding(model: MutableList<PacketItemDisplay>, location: Location) {
        model.addDisplay(Models.SCAFFOLDING_FULL_VERTICAL, location.add(.5, .5, .5))
    }
    //</editor-fold>
    
    companion object : BlockBehavior {
        
        override suspend fun canPlace(block: Block, data: NovaBlockState, ctx: Context<BlockPlace>): Boolean {
            val facing = data.getOrThrow(DefaultBlockStateProperties.FACING_HORIZONTAL)
            
            val (minX, minZ, maxX, maxZ) = getMinMaxPositions(
                block,
                MIN_SIZE, MIN_SIZE,
                BlockSide.BACK.getBlockFace(facing),
                BlockSide.RIGHT.getBlockFace(facing)
            )
            
            val itemStack = ctx[BlockPlace.BLOCK_ITEM_STACK] ?: ItemStack.empty()
            val tileEntity = ctx[BlockPlace.SOURCE_TILE_ENTITY]
            val player = ctx[BlockPlace.RESPONSIBLE_PLAYER]
            
            if (tileEntity != null) {
                return checkBlockPermissions(minX, maxX, minZ, maxZ, block.y, block.world) {
                    ProtectionManager.canPlace(tileEntity, itemStack, it)
                }
            } else if (player != null) {
                return checkBlockPermissions(minX, maxX, minZ, maxZ, block.y, block.world) {
                    ProtectionManager.canPlace(player, itemStack, it)
                }
            }
            
            return true
        }
        
        private suspend fun canBreak(
            owner: OfflinePlayer?,
            pos: Block,
            minX: Int, maxX: Int,
            minZ: Int, maxZ: Int
        ): Boolean {
            if (owner == null)
                return true
            
            return checkBlockPermissions(minX, maxX, minZ, maxZ, pos.y, pos.world) {
                ProtectionManager.canBreak(owner, null, it)
            }
        }
        
        private suspend fun checkBlockPermissions(
            minX: Int, maxX: Int,
            minZ: Int, maxZ: Int,
            y: Int, world: World,
            check: suspend (Block) -> Boolean
        ): Boolean {
            for (x in minX..maxX) {
                for (z in minZ..maxZ) {
                    if (!check(world.getBlockAt(x, y, z)))
                        return false
                }
            }
            
            return true
        }
        
        private fun getMinMaxPositions(pos: Block, sizeX: Int, sizeZ: Int, back: BlockFace, right: BlockFace): IntArray {
            val modX = back.modX.takeUnless { it == 0 } ?: right.modX
            val modZ = back.modZ.takeUnless { it == 0 } ?: right.modZ
            
            val distanceX = modX * (sizeX + 1)
            val distanceZ = modZ * (sizeZ + 1)
            
            val minX = min(pos.x, pos.x + distanceX)
            val maxX = max(pos.x, pos.x + distanceX)
            val minZ = min(pos.z, pos.z + distanceZ)
            val maxZ = max(pos.z, pos.z + distanceZ)
            
            return intArrayOf(minX, minZ, maxX, maxZ)
        }
        
    }
    
}
