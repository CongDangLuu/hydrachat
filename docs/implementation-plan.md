# HydraChat — Implementation Plan

Companion to the product/design doc [`Nhắc uống nước.md`](Nh%E1%BA%AFc%20u%E1%BB%91ng%20n%C6%B0%E1%BB%9Bc.md).

**Goal: release HydraChat on Google Play and the Apple App Store** from one Kotlin Multiplatform (KMP)
+ Compose Multiplatform codebase. The Android MVP exists; the foundation work (phases 1–4) is done;
the release work (phases R1–R6) is next.

## 1. Current status (as of commit `ac1cb96`)

| Area from the design doc | Status | Where |
|---|---|---|
| Conversational onboarding (persona → weight → work hours → cup → permissions, welcome bubble) | Done (Android) | `ui/OnboardingScreen.kt` |
| Floating chat head (WindowManager overlay, badge, preview, sound, edge snap, drag-to-dismiss) | Done (Android) | `bubble/BubbleService.kt`, `bubble/BubbleUi.kt` |
| Mini chat panel with quick replies (cup / 100 ml sip / snooze 20 min), auto-collapse 1.5 s | Done (Android) | `bubble/BubbleUi.kt` |
| Fallback without overlay permission: conversation notification + Android system bubble | Done (Android) | `bubble/BubbleActivity.kt`, `reminder/ReminderNotifier.kt` |
| Exact alarms with inexact fallback, re-armed after reboot / time change | Done (Android) | `reminder/ReminderScheduler.kt`, `BootReceiver` |
| Anti-annoyance: Do Not Disturb, landscape, user-selected apps in foreground → defer 10 min | Done (Android) | `reminder/ContextDetector.kt` |
| Gemini structured output + 5-minute prefetch | Done (Android) | `ai/GeminiClient.kt`, `ai/ProxyClient.kt` |
| Gemini key protection: Firebase callable + App Check | Code done, not deployed | `backend/functions` |
| Offline line library (200 lines, 40 quests) | Done | `assets/offline_lines.json` |
| In-app tabs: chat & plant / quests & store / stats & settings | Done (Android) | `ui/*Screen.kt` |
| Tests | Minimal | `HydrationMathTest`, backend `nudge.test.ts` |
| iOS app | Not started | — |
| Store readiness (targetSdk 36, signing, R8, store declarations) | Not started | — |

## 2. Decisions

| Topic | Decision |
|---|---|
| Release targets | Google Play **and** Apple App Store |
| Cross-platform approach | Kotlin Multiplatform + Compose Multiplatform; platform-specific code only where the OS differs (reminders, overlays, App Check) |
| iOS build machine | No Mac available → iOS is built, tested and signed on **GitHub Actions macOS runners** |
| Android toolchain | Upgrade to Gradle 9.x + AGP 9.5 (built-in Kotlin) + Kotlin 2.4 + targetSdk 36 (phase R1). The earlier "stay on Gradle 8.11.1" decision was only for the foundation phases |
| Backend for the Gemini key | Firebase Cloud Functions (2nd gen callable) + Firebase App Check (Play Integrity on Android, App Attest on iOS) |
| Bring-your-own-key | Kept as an optional setting; the proxy is used when no personal key is set |
| Backend location | `backend/` in this repository |
| iOS AI lines: local prefetch vs. server push | **Open** — decide in R4 (see §3.3) |

## 3. Target architecture

```
hydrachat/
├─ shared/              KMP module: domain, data, AI client, Compose UI, ViewModels
│   ├─ commonMain/
│   ├─ androidMain/
│   └─ iosMain/
├─ androidApp/          Android-only: MainActivity, overlay chat head, system bubble,
│                       AlarmManager/WorkManager, ContextDetector, Firebase + App Check
├─ iosApp/              Xcode project: hosts the shared Compose UI, local notifications,
│                       Notification Content Extension (Swift), Firebase iOS + App Check
├─ backend/             Firebase Cloud Functions (unchanged; App Check for both platforms)
└─ .github/workflows/   Linux job (Android + backend) and macOS job (iOS)
```

AGP 9 does not allow `com.android.application` and `org.jetbrains.kotlin.multiplatform` in the same
module, so the shared code must live in its own module using
`com.android.kotlin.multiplatform.library`.

### 3.1 What is shared and what is not

