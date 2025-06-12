@file:Suppress("unused")

package xyz.xenondevs.nova.addon.logistics.registry

import net.minecraft.core.Direction.Axis
import net.minecraft.world.level.block.Blocks
import org.bukkit.Material
import xyz.xenondevs.nova.addon.logistics.Logistics.tileEntity
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
import xyz.xenondevs.nova.resources.builder.layout.block.BackingStateCategory
import xyz.xenondevs.nova.world.block.NovaTileEntityBlock
import xyz.xenondevs.nova.world.block.NovaTileEntityBlockBuilder
import xyz.xenondevs.nova.world.block.TileEntityConstructor
import xyz.xenondevs.nova.world.block.behavior.BlockSounds
import xyz.xenondevs.nova.world.block.behavior.Breakable
import xyz.xenondevs.nova.world.block.behavior.Bucketable
import xyz.xenondevs.nova.world.block.behavior.TileEntityDrops
import xyz.xenondevs.nova.world.block.behavior.TileEntityInteractive
import xyz.xenondevs.nova.world.block.behavior.TileEntityLimited
import xyz.xenondevs.nova.world.block.sound.SoundGroup
import xyz.xenondevs.nova.world.block.state.property.DefaultScopedBlockStateProperties.AXIS
import xyz.xenondevs.nova.world.item.tool.VanillaToolCategories
import xyz.xenondevs.nova.world.item.tool.VanillaToolTiers
import net.minecraft.world.level.block.state.properties.BlockStateProperties as MojangBlockStateProperties

@Init(stage = InitStage.PRE_PACK)
object Blocks {
    
    private val CABLE = Breakable(0.0, requiresToolForDrops = false)
    private val POWER_CELL = Breakable(4.0, setOf(VanillaToolCategories.PICKAXE), VanillaToolTiers.STONE, true, Material.IRON_BLOCK)
    private val TANK = Breakable(2.0, setOf(VanillaToolCategories.PICKAXE), VanillaToolTiers.STONE, true, Material.GLASS)
    private val OTHER = Breakable(4.0, setOf(VanillaToolCategories.PICKAXE), VanillaToolTiers.STONE, true, Material.COBBLESTONE)
    
    val BASIC_CABLE = cable("basic", ::BasicCable)
    val ADVANCED_CABLE = cable("advanced", ::AdvancedCable)
    val ELITE_CABLE = cable("elite", ::EliteCable)
    val ULTIMATE_CABLE = cable("ultimate", ::UltimateCable)
    val CREATIVE_CABLE = cable("creative", ::CreativeCable)
    
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
    
    val STORAGE_UNIT = interactiveTileEntity("storage_unit", ::StorageUnit) { behaviors(OTHER, BlockSounds(SoundGroup.STONE)) }
    val FLUID_STORAGE_UNIT = interactiveTileEntity("fluid_storage_unit", ::FluidStorageUnit) { behaviors(Bucketable, OTHER, BlockSounds(SoundGroup.STONE)) }
    val VACUUM_CHEST = interactiveTileEntity("vacuum_chest", ::VacuumChest) { behaviors(OTHER, BlockSounds(SoundGroup.STONE)) }
    val TRASH_CAN = interactiveTileEntity("trash_can", ::TrashCan) {
        behaviors(OTHER, BlockSounds(SoundGroup.STONE))
        stateProperties(AXIS)
        entityBacked { defaultModel.rotated() }
    }
    
    private fun cable(tier: String, constructor: TileEntityConstructor): NovaTileEntityBlock =
        tileEntity("${tier}_cable", constructor) {
            tickrate(0)
            behaviors(TileEntityLimited, TileEntityDrops, CABLE, BlockSounds(SoundGroup.METAL))
            stateProperties(
                ScopedBlockStateProperties.NORTH,
                ScopedBlockStateProperties.EAST,
                ScopedBlockStateProperties.SOUTH,
                ScopedBlockStateProperties.WEST,
                ScopedBlockStateProperties.UP,
                ScopedBlockStateProperties.DOWN
            )
            
            entityBacked({
                val north = getPropertyValueOrThrow(BlockStateProperties.NORTH)
                val east = getPropertyValueOrThrow(BlockStateProperties.EAST)
                val south = getPropertyValueOrThrow(BlockStateProperties.SOUTH)
                val west = getPropertyValueOrThrow(BlockStateProperties.WEST)
                val up = getPropertyValueOrThrow(BlockStateProperties.UP)
                val down = getPropertyValueOrThrow(BlockStateProperties.DOWN)
                
                when {
                    east && west -> Blocks.CHAIN.defaultBlockState()
                        .setValue(MojangBlockStateProperties.AXIS, Axis.X)
                    
                    north && south -> Blocks.CHAIN.defaultBlockState()
                        .setValue(MojangBlockStateProperties.AXIS, Axis.Z)
                    
                    up && down -> Blocks.CHAIN.defaultBlockState()
                        .setValue(MojangBlockStateProperties.AXIS, Axis.Y)
                    
                    else -> Blocks.STRUCTURE_VOID.defaultBlockState()
                }
            }, {
                val id = MathUtils.encodeToInt(
                    getPropertyValueOrThrow(BlockStateProperties.NORTH),
                    getPropertyValueOrThrow(BlockStateProperties.EAST),
                    getPropertyValueOrThrow(BlockStateProperties.SOUTH),
                    getPropertyValueOrThrow(BlockStateProperties.WEST),
                    getPropertyValueOrThrow(BlockStateProperties.UP),
                    getPropertyValueOrThrow(BlockStateProperties.DOWN)
                )
                
                getModel("block/cable/$tier/$id")
            })
        }
    
    private fun interactiveTileEntity(
        name: String,
        constructor: TileEntityConstructor,
        init: NovaTileEntityBlockBuilder.() -> Unit
    ): NovaTileEntityBlock = tileEntity(name, constructor) {
        init()
        behaviors(TileEntityLimited, TileEntityDrops, TileEntityInteractive)
    }
    
    private fun powerCell(tier: String, constructor: TileEntityConstructor): NovaTileEntityBlock =
        interactiveTileEntity("${tier}_power_cell", constructor) {
            behaviors(POWER_CELL, BlockSounds(SoundGroup.METAL))
            stateBacked(BackingStateCategory.NOTE_BLOCK, BackingStateCategory.MUSHROOM_BLOCK) {
                getModel("block/power_cell/$tier")
            }
        }
    
    private fun tank(tier: String, constructor: TileEntityConstructor): NovaTileEntityBlock =
        interactiveTileEntity("${tier}_fluid_tank", constructor) {
            behaviors(Bucketable, TANK, BlockSounds(SoundGroup.GLASS))
            entityBacked { getModel("block/fluid_tank/$tier") }
        }
    
}