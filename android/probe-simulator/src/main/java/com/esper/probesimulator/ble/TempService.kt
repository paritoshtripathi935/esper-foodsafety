package com.esper.probesimulator.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

object TempService {
    val SERVICE_UUID: UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
    val TEMP_CHAR_UUID: UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef1")
    val CLIENT_CONFIG_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") // standard CCCD

    fun encodeTemperature(tempF: Float): ByteArray {
        val buffer = ByteBuffer.allocate(4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.putFloat(tempF)
        return buffer.array()
    }
}
