package xyz.xenondevs.nova.addon.logistics.util

object MathUtils {
    
    @JvmName("encodeToIntVararg")
    fun encodeToInt(vararg booleans: Boolean): Int =
        encodeToInt(booleans)
    
    @JvmName("encodeToIntArray")
    fun encodeToInt(booleans: BooleanArray): Int {
        var result = 0
        for (i in booleans.indices) {
            if (booleans[i]) {
                result = result or (1 shl i)
            }
        }
        return result
    }
    
}