@file:Suppress("unused")

package xyz.xenondevs.nova.addon.logistics.registry

import io.papermc.paper.registry.keys.SoundEventKeys
import org.bukkit.Axis
import org.bukkit.block.BlockType
import org.joml.Matrix4f
import xyz.xenondevs.nova.addon.logistics.Logistics.tileEntity
import xyz.xenondevs.nova.addon.logistics.registry.BlockStateProperties.DOWN
import xyz.xenondevs.nova.addon.logistics.registry.BlockStateProperties.EAST
import xyz.xenondevs.nova.addon.logistics.registry.BlockStateProperties.FACADE
import xyz.xenondevs.nova.addon.logistics.registry.BlockStateProperties.NORTH
import xyz.xenondevs.nova.addon.logistics.registry.BlockStateProperties.SOUTH
import xyz.xenondevs.nova.addon.logistics.registry.BlockStateProperties.UP
import xyz.xenondevs.nova.addon.logistics.registry.BlockStateProperties.WEST
import xyz.xenondevs.nova.addon.logistics.tileentity.AdvancedCable
import xyz.xenondevs.nova.addon.logistics.tileentity.AdvancedFluidTank
import xyz.xenondevs.nova.addon.logistics.tileentity.AdvancedPowerCell
import xyz.xenondevs.nova.addon.logistics.tileentity.BasicCable
import xyz.xenondevs.nova.addon.logistics.tileentity.BasicFluidTank
import xyz.xenondevs.nova.addon.logistics.tileentity.BasicPowerCell
import xyz.xenondevs.nova.addon.logistics.tileentity.CreativeCable
import xyz.xenondevs.nova.addon.logistics.tileentity.CreativeFluidTank
import xyz.xenondevs.nova.addon.logistics.tileentity.CreativePowerCell
import xyz.xenondevs.nova.addon.logistics.tileentity.EliteCable
import xyz.xenondevs.nova.addon.logistics.tileentity.EliteFluidTank
import xyz.xenondevs.nova.addon.logistics.tileentity.ElitePowerCell
import xyz.xenondevs.nova.addon.logistics.tileentity.FacadedAdvancedCable
import xyz.xenondevs.nova.addon.logistics.tileentity.FacadedBasicCable
import xyz.xenondevs.nova.addon.logistics.tileentity.FacadedCreativeCable
import xyz.xenondevs.nova.addon.logistics.tileentity.FacadedEliteCable
import xyz.xenondevs.nova.addon.logistics.tileentity.FacadedUltimateCable
import xyz.xenondevs.nova.addon.logistics.tileentity.FluidStorageUnit
import xyz.xenondevs.nova.addon.logistics.tileentity.StorageUnit
import xyz.xenondevs.nova.addon.logistics.tileentity.TrashCan
import xyz.xenondevs.nova.addon.logistics.tileentity.UltimateCable
import xyz.xenondevs.nova.addon.logistics.tileentity.UltimateFluidTank
import xyz.xenondevs.nova.addon.logistics.tileentity.UltimatePowerCell
import xyz.xenondevs.nova.addon.logistics.tileentity.VacuumChest
import xyz.xenondevs.nova.addon.logistics.util.MathUtils
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.registry.NovaBlockBuilder
import xyz.xenondevs.nova.registry.NovaTileEntityBlockBuilder
import xyz.xenondevs.nova.registry.entries.BlockTypeEntries
import xyz.xenondevs.nova.registry.entries.ItemTypeEntries
import xyz.xenondevs.nova.resources.builder.data.EndCubeEffect
import xyz.xenondevs.nova.resources.builder.layout.block.BackingStateCategory
import xyz.xenondevs.nova.world.block.ColliderCube
import xyz.xenondevs.nova.world.block.FluidFlowMode
import xyz.xenondevs.nova.world.block.TileEntityConstructor
import xyz.xenondevs.nova.world.block.behavior.Bucketable
import xyz.xenondevs.nova.world.block.behavior.TileEntityDrops
import xyz.xenondevs.nova.world.block.behavior.TileEntityInteractive
import xyz.xenondevs.nova.world.block.sound.SoundGroup
import xyz.xenondevs.nova.world.block.state.property.DefaultBlockStateProperties.AXIS
import xyz.xenondevs.nova.world.block.state.property.DefaultBlockStateProperties.WATERLOGGED
import xyz.xenondevs.nova.world.item.tool.VanillaToolCategories
import xyz.xenondevs.nova.world.item.tool.VanillaToolTiers

