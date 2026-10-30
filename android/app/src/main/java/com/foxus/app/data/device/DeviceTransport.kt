package com.foxus.app.data.device

import com.foxus.app.data.remote.DeviceEventRequestDto
import com.foxus.app.data.remote.DeviceEventResponseDto
import com.foxus.app.data.remote.FoxusApiService
import com.foxus.app.data.remote.NextDeviceCommandResponseDto

/**
 * Hardware communication abstraction (Section 10).
 * Allows switching between Wi-Fi HTTP backend coordination and direct Bluetooth LE
 * without rewriting mission domain logic.
 */
interface DeviceTransport {
    val transportName: String

    suspend fun pollNextCommand(deviceId: String): Result<NextDeviceCommandResponseDto>

    suspend fun sendButtonAction(
        deviceId: String,
        eventType: String,
        commandId: String? = null,
        microtaskId: String?,
        hookId: String?,
        sessionId: String?,
        source: String = "simulator"
    ): Result<DeviceEventResponseDto>
}

class WifiHttpTransport(
    private val apiService: FoxusApiService
) : DeviceTransport {
    override val transportName: String = "Wi-Fi HTTP (/api/v1/devices)"

    override suspend fun pollNextCommand(deviceId: String): Result<NextDeviceCommandResponseDto> =
        runCatching {
            apiService.getNextDeviceCommand(deviceId)
        }

    override suspend fun sendButtonAction(
        deviceId: String,
        eventType: String,
        commandId: String?,
        microtaskId: String?,
        hookId: String?,
        sessionId: String?,
        source: String
    ): Result<DeviceEventResponseDto> = runCatching {
        apiService.sendDeviceEvent(
            deviceId = deviceId,
            payload = DeviceEventRequestDto(
                eventType = eventType,
                commandId = commandId,
                microtaskId = microtaskId,
                hookId = hookId,
                sessionId = sessionId,
                source = source
            )
        )
    }
}

class BleTransport : DeviceTransport {
    override val transportName: String = "Bluetooth LE GATT (Experimental Extension)"

    override suspend fun pollNextCommand(deviceId: String): Result<NextDeviceCommandResponseDto> =
        Result.failure(
            UnsupportedOperationException(
                "BLE directo preparado como extensión posterior; el MVP utiliza WifiHttpTransport."
            )
        )

    override suspend fun sendButtonAction(
        deviceId: String,
        eventType: String,
        commandId: String?,
        microtaskId: String?,
        hookId: String?,
        sessionId: String?,
        source: String
    ): Result<DeviceEventResponseDto> = Result.failure(
        UnsupportedOperationException(
            "BLE directo preparado como extensión posterior; el MVP utiliza WifiHttpTransport."
        )
    )
}
