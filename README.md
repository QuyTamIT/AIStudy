# AI Study Mentor – BrightPath Learning

Android study-assistant app built with Kotlin, Android Views/XML, Room (SQLite), and the Google Gemini REST API. Learners can submit text or photo questions, read structured AI explanations, review saved lessons offline with spaced repetition, follow a daily study plan, take practice quizzes and revisit mistakes, play local brain games, and track progress. The interface defaults to **English** and supports switching to **Tiếng Việt** from the auth screens or profile settings.

## 1. Software Package & Release Version

| Field | Details |
| --- | --- |
| Source repository | [QuyTamIT/AIStudy](https://github.com/QuyTamIT/AIStudy) |
| Release baseline | Branch `master`, commit [`83c9705df1b3342e1876cc16cb37ee5f70e3214d`](https://github.com/QuyTamIT/AIStudy/commit/83c9705df1b3342e1876cc16cb37ee5f70e3214d) |
| Application ID & version | `com.aistudy.aistudy` — `versionName "1.0"` (`versionCode 1`) |
| Android API levels | Minimum API 33 (Android 13); compile & target API 37 (Android 17) |
| Package contents | Single Gradle module (`:app`), XML resources, Gradle wrapper `9.5.0`, JVM & Android test suites, and [DESIGN.md](DESIGN.md) |
| Local build output | `app/build/outputs/apk/debug/app-debug.apk` (from `:app:assembleDebug`) |
| APK distribution | Supplied separately with its verified SHA-256 digest |

> **Security notice:** Do not include real API keys, tokens, or real user passwords in source code, `local.properties` commits, build logs, or reports.

## 2. Environment Setup

- **IDE & JDK:** Android Studio compatible with AGP `9.3.3` and JetBrains Runtime (JBR) / JDK `25` (Java source/target compatibility `11`).
- **Android SDK:** Install Android SDK Platform `37`, matching SDK Build-Tools, and Platform-Tools (`adb`).
- **Target device:** Physical Android device (USB debugging enabled) or AVD running **API 33+**. Configure an emulator camera or sample gallery image to test image questions.
- **Gradle JDK path:** [gradle.properties](gradle.properties) pins `org.gradle.java.home` and `org.gradle.java.installations.paths` to `C:/Program Files/Android/Android Studio/jbr` with toolchain auto-detection disabled. If your JBR 25 directory differs, override both properties in `%USERPROFILE%\.gradle\gradle.properties` using forward slashes, and add `JAVA_HOME` and SDK Platform-Tools to `PATH` for CLI usage.

## 3. Environment Variables & API Configuration

Live AI answers and practice quiz generation require your own Google AI Studio key ([Gemini API key guide](https://ai.google.dev/gemini-api/docs/api-key)). Add the placeholder below to `local.properties` at the project root while keeping `sdk.dir`:

```properties
GEMINI_API_KEY=<your-gemini-api-key>
```

- [app/build.gradle.kts](app/build.gradle.kts) reads `local.properties` at build time and injects the value into `BuildConfig.GEMINI_API_KEY` (shell environment variables alone are not used).
- Keep `local.properties` local and untracked. Sync, rebuild, and reinstall after changing the key.
- Leaving `GEMINI_API_KEY` blank still lets you verify offline flows (accounts, saved library, spaced review, daily study plan, progress, and Playground games).

## 4. Build & Run

### Build from Source (PowerShell)

```powershell
git clone https://github.com/QuyTamIT/AIStudy.git
Set-Location AIStudy
git switch --detach 83c9705df1b3342e1876cc16cb37ee5f70e3214d

adb devices
.\gradlew.bat :app:assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.aistudy.aistudy/.MainActivity
```

**Android Studio:** Open the folder containing `settings.gradle.kts`, confirm Gradle JDK 25, configure `local.properties`, run **Sync Project with Gradle Files**, and launch the **app** configuration on an API 33+ device or AVD.

### Install a Supplied APK

Verify the APK's SHA-256 digest before installing:

```powershell
Get-FileHash -Algorithm SHA256 .\app-debug.apk
adb install -r .\app-debug.apk
adb shell am start -n com.aistudy.aistudy/.MainActivity
```

## 5. Controlled Demo Account

Authentication runs locally against Room with salted PBKDF2-HMAC-SHA256 password hashes; no hardcoded or real credentials exist in the codebase. Create controlled disposable accounts on the test device:

1. On **Log in**, tap **Don't have an account? Sign up**.
2. Enter fictional details, for example:
   - **Full name:** `Demo Learner A`
   - **Email:** `demo-a@example.test`
   - **Password:** a private local test password (at least 6 characters; never reuse a real password)
3. Tap **Sign up** to start a local session. For multi-user isolation testing, sign out and register a second local account (`demo-b@example.test`).

## 6. User Guide

AIStudy defaults to **English**. To switch between **English** and **Tiếng Việt**, use the language picker beside the branding header on **Log in** / **Sign up**, or tap the profile avatar on **Home** → **Student profile** → **Account & security** → **Language**.

1. **Onboarding:** Select study level (**Continue**), pick at least one subject (**Continue**), choose an explanation style, and tap **Start learning** to open **Home**.
2. **Ask AI:** Open **Ask AI** (or tap **Ask a new question** on **Home**). Type a question and/or attach an image via **Take photo** or **Gallery**, then tap **Get an answer**.
3. **Answer, Retry & Spaced Review:** Validated answers open on the **Answer** screen and save automatically (+50 XP on submission; +10 XP on first daily review). Rate recall as **Again**, **Hard**, **Good**, or **Easy** to schedule the next review (from 10 minutes to progressively longer intervals). If a network error occurs, your draft remains on screen—restore connectivity and tap **Try again**.
4. **History (`Lesson library`):** Reopen saved lessons offline, search by text, filter by subject or **★ Starred**, delete one entry (**Delete**), or remove the signed-in account's full history (**Clear all**).
5. **Practice Quiz & Daily Study Plan:** Save at least two eligible answers in one subject, then tap **Practice \<Subject\>** from an answer or filtered history to complete four AI-generated items (**Check** → **Next question** / **See results**). From **Home**, open **Today’s study plan**, **Spaced review**, or **Review mistakes** to follow the daily checklist, review due lessons, and revisit missed quiz answers with explanations.
6. **Playground & Progress:** Open **Playground** to play offline **Speed Math** (45 s) or **Memory Match** (**Start game**). Open **Progress** to view **Total XP**, streak, **Subject analysis**, **Practice accuracy**, **Review time**, and the offline **Weekly leaderboard**.
7. **Profile & Sign Out:** Tap the avatar on **Home** to open **Student profile** to **Edit** / **Save changes**, select an avatar, **Change password**, toggle **Daily study reminders**, change **Language**, or **Sign out**.

## 7. API & Architecture Note

- **App Structure:** Single-module Android Views/XML app. `MainActivity` inspects the local session (`SharedPreferences`) and routes to Login, Onboarding, or five bottom-navigation Fragments (**Home**, **Ask AI**, **Playground**, **History**, **Progress**). Profile, Answer, Study Tools (daily study plan, spaced review, and mistake review), Quiz, and Playground games run as separate Activities.
- **Persistence & Background Work:** Room SQLite (`aistudy.db`, schema version `9`) stores `users`, `profiles`, `question_answers` (including spaced-review intervals), `quiz_sessions`, `quiz_items`, and locally seeded `friends`. Game bests and daily study plan completion use user-scoped or local `SharedPreferences` state. Attached images are stored in app-private storage; `WorkManager` schedules local reminders. No Firebase or cloud sync is active.
- **Gemini REST Client (`GeminiClient`):** Calls `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent` via `HttpURLConnection` (`POST`, `x-goog-api-key` header) on a background thread with structured JSON schema output.
  - **Retry & model fallback:** Starts with `gemini-3.6-flash` and performs up to two automatic retries with exponential backoff (max 30 s delay), advancing through `gemini-3.5-flash-lite`, `gemini-flash-lite-latest`, and `gemini-3.1-flash-lite` on HTTP `429`, `500`, `502`, `503`, or `504`. Automatic retries stop immediately on recognized daily quota exhaustion.
  - **Transactional integrity:** Responses are validated for completeness and required JSON fields before inserting into Room in one transaction; failed requests create no partial lessons.

## 8. Testing & Reproducible Verification

### Automated Tests

Run from the project root in PowerShell (with an API 33+ device or disposable AVD connected for instrumentation):

```powershell
.\gradlew.bat :app:testDebugUnitTest
adb devices
.\gradlew.bat :app:connectedDebugAndroidTest
```

- At commit `83c9705`: **24 JVM test files (162 `@Test` methods)** and **12 Android instrumentation test files (52 `@Test` methods)**.
- HTML reports: `app/build/reports/tests/` and `app/build/reports/androidTests/`.
- Debug AI request traces: `adb logcat -s AIStudyAI:D`.

### Functional Smoke Test

Using commit `83c9705`, a configured `GEMINI_API_KEY`, and fresh demo account `demo-a@example.test`, run in order:

| Case | Steps | Expected Result |
| --- | --- | --- |
| 1. Empty input | On **Ask AI**, leave text and image empty and tap **Get an answer**. | Validation error shown; no lesson created. |
| 2. Text answer | Submit `Solve 7x - 5 = 23`. | Validated **Answer** opens with `x = 4`. |
| 3. Image answer | Attach a clear image of `3x - 7 = 14` via **Gallery** (blank text) and submit. | Saved **Answer** opens with `x = 7`. |
| 4. Persistence | Open both in **History**, run `adb shell am force-stop com.aistudy.aistudy`, relaunch, and reopen a row. | Both saved lessons reopen offline without regenerating. |
| 5. Progress & XP | On the same day (before quiz/games), open **Progress**. | Question count increases by `2`; total XP rises by `120 XP` (`2 × 50` ask + `2 × 10` automatic daily review). |
| 6. Offline retry | Disconnect internet on **Ask AI**, submit a question, reconnect, and tap **Try again**. | Draft stays intact; one successful retry saves exactly one new lesson. |
| 7. Account isolation | In `demo-a@example.test` **History**, cancel a **Delete**, delete one row, then confirm **Clear all**. Relaunch and sign into `demo-b@example.test`. | Account A's library is empty; Account B's saved lessons still open. |

### Reproducing a Test Record

Build from the documented commit (`83c9705`) and log the exact source revision, APK digest, and Android API level with your test report:

```powershell
git rev-parse HEAD
Get-FileHash -Algorithm SHA256 .\app\build\outputs\apk\debug\app-debug.apk
adb shell getprop ro.build.version.sdk
```
