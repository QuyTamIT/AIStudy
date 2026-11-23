# AI Study Mentor – BrightPath Learning

## 1. Project Overview

AI Study Mentor helps secondary-school students, university students and independent learners ask a study question, read an explanation and return to it later. The Android app accepts typed questions and photographs of exercises. It keeps a library and a progress record for each learner on the device. The interface is in Vietnamese. This guide includes the labels that appear on screen.

For a first installation, follow sections 6–9. The application uses Google's Gemini REST API for live AI features.

## 2. Implemented Features

| Core feature | Status | What a learner can do |
| --- | --- | --- |
| AI question submission | Implemented | Type a question, attach a camera or gallery image, or submit the image alone. Ask rejects an empty submission. |
| AI answer generation | Implemented, service-dependent | Receive an explanation adapted to the selected level and style. The client checks the response structure before saving it. A response can also ask for clarification. |
| Question history | Implemented | Reopen an answer, search question text, filter by subject or star, and delete one entry or the account's entire question history. |
| Basic progress tracking | Implemented | See saved-question counts, subject totals, starred items, XP, level, streak, review time and accuracy on attempted quiz items. |

| Supporting feature | Implementation and scope |
| --- | --- |
| Accounts and profiles | Local registration, login, logout, three-step onboarding, profile editing, avatar selection and password change. |
| Practice quiz | Generates four items from eligible saved answers in one subject. The app records attempts and scores in Room. Generation requires an AI service. |
| Playground | Speed Math and Memory Match run locally. Game results contribute to profile XP. Personal bests are stored on the device. |
| Learning reminders | WorkManager schedules local reminders. Delivery depends on the app setting, Android notification permission and background scheduling. |
| Leaderboard | Partially implemented as a social feature. The comparison uses locally seeded peers. Live multi-user rankings are unavailable. |
| Cloud synchronisation and offline submission queue | Not implemented. Existing lessons can be read locally. A failed AI submission requires another attempt. |

## 3. Technology Stack

| Component | Project configuration |
| --- | --- |
| Language and IDE | Kotlin with Android Gradle Plugin (AGP) built-in Kotlin support. Android Studio Quail 3 (2026.1.3 Patch 1) was recorded in the development evidence. |
| UI | Android Views, XML layouts, Activities and AndroidX Fragments. AppCompat 1.8.0, Material Components 1.14.0, RecyclerView 1.4.0 and ConstraintLayout 2.2.2. |
| Android SDK | Minimum API 33 (Android 13). Compile and target API 37 (Android 17). |
| Build | Gradle Wrapper 9.5.0, AGP 9.3.3 and KSP 2.3.12. Gradle daemon JDK 25, JetBrains Java toolchain 25, Java source and target compatibility 11. |
| Persistence and background work | Room 2.7.2 (SQLite), SharedPreferences, app-private image files and WorkManager 2.11.2. |
| Networking | Platform `HttpURLConnection` and JSON over HTTPS to the Google Gemini REST API. |
| Testing | JUnit 4.13.2 and `org.json:json:20250517` for JVM tests. AndroidX Test JUnit 1.3.0, Espresso Core 3.7.0 and `AndroidJUnitRunner` for device tests. |
| Version control | Git and GitHub. Section 11 identifies the source revision. |

Dependency versions and build settings are in the [version catalog](gradle/libs.versions.toml), [app build script](app/build.gradle.kts) and [wrapper configuration](gradle/wrapper/gradle-wrapper.properties).

## 4. Project Structure

```text
app/
  build.gradle.kts
  src/
    main/
      AndroidManifest.xml
      java/com/aistudy/aistudy/
        MainActivity.kt                 Launcher and tab routing
        auth/  onboarding/  profile/    Local account and learner settings
        home/  navigation/              Home screen and bottom navigation
        ask/  answer/                   Input, GeminiClient, validation and answer display
        history/  progress/  xp/        Saved lessons, account metrics and XP rules
        playground/  quiz/  notify/     Games, practice sessions and reminders
        database/                       Room entities, DAOs and schema migrations
      res/                              XML layouts, strings, menus and graphics
    test/                               Local JVM tests
    androidTest/                        Tests that run on Android
gradle/                                 Wrapper, daemon settings and version catalog
docs/                                   Development notes from individual phases
DESIGN.md                               UI design reference
gradlew.bat                             Windows Gradle entry point
settings.gradle.kts                     Includes the single :app module
```

