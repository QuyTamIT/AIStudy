# M1 — Software Design Document: AI Study Mentor (nguyên liệu thô)

> **Phạm vi file này:** đây là *tài liệu thiết kế* để làm căn cứ cho phần **M1** (Activity 3) và cho **class diagram**. Nó **không phải** bài luận 400 từ — phần prose (diễn giải, Harvard referencing) là việc của bạn. Ở đây chỉ có dữ kiện, quyết định và lý do.
>
> **Đã chốt trước:** Jetpack Compose, AI provider = **Google Gemini**, lưu trữ **local-first không backend**.
> **Trạng thái code hiện tại:** Phase 0 (toolchain) + Phase 1 (main shell 5 tab) đã build thành công. Schema dưới đây là thiết kế *cho Phase 2 trở đi*, chưa có trong code.

---

## 1. Yêu cầu từ đề → phần thiết kế tương ứng

| # | Nhóm yêu cầu trong brief | Loại | Nơi xử lý trong thiết kế | Phase code |
|---|---|---|---|---|
| FR1 | User Registration & Authentication | F | `AuthRepository`, `User`, `SessionManager`, `PasswordHasher` | 3 |
| FR2 | Onboarding (level, subjects, style) | F | `Profile`, `ProfileDefaults`, `EducationLevel`, `ExplanationStyle` | 4 |
| FR3 | AI Question Submission (text + ảnh) | F | `StudyRepository.submitQuestion()`, `ImageStore`, `QuestionType` | 5 |
| FR4 | AI Answer Generation | F | `AiClient` / `GeminiAiClient`, `AiAnswer` | 6 |
| FR5 | Answer Improvement & Learning Support | F | `AiAnswer` (simplified / alternatives / keyConcepts / commonMistakes / followUps) | 7 |
| FR6 | Question History & Personal Library | F | `QuestionAnswerDao.search()`, `isBookmarked`, `subjectCode` | 8 |
| FR7 | AI-Generated Practice Questions | F | `QuizItem`, `QuestionType`, `PracticeRepository` | 9 |
| FR8 | Progress Tracking | F | `ProgressRepository`, `ProgressSummary` | 10 |
| FR9 | Gamification (XP, level, badge, leaderboard) | F | `XpRules`, `BadgeCatalog`, `XpEvent`, `UserBadge` | 11 |
| FR10 | Notifications | F | `Notification`, `NotificationScheduler` | 12 |
| N1 | Security (mật khẩu mạnh, mã hoá, 2FA) | NF | `PasswordHasher`, `CryptoBox`, `Totp`, `AbuseDetector` | 13 |
| N2 | Offline access | NF | Room là nguồn sự thật; `ConnectivityObserver` + hàng đợi `PendingSync` | 14 |
| N3 | Data privacy (GDPR, COPPA) | NF | `DataPolicy` (export/xóa dữ liệu), không thu thập dữ liệu nhạy cảm | 13 |
| — | AI Cost Management | ràng buộc | `questionHash` + `QuestionSimilarity` → cache, `servedFromCache` | 5–6 |

---

## 2. Kiến trúc: 4 tầng, 1 module

```
┌──────────────────────────────────────────────────────────┐
│  Tầng UI          Compose @Composable + ViewModel        │
│                   ui/screens, ui/shell, ui/theme         │
├──────────────────────────────────────────────────────────┤
│  Tầng Domain      Repository (Use-case) + model nghiệp vụ │
│                   domain/repository, domain/model,        │
│                   domain/rules (XpRules, BadgeCatalog)    │
├──────────────────────────────────────────────────────────┤
│  Tầng Data        Room entity + DAO + AppDatabase         │
│                   data/db, data/crypto, data/session      │
├──────────────────────────────────────────────────────────┤
│  Tầng Hạ tầng     AiClient (Gemini), Connectivity, File   │
│                   platform/ai, platform/net, platform/file│
└──────────────────────────────────────────────────────────┘
        Quy tắc: chỉ đi xuống. UI không gọi thẳng DAO.
```

**Vì sao 4 tầng, không phải 3 hay MVC gốc:**