| Component | `shared` (commonMain) | Android | iOS |
|---|---|---|---|
| Hydration math, personas, store, quests | Yes | | |
| Database (Room KMP), settings (DataStore) | Yes | | |
| Gemini / proxy HTTP calls (Ktor) | Yes | App Check token via Play Integrity | App Check token via App Attest |
| Onboarding and the three tabs | Yes (Compose Multiplatform) | | |
| Reminder surface | | Chat head overlay, system bubble, notification | Actionable notification + Notification Content Extension |
| Scheduling | | AlarmManager + WorkManager | `UNUserNotificationCenter` local notifications |
| Anti-annoyance | | DND, landscape, hidden apps | System Focus modes only |

### 3.2 Code moves required by KMP
- `java.time` → `kotlinx-datetime`
- `HttpURLConnection` → Ktor client
- `SharedPreferences` (`SettingsStore`) → DataStore (KMP)
- `assets/offline_lines.json` → Compose Multiplatform resources
- `AndroidViewModel` → KMP `ViewModel` with injected dependencies

### 3.3 iOS constraints (accepted product limits)
- **No chat heads over other apps on iOS.** The iOS reminder is an actionable notification with the
  same quick replies (drink a cup / sip / in a meeting), as already planned in design doc §2.2.
- **Notification content is fixed when it is scheduled**, and iOS keeps at most 64 pending local
  notifications. AI lines must be prefetched while the app runs (or in a best-effort background
  refresh); otherwise offline lines are used. Fresh AI lines for every reminder would need server
  push (FCM → APNs with a Notification Service Extension). Decision deferred to R4.
- **No "hide when app X is open" on iOS** — there is no public API to read the foreground app.

## 4. Phases

### Foundation (done)

| Phase | Commit | Summary |
|---|---|---|
| 1 — Stable build | — | Reverted an uncommitted Gradle 9.7.1 wrapper bump that did not match AGP 8.7.3 |
| 2 — Background-start crash guard | `fd4adfa` | `BubbleService.show()` catches foreground-service start refusals and falls back to the notification |
| 3 — System bubbles | `143fdae` | Conversation notification with `BubbleMetadata`; `BubbleActivity` reuses `ChatPanel` |
| 4 — Gemini proxy | `5821173` | Firebase callable `generateNudge` with App Check and input allowlist; Android client wiring |

### Release

Every phase ends with green builds and tests on CI (once R3 exists) and a regression pass on the
Android emulator.

#### R1 — Android toolchain and targetSdk 36
Google Play requires new apps and updates to target Android 16 (API 36) since 2026-08-31, and KMP +
Compose Multiplatform need a current Kotlin.
- Gradle 9.9.1 (AGP 9.5 needs ≥ 9.6.0), AGP 9.5.1, Kotlin 2.4.21, KSP 2.3.14, JDK 17.
- AGP 9 built-in Kotlin: remove `org.jetbrains.kotlin.android`, replace `kotlinOptions` (JVM target
  follows `compileOptions`). AGP 9.5 pulls KGP 2.2.10 / KSP 2.2.10-2.0.2 by default; the versions
  declared in the root `build.gradle.kts` win conflict resolution (check with `./gradlew buildEnvironment`).
- `targetSdk` 36, `compileSdk` 37 (androidx.core 1.19 requires it); upgrade AndroidX, Compose BoM,
  Room, WorkManager, Firebase BoM 35.2.0.
- Review Android 16 behavior changes against the app (edge-to-edge, predictive back, large-screen
  orientation rules, 16 KB native library alignment).

Outcome: done. Android 16 review found no required code change; the only deprecation surfaced by
compileSdk 37 (`AppOpsManager.unsafeCheckOpNoThrow`) was replaced with `checkOpNoThrow`. Bundled
native libraries (`libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`) are 16 KB aligned.
Still to check on an Android 16 device: `BubbleActivity` under enforced edge-to-edge.

#### R2 — KMP restructure (Android only)
- Create `shared`; move domain, data, AI client, UI and ViewModels into it (see §3.2).
- Rename `app/` → `androidApp/`; it keeps only Android-specific code.
- **Android must behave exactly as before.** Before moving code, add tests for `HydraRepository`,
  `ReminderScheduler` logic and the nudge source selection; finish with an emulator regression pass.

#### R3 — Continuous integration
- GitHub Actions, Linux job: `assembleDebug`, unit tests, lint, backend `npm test`.
- GitHub Actions, macOS job: compile `shared` for iOS (simulator target) and run its tests.
- Must exist before R4, because iOS code cannot be built locally.

#### R4 — iOS app
- `iosApp` Xcode project hosting the shared Compose UI.
- Local notifications with an actionable category (cup / sip / snooze), handled without opening the
  app; rolling scheduling window under the 64-notification limit.
- Notification Content Extension (Swift) for the persona look.
- Firebase iOS SDK + App Check (App Attest); Gemini only through the proxy.
- Decide local prefetch vs. server push for AI lines (§3.3).
- Simulator tests on the macOS CI job.

