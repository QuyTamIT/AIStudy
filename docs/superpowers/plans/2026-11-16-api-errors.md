# API Error Handling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Implement approved improvements 1 and 2 for consistent Gemini errors and accurate quota messaging.

**Architecture:** A small shared resource mapper handles API/network errors while
callers keep their domain-specific errors. GeminiClient parses structured quota
details and retry hints; existing trace receives only allowlisted reasons.

**Tech Stack:** Android Kotlin/XML, HttpURLConnection, org.json, JUnit 4; no new dependencies.

## Global Constraints

- Keep existing user changes to Manifest/backup files intact.
- No DB, model, prompt, layout, navigation, XP or release-logging changes.
- Preserve English UI and existing per-screen persistence/session handling.
- Never expose API keys, prompts, response bodies or provider identifiers in logs/UI.
- Do not infer quota type from unstructured provider messages or RetryInfo alone.
- Retain 2 retries / 30-second maximum delay; skip retries for known exhausted quota.

## Task 1: Classify structured quota failures and honor retry hints

Files: `app/src/main/java/com/aistudy/aistudy/ask/GeminiClient.kt`,
`app/src/main/java/com/aistudy/aistudy/ask/AiRequestTrace.kt`,
`app/src/test/java/com/aistudy/aistudy/ask/GeminiClientTest.kt`.

Interfaces: `RequestException.statusCode/reason/retryAfterMillis` remains the error
contract. Add enum reasons `RATE_LIMITED` and `QUOTA_EXHAUSTED`; no raw fields retained.

- [x] Add tests against real `GeminiClient.execute()` with fake HTTP connections:
  per-minute vs per-day, zero quota, mixed/unknown violations, no text heuristics,
  missing/malformed body, safe traces and RetryInfo/Retry-After delay precedence.
- [x] Run focused tests and record expected failures before implementation:
  `gradlew.bat :app:testDebugUnitTest --tests '*GeminiClientTest' --offline --console=plain`.
- [x] Parse recognized QuotaFailure details after existing key/billing reasons;
  apply known daily/zero > all-minute > unknown precedence only to HTTP 429.
- [x] Parse valid nonnegative protobuf duration strings with millisecond ceiling;
  ignore invalid hints and stop early when the required wait exceeds 30 seconds.
- [x] Add matching trace enum values and verify focused tests pass.

## Task 2: Share messages across all Gemini callers

Files: create `app/src/main/java/com/aistudy/aistudy/ask/AiErrorMessages.kt` and
`app/src/test/java/com/aistudy/aistudy/ask/AiErrorMessagesTest.kt`; modify
`AskFragment.kt`, `answer/AnswerViewModel.kt`, `answer/AnswerActivity.kt`,
`quiz/QuizViewModel.kt`, `res/values/strings.xml`, `res/values/strings_quiz.xml`.

Interface: `AiErrorMessages.message(error: Exception, @StringRes fallback: Int): Int`.
The mapper checks RequestException before IOException, SocketTimeoutException
before IOException, and leaves domain-specific fallbacks with callers.

- [x] Write mapper tests for HTTP statuses, all structured reasons, network,
  invalid API/JSON response and distinct caller fallbacks.
- [x] Replace duplicated Ask/More topics mappings with the shared mapper;
  retain session/content-change and domain validation handling locally.
- [x] Wire Learning extras and Quiz catches to the shared mapper. Quiz missing-key
  uses `ask_error_key_missing`; remove its unused developer-only string.
- [x] Add two specific 429 strings and replace the ambiguous old quota wording.
- [x] Verify mapper and client tests; inspect every Gemini caller and all enum usages.

## Task 3: Verification and implementation report

- [x] Run fresh full JVM tests, `:app:assembleDebug`, `:app:assembleDebugAndroidTest`.
- [x] If a device is available, run selected instrumented error/persistence checks
  without a live API call; otherwise document the unverified runtime behavior.
- [x] Obtain an independent code review; resolve actionable findings and rerun
  affected checks when changes require it.
- [x] Record test counts, exact files/behaviors and known limits in
  `docs/API_ERROR_HANDLING.md`; check `git diff --check` and final status.

## Task 4: Fix two P2 findings from OMP review

Scope explicitly approved by the user: quota integer parsing and Retry-After
overflow only. The reported Espresso button-visibility failure is out of scope.
Files: `ask/GeminiClient.kt`, `ask/GeminiErrorJson.kt`, `ask/GeminiClientTest.kt`,
`androidTest/.../ask/GeminiErrorParsingInstrumentedTest.kt`, this plan and the
error handling documentation. Existing unrelated changes stay intact.

- [x] Extend HTTP fixture tests to preserve raw decimal/exponent JSON zero;
  cover invalid present quotaValue, positive integer equivalents, null/unset,
  precedence and extreme Retry-After dates/clock spans. Run them before fixes.
- [x] Parse quota numbers/strings with BigDecimal.longValueExact(), accepting
  only nonnegative int64 values. Invalid present values block RATE_LIMITED;
  a known daily/zero violation still takes precedence across the response.
- [x] Calculate HTTP-date waits using Duration between Instants. Clamp past
  dates to zero and unrepresentably long positive waits to Long.MAX_VALUE,
  retaining RequestException and its original HTTP status/reason.
- [x] Preserve Android numeric wire tokens with JsonReader. Retain values
  outside Double/BigDecimal range as strings instead of discarding known
  quota/access reasons elsewhere in the error envelope. Add Android regressions.
- [x] Verify six parser checks against actual SDK37 JSON sources on host JVM;
  clearly record that emulator DLL failure prevented device instrumentation.
- [x] Run fresh full JVM tests and debug/app-test APK builds, review the focused
  patch independently, and update the report with measured results and limits.

On this Windows host, Gradle uses JBR 25 and a short socket directory:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir="C:\Users\CHI NGUYEN\AndroidStudioProjects\AIStudy\build\jvm-tmp"'
.\gradlew.bat :app:testDebugUnitTest --rerun --offline --console=plain
```
