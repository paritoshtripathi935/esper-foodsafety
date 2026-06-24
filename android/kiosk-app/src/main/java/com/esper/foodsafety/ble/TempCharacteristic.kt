package com.esper.foodsafety.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

object TempCharacteristic {
    fun decode(data: ByteArray?): Float? {
        if (data == null || data.size != 4) return null
        val buffer = ByteBuffer.wrap(data)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        return buffer.float
    }

    fun encode(tempF: Float): ByteArray {
        val buffer = ByteBuffer.allocate(4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.putFloat(tempF)
        return buffer.array()
    }
}
