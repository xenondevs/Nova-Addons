package xyz.xenondevs.nova.addon.vanillahammers.advancement

import net.kyori.adventure.text.Component
import xyz.xenondevs.nmsutils.advancement.AdvancementLoader
import xyz.xenondevs.nova.addon.vanillahammers.VanillaHammers
import xyz.xenondevs.nova.addon.vanillahammers.registry.Items
import xyz.xenondevs.nova.initialize.Init
import xyz.xenondevs.nova.initialize.InitFun
import xyz.xenondevs.nova.initialize.InitStage
import xyz.xenondevs.nova.util.advancement
import xyz.xenondevs.nova.util.icon
import xyz.xenondevs.nova.util.obtainNovaItemAdvancement

private val ROOT = advancement(VanillaHammers, "root") {
    display {
        icon(Items.WOODEN_HAMMER)
        title(Component.translatable("advancement.vanilla_hammers.root.title"))
        description("")
        background("minecraft:textures/block/stone.png")
        
        announceToChat(false)
        showToast(false)
    }
    
    criteria { tick("tick") {} }
}

private val STONE_HAMMER = obtainNovaItemAdvancement(VanillaHammers, ROOT, Items.STONE_HAMMER)
private val SLIME_HAMMER = obtainNovaItemAdvancement(VanillaHammers, ROOT, Items.SLIME_HAMMER)
private val IRON_HAMMER = obtainNovaItemAdvancement(VanillaHammers, STONE_HAMMER, Items.IRON_HAMMER)
private val QUARTZ_HAMMER = obtainNovaItemAdvancement(VanillaHammers, STONE_HAMMER, Items.QUARTZ_HAMMER)
private val LAPIS_HAMMER = obtainNovaItemAdvancement(VanillaHammers, STONE_HAMMER, Items.LAPIS_HAMMER)
private val PRISMARINE_HAMMER = obtainNovaItemAdvancement(VanillaHammers, STONE_HAMMER, Items.PRISMARINE_HAMMER)
private val GOLDEN_HAMMER = obtainNovaItemAdvancement(VanillaHammers, IRON_HAMMER, Items.GOLDEN_HAMMER)
private val DIAMOND_HAMMER = obtainNovaItemAdvancement(VanillaHammers, IRON_HAMMER, Items.DIAMOND_HAMMER)
private val EMERALD_HAMMER = obtainNovaItemAdvancement(VanillaHammers, IRON_HAMMER, Items.EMERALD_HAMMER)
private val NETHERITE_HAMMER = obtainNovaItemAdvancement(VanillaHammers, DIAMOND_HAMMER, Items.NETHERITE_HAMMER)
private val OBSIDIAN_HAMMER = obtainNovaItemAdvancement(VanillaHammers, DIAMOND_HAMMER, Items.OBSIDIAN_HAMMER)
private val FIERY_HAMMER = obtainNovaItemAdvancement(VanillaHammers, QUARTZ_HAMMER, Items.FIERY_HAMMER)
private val ENDER_HAMMER = obtainNovaItemAdvancement(VanillaHammers, EMERALD_HAMMER, Items.ENDER_HAMMER)

@Init(stage = InitStage.POST_PACK_PRE_WORLD)
object Advancements {
    
    @InitFun
    fun register() {
        AdvancementLoader.registerAdvancements(
            ROOT, STONE_HAMMER, IRON_HAMMER, GOLDEN_HAMMER, DIAMOND_HAMMER, NETHERITE_HAMMER,
            EMERALD_HAMMER, LAPIS_HAMMER, QUARTZ_HAMMER, OBSIDIAN_HAMMER, PRISMARINE_HAMMER, FIERY_HAMMER, SLIME_HAMMER,
            ENDER_HAMMER
        )
    }
    
}