## 5. System Architecture

`MainActivity` reads the local session and routes the learner to Login, Onboarding or Home. Its five tabs replace Fragments in one container. Profile, Answer, Quiz and the two games open as separate Activities. Registration stores a salted PBKDF2-HMAC-SHA256 password hash in Room. SharedPreferences holds the signed-in user's ID.

**From Ask to a saved lesson**

1. `AskFragment` checks for text or an image, a configured key and an active account. It prepares any image and adds the learner's level and preferred explanation style to the prompt.
2. `GeminiClient` sends the request on a background thread. The client checks for a complete, non-empty response. Ask parses its JSON and checks fields including subject, difficulty, answer content and final answer.
3. After validation, the app copies any attachment to private storage. A Room transaction rechecks the active user, inserts the question and answer, records daily activity and awards 50 XP.
4. Once the transaction succeeds, Ask clears the draft and opens `AnswerActivity` with the saved row ID. Opening a lesson for the first time that day also marks it reviewed automatically and awards 10 XP (`XpRules.REVIEW`). Library and Progress read the account's records from the same database. A failed request creates no completed lesson.

The Room database (`aistudy.db`, schema version 8) holds users, profiles, question/answer records, quiz sessions and items, and local leaderboard entries. Game bests use SharedPreferences keys that include the user ID. The profile package contains a Firestore payload formatter. Account authentication and persistence run locally. The application has no active Firebase Authentication, Firestore transport or Cloud Functions integration.

## 6. Development Environment

- Install Android Studio compatible with AGP 9.3.3. Select a JetBrains JDK 25 for Gradle. The recorded build used JetBrains Runtime (JBR) 25.0.2.
- In SDK Manager, install Android SDK Platform 37, the required SDK Build-Tools and Platform-Tools (including ADB). For an emulator, install Android Emulator and a compatible system image.
- Use an emulator or USB-debugging-enabled device running API 33 or higher. For camera testing, configure an emulator camera or use a device with a camera. For gallery testing, place a readable exercise image on the device.
- Use the included wrapper. The first sync needs access to the Gradle distribution, Google Maven and Maven Central. Live generation needs HTTPS access to the Gemini API. Local account, Library and Playground operations can run offline.

The repository's [Gradle properties](gradle.properties) point to a Windows Android Studio JBR installation and disable toolchain auto-detection. If your installation differs, set `org.gradle.java.home` and `org.gradle.java.installations.paths` to your JBR 25 directory in your **user-level** Gradle properties (normally `%USERPROFILE%\.gradle\gradle.properties`). Use forward slashes in the paths. For command-line use, set `JAVA_HOME` to that JDK and make SDK Platform-Tools available on `PATH`.

## 7. Installation and Setup

1. In PowerShell, clone a **new** copy and select the documented source revision. Alternatively, download the source archive for the commit in section 11 and extract it.

   ```powershell
   git clone https://github.com/QuyTamIT/AIStudy.git
   Set-Location AIStudy
   git switch --detach 33ba6c550c5beaa7f46c3ddfa2209fccaddb17ac
   ```

2. In Android Studio, choose **Open** and select the folder containing `settings.gradle.kts`. Select the Gradle JDK from section 6. Allow Studio to locate the SDK and create `local.properties`.
3. For live AI, configure the Gemini key in section 8. Keep the existing `sdk.dir` entry. Choose **Sync Project with Gradle Files**.
4. Start an AVD or connect an authorised device. Select the **app** run configuration and press **Run**. For the equivalent command-line build and installation, run these commands at the project root with one device connected.

   ```powershell
   adb devices
   .\gradlew.bat :app:assembleDebug
   adb install -r .\app\build\outputs\apk\debug\app-debug.apk
   adb shell am start -n com.aistudy.aistudy/.MainActivity
   ```

