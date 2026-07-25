# CLAUDE.md — Tides (Light Phone 3 tool)

Coastal users check tides like weather. NOAA's CO-OPS prediction API is free, keyless, and public-domain. Same architecture as the SDK's own weather example, different audience, unclaimed. **v1 is US-coastal only (NOAA)** — say so plainly in the listing; international sources are a later, per-country decision.

**Division of labor:** this doc is the plan of record; Claude Code owns compile–run–debug. SDK source outranks this doc.

## Verified SDK facts this tool is built on

- `examples/weather` is the direct template: Ktor(OkHttp) client + kotlinx-serialization DTOs, DataStore persistence, `onScreenShow` refresh with a `skipRefreshOnNextScreenShow` guard, cached-content-first rendering, `LightTextInputEditor` typed search, error-modal handling. Clone its bones.
- `@LightJob` / `LightWork` (androidx.work, allowlisted) — background handlers receiving a `SealedLightContext`, returning `Success/Retry/Error`; system-scheduled, explicitly not time-sensitive. Perfect for a daily prediction top-up.
- INTERNET + ACCESS_NETWORK_STATE allowlisted. Room allowlisted (used for prediction rows). `java.time` yes; `kotlinx-datetime` no.
- No location access exists for tool code (00-ASSESSMENT.md §1.2) → station selection is **typed search or geocode-nearest**, exactly like weather's location flow; "use my position" becomes a one-liner when the SDK ships location.

## External API (verify field names with curl in Phase 0 before writing DTOs)

- **Predictions:** `https://api.tidesandcurrents.noaa.gov/api/prod/datagetter?product=predictions&datum=MLLW&interval=hilo&units=english&time_zone=lst_ldt&format=json&station={id}&begin_date={YYYYMMDD}&end_date={YYYYMMDD}&application=dev.tyler.tides` → `{"predictions":[{"t":"2026-07-20 04:12","v":"5.213","type":"H"},…]}`. `lst_ldt` returns **station-local clock time as a naive string** — store and display it verbatim (`LocalDateTime.parse` with a space-separated formatter); do not convert through the phone's zone.
- **Station directory:** `https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/stations.json?type=tidepredictions` → id, name, state, lat, lng for ~3 000 stations.
- Courtesy: always send `application=`, one fetch per station per day in normal use, `Retry` on transient failures. No key, no auth, no cost.

## lighttool.toml

```toml
[tool]
id          = "dev.tyler.tides"
label       = "Tides"
versionCode = 1
versionName = "0.1.0"
permissions = ["android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE"]
serverPackage = "com.thelightphone.sdk.emulator"   # "com.lightos" on the LP3
```

## Architecture

```
tool/src/main/kotlin/dev/tyler/tides/
  ToolEntryPoint.kt          empty screen hooks — onToolCreate(serverData) receives no
                             SealedLightContext, so it cannot call LightWork itself; it does
                             NOT host the job (see data/TidesJob.kt and ui/HomeScreen.kt below)
  stations/
    StationIndex.kt          loads bundled asset; nearestTo(lat,lon,k) via haversine — pure JVM
    stations.json            ← generated asset (see below): [{id,name,state,lat,lon},…]
  api/TidesApi.kt            datagetter client + DTOs
  data/
    TideDatabase.kt          Room: predictions(stationId TEXT, t TEXT /*naive local*/,
                             heightFt REAL, type TEXT 'H'|'L', PRIMARY KEY(stationId,t));
                             meta via DataStore: stationId, stationName, lastFetchEpochDay
    TideRepository.kt        cache-first read; fetch window = today..today+6; prune < today−1
    TidesJob.kt              @LightJob "tides-refresh" handler — lives here, not in
                             ToolEntryPoint, since the annotation just needs a top-level
                             LightJobHandler val anywhere in the module
  ui/
    HomeScreen.kt            @InitialScreen — next tides + day strip; also the one that calls
                             LightWork.enqueuePeriodic(TidesJob) on every show, since it's the
                             first screen shown and the only place a SealedLightContext exists
                             this early — enqueuePeriodic's UPDATE policy makes that idempotent
    StationScreen.kt         typed search over the bundled index (offline) → save
tool/src/test/kotlin/.../   StationIndexTest.kt, RepositoryTest.kt   ← pure-JVM gate
```

