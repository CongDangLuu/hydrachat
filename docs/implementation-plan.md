# HydraChat — Implementation Plan: Stability, System Bubbles, Gemini Proxy

Companion to the product/design doc [`Nhắc uống nước.md`](Nh%E1%BA%AFc%20u%E1%BB%91ng%20n%C6%B0%E1%BB%9Bc.md).
This plan covers the work needed to get the current Android MVP from "feature-complete on paper" to
"safe to run on modern Android and safe to release".

## 1. Current status (as of commit `4ef8470`)

| Area from the design doc | Status | Where |
|---|---|---|
| Conversational onboarding (persona → weight → work hours → cup → permissions, welcome bubble) | Done | `ui/OnboardingScreen.kt` |
| Floating chat head (WindowManager overlay, unread badge, preview, sound, edge snap, drag-to-dismiss) | Done | `bubble/BubbleService.kt`, `bubble/BubbleUi.kt` |
| Mini chat panel (~40% screen) with quick replies (cup / 100 ml sip / snooze 20 min), auto-collapse 1.5 s | Done | `bubble/BubbleUi.kt` |
| Exact alarms with inexact fallback, re-armed after reboot / time change | Done | `reminder/ReminderScheduler.kt`, `BootReceiver` |
| Anti-annoyance: Do Not Disturb, landscape, user-selected apps in foreground → defer 10 min | Done | `reminder/ContextDetector.kt` |
| Gemini structured output + 5-minute WorkManager prefetch | Done | `ai/GeminiClient.kt` (plain REST; the `generativeai` SDK in the doc is deprecated) |
| Offline line library (200+ lines) | Done | `assets/offline_lines.json` — 4 personas × 40 + 2 packs × 20, 40 quests |
| In-app tabs: chat & plant / quests & store / stats & settings | Done | `ui/*Screen.kt` |
| Fallback when overlay permission is denied | Partial | Plain notification with action buttons only — **no Android Bubbles API** |
| API key protection (backend proxy + App Check) | Missing | Users paste their own key (stored in SharedPreferences) |
| Tests | Minimal | Only `HydrationMathTest` |
| iOS | Out of scope | Native Android only |

### Risks found during code review
1. **Build may be broken locally** — the Gradle wrapper had been bumped to 9.7.1 (uncommitted) while the
   project uses AGP 8.7.3. Decision: stay on Gradle 8.11.1.
2. **Possible crash on Android 12+/15** — if the user has not granted exact alarms (denied by default on
   Android 14+), the reminder fires as an inexact alarm and `BubbleService` is started from the
   background. That can throw `ForegroundServiceStartNotAllowedException`, which nothing catches today.

## 2. Decisions

| Topic | Decision |
|---|---|
| Gradle | Keep Gradle 8.11.1 + AGP 8.7.3; revert the wrapper bump |
| Backend for the Gemini key | Firebase Cloud Functions (2nd gen callable) + Firebase App Check |
| Bring-your-own-key | Kept as an optional setting; the proxy is used when no personal key is set |
| Backend location | `backend/` in this repository |

## 3. Phases

Each phase ends with a green `./gradlew assembleDebug testDebugUnitTest`.

### Phase 1 — Stable build
- Revert the Gradle wrapper to 8.11.1.
- Build and run unit tests; fix any root cause that shows up.
- Note: `run-emulator.sh` boots an AVD named `hydra_test`. If your AVD has a different name, boot it
  yourself first — the script skips booting when an emulator is already running.

### Phase 2 — No crash when the bubble cannot start from the background
- `BubbleService.start()` returns `Boolean` and catches `IllegalStateException`
  (parent of `ForegroundServiceStartNotAllowedException`).
- `BubbleService.onStartCommand` guards `startForeground` the same way.
- On failure, callers (`ReminderReceiver`, `MainViewModel`) fall back to the reminder notification.

### Phase 3 — Android Bubbles API (Android 11+)
- The fallback reminder notification becomes a conversation notification (`MessagingStyle`, persona
  `Person`, long-lived dynamic shortcut) with `BubbleMetadata`.