- Đề yêu cầu *"requirements, system analysis, system design, coding, testing, implementation"* — mô hình phân tầng làm ranh giới "design" hiện rõ trên class diagram, giúp marker nhìn ra được OOAD thật chứ không chỉ là app chạy.
- Junior team + 14 tuần: mỗi tầng một thư mục, quy tắc phụ thuộc đơn giản, không cần DI framework.
- `domain/` **không** import `androidx.room` → test business rule (`XpRules`, `QuestionSimilarity`, `PasswordStrength`) bằng JUnit thuần, không cần emulator. Đây là lý lẽ định lượng để nói trong D1 về lợi thế của kiến trúc đã chọn.

**Vì sao không có backend:** brief nói "Limited budget", "team junior", và chỉ yêu cầu *"global or friends-based leaderboard"*. Xây server vượt khả năng 14 tuần. → **local-first**: leaderboard tính trên thiết bị, ghi rõ giới hạn này vào design doc + nêu ở P2 (rủi ro). Đây là điểm cần nói thẳng, không giấu.

---

## 3. Quyết định thiết kế (ADR tóm tắt)

| # | Quyết định | Lựa chọn | Lý do | Cái giá phải trả |
|---|---|---|---|---|
| D-1 | UI framework | **Jetpack Compose** | Đã cài sẵn, code ít hơn View ~40%, dễ dựng layout phức tạp | Bundle debug lớn hơn; phải học Compose |
| D-2 | Điều hướng | **navigation-compose** 2.10.2 | 1 Activity duy nhất, tránh rò rỉ Context, hỗ trợ deep link `question/{id}` | Thêm 1 dependency |
| D-3 | Lưu trữ | **Room (SQLite)** | Offline là NFR bắt buộc; Room sinh code, có migration test được | Migration schema khi lên phiên bản sau |
| D-4 | Truy vấn | **Flow**, không dùng LiveData | Lifecycle-aware, test được không cần Android, hợp coroutine | Không dùng được Java (project là Kotlin) |
| D-5 | Gọi AI | **`HttpURLConnection` + `org.json`**, KHÔNG Retrofit/OkHttp | 0 dependency mới → build không cần mạng, APK nhỏ, không xung đột version với AGP 9.3.3 | Tự viết ~80 dòng parse JSON; không có interceptor/logging sẵn |
| D-6 | Mật khẩu | **PBKDF2-HMAC-SHA256**, 120.000 vòng, salt 16 byte ngẫu nhiên | Không lưu plaintext, chống rainbow table, không cần BCrypt ngoài | Chậm ~80 ms/lần → phải chạy ngoài luồng chính |
| D-7 | Khoá phiên | **EncryptedSharedPreferences** | `PREFS_AUTH` trong tài liệu, đồng thời thoả "encrypted storage" | Phụ thuộc `androidx.security:security-crypto` |
| D-8 | Ảnh câu hỏi | **AES-GCM-256**, khoá trong **Android Keystore**, file trong `filesDir` | NFR "encrypted storage for uploaded images" | Phải xử lý khi user đổi máy (không backup được khoá) |
| D-9 | Cache AI | **SHA-256 hash + Jaccard similarity ≥ 0.85** | Brief **bắt buộc** "reduce repeated AI calls through caching" | Trả nhầm câu trả lời nếu ngưỡng quá thấp → cần hiển thị badge "tái dùng" |
| D-10 | Enum trong DB | Room tự lưu `String` | Đọc DB được (không phải số 1/2/3 vô nghĩa) | Đổi tên enum = đổi dữ liệu → phải có migration |
| D-11 | Môn học | `Subject` là enum, lưu dạng `String` | 5 môn cố định trong brief, không cần bảng riêng | Thêm môn mới = migration + logic AI |
| D-12 | Leaderboard | **Placeholder local** | Không có server trong MVP | Không đúng nghĩa "global" → phải ghi rõ trong design doc |

---

## 4. Room schema — thiết kế đầy đủ (đây là nền cho class diagram)

> 7 bảng. `Long` = autogen primary key. Timestamp = `Long` epoch millis. Enum lưu dạng `String` (D-10).

### 4.1 `users` — FR1, FR9