5. A fresh install opens **Đăng nhập** (Login). A returning user with a saved session reaches Home or resumes onboarding. Complete section 9 to verify the first-use path.

**Installing a supplied APK.** Obtain the package and its SHA-256 digest. For a file named `app-debug.apk`, check the digest with `Get-FileHash -Algorithm SHA256 .\app-debug.apk`, then install with `adb install -r .\app-debug.apk` and use the same launch command. See section 11 for download availability.

## 8. AI Configuration

Create a key for your own authorised project in Google AI Studio. Put it in `local.properties` at the project root, replacing the placeholder below. The build reads this file directly, so setting a shell environment variable alone has no effect. [Google's Gemini API key guide](https://ai.google.dev/gemini-api/docs/api-key)

```properties
GEMINI_API_KEY=<your-valid-Gemini-API-key>
```

Sync, rebuild and reinstall after changing the key. Gradle writes the value into `BuildConfig.GEMINI_API_KEY` at build time, so the resulting APK contains it. Keep `local.properties` private and use credential-bearing builds on your own test device. Omit the key to explore the local features.

The app declares `android.permission.INTERNET`. `GeminiClient` uses `HttpURLConnection` to send questions and any attached images via HTTPS POST to `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent` with an `x-goog-api-key` header. Live generation requires a valid key, access to the requested model, available quota and device connectivity.

Model selection is defined in `GeminiClient.CANDIDATE_MODELS`. A request starts with `gemini-3.6-flash`. Up to two automatic retries can advance to `gemini-3.5-flash-lite` and `gemini-flash-lite-latest`. The fourth entry, `gemini-3.1-flash-lite`, is beyond that retry limit. Retries apply to HTTP 429, 500, 502, 503 and 504 with backoff. The client stops automatic retries for recognised quota exhaustion or a requested delay over 30 seconds. Ask shows an error and offers **Thử lại** when the request can be retried manually.

## 9. User Guide

AIStudy defaults to English. To change the interface language, open the profile from Home and go to **Account & security** (**Cài đặt tài khoản & Bảo mật**) → **Language** (**Ngôn ngữ**). Choose **English** or **Tiếng Việt**. Android remembers the selection when you reopen AIStudy. Saved questions and answers keep their original language. The step labels below use Vietnamese.

1. **Create a controlled test account.** On **Đăng nhập**, select **Chưa có tài khoản? Đăng ký ngay**. Enter a fictional name, a unique test address such as `learner-a@example.test`, and a private password of at least six characters. Tap **Đăng ký**. Registration starts a local session. For a returning account, enter its credentials on the same installation and tap **Đăng nhập**.
2. **Complete onboarding.** Choose the learner's level, tap **Tiếp tục**, select at least one subject, tap **Tiếp tục**, then choose an explanation style. Tap **Bắt đầu học ngay** to open **Trang chủ** (Home). To change preferences later, follow step 8.
3. **Ask a text question.** Open **Hỏi đáp** (Ask) or tap **Đặt câu hỏi mới** on Home. Type the question and tap **Nhận lời giải**.
4. **Ask with an image.** In Ask, tap **Chụp ảnh** to take a photo or **Thư viện** to select one. Grant the requested camera or image access. Check the preview, add text if desired and tap **Nhận lời giải**. The gallery button inside Ask selects images. The bottom tab **Lịch sử** opens saved lessons.
5. **Read the answer.** On **Lời giải**, read the explanation and final answer. The available sections depend on the response. Return to the other screens with Back. A successfully generated answer is already saved.
6. **Find and delete lessons.** Open **Lịch sử** → **Thư viện bài học**. Tap a row to reopen it. Use the search field, subject filters or **★ Đã lưu** for starred items. The star marks a favourite. To delete one question, tap its trash icon and confirm **Xóa**. **Xóa tất cả** requires confirmation and removes the signed-in account's entire question history, including entries hidden by a filter.
7. **Check progress.** Tap **Tiến độ**. Compare the total questions and subject breakdown with the lessons you saved. Quiz accuracy becomes meaningful after answering quiz items.
8. **Edit the profile.** Tap the profile avatar on Home to open **Hồ sơ học sinh**. Choose **Chỉnh sửa**, change the name or learning preferences and tap **Lưu thay đổi**. The profile also provides avatar selection, **Đổi mật khẩu** and the **Nhắc nhở học tập hàng ngày** switch. Allow Android notifications to receive reminders.
9. **Use Playground.** Open **Sân chơi** and tap **Bắt đầu chơi** on the desired card. In **Đấu Trí Siêu Tốc** (Speed Math), choose answers during the 45-second round. In **Ghép Cặp Trí Tuệ** (Memory Match), turn over cards to match terms with their definitions. Finish the game to see its result and return to Playground for personal bests.
10. **Take a quiz.** Save at least two eligible answers in the same subject. From an answer (or from a subject filter in **Lịch sử**), tap **Luyện tập môn …**. Quiz uses up to five recent records in that subject and needs at least two suitable sources. Clarification and limitation responses are excluded. Answer each item, tap **Kiểm tra**, then **Câu tiếp theo** or **Xem kết quả**. New quiz generation requires the configured service.
11. **Retry a network error.** Stay on Ask. The question and selected image remain available after a failed request. Restore the connection, wait for any countdown and tap **Thử lại** when offered. Credential errors require the configuration fix in section 8.
12. **Log out.** On **Hồ sơ học sinh** (or **Trang chủ** / **Tiến độ**), tap **Đăng xuất** and confirm. Signing in again on this installation restores access to the account's local lessons. For an isolation test, register a second fictional learner with a different test address and private password.

## 10. Testing Instructions

Run from the project root in PowerShell. JVM tests run on the development machine. Start one authorised emulator or device (API 33+) before running instrumentation. A disposable AVD keeps test accounts and database fixtures separate from personal data.

```powershell
.\gradlew.bat :app:testDebugUnitTest
adb devices
.\gradlew.bat :app:connectedDebugAndroidTest
```

The committed `33ba6c5` source contains 22 JVM test files (156 `@Test` methods) covering response validation, retry rules, password hashing, quiz logic, XP and game engines, plus 11 Android instrumentation test files (48 `@Test` methods) exercising local authentication, account ownership, Room migrations, image handling, answer loading and quiz restoration. Inspect the HTML reports under `app/build/reports/tests/` and `app/build/reports/androidTests/`, JUnit XML results under `app/build/test-results/testDebugUnitTest/`, and debug AI traces with `adb logcat -s AIStudyAI:D`. A passing automated suite alone does not establish live AI connectivity.

**Functional smoke test.** Use the source revision in section 11, the Gemini configuration in section 8 and a fresh test account (A). Record the commit, APK digest, Android API and starting question count and XP. Run the cases in order. The final case also needs a second local account (B) with its own saved lesson.

| Case | Test steps | Expected result |
| --- | --- | --- |
| Empty input | On Ask, leave the text blank, attach nothing and tap **Nhận lời giải**. | The input shows a validation error. No lesson is created. |
| Text and answer | With Gemini reachable, submit `Solve 7x - 5 = 23`. Read the explanation and substitute the answer in the original equation. | A validated answer opens. The mathematical check is `x = 4`. Record an incorrect answer as a content failure even if the request succeeds. |
| Image and answer | Attach a clear image of `3x - 7 = 14` via Gallery, leave the text blank, check the preview and submit. Test Camera separately after the progress check. | The image-only request opens a saved explanation. The mathematical check is `x = 7`. |
| Saved answer and relaunch | Open both records from **Lịch sử**. Close the app with `adb shell am force-stop com.aistudy.aistudy`, relaunch it and reopen a row. | The same account sees both lessons and can read the stored answer without regenerating it. |
| Progress | On the same day, after the two successful submissions and first views, open **Tiến độ** before taking a quiz or playing Playground. | The question count rises by two and total XP rises by 120 XP (2 × 50 XP from Ask plus 2 × 10 XP from automatic review). Check the subject counts against the subjects stored with the answers. |
| Network failure and retry | On Ask, disconnect the test device from the internet, submit a new question and stay on the screen. Restore the connection, then tap **Thử lại** when enabled. | The failed attempt retains its input. One successful retry opens one new lesson. No duplicate is saved for the failed attempt. |
| Account-scoped deletion | In A's Library, cancel deletion of one row and check it is still visible. Delete that row, check the other rows remain, then confirm **Xóa tất cả**. Relaunch, check A's unfiltered Library, then sign into B and open its saved lesson. | Cancel keeps the row. Deleting one removes only that row. After clearing and relaunching, A's Library is empty and B's lesson still opens. |

## 11. Software Package and Release Information

| Package field | Verified detail |
| --- | --- |
| Source repository | [QuyTamIT/AIStudy on GitHub](https://github.com/QuyTamIT/AIStudy) |
| Documented source | Branch `master`, application baseline [`33ba6c550c5beaa7f46c3ddfa2209fccaddb17ac`](https://github.com/QuyTamIT/AIStudy/commit/33ba6c550c5beaa7f46c3ddfa2209fccaddb17ac). The single Android module, resources, Gradle wrapper and test sources are included. |
| Application | ID `com.aistudy.aistudy`, version name `1.0`, version code `1`. Minimum API 33, target API 37. |
| Local build output | `app/build/outputs/apk/debug/app-debug.apk` from `:app:assembleDebug` (section 7). This is a debug APK for development and testing. |
| Distribution status | An APK for this source revision is pending verification for distribution. |

APK download: To be provided

For a reproducible test record, build from the documented commit using sections 6–8. Use `git rev-parse HEAD` to record the source and `Get-FileHash -Algorithm SHA256 .\app\build\outputs\apk\debug\app-debug.apk` to identify the exact APK you tested.

## 12. Known Limitations

- AI explanations can contain factual or mathematical errors. Structural validation detects malformed or incomplete responses. Check important results against the exercise or a trusted reference.
- Room records, images and preferences belong to the app installation. Uninstalling or clearing app storage can remove them. Android backup is disabled and there is no automatic cross-device sync.
- Reading a saved answer and playing the two local games work without AI access. New answers, quiz generation and new AI follow-ups require access to Gemini. A failed draft has no durable offline queue.

## 13. Troubleshooting

| Problem | Possible cause | Recommended action |
| --- | --- | --- |
| Gradle sync or dependency resolution fails | Wrong JBR path, unavailable repository or an empty offline cache | Check both JBR path settings in section 6, select JetBrains JDK 25, allow access to the configured repositories and sync with the wrapper. |
| SDK missing or device absent | Platform 37 missing, AVD stopped or USB debugging unauthorised | Install Platform 37 and Platform-Tools, start the AVD or accept the device's USB prompt, then check `adb devices`. |
| APK installation fails | Device below API 33 or an existing package signed with a different key | Use a compatible device. For a signature mismatch, use a fresh test AVD to preserve data on the original installation. |
| Gemini key missing, invalid or restricted | Wrong build-time property, stale installed APK, disabled service or key restrictions | Check `GEMINI_API_KEY` privately and verify model access for that key. Rebuild and reinstall the APK. |
| HTTP 429, server error or unusable AI answer | Provider rate limit, exhausted quota, temporary service fault or incomplete response | Follow the displayed delay, check the account quota or service status and use **Thử lại** when offered. Check the saved answer separately for correctness. |
| Image attachment fails | Permission denied, unavailable camera, unreadable image or excessive file size | Allow the requested permission or use Gallery. Try a smaller, clear JPEG or PNG. The input limit is 20 MiB and the prepared request image is limited to 2 MiB. |
| Offline error or Retry unavailable | Network is down, cooldown is active or the error requires configuration changes | Keep Ask open, restore access and wait for **Thử lại**. If the message identifies authentication or daily quota, correct the key or resolve the quota condition before submitting again. |
