package dev.tyler.tides.data

import android.util.Log
import com.thelightphone.sdk.LightJob
import com.thelightphone.sdk.LightJobHandler
import com.thelightphone.sdk.LightJobResult
import com.thelightphone.sdk.buildDatabase
import dev.tyler.tides.api.TidesStationError
import java.time.LocalDate

private const val LOG_TAG = "TidesJob"

const val TIDES_REFRESH_JOB_KEY = "tides-refresh"

/**
 * Daily top-up. Self-contained per the `@LightJob` contract: builds its own repository from
 * the [com.thelightphone.sdk.SealedLightContext] it receives rather than closing over any
 * app-level singleton — [TideRepository.getInstance] still returns the shared instance when
 * the tool process is already warm.
 *
 * loadTides already no-ops when the cache is fresh, so a job run on an already-fresh station
 * costs nothing beyond a DataStore/Room read. Failure is invisible-by-design (CLAUDE.md) — the
 * on-open path always self-heals.
 */
@LightJob(TIDES_REFRESH_JOB_KEY)
val tidesRefreshJob: LightJobHandler = { ctx, _ ->
    val repository = TideRepository.getInstance(dataStore = ctx.dataStore) {
        ctx.buildDatabase(TideDatabase::class.java, TideDatabase.DATABASE_NAME)
    }
    val snapshot = repository.loadTides(LocalDate.now())
    val result = snapshot.toJobResult()
    // Cheap and only place this job's outcome is observable on a real device without a debugger
    // attached — `adb logcat -s TidesJob` is the verification path for the daily top-up.
    Log.d(LOG_TAG, "ran for station=${snapshot?.station?.id}, stale=${snapshot?.stale}, result=$result")
    result
}

// Split out from the handler above so the decision itself — the only branching in this job —
// is testable without an Android runtime.
//
// TidesStationError (NOAA's {"error":{...}} payload) means the query itself is invalid — a
// permanent condition retrying won't fix, so this resumes the normal daily cadence (Error) rather
// than have WorkManager retry-with-backoff up to several times a day forever on a dead station.
// Any other failure (network/HTTP, including a transient 5xx) is worth trying again soon (Retry).
internal fun TidesSnapshot?.toJobResult(): LightJobResult = when {
    this == null || !stale -> LightJobResult.Success()
    staleCause is TidesStationError -> LightJobResult.Error()
    else -> LightJobResult.Retry
}
