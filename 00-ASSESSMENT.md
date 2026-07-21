# LP3 Tool Ideas — Permissibility × Feasibility Assessment

Date: 2026-07-20 · Basis: Light SDK **July 2026 snapshot, actual source** (not the README) — `plugin/LightSdkPlugin.kt`, `plugin/LightToolMetadata.kt`, `sdk/shared/LightServiceMethod.kt`, `sdk/client/*`, `sdk/ui/LightQrCodeScanner.kt`, `examples/weather`, `examples/authenticator`, and the sample ringtone tool.

Eight ideas assessed: Ringtone Studio, QR Drop, Correspondence Chess, Cycle Tracker, Parking Pin, Sun & Sky, Ride Computer, Tides.

---

## 1. The ground truth that decides everything

Three findings from the source reorder this whole list. Every per-project brief repeats the parts it depends on; this is the consolidated view.

### 1.1 The sandbox is a hard compile-time wall, not a policy suggestion

`LightSdkPlugin` scans tool source at build time and **fails the build** on:

- Blocked imports: `android.app.*`, `android.content.Context` / `Intent` / `ComponentName` / `BroadcastReceiver` / `ContentProvider` / `ServiceConnection`, `LocalContext` / `LocalView` / `LocalLifecycleOwner`, `ComponentActivity`, `setContent`, `androidx.appcompat.*`, all reflection (`java.lang.reflect.*`, `kotlin.reflect.*`).
- Blocked patterns: `getSystemService(`, `startActivity(`, `startService(`, `bindService(`, `registerReceiver(`, `contentResolver`, `as Activity`, `.javaClass`, `Class.forName`, `getDeclaredMethod/Field`.
- Dependencies outside `ALLOWED_DEPENDENCIES` (exact set): kotlin-stdlib/-test, `androidx.compose.*`, activity-compose, androidx.annotation, kotlinx-coroutines, androidx.lifecycle, androidx.datastore, okhttp, **io.ktor (client AND server)**, kotlinx-serialization, kotlinx-io, unifiedpush connector, core-splashscreen, lp3keyboard, **androidx.room**, **androidx.work**, androidx.startup, anki-android-backend. File/jar deps rejected; resolved-graph substitution attacks detected.
- A hand-written `AndroidManifest.xml` (metadata lives only in `lighttool.toml`).

Notably **not** blocked: `android.media.*` (Context-free classes — `AudioTrack`, `AudioRecord` — survive), `LocalHapticFeedback`, material3, `androidx.compose.ui.window.Dialog`, `java.util.zip`, `java.time`. `kotlinx-datetime` is NOT allowlisted — use `java.time`.

### 1.2 Permissions allowlisted ≠ capabilities reachable

`LightToolPolicy.ALLOWED_PERMISSIONS` = INTERNET, ACCESS_NETWORK_STATE, WAKE_LOCK, VIBRATE, POST_NOTIFICATIONS, CAMERA, RECORD_AUDIO, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION.

But a permission is only useful if tool code can reach the capability behind it, and in this snapshot:

| Permission | Reachable from tool code today? | Why |
|---|---|---|
| INTERNET / NETWORK_STATE | **Yes** | Ktor/OkHttp allowlisted; `examples/weather` hits open-meteo.com |
| CAMERA | **Yes, QR only** | via SDK-provided `LightQrCodeScanner` composable (CameraX/MLKit live inside `sdk:ui`, which is exempt from the scan; tools cannot declare camerax themselves) |
| RECORD_AUDIO | **Probably** | `android.media.AudioRecord` is Context-free and `android.media.*` isn't blocked — untested, verify on device |
| VIBRATE | Moot | `LocalHapticFeedback` works without it |
| **ACCESS_FINE/COARSE_LOCATION** | **NO** | `getSystemService` blocked → no `LocationManager`; no fused-location dep allowlisted (non-Play device anyway); **no SDK location wrapper exists anywhere in the snapshot** |
| **WAKE_LOCK** | **NO** | `PowerManager` needs `getSystemService`; no `LocalView.keepScreenOn` path (LocalView blocked); no SDK wrapper |
| **POST_NOTIFICATIONS** | **NO** | `NotificationManager(Compat)` needs Context/getSystemService; no SDK wrapper; consistent with the known LightOS stance (surface state on open, not via notifications) |

