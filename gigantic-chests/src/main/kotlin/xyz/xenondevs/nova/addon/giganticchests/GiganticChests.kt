package xyz.xenondevs.nova.addon.giganticchests

import org.bukkit.block.BlockType
import org.joml.Vector3d
import xyz.xenondevs.nova.addon.Addon
import xyz.xenondevs.nova.addon.giganticchests.GiganticChests.guiTexture
import xyz.xenondevs.nova.addon.giganticchests.GiganticChests.item
import xyz.xenondevs.nova.addon.giganticchests.GiganticChests.tileEntity
import xyz.xenondevs.nova.context.intention.BlockBreak
import xyz.xenondevs.nova.context.intention.BlockPlace
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.registry.NovaBlockBuilder
import xyz.xenondevs.nova.registry.entries.BlockTypeEntries
import xyz.xenondevs.nova.registry.entries.ItemTypeEntries
import xyz.xenondevs.nova.resources.builder.layout.block.BackingStateCategory
import xyz.xenondevs.nova.world.block.behavior.TileEntityDrops
import xyz.xenondevs.nova.world.block.behavior.TileEntityInteractive
import xyz.xenondevs.nova.world.block.sound.SoundGroup
import xyz.xenondevs.nova.world.item.tool.VanillaToolCategories
import xyz.xenondevs.nova.world.item.tool.VanillaToolTiers

object GiganticChests : Addon()

@Init(InitStage.PRE_PACK)
object Blocks {
    
    val GIGANTIC_CHEST_PART = tileEntity("gigantic_chest_part", ::GiganticChestPartTileEntity) {
        woodenChest()
        behaviors(TileEntityDrops, GiganticChestPartBlock)
        stateBacked(BackingStateCategory.MUSHROOM_BLOCK, BackingStateCategory.NOTE_BLOCK)
    }
    
    val GIGANTIC_CHEST = tileEntity("gigantic_chest", ::GiganticChestTileEntity) {
        item(Items.GIGANTIC_CHEST_PART)
        woodenChest()
        behaviors(TileEntityDrops, TileEntityInteractive, GiganticChestBlock)
        modelLess { BlockType.BARRIER.createBlockData() }
    }
    
    val GIGANTIC_CHEST_LIKE = GiganticChests.blockTag("gigantic_chest_like") {
        add(GIGANTIC_CHEST_PART, GIGANTIC_CHEST)
    }
    
    private fun NovaBlockBuilder.woodenChest() {
        sounds(SoundGroup.WOOD)
        breakable(
            hardness = 2.5,
            toolCategories = setOf(VanillaToolCategories.AXE),
            toolTier = VanillaToolTiers.WOOD,
            requiresToolForDrops = false,
            hitParticles = ItemTypeEntries.CHEST,
            breakParticles = BlockTypeEntries.CHEST
        )
    }
    
}

@Init(InitStage.PRE_PACK)
object Items {
    
    val GIGANTIC_CHEST_PART = item(Blocks.GIGANTIC_CHEST_PART) {}
    
}


@Init(InitStage.PRE_PACK)
object GuiTextures {
    
    val GIGANTIC_CHEST = guiTexture("gigantic_chest") {}
    
}

@Init(InitStage.PRE_PACK)
object Models {
    
    val CHEST_BOTTOM = item("model/chest_bottom") {
        hidden(true)
        modelDefinition {
            model = buildModel {
                // resize to full block
                getModel("block/chest_bottom")
                    .translate(Vector3d(0.0, 1.0, 0.0))
                    .scale(16.0 / 14.0)
            }
        }
    }
    
    val CHEST_LID = item("model/chest_lid") {
        hidden(true)
        modelDefinition {
            model = buildModel {
                // resize to full block, move hinge to center (8, 8, 8)
                getModel("block/chest_lid")
                    .translate(Vector3d(0.0, -8.0, 0.0))
                    .scale(16 / 14.0)
                    .translate(Vector3d(0.0, 8.0, -8.0))
            }
        }
    }
    
}

object ContextParamTypes {
    val SKIP_MULTIBLOCK_FORMING_FROM_PART_PLACE = BlockPlace.addDefaultingParamType(false)
    val SKIP_MULTIBLOCK_RESET_FROM_TE_BREAK = BlockBreak.addDefaultingParamType(false)
}