@Init(stage = InitStage.PRE_PACK)
object Blocks {
    
    val BASIC_CABLE = unfacadedCable("basic", ::BasicCable)
    val ADVANCED_CABLE = unfacadedCable("advanced", ::AdvancedCable)
    val ELITE_CABLE = unfacadedCable("elite", ::EliteCable)
    val ULTIMATE_CABLE = unfacadedCable("ultimate", ::UltimateCable)
    val CREATIVE_CABLE = unfacadedCable("creative", ::CreativeCable)
    
    val FACADED_BASIC_CABLE = facadedCable("basic", ::FacadedBasicCable)
    val FACADED_ADVANCED_CABLE = facadedCable("advanced", ::FacadedAdvancedCable)
    val FACADED_ELITE_CABLE = facadedCable("elite", ::FacadedEliteCable)
    val FACADED_ULTIMATE_CABLE = facadedCable("ultimate", ::FacadedUltimateCable)
    val FACADED_CREATIVE_CABLE = facadedCable("creative", ::FacadedCreativeCable)
    
    val BASIC_POWER_CELL = powerCell("basic", ::BasicPowerCell)
    val ADVANCED_POWER_CELL = powerCell("advanced", ::AdvancedPowerCell)
    val ELITE_POWER_CELL = powerCell("elite", ::ElitePowerCell)
    val ULTIMATE_POWER_CELL = powerCell("ultimate", ::UltimatePowerCell)
    val CREATIVE_POWER_CELL = powerCell("creative", ::CreativePowerCell)
    
    val BASIC_FLUID_TANK = tank("basic", ::BasicFluidTank)
    val ADVANCED_FLUID_TANK = tank("advanced", ::AdvancedFluidTank)
    val ELITE_FLUID_TANK = tank("elite", ::EliteFluidTank)
    val ULTIMATE_FLUID_TANK = tank("ultimate", ::UltimateFluidTank)
    val CREATIVE_FLUID_TANK = tank("creative", ::CreativeFluidTank)
    
    val STORAGE_UNIT = interactiveTileEntity("storage_unit", ::StorageUnit) {
        breakableOther()
    }
    
    val FLUID_STORAGE_UNIT = interactiveTileEntity("fluid_storage_unit", ::FluidStorageUnit) {
        behaviors(Bucketable)
        breakableOther()
        stateProperties(BlockStateProperties.LAVA)
        lightEmission { if (getPropertyValueOrThrow(BlockStateProperties.LAVA)) 15 else 0 }
    }
    
    val VACUUM_CHEST = interactiveTileEntity("vacuum_chest", ::VacuumChest) {
        stateProperties(WATERLOGGED)
        entityItemBacked(
            stateSelector = { BlockType.STRUCTURE_VOID.createBlockData() },
            extraColliderSelector = { [ColliderCube(5.25 / 16.0, 5.25 / 16.0, 5.25 / 16.0, 5.5 / 16.0)] },
            extraHitboxSelector = { [] }
        ) {
            model = endCubeSpecialModel {
                effect = EndCubeEffect.GATEWAY
                base = { getModel("block/vacuum_chest") }
                transformation = Matrix4f().translate(5f / 16f, 5f / 16f, 5f / 16f).scale(6f / 16f)
            }
        }
        sounds(SoundGroup.STONE)
        breakable(
            hardness = 4.0,
            toolCategories = setOf(VanillaToolCategories.PICKAXE),
            toolTier = VanillaToolTiers.STONE,
            requiresToolForDrops = true,
            hitParticles = ItemTypeEntries.BLACK_CONCRETE
        )
    }
    