| Cột | Kiểu | Ràng buộc | Vì sao có |
|---|---|---|---|
| `id` | `Long` | PK, autoGenerate | — |
| `email` | `String` | **UNIQUE index**, `NOT NULL` | `findByEmail(email)` trả về 1 dòng |
| `passwordHash` | `String` | `NOT NULL` | Base64 PBKDF2, **không bao giờ** plaintext (D-6) |
| `passwordSalt` | `String` | `NOT NULL` | Base64 salt 16 byte, mỗi user 1 salt riêng |
| `displayName` | `String` | `NOT NULL` | Tên hiển thị trên dashboard |
| `twoFactorSecret` | `String?` | nullable | Chỉ có khi bật 2FA (FR/N1) |
| `totalXp` | `Int` | default 0 | Denormalised từ `xp_events` để dashboard không phải SUM |
| `level` | `Int` | default 1 | Tính bằng `XpRules.levelFor(totalXp)` |
| `currentStreakDays` | `Int` | default 0 | Điều kiện badge `STREAK_7` |
| `lastQuestionDate` | `Long?` | nullable | Tính streak ngày hôm nay vs. hôm qua |
| `createdAt` | `Long` | `NOT NULL` | — |
| `lastLoginAt` | `Long?` | nullable | Hiển thị "lần cuối" trong Hồ sơ |

**Index:** `index_users_email` (unique), `index_users_displayName` (tìm nhanh hơn khi search leaderboard).

### 4.2 `profiles` — FR2

| Cột | Kiểu | Ràng buộc | Vì sao có |
|---|---|---|---|
| `id` | `Long` | PK, autoGenerate | — |
| `userId` | `Long` | **FK → users.id**, **UNIQUE index**, `NOT NULL` | 1 hồ sơ / 1 user; unique chặn trùng khi onboarding chạy 2 lần |
| `educationLevel` | `String` | `NOT NULL` | `MIDDLE` \| `HIGH` \| `UNIVERSITY` |
| `preferredStyle` | `String` | `NOT NULL` | `SHORT` \| `DETAILED` \| `STEP_BY_STEP` |
| `subjectCodes` | `String` | `NOT NULL` | Danh sách môn, `TypeConverter` ⇄ `Set<Subject>` |
| `onboardedAt` | `Long?` | nullable | `null` ⇒ chưa onboarding ⇒ gate vào `MainShell` |

**Quyết định:** dùng `Set<Subject>` + `TypeConverter` thay vì bảng nối `profile_subjects`.
*Lý do:* chỉ 5 môn cố định, luôn đọc trọn vẹn cùng hồ sơ, không cần truy vấn một phần. Bảng nối chỉ đáng làm khi danh mục môn lớn, động, hoặc cần thống kê theo môn từ phía hồ sơ — điều không xảy ra ở đây (thống kê theo môn lấy từ `question_answers.subjectCode`).
*Nói điều này trong D1:* đây là ví dụ cụ thể của *trade-off* thiết kế vs. bản lĩnh toàn phân biệt (denormalisation có chủ đích).

### 4.3 `question_answers` — FR3–FR6 (bảng trung tâm)

| Cột | Kiểu | Ràng buộc | Vì sao có |
|---|---|---|---|
| `id` | `Long` | PK, autoGenerate | Khoá cho deep link `question/{id}` |
| `userId` | `Long` | **FK → users.id**, **index**, `NOT NULL` | Mỗi câu hỏi thuộc 1 học sinh |
| `subjectCode` | `String` | **index**, `NOT NULL` | Nhóm theo môn (FR6) + môn hay hỏi (FR8) |
| `questionText` | `String` | `NOT NULL` | Nội dung gõ; rỗng nếu chỉ up ảnh |
| `questionImagePath` | `String?` | nullable | Đường dẫn file đã **mã hoá** trong `filesDir` (D-8) |
| `questionImageIv` | `String?` | nullable | IV của AES-GCM, cần cho giải mã |
| `questionHash` | `String` | **index**, `NOT NULL` | SHA-256 của text đã chuẩn hoá → tra cache exact-match (D-9) |
| `servedFromCache` | `Boolean` | default false | Hiện nhãn "tái dùng" cho người dùng → minh bạch |
| `matchedQuestionId` | `Long?` | nullable | Nếu khớp do similarity, trỏ tới bản ghi gốc đã trả tiền |
| `detectedDifficulty` | `String?` | nullable | `EASY` \| `MEDIUM` \| `HARD` — AI trả về (FR4) |
| `answerText` | `String` | `NOT NULL` | Câu trả lời chính |
| `simplifiedExplanation` | `String?` | nullable | FR5 "simplified explanations" |
| `alternativeApproach` | `String?` | nullable | FR5 "alternative solution approaches" |
| `keyConcepts` | `String?` | nullable | FR5 "key concepts summary" |
| `commonMistakes` | `String?` | nullable | FR5 "common mistakes to avoid" |
| `followUpQuestions` | `String?` | nullable | FR5 "suggested follow-up questions" |
| `isBookmarked` | `Boolean` | **index**, default false | FR6 bookmark |
| `reviewTimeSeconds` | `Int` | default 0 | FR8 "time spent reviewing answers" |
| `inputTokens` / `outputTokens` | `Int` | default 0 | Đo **AI cost management** (bài yêu cầu tối ưu chi phí) |
| `createdAt` | `Long` | `NOT NULL` | Sắp xếp history + nhóm theo ngày |

