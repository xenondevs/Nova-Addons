package xyz.xenondevs.nova.addon.logistics.util

import kotlin.math.min

class LongRingBuffer(size: Int) {
    
    private val buffer = LongArray(size)
    
    private var nextIdx = 0
    private var size = 0
    
    operator fun get(index: Int): Long {
        require(index in 0..<size) { "Index out of bounds: $index" }
        return buffer[(nextIdx + index) % size]
    }
    
    fun forEach(action: (index: Int, value: Long) -> Unit) {
        repeat(size) {
            val i = (nextIdx - size + it).mod(buffer.size)
            action(it, buffer[i])
        }
    }
    
    fun max(): Long {
        return buffer.max()
    }
    
    fun average(): Long {
        return buffer.average().toLong()
    }
    
    fun add(value: Long) {
        buffer[nextIdx] = value
        nextIdx = (nextIdx + 1) % buffer.size
        size = min(size + 1, buffer.size)
    }
    
    operator fun plusAssign(value: Long) = add(value)
    
}