    val TRASH_CAN = interactiveTileEntity("trash_can", ::TrashCan) {
        breakableOther()
        stateProperties(AXIS, WATERLOGGED)
    }
    
    private fun unfacadedCable(tier: String, ctor: TileEntityConstructor) = cable("${tier}_cable", ctor) {
        stateProperties(NORTH, EAST, SOUTH, WEST, UP, DOWN, WATERLOGGED)
        sounds(SoundGroup.METAL)
        entityBacked(
            stateSelector = {
                val north = getPropertyValueOrThrow(NORTH)
                val east = getPropertyValueOrThrow(EAST)
                val south = getPropertyValueOrThrow(SOUTH)
                val west = getPropertyValueOrThrow(WEST)
                val up = getPropertyValueOrThrow(UP)
                val down = getPropertyValueOrThrow(DOWN)
                
                when {
                    east && west -> BlockType.IRON_CHAIN.createBlockData()
                        .apply { axis = Axis.X }
                    
                    north && south -> BlockType.IRON_CHAIN.createBlockData()
                        .apply { axis = Axis.Z }
                    
                    up && down -> BlockType.IRON_CHAIN.createBlockData()
                        .apply { axis = Axis.Y }
                    
                    else -> BlockType.STRUCTURE_VOID.createBlockData()
                }
            },
            extraColliderSelector = {
                val north = getPropertyValueOrThrow(NORTH)
                val east = getPropertyValueOrThrow(EAST)
                val south = getPropertyValueOrThrow(SOUTH)
                val west = getPropertyValueOrThrow(WEST)
                val up = getPropertyValueOrThrow(UP)
                val down = getPropertyValueOrThrow(DOWN)
                
                val chainAxis = when {
                    east && west -> Axis.X
                    north && south -> Axis.Z
                    up && down -> Axis.Y
                    else -> null
                }
                
                buildList {
                    // add centerpiece when using light
                    if (chainAxis == null)
                        add(ColliderCube(6.5 / 16.0, 6.5 / 16.0, 6.5 / 16.0, 3.0 / 16.0))
                    
                    fun addArm(axis: Axis, positive: Boolean) {
                        val size = 3.0
                        val gap = 0.25
                        val firstMin = if (positive) 9.75 else 0.0
                        repeat(2) { segment ->
                            val axisMin = (firstMin + segment * (size + gap)) / 16.0
                            val sideMin = 6.5 / 16.0
                            val normalizedSize = size / 16.0
                            add(
                                when (axis) {
                                    Axis.X -> ColliderCube(axisMin, sideMin, sideMin, normalizedSize)
                                    Axis.Y -> ColliderCube(sideMin, axisMin, sideMin, normalizedSize)
                                    Axis.Z -> ColliderCube(sideMin, sideMin, axisMin, normalizedSize)
                                }
                            )
                        }
                    }
                    
                    if (chainAxis != Axis.Z) {
                        if (north) addArm(Axis.Z, false)
                        if (south) addArm(Axis.Z, true)
                    }
                    if (chainAxis != Axis.X) {
                        if (east) addArm(Axis.X, true)
                        if (west) addArm(Axis.X, false)
                    }
                    if (chainAxis != Axis.Y) {
                        if (up) addArm(Axis.Y, true)
                        if (down) addArm(Axis.Y, false)
                    }
                }
            },
            extraHitboxSelector = { [] },
            modelSelector = {
                val id = MathUtils.encodeToInt(
                    getPropertyValueOrThrow(NORTH),
                    getPropertyValueOrThrow(EAST),
                    getPropertyValueOrThrow(SOUTH),
                    getPropertyValueOrThrow(WEST),
                    getPropertyValueOrThrow(UP),
                    getPropertyValueOrThrow(DOWN)
                )
                
                getModel("block/cable/$tier/$id")
            }
        )
    }
    