The generic permission plumbing (`GetPermission` → `Unknown/BlockedByServer/Granted/Denied`, `RequestPermissionComponent`, the emulator's grant Activity) already handles *arbitrary* permission names, and `PERMISSION_IMPLIED_FEATURES` maps the location permissions to GPS hardware features. **Location/wake-lock/notification access is plainly planned but not shipped.** Standing instruction for Claude Code, every SDK update: `rg -in "location|gnss|wakelock|keepScreenOn|notification" sdk/ plugin/ --type kotlin` and re-run the assessment of the gated projects below.

### 1.3 Push is real, and better than hoped

LightOS **is itself a UnifiedPush distributor** (`LightPushDistributor` in `sdk:server`; `UnifiedPush.saveDistributor(this, serverPackage)` is forced in `LightSdkApplication`). Two channels: a local IPC channel, and a **remote channel with VAPID web-push** (`LIGHT_VAPID_KEY` BuildConfig) gated on `LightEntryPoint.enablePushNotifications = true`. Push credentials stream to the tool via `onToolCreate(serverData: StateFlow<LightServerData?>)` (send the endpoint to your backend); payloads arrive in `onPushNotification(data: ByteArray)`. The pre-installed-distributor problem that kills UnifiedPush on normal Android does not exist here.

Caveat: nothing indicates delivery produces a *user-visible* nudge — treat push as a silent background state-warmer and surface on open. `LightWork` (`@LightJob` handlers over androidx.work, non-time-sensitive) exists for scheduled background refresh.

### 1.4 Other verified surfaces (exact shapes, use verbatim)

- `SetRingtone`: `callRemoteServiceMethod(LightServiceMethod.SetRingtone, SetRingtone.Request(type = 1, uri = fileShare.getUri("ringtones/x.wav").toString()))` → `LightResult<Unit>`. Sample tool does exactly this; server imports `RingtoneManager` (type 1 = ringtone; 2/notification and 4/alarm unverified). **The Ringtone Studio hook is confirmed end-to-end.**
- `LightFileShare`: `list(dir)`, `getUri(path)`; docs: "files written here can be read by LightOS via a content provider (e.g., ringtones, wallpapers)". The *write* path needs a one-minute source read (see briefs).
- `GetToken` → `{token: String}` (server issues per-UID UUID session tokens — a session credential, **not** a stable identity; don't build identity on it).
- `LightQrCodeScanner(onScanned, onBack, title)` client wrapper handles the CAMERA permission dance itself. One-shot per composition (internal `AtomicBoolean` latch) — chained scanning = force recomposition via `key(n) { ... }`.
- Screens/VMs: `@InitialScreen`, `LightScreen<R, VM>` + `LightViewModel<R>` (`onScreenShow`, `onBackPressed`), `navigateTo(::Screen) { result }`, `goBack(result)`; SDK owns the back stack/bar. `SealedLightContext` exposes exactly `dataStore` (shared `DEFAULT_DATASTORE`), `filesDir`, `fileShare`.
- UI kit: `LightTheme` + `LightThemeController.colors` (dual palette — **follow it**, don't hardcode black; the panel is color OLED and monochrome is our job), `LightThemeTokens`, `LightText` (Heading/Subheading/Paragraph/Copy/Detail), `LightTopBar`/`LightBottomBar`/`LightBarButton`, `LightIcons`, `lightClickable`, `gridUnitsAsDp()`, `LightTextInputEditor` (see `examples/weather` for the full typed-input pattern).
- `lighttool.toml`: `id` (permanent, globally unique), `label`, `versionCode`, `versionName`, `permissions` (allowlist only), `serverPackage` = `com.thelightphone.sdk.emulator` on AVD / `com.lightos` on the LP3.
- Toolchain: Gradle 9.0.0 wrapper · AGP 8.12.3 · Kotlin 2.3.20 · Compose BOM 2026.03.01 · Ktor 3.4.2 · Room 2.7.0 · Work 2.10.0 · JVM 17 · compileSdk/targetSdk 36 · GitHub `read:packages` PAT required (README's env-var names are wrong — read the gradle scripts).

---

## 2. Verdicts

| # | Idea | Permissibility (Tool Library policy) | Feasibility (this SDK snapshot) | Effort | Verdict |
|---|------|---|---|---|---|
| 1 | **Ringtone Studio** | Clean. Finite by nature, no feed, no category overlap. Only tool that personalizes the phone itself. | **High.** SetRingtone + fileShare + AudioTrack all source-verified. One unknown: fileShare write path (trivial to resolve). | M | **Build — flagship** |
| 2 | **QR Drop** | Clean. Stores finite user-initiated text; no browsing, no rendering of remote content, no feed. Defense one-pager still worth writing (reader-adjacent). | **High.** Scanner is a first-class SDK composable; chunk protocol is pure Kotlin; companion page is off-phone (unconstrained). | M | **Build — infrastructure** |
| 3 | **Correspondence Chess** | Good, with care: social-without-feed is philosophically on-brand; spec **no chat** to stay clear of messaging category; relay must be FOSS + self-hostable for non-commercial cleanliness. | **Medium.** Push architecture verified (§1.3) — the hard part exists. Costs: chess legality engine (well-trodden, perft-testable), a ~200-line relay Tyler must host, GetToken is *not* identity (roll game-scoped secrets). No visible nudge → poll-on-open + silent push warm. | L | **Build later — gated on ops appetite** |
| 4 | **Cycle Tracker** | Clean *because* of the design: offline, on-device, open-source; the privacy story is the submission defense. Health-adjacent → in-app "estimates only, not for contraception/medical decisions" copy is mandatory; v1 predicts period starts only, no fertility-window claims. | **High.** Pure local CRUD: Room + java.time + Compose calendar. "Discreet notification" from the pitch is **cut** (§1.2) — surface on open. | M | **Build** |
| 5 | **Parking Pin** | Clean (no maps, text output only). | **BLOCKED.** The entire product is a live GPS read, and no location path exists (§1.2). Nothing to build until the SDK ships one. | S (once unblocked) | **Spec'd + parked** |
| 6 | **Sun & Sky** | Clean. Zero category risk; go-outside ethos. | **High with one pivot:** auto-location is gated, so v1 uses the weather example's typed-location/geocode pattern (one-time setup, then pure offline math). ISS passes need TLE fetch + an SGP4 port — deferred to a stretch milestone; sun/moon/twilight/planets are hand-rollable and pure-JVM testable. | M | **Build — best vetting submission** |
| 7 | **Ride Computer** | Fine as a category (community GPSLogger fork precedent). Battery honesty required. | **BLOCKED twice:** continuous GPS (no location path) *and* screen-on/wake-lock (no path). | L (once unblocked) | **Spec'd + parked** |
| 8 | **Tides** | Clean; NOAA is free/public, no key, non-commercial by nature. US-coastal-only in v1 (say so in the listing). | **High.** Same architecture as `examples/weather` (Ktor + DataStore/Room cache + typed station pick or geocode-nearest from a bundled station list). `@LightJob` daily refresh. | S–M | **Build — fastest ship** |

Portfolio note: `examples/authenticator` now exists in the SDK — the 2FA idea from the earlier shortlist has been claimed by an official example. Verify-before-trust keeps paying.

## 3. Recommended order

1. **Tides** — smallest network tool; exercises Ktor + cache + `@LightJob` with the weather example as a template. A confidence rep for Claude Code.
2. **Sun & Sky** — pure-JVM math engine with hard test vectors; strongest Tool Library submission (zero risk, high charm) alongside Light Wiki in the Aug–Sep window.
3. **Ringtone Studio** — the flagship; resolve the fileShare write path in Phase 0, then it's a clean run. Second submission candidate.
4. **QR Drop** — infrastructure that makes every later tool (and daily life) better; companion page ships on GitHub Pages.
5. **Cycle Tracker** — high want, unclaimed; take the time to get the copy and deletion story right.
6. **Correspondence Chess** — when Tyler wants to run a relay.
7. **Parking Pin / Ride Computer** — the moment `rg -i location sdk/` stops coming back empty.

For the vetting window: submit 2–3 polished tools, not 6 rough ones. Wiki + Sun & Sky + (Ringtone Studio or Tides) is the strongest slate; each brief ends with its defense paragraph seed.

## 4. Shared Phase-0 (first Claude Code session per repo)

1. Read actual PAT property names from the SDK's gradle scripts; confirm build with `serverPackage = com.thelightphone.sdk.emulator`, AVD API 34 arm64-v8a non-Play.
2. Confirm `minSdk` from `tool/build.gradle.kts` (expected 34) — briefs assume `java.time` without desugaring.
3. `rg -n "class LightFileShare" -A 40 sdk/` — pin the write path.
4. Re-run the §1.2 capability grep if the SDK snapshot has moved.
