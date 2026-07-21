package dev.tyler.tides

import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.sdk.shared.LightServerData
import kotlinx.coroutines.flow.StateFlow

@EntryPoint
object ToolEntryPoint : LightEntryPoint {
    override suspend fun onToolCreate(
        serverData: StateFlow<LightServerData?>,
    ) {
        // Tides has no push-backed features; nothing to send upstream.
    }

    override suspend fun onPushNotification(
        data: ByteArray,
    ) {
        // Not used — refresh happens via the "tides-refresh" LightJob (M3).
    }
}