**Index:** `idx_qa_user` (userId), `idx_qa_user_subject` (userId, subjectCode), `idx_qa_user_bookmarked` (userId, isBookmarked), `idx_qa_user_created` (userId, createdAt DESC), `idx_qa_hash` (questionHash).

**Cố ý không có:** cột `query` cho full-text search. SQLite FTS5 không được Room hỗ trợ out-of-the-box; MVP dùng `LIKE '%…%'` trên `questionText` + index. Nêu giới hạn này ở **NFR Usability/Performance** trong design doc.

### 4.4 `quiz_items` — FR7

| Cột | Kiểu | Ràng buộc | Vì sao có |
|---|---|---|---|
| `id` | `Long` | PK | — |
| `userId` | `Long` | **FK → users.id**, **index** | — |
| `sourceQuestionId` | `Long?` | **FK → question_answers.id**, nullable | Câu luyện tập sinh ra từ câu hỏi nào ⇒ truy vết được |
| `subjectCode` | `String` | **index** | Lọc quiz theo môn |
| `questionType` | `String` | `NOT NULL` | `MULTIPLE_CHOICE` \| `SHORT_ANSWER` \| `FILL_BLANK` |
| `prompt` | `String` | `NOT NULL` | — |
| `optionA`…`optionD` | `String?` | nullable | Chỉ dùng cho `MULTIPLE_CHOICE` |
| `correctAnswer` | `String` | `NOT NULL` | So sánh chuỗi đã chuẩn hoá (bỏ khoảng trắng/thống kê) |
| `explanation` | `String?` | nullable | Giải thích sau khi trả lời |
| `isCorrect` | `Boolean?` | **nullable = chưa làm** | Đây là lý do phải nullable: `false` (sai) khác `null` (chưa thử) |
| `attempts` | `Int` | default 0 | — |
| `createdAt` / `answeredAt` | `Long` | — | Tính tỉ lệ chính xác theo thời gian |

### 4.5 `xp_events` — FR9 (sổ cái XP, không lưu counter đơn thuần)

| Cột | Kiểu | Vì sao có |
|---|---|---|
| `id` | `Long` PK | — |
| `userId` | `Long` FK, **index** | — |
| `reason` | `String` | `ASK_QUESTION` \| `REVIEW_ANSWER` \| `COMPLETE_QUIZ` \| `QUIZ_BONUS` \| `DAILY_STREAK` |
| `xpAwarded` | `Int` | Giá trị lấy từ `XpRules`, không hardcode chỗ khác |
| `createdAt` | `Long` | Thống kê theo tuần (FR10 daily/weekly summary) |

**Vì sao có cả `users.totalXp` lẫn `xp_events`:** `totalXp` là dữ liệu dẫn xuất, để dashboard đọc 1 dòng thay vì `SUM()` hàng nghìn dòng (NFR Performance). `xp_events` là **nguồn sự thật** — cần nó để giải thích *tại sao* lên level và để tính chuỗi ngày. Đây là normalise dưới + denormalise có kiểm soát, ghi rõ trong design doc.

### 4.6 `user_badges` — FR9

| Cột | Kiểu | Ràng buộc |
|---|---|---|
| `userId` | `Long` | **FK → users.id**, PK (phần 1) |
| `badgeCode` | `String` | PK (phần 2) |
| `earnedAt` | `Long` | `NOT NULL` |

