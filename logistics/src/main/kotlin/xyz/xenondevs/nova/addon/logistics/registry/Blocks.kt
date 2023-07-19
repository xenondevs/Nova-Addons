@file:Suppress("unused")

package xyz.xenondevs.nova.addon.logistics.registry

import org.bukkit.Material.*
import xyz.xenondevs.nova.addon.registry.BlockRegistry
import xyz.xenondevs.nova.data.world.block.property.Directional
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.item.options.BlockOptions
import xyz.xenondevs.nova.item.tool.VanillaToolCategories
import xyz.xenondevs.nova.item.tool.VanillaToolTiers
import xyz.xenondevs.nova.addon.logistics.Logistics
import xyz.xenondevs.nova.addon.logistics.tileentity.AdvancedCable
import xyz.xenondevs.nova.addon.logistics.tileentity.AdvancedFluidTank
import xyz.xenondevs.nova.addon.logistics.tileentity.BasicCable
import xyz.xenondevs.nova.addon.logistics.tileentity.BasicFluidTank
import xyz.xenondevs.nova.addon.logistics.tileentity.CreativeCable
import xyz.xenondevs.nova.addon.logistics.tileentity.CreativeFluidTank
import xyz.xenondevs.nova.addon.logistics.tileentity.EliteCable
import xyz.xenondevs.nova.addon.logistics.tileentity.EliteFluidTank
import xyz.xenondevs.nova.addon.logistics.tileentity.FluidStorageUnit
import xyz.xenondevs.nova.addon.logistics.tileentity.StorageUnit
import xyz.xenondevs.nova.addon.logistics.tileentity.TrashCan
import xyz.xenondevs.nova.addon.logistics.tileentity.UltimateCable
import xyz.xenondevs.nova.addon.logistics.tileentity.UltimateFluidTank
import xyz.xenondevs.nova.addon.logistics.tileentity.VacuumChest
import xyz.xenondevs.nova.addon.logistics.tileentity.createAdvancedPowerCell
import xyz.xenondevs.nova.addon.logistics.tileentity.createBasicPowerCell
import xyz.xenondevs.nova.addon.logistics.tileentity.createCreativePowerCell
import xyz.xenondevs.nova.addon.logistics.tileentity.createElitePowerCell
import xyz.xenondevs.nova.addon.logistics.tileentity.createUltimatePowerCell
import xyz.xenondevs.nova.world.block.sound.SoundGroup

@Init
object Blocks : BlockRegistry by Logistics.registry {
    
    private val CABLE = BlockOptions(0.0, SoundGroup.STONE)
    private val POWER_CELL = BlockOptions(4.0, VanillaToolCategories.PICKAXE, VanillaToolTiers.STONE, true, SoundGroup.METAL, IRON_BLOCK)
    private val TANK = BlockOptions(2.0, VanillaToolCategories.PICKAXE, VanillaToolTiers.STONE, true, SoundGroup.GLASS, GLASS)
    private val OTHER = BlockOptions(4.0, VanillaToolCategories.PICKAXE, VanillaToolTiers.STONE, true, SoundGroup.STONE, COBBLESTONE)
    
    val BASIC_CABLE = tileEntity( "basic_cable", ::BasicCable).blockOptions(CABLE).interactive(false).register()
    val ADVANCED_CABLE = tileEntity( "advanced_cable", ::AdvancedCable).blockOptions(CABLE).interactive(false).register()
    val ELITE_CABLE = tileEntity( "elite_cable", ::EliteCable).blockOptions(CABLE).interactive(false).register()
    val ULTIMATE_CABLE = tileEntity( "ultimate_cable", ::UltimateCable).blockOptions(CABLE).interactive(false).register()
    val CREATIVE_CABLE = tileEntity("creative_cable", ::CreativeCable).blockOptions(CABLE).interactive(false).register()
    
    val BASIC_POWER_CELL = tileEntity( "basic_power_cell", ::createBasicPowerCell).blockOptions(POWER_CELL).register()
    val ADVANCED_POWER_CELL = tileEntity( "advanced_power_cell", ::createAdvancedPowerCell).blockOptions(POWER_CELL).register()
    val ELITE_POWER_CELL = tileEntity( "elite_power_cell", ::createElitePowerCell).blockOptions(POWER_CELL).register()
    val ULTIMATE_POWER_CELL = tileEntity("ultimate_power_cell", ::createUltimatePowerCell).blockOptions(POWER_CELL).register()
    val CREATIVE_POWER_CELL = tileEntity("creative_power_cell", ::createCreativePowerCell).blockOptions(POWER_CELL).register()
    
    val BASIC_FLUID_TANK = tileEntity("basic_fluid_tank", ::BasicFluidTank).blockOptions(TANK).register()
    val ADVANCED_FLUID_TANK = tileEntity("advanced_fluid_tank", ::AdvancedFluidTank).blockOptions(TANK).register()
    val ELITE_FLUID_TANK = tileEntity("elite_fluid_tank", ::EliteFluidTank).blockOptions(TANK).register()
    val ULTIMATE_FLUID_TANK = tileEntity("ultimate_fluid_tank", ::UltimateFluidTank).blockOptions(TANK).register()
    val CREATIVE_FLUID_TANK = tileEntity("creative_fluid_tank", ::CreativeFluidTank).blockOptions(TANK).register()
    
    val STORAGE_UNIT = tileEntity("storage_unit", ::StorageUnit).blockOptions(OTHER).register()
    val FLUID_STORAGE_UNIT = tileEntity("fluid_storage_unit", ::FluidStorageUnit).blockOptions(OTHER).register()
    val VACUUM_CHEST = tileEntity("vacuum_chest", ::VacuumChest).blockOptions(OTHER).register()
    val TRASH_CAN = tileEntity("trash_can", ::TrashCan).blockOptions(OTHER).properties(Directional.NORMAL).register()
    
}