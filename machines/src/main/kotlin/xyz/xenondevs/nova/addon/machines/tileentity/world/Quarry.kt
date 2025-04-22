package xyz.xenondevs.nova.addon.machines.tileentity.world

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.core.particles.ParticleTypes
import org.bukkit.Axis
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.joml.Quaternionf
import org.joml.Vector3f
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.enumSetOf
import xyz.xenondevs.commons.provider.combinedProvider
import xyz.xenondevs.commons.provider.map
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.item.AbstractItem
import xyz.xenondevs.invui.Click
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.nova.addon.machines.registry.Blocks.QUARRY
import xyz.xenondevs.nova.addon.machines.registry.Models
import xyz.xenondevs.nova.addon.machines.util.rangeAffectedValue
import xyz.xenondevs.nova.addon.machines.util.speedMultipliedValue
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.api.NovaEventFactory
import xyz.xenondevs.nova.config.GlobalValues
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.DefaultContextIntentions
import xyz.xenondevs.nova.context.intention.DefaultContextIntentions.BlockPlace
import xyz.xenondevs.nova.context.param.DefaultContextParamTypes
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.item.AddNumberItem
import xyz.xenondevs.nova.ui.menu.item.RemoveNumberItem
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.BlockUtils
import xyz.xenondevs.nova.util.Location
import xyz.xenondevs.nova.util.LocationUtils
import xyz.xenondevs.nova.util.center
import xyz.xenondevs.nova.util.getNextBlockBelow
import xyz.xenondevs.nova.util.getRectangle
import xyz.xenondevs.nova.util.getStraightLine
import xyz.xenondevs.nova.util.hardness
import xyz.xenondevs.nova.util.item.ToolUtils
import xyz.xenondevs.nova.util.novaBlock
import xyz.xenondevs.nova.util.particle.block
import xyz.xenondevs.nova.util.particle.particle
import xyz.xenondevs.nova.util.positionEquals
import xyz.xenondevs.nova.util.sendTo
import xyz.xenondevs.nova.util.serverTick
import xyz.xenondevs.nova.util.setBreakStage
import xyz.xenondevs.nova.util.toVector3f
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.behavior.BlockBehavior
import xyz.xenondevs.nova.world.block.behavior.Breakable
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.state.property.DefaultBlockStateProperties
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.item.DefaultGuiItems
import xyz.xenondevs.nova.world.model.Model
import xyz.xenondevs.nova.world.model.MovableMultiModel
import xyz.xenondevs.nova.world.model.MultiModel
import xyz.xenondevs.nova.world.pos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val BLOCKED_SIDES = enumSetOf(BlockSide.FRONT)

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

class Quarry(pos: BlockPos, blockState: NovaBlockState, compound: Compound) : NetworkedTileEntity(pos, blockState, compound) {
    