Danh mục huy hiệu nằm trong code (`object BadgeCatalog`), **không** tạo bảng `badges` — danh mục là hằng số của nhà phát triển, không phải dữ liệu người dùng. Composite primary key chặn trùng huy hiệu.

### 4.7 `notifications` — FR10

| Cột | Kiểu | Ràng buộc | Vì sao có |
|---|---|---|---|
| `id` | `Long` | PK | — |
| `userId` | `Long` | **FK → users.id**, **index** | — |
| `type` | `String` | `NOT NULL` | `REVIEW_REMINDER` \| `MISTAKE_ALERT` \| `MOTIVATIONAL` \| `DAILY_SUMMARY` |
| `title` / `body` | `String` | `NOT NULL` | — |
| `deepLink` | `String?` | nullable | Trỏ tới `question/{id}` để bấm là mở đúng nơi |
| `isRead` | `Boolean` | **index**, default false | Badge chưa đọc trên thanh nav |
| `createdAt` | `Long` | `NOT NULL` | Gom theo ngày/tuần |

### 4.8 Sơ đồ quan hệ (FK)

```
users 1 ──1 profiles
  1 ──* question_answers
  1 ──* quiz_items
  1 ──* xp_events
  1 ──* user_badges
  1 ──* notifications

question_answers 1 ──* quiz_items        (sourceQuestionId, nullable)
```

Quan hệ đều **1-n**, không có bảng nối nào ngoài `user_badges` (composite key) — vì không tồn tại quan hệ n-n thực sự. Đây là điểm cần nêu: *đừng tạo bảng nối khi không có quan hệ n-n*.

---

## 5. Bản đồ màn hình ↔ class ↔ phase

| Tab (route) | Composable | ViewModel | Repository chính | Phase |
|---|---|---|---|---|
| — (gate) | `AuthGate` | `AuthViewModel` | `AuthRepository` | 3 |
| `ask` | `AskScreen` | `AskViewModel` | `StudyRepository` | 5–7 |
| `history` | `HistoryScreen` | `HistoryViewModel` | `StudyRepository` | 8 |
| `practice` | `PracticeScreen` | `PracticeViewModel` | `PracticeRepository` | 9 |
| `progress` | `ProgressScreen` | `ProgressViewModel` | `ProgressRepository` | 10 |
| `profile` | `ProfileScreen` | `ProfileViewModel` | `GamificationRepository`, `ProfileRepository` | 4, 11 |
| `question/{id}` | `QuestionDetailScreen` | `AskViewModel` (reuse) | `StudyRepository` | 8 |
| — (auth) | `RegisterScreen`, `LoginScreen` | `AuthViewModel` | `AuthRepository` | 3 |
| — (onboarding) | `OnboardingScreen` | `ProfileViewModel` | `ProfileRepository` | 4 |

`MainShell` (đã có, Phase 1) là **auth gate**: `SessionManager.isLoggedIn` + `Profile.onboardedAt` quyết định `NavHost` bắt đầu ở `Routes.LOGIN` hay `Routes.ASK`. Điều này giữ đúng 1 Activity, không vi phạm D-2.

---

## 6. Luồng chính: hỏi câu 1 lần (có cache) — cơ sở cho activity diagram

```
1. AskViewModel.submit(text, imageUri?)
2.  → StudyRepository.submitQuestion()
3.     a) chuẩn hoá text → questionHash = SHA256
4.     b) QuestionAnswerDao.findByHash(hash)        ─┐
5.     c) QuestionSimilarity.findSimilar(text, 0.85) ├─ HIT  → trả QuestionAnswer cũ, servedFromCache=true, XẾT bước 9
6.     d) ImageStore.encrypt(uri) → path + iv       │        (KHÔNG tốn tiền AI)
7.     e) AiClient.generateAnswer(AiRequest)        │
8.     f) parse AiResponse → AiAnswer               ┘
9.     g) QuestionAnswerDao.insert(QuestionAnswer)
10.    h) GamificationRepository.award(ASK_QUESTION)  → XpRules
11.    i) NotificationRepository.maybeSchedule(...)
12.  ← StateFlow<AskUiState>
```