    private fun facadedCable(tier: String, ctor: TileEntityConstructor) = cable("facaded_${tier}_cable", ctor) {
        config("${tier}_cable")
        stateProperties(FACADE)
        modelLess { getPropertyValueOrThrow(FACADE).type.get().createBlockData() }
        lightEmission { getPropertyValueOrThrow(FACADE).lightEmission }
        sounds {
            val facadeSoundGroup = getPropertyValueOrThrow(FACADE).soundGroup
            SoundGroup(
                volume = 1f,
                pitch = 1f,
                placeSound = SoundEventKeys.ENTITY_ITEM_FRAME_ADD_ITEM.asString(),
                breakSound = SoundEventKeys.ENTITY_ITEM_FRAME_ADD_ITEM.asString(),
                stepSound = facadeSoundGroup.stepSound,
                hitSound = facadeSoundGroup.hitSound,
                fallSound = facadeSoundGroup.fallSound
            )
        }
    }
    
    private fun cable(
        name: String,
        constructor: TileEntityConstructor,
        configure: NovaTileEntityBlockBuilder.() -> Unit
    ) = tileEntity(name, constructor) {
        tickrate(0)
        behaviors(TileEntityDrops)
        sounds(SoundGroup.METAL)
        breakable(hardness = 0.0, requiresToolForDrops = false)
        fluidFlowMode(FluidFlowMode.WATERLOG_OUT)
        configure()
    }
    
    private fun interactiveTileEntity(
        name: String,
        constructor: TileEntityConstructor,
        init: NovaTileEntityBlockBuilder.() -> Unit
    ) = tileEntity(name, constructor) {
        init()
        behaviors(TileEntityDrops, TileEntityInteractive)
    }
    
    private fun powerCell(tier: String, constructor: TileEntityConstructor) =
        interactiveTileEntity("${tier}_power_cell", constructor) {
            sounds(SoundGroup.METAL)
            breakable(
                hardness = 4.0,
                toolCategories = setOf(VanillaToolCategories.PICKAXE),
                toolTier = VanillaToolTiers.STONE,
                requiresToolForDrops = true,
                hitParticles = ItemTypeEntries.IRON_BLOCK,
                breakParticles = BlockTypeEntries.IRON_BLOCK
            )
            stateBacked(BackingStateCategory.NOTE_BLOCK, BackingStateCategory.MUSHROOM_BLOCK) {
                getModel("block/power_cell/$tier")
            }
        }
    
    private fun tank(tier: String, constructor: TileEntityConstructor) =
        interactiveTileEntity("${tier}_fluid_tank", constructor) {
            stateProperties(WATERLOGGED, BlockStateProperties.LAVA)
            lightEmission { if (getPropertyValueOrThrow(BlockStateProperties.LAVA)) 15 else 0 }
            behaviors(Bucketable)
            sounds(SoundGroup.GLASS)
            breakable(
                hardness = 2.0,
                toolCategories = setOf(VanillaToolCategories.PICKAXE),
                toolTier = VanillaToolTiers.STONE,
                requiresToolForDrops = true,
                hitParticles = ItemTypeEntries.GLASS,
                breakParticles = BlockTypeEntries.GLASS
            )
            entityBacked { getModel("block/fluid_tank/$tier") }
        }
    
    private fun NovaBlockBuilder.breakableOther() {
        sounds(SoundGroup.STONE)
        breakable(
            hardness = 4.0,
            toolCategories = setOf(VanillaToolCategories.PICKAXE),
            toolTier = VanillaToolTiers.STONE,
            requiresToolForDrops = true,
            hitParticles = ItemTypeEntries.COBBLESTONE,
            breakParticles = BlockTypeEntries.COBBLESTONE
        )
    }
    
}
