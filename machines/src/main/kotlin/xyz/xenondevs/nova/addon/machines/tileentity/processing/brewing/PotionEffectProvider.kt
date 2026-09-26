package xyz.xenondevs.nova.addon.machines.tileentity.processing.brewing

import net.kyori.adventure.key.Key.key
import org.bukkit.Registry
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import xyz.xenondevs.cbf.Cbf
import xyz.xenondevs.cbf.io.ByteReader
import xyz.xenondevs.cbf.io.ByteWriter
import xyz.xenondevs.cbf.serializer.BinarySerializer
import xyz.xenondevs.commons.provider.MutableProvider
import xyz.xenondevs.commons.provider.combinedProvider
import xyz.xenondevs.commons.provider.mutableProvider
import xyz.xenondevs.nova.addon.machines.recipe.ElectricBrewingStandRecipe
import kotlin.math.pow
import kotlin.math.roundToInt

class PotionEffectProvider(
    val type: MutableProvider<PotionEffectType?> = mutableProvider(null),
    val durationLevel: MutableProvider<Int> = mutableProvider(0),
    val amplifierLevel: MutableProvider<Int> = mutableProvider(0)
) {
    
    constructor(type: PotionEffectType?, durationLevel: Int, amplifierLevel: Int) : this(
        mutableProvider(type),
        mutableProvider(durationLevel),
        mutableProvider(amplifierLevel)
    )
    
    val maxDurationLevel = type.map { ElectricBrewingStand.AVAILABLE_POTION_EFFECTS[it]?.maxDurationLevel ?: 0 }
    val maxAmplifierLevel = type.map { ElectricBrewingStand.AVAILABLE_POTION_EFFECTS[it]?.maxAmplifierLevel ?: 0 }
    
    val result = combinedProvider(
        type, durationLevel, amplifierLevel
    ) { type, durationLevel, amplifierLevel -> PotionEffectWithExtraInfo.of(type, durationLevel, amplifierLevel) }
    
    override fun toString() = result.get().toString()
    
}

data class PotionEffectWithExtraInfo(
    val effect: PotionEffect,
    val recipe: ElectricBrewingStandRecipe,
    val type: PotionEffectType,
    val durationLevel: Int,
    val amplifierLevel: Int
) {
    
    override fun toString() = "${effect.type.key.asString()}(amplifier=$amplifierLevel, duration=$durationLevel)"
    
    companion object {
        
        fun of(type: PotionEffectType?, durationLevel: Int, amplifierLevel: Int): PotionEffectWithExtraInfo? {
            type ?: return null
            val recipe = ElectricBrewingStand.AVAILABLE_POTION_EFFECTS[type]
                ?: return null
            
            val defaultDuration = recipe.defaultTime
            var duration = defaultDuration.toDouble()
            if (durationLevel > 0) duration *= durationLevel * recipe.redstoneMultiplier
            duration *= recipe.glowstoneMultiplier.pow(amplifierLevel)
            
            return PotionEffectWithExtraInfo(
                PotionEffect(type, duration.roundToInt(), amplifierLevel, false, true, true),
                recipe,
                type,
                durationLevel,
                amplifierLevel
            )
        }
        
    }
    
}

object PotionEffectProviderBinarySerializer : BinarySerializer<PotionEffectProvider> {
    
    override fun write(obj: PotionEffectProvider?, writer: ByteWriter) {
        val type = obj?.type?.get()
        if (obj == null || type == null) {
            writer.writeUnsignedByte(0U)
        } else {
            writer.writeUnsignedByte(3.toUByte()) // current version
            writer.writeString(type.key.asString())
            writer.writeInt(obj.durationLevel.get())
            writer.writeInt(obj.amplifierLevel.get())
        }
    }
    
    override fun read(reader: ByteReader): PotionEffectProvider? {
        return when (val version = reader.readUnsignedByte()) {
            0.toUByte() -> null
            1.toUByte(), 2.toUByte() -> readLegacyCompoundSerialization(version, reader)
            3.toUByte() -> readV3(reader)
            else -> throw UnsupportedOperationException()
        }
    }
    
    // previously, this was serialized as a compound (compound versions 1, 2)
    private fun readLegacyCompoundSerialization(version: UByte, reader: ByteReader): PotionEffectProvider {
        val mapSize = reader.readVarInt()
        val entryMap = HashMap<String, ByteArray>(mapSize)
        
        repeat(mapSize) {
            val key = reader.readString()
            val length = reader.readVarInt()
            val bytes = reader.readBytes(length)
            
            // v1 wrote null values into the binary format, which we want to ignore now
            if (version == 1.toUByte() && length == 1 && bytes[0] == 0.toByte())
                return@repeat
            
            entryMap[key] = bytes
        }
        
        return PotionEffectProvider(
            Cbf.read<PotionEffectType>(entryMap["type"]!!)!!,
            Cbf.read<Int>(entryMap["duration"]!!)!!,
            Cbf.read<Int>(entryMap["amplifier"]!!)!!
        )
    }
    
    private fun readV3(reader: ByteReader) = PotionEffectProvider(
        Registry.POTION_EFFECT_TYPE.getOrThrow(key(reader.readString())),
        reader.readInt(),
        reader.readInt()
    )
    
    override fun copy(obj: PotionEffectProvider?): PotionEffectProvider? {
        if (obj == null)
            return null
        
        return PotionEffectProvider(
            mutableProvider(obj.type.get()),
            mutableProvider(obj.durationLevel.get()),
            mutableProvider(obj.amplifierLevel.get())
        )
    }
    
}