- New `BubbleActivity` reuses the existing `ChatPanel` composable, so the system bubble looks and
  behaves like the overlay panel (drink / sip / snooze / mini quest).
- If the user disallows bubbles, Android shows it as a normal notification with the same action
  buttons, so there is no extra fallback branch.
- Settings → Permissions gets a "system bubbles" row linking to the app's bubble settings.

### Phase 4 — Gemini proxy (Firebase Cloud Functions + App Check)
**Backend (`backend/`)**
- Callable function `generateNudge` (region `asia-southeast1`, `enforceAppCheck: true`, low `maxInstances`).
- The Gemini key lives in Secret Manager (`GEMINI_API_KEY`); it is never in the app or the repo.
- Input is validated at the boundary: `personaId` (allowlist), `timeContext` (fixed format),
  `currentMl` / `targetMl` (bounded integers). The server owns the prompt, system instruction and
  response schema, so the endpoint cannot be used as a general-purpose LLM proxy.

**Android client**
- Firebase (Functions + App Check) is wired in only when `app/google-services.json` exists, so the app
  still builds and runs offline before Firebase is set up. `google-services.json` is git-ignored.
- App Check provider per build type: Play Integrity for release, Debug provider for debug builds.
- Nudge source priority: personal key → direct Gemini call; otherwise Firebase available → proxy;
  otherwise → offline library.

**One-time setup (project owner)**
1. Create a Firebase project on the **Blaze** plan (required to deploy Functions).
2. Register Android app `com.hydrachat.app`, download `google-services.json` into `app/`.
3. Enable App Check: Play Integrity for release; register the debug token printed in logcat for debug builds.
4. `firebase functions:secrets:set GEMINI_API_KEY`
5. `firebase deploy --only functions`

## 4. How to verify

- Every phase: `./gradlew assembleDebug testDebugUnitTest` passes, with or without `google-services.json`.
- Backend: `cd backend/functions && npm ci && npm run build`; optionally `firebase emulators:start --only functions`.
- On an API 35 emulator (`./run-emulator.sh --fresh`):
  - **Phase 2**: deny exact alarms (`adb shell appops set com.hydrachat.app SCHEDULE_EXACT_ALARM deny`),
    set the reminder interval to 30 min, send the app to the background, wait for the alarm → no crash;
    logcat shows the notification fallback if the service was blocked.
  - **Phase 3**: revoke the overlay permission, tap "🫧 Thử" → a bubble-capable notification appears;
    open the bubble, tap drink → today's total increases and the bubble closes after 1.5 s.
  - **Phase 4**: with Firebase configured and no personal key, prefetch goes through the proxy (logcat);
    without Firebase, the app keeps using the offline library and does not crash.

## 5. Progress

All four phases are implemented. `assembleDebug`, `compileReleaseKotlin`, `testDebugUnitTest` and
`lintDebug` (0 errors) pass without `google-services.json`; backend `npm test` passes (4 tests).

| Phase | Status | Verified on device (API 35 emulator) |
|---|---|---|
| 1 — Stable build | Done | App installs, onboarding completes |
| 2 — Background-start crash guard | Done | Not yet — needs the exact-alarm-denied, app-in-background scenario above |
| 3 — System bubbles | Done | Bubble notification posted (`flags=BUBBLE`), bubble expands into `BubbleActivity`, drink is logged |
| 4 — Gemini proxy | Done (code) | Not yet — needs the one-time Firebase setup above |

### Known follow-ups
- Android logs "Bubbles work best with icons of TYPE_URI or TYPE_URI_ADAPTIVE_BITMAP. In the future,
  using an icon of this type will be required." The persona icon is an in-memory adaptive bitmap today;
  switching to a `content://` URI (FileProvider) will be needed on a future Android release.
- `PERSONA_ROLES` in `backend/functions/src/nudge.ts` duplicates `Persona.promptRole`; keep them in sync.
- Pre-existing lint/Sonar warnings outside this plan's scope: outdated AndroidX dependencies,
  `allowBackup` deprecation, `BootReceiver` exported without a permission check, redundant `Json`
  instance in `OfflineLines.kt`.