    private val inventory = storedInventory("quarryInventory", 9)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY, UpgradeTypes.RANGE)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(inventory to EXTRACT, blockedSides = BLOCKED_SIDES)
    
    private var sizeXZProvider = storedValue("sizeX") { DEFAULT_SIZE_X }
    private var sizeXZ by sizeXZProvider
    private var sizeY by storedValue("sizeY") { DEFAULT_SIZE_Y }
    
    private val solidScaffolding = MovableMultiModel()
    private val armX = MovableMultiModel()
    private val armZ = MovableMultiModel()
    private val armY = MovableMultiModel()
    private val drill = MovableMultiModel()
    
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
        get() = max(pos.world.minHeight, pos.y - 1 - sizeY)
    
    private val minBreakX: Int
        get() = minX + 1
    private val minBreakY: Int
        get() = minY + 1
    private val minBreakZ: Int
        get() = minZ + 1
    private val maxBreakX: Int
        get() = maxX - 1
    private val maxBreakY: Int
        get() = pos.y - 2
    private val maxBreakZ: Int
        get() = maxZ - 1
    
    private var lastPointerLocation by storedValue("lastPointerLocation") { Location(pos.world, 0.0, 0.0, 0.0) }
    private var pointerLocation by storedValue("pointerLocation") { Location(pos.world, minX + 1.5, pos.y - 2.0, minZ + 1.5) }
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
    
    init {
        maxSizeProvider.subscribe { resize(sizeXZ.coerceIn(MIN_SIZE, it)) }
    }
    
    override fun handleEnable() {
        super.handleEnable()
        updateBounds(true)
        createScaffolding()
    }
    
    override fun handleDisable() {
        super.handleDisable()
        
        // despawn multi-models
        solidScaffolding.clear()
        armX.clear()
        armZ.clear()
        armY.clear()
        drill.clear()
        
        // reset break stage of current block
        pointerDestination?.block?.setBreakStage(uuid.hashCode(), -1)
    }
    
    private fun updateBounds(checkPermission: Boolean): Boolean {
        val facing = blockState.getOrThrow(DefaultBlockStateProperties.FACING)
        val (minX, minZ, maxX, maxZ) = getMinMaxPositions(
            pos,
            sizeXZ, sizeXZ,
            BlockSide.BACK.getBlockFace(facing), BlockSide.RIGHT.getBlockFace(facing)
        )
        this.minX = minX
        this.maxX = maxX
        this.minZ = minZ
        this.maxZ = maxZ
        
        if (owner == null || (checkPermission && runBlocking { !canBreak(owner!!, pos, minX, maxX, minZ, maxZ) })) { // TODO: non-blocking
            if (sizeXZ == MIN_SIZE) {
                val ctx = Context.intention(DefaultContextIntentions.BlockBreak)
                    .param(DefaultContextParamTypes.BLOCK_POS, pos)
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
        
        if (updateBounds(true)) {
            drilling = false
            drillProgress = 0.0
            done = false
            pointerDestination = null
            pointerLocation = Location(pos.world, minX + 1.5, pos.y - 2.0, minZ + 1.5)
            
            solidScaffolding.clear()
            armX.clear()
            armY.clear()
            armZ.clear()
            drill.clear()
            
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
        CoroutineScope(coroutineSupervisor).launch {
            while (true) {
                if (!done && energyHolder.energy != 0L)
                    updatePointer()
                delay(50)
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
            block.hardness,
            correctForDrops = true,
            speed = currentDrillSpeedMultiplier
        ).coerceAtMost(DRILL_SPEED_CLAMP)
        drillProgress = min(1.0, drillProgress + damage)
        
        // lower the drill
        pointerLocation.y = pointerDestination!!.y + 1 - drillProgress
        // particle effects
        spawnDrillParticles(block)
        
        if (drillProgress >= 1) { // is done drilling
            val ctx = Context.intention(DefaultContextIntentions.BlockBreak)
                .param(DefaultContextParamTypes.BLOCK_POS, block.pos)
                .param(DefaultContextParamTypes.SOURCE_TILE_ENTITY, this)
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
                    pos.world.dropItemNaturally(block.location, drop)
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
            armX.useMetadata {
                it.transformationInterpolationDelay = 0
                it.translation = Vector3f(
                    it.translation.x(),
                    it.translation.y(),
                    (pointerLocation.z - pos.z).toFloat()
                )
            }
        }
        
        // move arm z
        if (force || lastPointerLocation.x != pointerLocation.x) {
            armZ.useMetadata {
                it.transformationInterpolationDelay = 0
                it.translation = Vector3f(
                    (pointerLocation.x - pos.x).toFloat(),
                    it.translation.y(),
                    it.translation.z()
                )
            }
        }
        
        // extend / retract arm y
        var extended = false
        if (force || lastPointerLocation.y != pointerLocation.y) {
            // extend
            for (y in (pos.y - 1) downTo (pointerLocation.blockY + 1)) {
                val transY = y + 0.5f - pos.y
                if (armY.itemDisplays.none { it.metadata.translation.y() == transY }) {
                    val entity = armY.add(Model(
                        item = Models.SCAFFOLDING_FULL_SLIM_VERTICAL,
                        location = pos.location,
                        translation = Vector3f(0f, transY, 0f)
                    ))
                    entity.metadata.transformationInterpolationDuration = 1
                    extended = true
                }
            }
            
            // retract
            armY.removeIf { it.metadata.translation.y() < (pointerLocation.y + 0.5 - pos.y) }
        }
        
        // move arm y
        if (force || extended || lastPointerLocation.x != pointerLocation.x || lastPointerLocation.z != pointerLocation.z) {
            armY.useMetadata {
                it.transformationInterpolationDelay = 0
                it.translation = Vector3f(
                    (pointerLocation.x - pos.x).toFloat(),
                    it.translation.y(),
                    (pointerLocation.z - pos.z).toFloat()
                )
            }
        }
        
        // move and rotate drill
        drill.useMetadata {
            it.transformationInterpolationDelay = 0
            it.translation = Vector3f(
                (pointerLocation.x - pos.x).toFloat(),
                (pointerLocation.y - pos.y + 0.5).toFloat(),
                (pointerLocation.z - pos.z).toFloat()
            )
            val rotAngle = if (drilling) 25 * (2 - drillProgress) else 0.0
            it.leftRotation = it.leftRotation.rotateY(Math.toRadians(rotAngle).toFloat(), Quaternionf())
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
                    
                    val topLoc = LocationUtils.getTopBlockBetween(pos.world, x, z, maxBreakY, minBreakY)
                    if (topLoc != null
                        && topLoc.block.hardness >= 0
                        && runBlocking { ProtectionManager.canBreak(this@Quarry, null, topLoc.pos) } // TODO: non-blocking
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
            block(block.novaBlock?.getBehaviorOrNull<Breakable>()?.breakParticles ?: block.type)
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
        drill.add(Model(Models.NETHERITE_DRILL, pos.location))
        
        armX.useMetadata(false) { it.transformationInterpolationDuration = 1 }
        armZ.useMetadata(false) { it.transformationInterpolationDuration = 1 }
        armY.useMetadata(false) { it.transformationInterpolationDuration = 1 }
        drill.useMetadata(false) { it.transformationInterpolationDuration = 1 }
        
        updatePointer(true)
    }
    
    private fun createScaffoldingOutlines() {
        val min = Location(pos.world, minX.toDouble(), pos.y, minZ.toDouble())
        val max = Location(pos.world, maxX.toDouble(), pos.y, maxZ.toDouble())
        
        min.getRectangle(max, true).forEach { (axis, locations) ->
            locations.forEach { createHorizontalScaffolding(solidScaffolding, it, axis) }
        }
    }
    
    private fun createScaffoldingArms() {
        val baseLocation = pos.location.add(0.0, 0.5, 0.0)
        
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
        
        armY.add(Model(Models.SCAFFOLDING_SLIM_VERTICAL_DOWN, baseLocation.clone()))
    }
    
    private fun createScaffoldingPillars() {
        for (corner in getCornerLocations()) {
            corner.y -= 1
            
            val blockBelow = corner.getNextBlockBelow(countSelf = true, requiresSolid = true)
            if (blockBelow != null && blockBelow.positionEquals(corner)) continue
            
            corner
                .getStraightLine(Axis.Y, (blockBelow?.blockY ?: pos.world.minHeight) + 1)
                .forEach { createVerticalScaffolding(solidScaffolding, it) }
        }
    }
    
    private fun createScaffoldingCorners() {
        val corners = getCornerLocations()
            .filterNot { it.pos == pos }
            .map { it.add(.5, .5, .5) }
        
        solidScaffolding.addAll(corners.map { Model(Models.SCAFFOLDING_CORNER_DOWN, it) })
    }
    
    private fun getCornerLocations(): List<Location> =
        listOf(
            Location(pos.world, maxX.toDouble(), pos.y, maxZ.toDouble(), 180f, 0f),
            Location(pos.world, minX.toDouble(), pos.y, maxZ.toDouble(), 270f, 0f),
            Location(pos.world, maxX.toDouble(), pos.y, minZ.toDouble(), 90f, 0f),
            Location(pos.world, minX.toDouble(), pos.y, minZ.toDouble(), 0f, 0f),
        )
    
    private fun createSmallHorizontalScaffolding(model: MultiModel, location: Location, extraRot: Float, axis: Axis) {
        val modelLocation = pos.location
        val translation = location.toVector3f().sub(modelLocation.toVector3f())
        
        model.add(Model(
            item = Models.SCAFFOLDING_SMALL_HORIZONTAL,
            location = modelLocation,
            translation = translation,
            leftRotation = Quaternionf().rotateY((if (axis == Axis.Z) Math.PI else Math.PI / -2).toFloat() + extraRot)
        ))
    }
    
    private fun createHorizontalScaffolding(model: MultiModel, location: Location, axis: Axis, center: Boolean = true) {
        val modelLocation = pos.location
        var translation = location.toVector3f().sub(modelLocation.toVector3f())
        if (center)
            translation.add(0.5f, 0.5f, 0.5f)
        
        model.add(Model(
            item = Models.SCAFFOLDING_FULL_HORIZONTAL,
            location = modelLocation,
            translation = translation,
            leftRotation = Quaternionf().rotateY((if (axis == Axis.Z) Math.PI else Math.PI / -2).toFloat())
        ))
    }
    
    private fun createVerticalScaffolding(model: MultiModel, location: Location) {
        model.add(Model(Models.SCAFFOLDING_FULL_VERTICAL, location.add(.5, .5, .5)))
    }
    
    companion object : BlockBehavior {
        
        override suspend fun canPlace(pos: BlockPos, state: NovaBlockState, ctx: Context<BlockPlace>): Boolean {
            val facing = ctx[DefaultContextParamTypes.BLOCK_STATE_NOVA]?.get(DefaultBlockStateProperties.FACING)
                ?: return false
            
            val (minX, minZ, maxX, maxZ) = getMinMaxPositions(
                pos,
                MIN_SIZE, MIN_SIZE,
                BlockSide.BACK.getBlockFace(facing),
                BlockSide.RIGHT.getBlockFace(facing)
            )
            
            val itemStack = ctx[DefaultContextParamTypes.BLOCK_ITEM_STACK] ?: ItemStack(Material.AIR)
            val tileEntity = ctx[DefaultContextParamTypes.SOURCE_TILE_ENTITY]
            val player = ctx[DefaultContextParamTypes.RESPONSIBLE_PLAYER]
            
            if (tileEntity != null) {
                return checkBlockPermissions(minX, maxX, minZ, maxZ, pos.y, pos.world) {
                    ProtectionManager.canPlace(tileEntity, itemStack, it)
                }
            } else if (player != null) {
                return checkBlockPermissions(minX, maxX, minZ, maxZ, pos.y, pos.world) {
                    ProtectionManager.canPlace(player, itemStack, it)
                }
            }
            
            return true
        }
        
        private suspend fun canBreak(
            owner: OfflinePlayer?,
            pos: BlockPos,
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
            check: suspend (BlockPos) -> Boolean
        ): Boolean {
            for (x in minX..maxX) {
                for (z in minZ..maxZ) {
                    if (!check(BlockPos(world, x, y, z)))
                        return false
                }
            }
            
            return true
        }
        
        private fun getMinMaxPositions(pos: BlockPos, sizeX: Int, sizeZ: Int, back: BlockFace, right: BlockFace): IntArray {
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
    
    @TileEntityMenuClass
    inner class QuarryMenu : GlobalTileEntityMenu() {
        
        private val sideConfigGui = SideConfigMenu(
            this@Quarry,
            mapOf(itemHolder.getNetworkedInventory(inventory) to "inventory.nova.default"),
            ::openWindow
        )
        
        private val sizeItems = ArrayList<Item>()
        private val depthItems = ArrayList<Item>()
        
        override val gui = Gui.builder()
            .setStructure(
                "1 - - - - - - - 2",
                "| s u # # # # e |",
                "| # # # i i i e |",
                "| m n p i i i e |",
                "| M N P i i i e |",
                "3 - - - - - - - 4")
            .addIngredient('i', inventory)
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('m', RemoveNumberItem({ MIN_SIZE..maxSize }, { sizeXZ }, ::setSize).also(sizeItems::add))
            .addIngredient('n', SizeDisplayItem { sizeXZ }.also(sizeItems::add))
            .addIngredient('p', AddNumberItem({ MIN_SIZE..maxSize }, { sizeXZ }, ::setSize).also(sizeItems::add))
            .addIngredient('M', RemoveNumberItem({ MIN_DEPTH..MAX_DEPTH }, { sizeY }, ::setDepth).also(depthItems::add))
            .addIngredient('N', DepthDisplayItem { sizeY }.also(depthItems::add))
            .addIngredient('P', AddNumberItem({ MIN_DEPTH..MAX_DEPTH }, { sizeY }, ::setDepth).also(depthItems::add))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('e', EnergyBar(4, energyHolder))
            .build()
        
        private fun setSize(size: Int) {
            resize(size)
            sizeItems.forEach(Item::notifyWindows)
        }
        
        private fun setDepth(depth: Int) {
            sizeY = depth
            done = false
            depthItems.forEach(Item::notifyWindows)
        }
        
        private inner class SizeDisplayItem(private val getNumber: () -> Int) : AbstractItem() {
            
            override fun getItemProvider(player: Player): ItemProvider {
                val number = getNumber()
                return DefaultGuiItems.NUMBER.createClientsideItemBuilder().addCustomModelData(getNumber())
                    .setName(Component.translatable("menu.machines.quarry.size", Component.text(number), Component.text(number)))
                    .addLoreLines(Component.translatable("menu.machines.quarry.size_tip", NamedTextColor.GRAY))
            }
            
            override fun handleClick(clickType: ClickType, player: Player, click: Click) = Unit
            
        }
        
        private inner class DepthDisplayItem(private val getNumber: () -> Int) : AbstractItem() {
            
            override fun getItemProvider(player: Player): ItemProvider {
                val number = getNumber()
                return DefaultGuiItems.NUMBER.createClientsideItemBuilder().addCustomModelData(getNumber())
                    .setName(Component.translatable("menu.machines.quarry.depth", Component.text(number)))
                    .addLoreLines(Component.translatable("menu.machines.quarry.depth_tip", NamedTextColor.GRAY))
            }
            
            override fun handleClick(clickType: ClickType, player: Player, click: Click) = Unit
            
        }
        
    }
    
}