**Station asset generation** (desktop, one-time, committed): `tools/gen-stations.sh` curls the MDAPI directory, jq-trims to the five fields, and drops NOAA's blank-state foreign holdovers (~3,076 stations, ~290 KB) — v1 is US-coastal only. NOAA leaves a handful of genuine US-territory/COFA stations blank or miscoded (Apra Harbor, Pago Pago, Saipan, Kwajalein/Majuro, Malakal) and stamps "AS" on foreign Apia; the script's id→state patch map restores the former and excludes the latter, `STATE_ZONES` carries the matching MP/MH/PW zones, and `StationIndexTest` guards all of it across regens. Station *search* is therefore fully offline; only predictions need the network. Regenerate ahead of each release.

## Behavior

- **Home:** short station name (`shortStationName` — first-comma truncation, safe against the whole directory) · "Next" headline at **subtitle scale, not Title** (115sp Title wraps the sentence to ~5 lines on the 1080px device — verified on LP3) with a "in 2h 40m" subline (compare naive station time to naive phone time — imperfect across zones, fine for someone standing on that coast; if station state ≠ plausible phone zone, show a "Times are station-local" note instead of a wrong countdown) · today's remaining H/L rows · a 7-day strip of per-day H/L rows. Finite: seven days, then nothing to scroll.
- Cache-first always: render from Room instantly, show "updated {relative}" in Detail variant; fetch only when `lastFetchEpochDay < today` or station changed; offline + stale-but-present → render with the stamp, no error modal; offline + empty → weather-style error state.
- **Station picker:** `LightTextInputEditor` over the bundled index — ranked tiers: exact state match, then name prefix, then bare substring ("or" ⊂ every "Harbor", so an unranked "OR" query returned zero Oregon stations); top 12 results, finite. First launch lands here (`canCancel=false`). Changing station wipes the other station's rows.
- **`@LightJob "tides-refresh"`:** if `lastFetchEpochDay < today`, fetch and store (reuses `TideRepository.loadTides`, so a run on an already-fresh day costs nothing beyond a read). Confirmed: `LightWork.enqueuePeriodic(context, key, repeatInterval, tag)` exposes WorkManager's own periodic API directly (15-minute floor) — no need to hand-roll a daily reschedule. Scheduled from `HomeScreen` at 24h, not from `ToolEntryPoint` (see Architecture note above). Job failure is invisible-by-design; the on-open path always self-heals — and the job itself distinguishes a permanent NOAA error (bad station/query — resolves to `Error`, resumes the normal daily cadence) from a transient HTTP/network failure (`Retry`, so WorkManager backs off and tries again soon), so a dead station doesn't get hammered forever.
- Settings: exactly one — units ft/m (**default ft**, since v1 is NOAA/US; keep the settings-default-off spirit by adding nothing else). `units=english` fetch + local conversion for display, or refetch metric — convert locally, simpler. Plus a non-interactive fine-print footer, mirroring weather's attribution slot: "Tide predictions provided by NOAA (US stations)." — states source and US scope in-product.
- Monochrome discipline: `LightTheme`/`LightThemeTokens`, dual palette, no color literals; H/L distinguished by ▲/▼ glyphs and weight, not color.

## Milestones · definitions of done

- **M0 Phase 0** — curl both endpoints, pin real JSON fixtures into `test/resources`; confirm PAT property names + `minSdk`; generate `stations.json`.
- **M1 Data core (pure JVM)** — `:tool:test` green: DTO parsing from the real fixtures; haversine nearest-k against 5 hand-checked coastal points; repository cache logic (fresh/stale/offline matrix) over an in-memory fake; naive-time parsing/formatting round-trip. **No UI before green.**
- **M2 UI on AVD** — first-run picker → Home; airplane-mode relaunch renders from cache with stamp.
- **M3 Device + job** — LP3 over cellular; `@LightJob` verified to top up (log + inspect Room after a day).
- **M4 Polish + submission** — US-only copy in README/listing, license, defense paragraph.

## Vetting defense (seed)

Tides fetches a seven-day table of public-domain NOAA predictions for one user-chosen station, once a day, and renders it as a finite list. No account, no key, no feed, no infinite anything — the screen ends where the week ends. It is the weather example's architecture pointed at the coast, for people whose "go outside" depends on the water.

## Sharp edges

- The naive `lst_ldt` timestamp is the whole timezone story — never `Instant`-ify it. The one place phone-clock meets station-clock (the countdown) is replaced by a "Times are station-local" note rather than converted when the phone's zone is implausible for the station's state.
- `datagetter` returns `{"error":{"message":…}}` with HTTP 200 for bad stations — check for the error key, not just status.
- Heights are strings in the JSON (`"5.213"`) — parse defensively.
- A handful of directory stations are subordinate/sparse; if a fetch returns < 2 events for today, show them anyway — never fabricate.
- Resist a tide *graph* in v1 — hilo interval keeps payloads tiny and the UI honest; curves invite the six-minute-interval product and a chart library you don't have.