#### R5 — Backend production (can run in parallel)
- Firebase project on the Blaze plan; deploy `generateNudge`.
- App Check registration: Android with the **App Signing** key SHA-256 from Play Console (not the
  upload key); iOS App Attest with the Apple Team ID.
- Gemini paid tier decision (all users share one key's quota), budget alerts, function monitoring.

#### R6 — Store submission
- **Google Play:** upload key + Play App Signing (keystore never in the repo), R8 enabled and tested,
  foreground service `specialUse` declaration with demo video, Data safety form, privacy policy URL,
  in-app reporting for AI-generated content, closed test (personal accounts: 12 testers for 14 days),
  real-device testing on Xiaomi / Oppo / Samsung.
- **App Store:** signing certificates and profiles managed on CI (fastlane match), App Store Connect
  listing, privacy nutrition labels + `PrivacyInfo.xcprivacy`, TestFlight, App Review.

**Order:** R1 → R2 → R3 → R4 → R6. R5 can run at any time.

## 5. Verification

- Android: `./gradlew assembleDebug compileReleaseKotlin testDebugUnitTest lintDebug`, with and without
  `app/google-services.json`.
- Backend: `cd backend/functions && npm ci && npm test`; optionally `firebase emulators:start --only functions`.
- On an API 35/36 emulator (`./run-emulator.sh --fresh`):
  - **Background-start guard**: deny exact alarms (`adb shell appops set com.hydrachat.app SCHEDULE_EXACT_ALARM deny`),
    set the reminder interval to 30 min, send the app to the background, wait for the alarm → no crash;
    logcat shows the notification fallback if the service was blocked.
  - **System bubbles**: revoke the overlay permission, enable bubbles
    (`adb shell cmd notification set_bubbles com.hydrachat.app 1`), tap "🫧 Thử" → bubble appears;
    open it, tap drink → today's total increases and the bubble closes after 1.5 s.
  - **Proxy**: with Firebase configured and no personal key, prefetch goes through the proxy (logcat);
    without Firebase, the app keeps using the offline library and does not crash.
- iOS (from R3/R4): macOS CI job green; simulator tests for scheduling and notification actions.

## 6. One-time setup and accounts

| Item | Cost | Needed before |
|---|---|---|
| Firebase project on the Blaze plan | Pay as you go | R5 |
| Google Play developer account | USD 25 once | R6 |
| Apple Developer Program | USD 99 / year | R4 (signing); simulator-only work can start earlier |
| GitHub Actions macOS minutes | Private repos bill macOS at 10× Linux minutes | R3 |

Firebase steps: register `com.hydrachat.app` (and the iOS bundle ID in R4), put `google-services.json`
in the Android app module, enable App Check, enable **Anonymous** sign-in (Authentication) and create
the Firestore database (the proxy keeps a per-user daily quota of 30 nudges in `nudgeQuota/{uid}`),
`firebase functions:secrets:set GEMINI_API_KEY`, `firebase use --add`,
`firebase deploy --only functions,firestore:rules`.

## 7. Progress

| Phase | Status | Verified on device |
|---|---|---|
| 1–4 Foundation | Done | Phase 3 on API 35 emulator; phases 2 and 4 not yet (see §5) |
| R1 Android toolchain | Done | API 35 emulator: app starts, existing Room data opens, system bubble posts; no API 36 image tested yet |
| R2 KMP restructure | Not started | — |
| R3 CI | Not started | — |
| R4 iOS app | Not started | — |
| R5 Backend production | Not started | — |
| R6 Store submission | Not started | — |

## 8. Risks and follow-ups

**Risks**
1. No Mac: every iOS change is a CI round trip, and Notification Extension debugging is hard. Borrow
   or rent a Mac at least for R4.
2. R2 is a large refactor and can regress Android features; mitigated by tests written before moving code.
3. Play may reject the `specialUse` foreground service; fallback is the system bubble path, which needs no FGS.
4. Gemini quota and cost grow with users.

**Follow-ups**
- Android logs "Bubbles work best with icons of TYPE_URI or TYPE_URI_ADAPTIVE_BITMAP"; switch the
  persona icon to a `content://` URI (FileProvider) before Android requires it.
- `PERSONA_ROLES` in `backend/functions/src/nudge.ts` duplicates `Persona.promptRole`; keep them in sync
  (R2 can make the shared module the single source for the client side).
- Pre-existing warnings: `BootReceiver` exported without a permission check, `allowBackup`
  deprecation, redundant `Json` instance in `OfflineLines.kt`.