Bước 4–6 là **cơ chế AI Cost Management mà brief bắt buộc**. Đây là use case "extend"/"include" riêng trong use case diagram, không phải thu nhỏ của "Ask question" — vẽ riêng để thấy rõ.

---

## 7. Mô hình bảo mật (đưa vào design doc + test plan)

| Dữ liệu | Cách bảo vệ | NFR |
|---|---|---|
| Mật khẩu | PBKDF2-HMAC-SHA256, 120k vòng, salt ngẫu nhiên/user | N1 |
| Phiên đăng nhập | `EncryptedSharedPreferences`, tự xoá khi logout | N1, N3 |
| Ảnh câu hỏi | AES-GCM-256, khoá trong Android Keystore | N1, N3 |
| Câu hỏi + câu trả lời | SQLite không mã hoá ⇒ **không** gọi là "encrypted". Đã ghi rõ giới hạn | N1, N3 |
| API key Gemini | `local.properties` + `BuildConfig` (debug), **không** commit lên git | N1 |
| Quyền xoá dữ liệu | `DataPolicy.deleteAll(userId)` — đáp ứng quyền của chủ thể dữ liệu | N3 GDPR/COPPA |
| Mật khẩu yếu | `PasswordHasher.strength()` chặn trước khi hash | N1 |
| 2FA | TOTP (HMAC-SHA1, 6 số, 30s) — tự cài, không cần dependency | N1 |

**Phải nói thẳng trong design doc:** brief đòi "All user data must be securely encrypted" nhưng Room/SQLite không mã hoá cột. Giải pháp đúng cho MVP: mã hoá ở **tầng ứng dụng** (`CryptoBox.encrypt/decrypt`) cho các cột nhạy cảm (`questionText`, `answerText`, `questionImagePath`) — nhưng thế thì không index/query được plaintext. **Đây là trade-off thật** và là câu trả lời tốt cho phần "debate why this design will work well" / D1: chọn *mã hoá cột nhạy cảm + index trên hash*, chấp nhận search chỉ exact-hash, còn search theo nội dung dùng index phụ trên `subjectCode`.

---

## 8. Danh mục diagram (nguồn `.puml` trong `docs/uml/`, PNG trong `docs/uml/png/`)

**13 diagram, tất cả đã render thành công.** Kích thước PNG ghi cạnh để biết cách in.

| File | Loại | Kích thước PNG | Phục vụ M1 ở mục nào |
|---|---|---|---|
| `01_use_case.puml` | Use case (tổng quan) | 1328×962 | system analysis — 7 use case cấp cao, phủ 10 FR + 3 NFR |
| `01b_use_case_detail.puml` | Use case (chi tiết theo FR) | 4096×846 | đối chiếu từng FR. **In khổ A3 ngang** hoặc dùng làm checklist nội bộ |
| `02_class_domain.puml` | Class — domain model | 1853×1481 | system design — 7 entity, 9 enum, quan hệ 1-n |
| `02b_class_persistence.puml` | Class — Room + rules | 2459×2110 | system design — 7 DAO, AppDatabase, 4 rule object |
| `03_class_architecture.puml` | Class — presentation | 2041×939 | coding — 10 màn hình, 6 ViewModel, 5 UiState |
| `03b_class_repository.puml` | Class — use case layer | 1335×1790 | coding — 7 Repository interface + model |
| `03c_class_platform.puml` | Class — platform | 1639×1411 | coding — Gemini, mạng, bảo mật |
| `04_activity_ask_flow.puml` | Activity | 1579×2278 | system design — luồng cache hit/miss |
| `05_activity_auth_onboarding.puml` | Activity | 2731×2843 | system design — auth gate + onboarding |
| `06_component.puml` | Component | 2317×1151 | system design — ranh giới module, 3 hợp đồng |
| `07_deployment.puml` | Deployment | 1240×955 | implementation — thiết bị, cloud, storage |
| `08_state_question.puml` | State machine | 1258×1364 | system design — vòng đời 1 câu hỏi |
| `09_erd.puml` | ER (crow's foot) | 1764×689 | system design — schema nhìn bằng mắt thường |

### Cách tái dựng diagram

```powershell
# Tải PlantUML một lần (file .jar, không cần cài)
Invoke-WebRequest -Uri "https://github.com/plantuml/plantuml/releases/latest/download/plantuml.jar" -OutFile plantuml.jar

# Render toàn bộ (cần UTF-8 vì diagram có dấu tiếng Việt)
java -jar plantuml.jar -tpng -charset UTF-8 -o png docs\uml\*.puml
```

> **Vì sao chọn PlantUML chứ không vẽ tay:** đề khuyến nghị viết bằng LaTeX. `.puml` là **văn bản thuần** nên nằm cạnh `.tex`, review được bằng diff, và chèn vào tài liệu bằng `\includegraphics{...}` là tái dựng được sau này. Không phụ thuộc công cụ GUI có bản quyền.

### Vài bài học PlantUML gặp khi dựng (để không mất thời gian lần sau)

1. **Tên swimlane có khoảng trắng là lỗi cú pháp** — dùng `|Student|`, không dùng `|Sinh viên|`.
2. **Chuyển lane phải có pipe đóng**: `|Repo|`, không phải `|Repo`.
3. **Nhãn nhiều dòng phải ghi `\n` trên một dòng** — xuống dòng nốt sẽ bị đọc nhầm thành phần tử mới và báo lỗi.
4. **Không dùng inline colour `#E8F5E9 :` trong nhánh `if/else`** của activity diagram — PlantUML 1.2026.8 từ chối.
5. **`note over <lane>` không hợp lệ trong swimlane** — dùng `note` trơn.
6. **`together { }` không dùng được cho class nằm trong `package`.** Cách ép bố trí dùng được: chuỗi hidden edge `A -[hidden]-> B -[hidden]-> C` để xếp dọc, giảm chiều rộng để in A4.
7. `skinparam linetype ortho` làm đường nối gọn hơn nhưng với sơ đồ lớn sẽ tạo đường đi rất dài — chỉ dùng cho diagram nhỏ.

---

## 9. Bản đồ truy vết test (chi tiết ở `PART1_M1_TEST_PLAN.md`)

| Nhóm | Test tự động | Test thủ công | Ghi chú |
|---|---|---|---|
| FR1 Auth | Unit: `PasswordHasher`, `Totp.verify` | TC-AUTH-01..06 | Đo thời gian hash ≥ 50 ms |
| FR2 Onboarding | Unit: `ProfileDefaults` | TC-ONB-01..05 | — |
| FR3 Submit | Unit: `QuestionSimilarity`; instrumented: `StudyRepository` với fake `AiClient` | TC-ASK-01..08 | **Bắt buộc** có `FakeAiClient` để test không tốn tiền |
| FR4–5 Answer | Instrumented với `FakeAiClient` | TC-ANS-01..06 | — |
| FR6 History | Instrumented: DAO search + bookmark | TC-HIS-01..06 | — |
| FR7 Practice | Instrumented: `PracticeRepository` | TC-QUI-01..07 | Case `isCorrect = null` vs `false` |
| FR8 Progress | Unit: `ProgressCalculator` | TC-PRO-01..05 | — |
| FR9 Gamification | Unit: `XpRules.levelFor` (bảng biên) | TC-GAM-01..05 | Biên level 1..10 |
| FR10 Notif | Instrumented | TC-NOT-01..04 | — |
| N1 Security | Unit: `PasswordHasher.strength`, `CryptoBox` round-trip | TC-SEC-01..07 | |
| N2 Offline | Instrumented: `ConnectivityObserver` | TC-OFF-01..04 | |
| N3 Privacy | Instrumented: `DataPolicy.deleteAll` | TC-PRV-01..04 | |

**Chiến lược test then chốt (đưa vào test plan):**
1. **Tầng `domain/`** → JUnit thuần, chạy nhanh, không cần emulator. Đây là nơi nhồi logic khó nhất (XP, similarity, hash) nên tập trung test ở đây trước.
2. **Tầng `data/`** → instrumented test (`androidTest`) với Room in-memory (`inMemoryDatabaseBuilder`) + `FakeAiClient`.
3. **UI** → `ComposeTestRule`, chỉ smoke test 5 tab + 1 luồng chính, không test pixel.
4. **Không bao giờ** gọi Gemini thật trong test — dùng `FakeAiClient` trả fixture JSON. Lý do: tiền thật + không độc lập (flaky) + cần tính lặp lại cao (NFR Performance).
