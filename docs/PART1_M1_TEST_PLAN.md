# M1 — Test Plan: AI Study Mentor

> **Phạm vi file này:** test plan thuộc **M1** (`"Create a test plan for the AI Study Mentor"` trong brief).
> Đây là **bảng ca kiểm thử** — phần diễn giải (vì sao chọn chiến lược này, trade-off) là việc của bạn; ở đây chỉ có dữ kiện và con số.
> **Trạng thái:** kế hoạch. Chưa có test nào được viết — test sẽ sinh ra kèm từng phase code (2–14).

---

## 1. Mục tiêu kiểm thử

| # | Mục tiêu | Cách đo |
|---|---|---|
| G1 | Mọi nhóm yêu cầu chức năng (FR1–FR10) đều có test phủ | Ma trận truy vết mục 3: không FR nào trống |
| G2 | Mọi yêu cầu phi chức năng (N1–N3) có test phù hợp | Mục 4 |
| G3 | Phần logic khó nhất được test tự động, không cần emulator | ≥ 70% ca tự động nằm ở tầng `domain/` |
| G4 | Không test nào tốn tiền API | 0 lời gọi `GeminiAiClient` trong test — kiểm bằng `./gradlew :app:checkDebugUnitTest --offline` |
| G5 | Test chạy lặp lại được (deterministic) | Không dùng `Thread.sleep`, không dùng thời gian thực, không phụ thuộc thứ tự test |

---

## 2. Chiến lược kiểm thử

### 2.1 Bốn tầng, bốn cách test

| Tầng | Công cụ | Chạy ở đâu | Nhanh? | Bao nhiêu ca |
|---|---|---|---|---|
| `domain/rules`, `domain/model` | JUnit 4 thuần | `./gradlew test` | < 5 giây | ~55 ca |
| `platform/security` | JUnit 4 + Robolectric *(nếu cần)* | `./gradlew test` | < 30 giây | ~20 ca |
| `data/db` (DAO) | Android instrumented test | emulator | ~3–5 phút | ~30 ca |
| `ui/screens` | Compose `createComposeRule` | emulator | ~3–5 phút | ~12 ca |

**Vì sao chia vậy:** ngưỡng XP, đường cong level, ngưỡng similarity và kiểm tra mật khẩu là những phần dễ sai nhất và lại thuần số — đặt chúng ở tầng `domain` cho phép chạy hàng nghìn lần trong vài giây. Đây là lập luận định lượng dùng cho phần *"debate why this design will work well"*.

### 2.2 Nguyên tắc bắt buộc

| # | Nguyên tắc | Lý do |
|---|---|---|
| P1 | **Không bao giờ gọi Gemini thật trong test** | Tốn tiền; phụ thuộc mạng ⇒ *flaky*; NFR Performance đòi chạy lặp nhiều vòng |
| P2 | Test DAO dùng `inMemoryDatabaseBuilder` | Nhanh, không đụng dữ liệu thật, tự dọn |
| P3 | Mỗi test một hành vi, tên test ghi rõ **điều kiện → kết quả mong đợi** | Đọc tên test là biết test cover gì, không cần mở code |
| P4 | Test biên (boundary) bắt buộc cho mọi con số có trong đề | Lỗi hay nằm ở biên, không nằm ở giữa |
| P5 | Test dữ liệu cố định, không dùng `Instant.now()` | Tránh test fail lúc nửa đêm |
| P6 | Ảnh test dùng fixture tĩnh trong `androidTest/assets/` | Không phụ thuộc camera thật |
| P7 | Mỗi bug tìm được phải có 1 ca hồi quy (regression test) | Ngăn bug quay lại |

### 2.3 Công cụ

| Công cụ | Vai trò | Lý do chọn |
|---|---|---|
| JUnit 4 | Unit test | Đã có sẵn trong `libs.versions.toml`, không thêm dependency |
| AndroidX Test + Espresso | Instrumented test | Đã có sẵn từ template |
| Compose UI Test JUnit4 | Test màn hình Compose | Đã khai báo ở Phase 1 |
| `kotlinx-coroutines-test` | Test coroutine | Cần thêm 1 dependency — chấp nhận |
| **KHÔNG dùng** MockK / Mockito | — | Thêm dependency lớn cho team junior; `FakeAiClient` viết tay chỉ ~40 dòng mà đủ dùng |

---

## 3. Ma trận truy vết — nhóm chức năng

> Cột "Mức" = mức kiểm thử tối thiểu bắt buộc: **U** = unit, **I** = instrumented, **M** = thủ công.
> Không nhóm FR nào được phép để trống cột "Ca kiểm thử".

### FR1 — User Registration & Authentication

| ID | Điều kiện | Kết quả mong đợi | Mức |
|---|---|---|---|
| TC-AUTH-01 | Đăng ký email hợp lệ, mật khẩu mạnh | Tạo `User` + `Profile` mặc định; `totalXp=0`, `level=1` | I |
| TC-AUTH-02 | Đăng ký lại cùng email | Thất bại, báo "email đã được dùng" | I |
| TC-AUTH-03 | Mật khẩu `abc` (3 ký tự) | `strength() = WEAK`, chặn đăng ký | U |
| TC-AUTH-04 | Mật khẩu chỉ chữ cái, ≥ 8 ký tự | `WEAK` hoặc `FAIR`, chặn | U |
| TC-AUTH-05 | Mật khẩu ≥ 8 ký tự, có chữ **và** số | `STRONG`, cho qua | U |
| TC-AUTH-06 | `hash()` rồi `verify()` với đúng mật khẩu | `true` | U |
| TC-AUTH-07 | `verify()` với sai mật khẩu | `false` | U |
| TC-AUTH-08 | Hash cùng 1 mật khẩu cho 2 user khác nhau | 2 hash **khác nhau** (salt ngẫu nhiên) | U |
| TC-AUTH-09 | Cùng 1 user hash 2 lần | Hash giống nhau (salt cố định theo user) | U |
| TC-AUTH-10 | Đăng nhập đúng email + mật khẩu | `lastLoginAt` được cập nhật; `SessionManager` lưu phiên | I |
| TC-AUTH-11 | Đăng nhập sai mật khẩu 5 lần | Bộ đếm tăng; lần 5 bị tạm khoá 5 phút | I |
| TC-AUTH-12 | `logout()` | `SessionManager.isLoggedIn() = false`, xoá khỏi EncryptedSharedPreferences | I |
| TC-AUTH-13 | Đăng nhập ở 2 thiết bị, cùng email | Cùng `userId` (cùng DB) | M |
| TC-AUTH-14 | Thời gian `hash()` | ≥ 50 ms (chứng minh PBKDF2 thực sự chạy) | U |
| TC-AUTH-15 | Mở app sau khi logout | `AuthGate` chuyển về `Routes.LOGIN` | I |

**FR1 bảo mật (xem thêm N1):** TC-SEC-01…07.

### FR2 — Onboarding

| ID | Điều kiện | Kết quả mong đợi | Mức |
|---|---|---|---|
| TC-ONB-01 | User mới, `onboardedAt = null` | `AuthGate` chuyển tới `Routes.ONBOARDING` | I |
| TC-ONB-02 | Lưu cấp học + ≥ 1 môn + phong cách | `Profile` lưu đủ 3 trường, `onboardedAt` có giá trị | I |
| TC-ONB-03 | Bỏ chọn môn, bấm Xong | Chặn, yêu cầu ≥ 1 môn | M |
| TC-ONB-04 | Bấm Xong 2 lần liên tiếp | `profiles` chỉ có **1** dòng (unique index `userId`) | I |
| TC-ONB-05 | Đổi `preferredStyle` sau đó | Câu hỏi mới gửi `AiRequest.preferredStyle` mới | I |
| TC-ONB-06 | `ProfileDefaults.forNewUser()` | `preferredStyle = STEP_BY_STEP`, `subjectCodes` mặc định | U |

### FR3 + FR4 + FR5 — Hỏi, nhận câu trả lời, hỗ trợ học tập

| ID | Điều kiện | Kết quả mong đợi | Mức |
|---|---|---|---|
| TC-ASK-01 | Hỏi bằng text, online | Gọi `AiClient` 1 lần; lưu 1 `QuestionAnswer`; `inputTokens>0` | I |
| TC-ASK-02 | Hỏi **trùng khít** câu đã hỏi | `servedFromCache=true`; **`AiClient` gọi 0 lần**; `matchedQuestionId` trỏ bản gốc | I |
| TC-ASK-03 | Hỏi **gần giống** (Jaccard ≥ 0.85) | `servedFromCache=true`; `AiClient` gọi 0 lần | I |
| TC-ASK-04 | Hỏi **khác** (Jaccard < 0.85) | `servedFromCache=false`; `AiClient` gọi 1 lần | I |
| TC-ASK-05 | Chỉ up ảnh, không gõ chữ | `questionText=""`, `questionImagePath` khác null | I |
| TC-ASK-06 | Chữ + ảnh | Cả hai được lưu, cả hai gửi lên AI | I |
| TC-ASK-07 | `questionText` rỗng **và** không ảnh | Chặn, không gọi AI, không tạo bản ghi | I |
| TC-ASK-08 | `questionText` đúng 2000 ký tự | Cho qua | I |
| TC-ASK-09 | `questionText` 2001 ký tự | Chặn | I |
| TC-ASK-10 | Ảnh > 5 MB | Chặn hoặc nén, báo rõ | I |
| TC-ASK-11 | `AiClient` trả lỗi (timeout / 5xx) | `Result.failure`, giữ nguyên nội dung đã gõ, **không** cộng XP | I |
| TC-ASK-12 | Bấm Thử lại sau lỗi | Gọi lại `AiClient` | I |
| TC-ANS-01 | Nhận `AiAnswer` đầy đủ | `answerText`, `steps`, `detectedSubject`, `detectedDifficulty` lưu đúng | I |
| TC-ANS-02 | `AiAnswer` thiếu trường tuỳ chọn | Các cột nullable lưu `null`, **không** crash | I |
| TC-ANS-03 | JSON của AI sai định dạng | Báo lỗi thân thiện, không crash | I |
| TC-ANS-04 | Bấm "Giải thích đơn giản" | `simplifiedExplanation` được điền, `AiClient` gọi thêm 1 lần | I |
| TC-ANS-05 | Bấm "Cách làm khác" | `alternativeApproach` được điền | I |
| TC-ANS-06 | Xem `keyConcepts`, `commonMistakes`, `followUpQuestions` | Cả 3 hiển thị đúng | I |
| TC-ANS-07 | `followUpQuestions` chứa 3 câu | Tạo được 3 `QuizItem` sinh tự động | I |

> **TC-ASK-02/03/04 là 3 ca quan trọng nhất của cả dự án** — chúng chứng minh "AI Cost Management" trong brief **thực sự chạy**, không phải chỉ nằm trên giấy.

### FR6 — Question History & Personal Library

| ID | Điều kiện | Kết quả mong đợi | Mức |
|---|---|---|---|
| TC-HIS-01 | Hỏi 5 câu, mở tab Lịch sử | Hiện 5, **mới nhất trước** | I |
| TC-HIS-02 | Mở app lại (kill → launch) | Lịch sử vẫn còn (Room là nguồn sự thật) | I |
| TC-HIS-03 | Tìm "photosynthesis" | Trả về câu có chứa từ đó | I |
| TC-HIS-04 | Tìm từ không tồn tại | Trả về rỗng, **không** crash | I |
| TC-HIS-05 | Bookmark rồi mở Lịch sử → lọc Bookmark | Chỉ hiện câu đã bookmark | I |
| TC-HIS-06 | Nhóm theo môn Toán | Chỉ hiện câu có `subjectCode = MATHEMATICS` | I |
| TC-HIS-07 | Nhập 2.000 câu hỏi, mở Lịch sử | Không giật; đo thời gian mở màn hình | M |
| TC-HIS-08 | `toggleBookmark` 2 lần liên tiếp | Giá trị quay về ban đầu | I |

### FR7 — AI-Generated Practice Questions

| ID | Điều kiện | Kết quả mong đợi | Mức |
|---|---|---|---|
| TC-QUI-01 | Bấm "Tạo câu hỏi", count=5 | Tạo đúng 5 `QuizItem` | I |
| TC-QUI-02 | `MULTIPLE_CHOICE` | Có `optionA..D` khác null | I |
| TC-QUI-03 | `SHORT_ANSWER` / `FILL_BLANK` | `optionA..D` null | I |
| TC-QUI-04 | Trả lời **đúng** | `isCorrect=true`, hiện giải thích, cộng XP | I |
| TC-QUI-05 | Trả lời **sai** | `isCorrect=false`, **không** cộng `QUIZ_BONUS` | I |
| TC-QUI-06 | Câu chưa trả lời | `isCorrect = null` (không phải `false`) | I |
| TC-QUI-07 | Tính `accuracy()` với 2 đúng / 1 sai / 1 chưa làm | `0.5` — **không** tính câu chưa làm vào mẫu số | I |
| TC-QUI-08 | Câu luyện tập sinh từ câu hỏi cũ | `sourceQuestionId` trỏ đúng bản ghi gốc | I |
| TC-QUI-09 | Trả lời "đúng" nhưng khác chữ hoa/thừa khoảng trắng | Vẫn tính đúng (đã chuẩn hoá) | I |

### FR8 — Progress Tracking

| ID | Điều kiện | Kết quả mong đợi | Mức |
|---|---|---|---|
| TC-PRO-01 | 3 câu hỏi, 2 môn khác nhau | `totalQuestions=3`, `questionsBySubject` đúng | I |
| TC-PRO-02 | 0 câu nào | Dashboard hiện 0, **không** chia 0, **không** crash | I |
| TC-PRO-03 | 0 câu luyện tập | `quizAccuracy = 0.0`, **không** `NaN` | U |
| TC-PRO-04 | 4 câu luyện tập: 3 đúng, 1 chưa làm | `accuracy = 0.75` | I |
| TC-PRO-05 | Mở 1 câu trả lời 45 giây | `reviewTimeSeconds = 45` | I |
| TC-PRO-06 | `refreshInsights()` | Trả về ≥ 1 chuỗi insight dạng thật | I |
| TC-PRO-07 | So sánh số câu hỏi tháng này vs tháng trước | Số liệu khớp với dữ liệu thô | I |

### FR9 — Gamification

| ID | Điều kiện | Kết quả mong đợi | Mức |
|---|---|---|---|
| TC-GAM-01 | Hỏi 1 câu | `totalXp += 5`, 1 dòng `xp_events` | I |
| TC-GAM-02 | `levelFor()` tại các mốc biên | 0→L1, 99→L1, 100→L2, 249→L2, 250→L3, 2699→L9, 2700→L10 | U |
| TC-GAM-03 | `levelFor(-1)` (XP âm do lỗi) | Trả về 1, không crash | U |
| TC-GAM-04 | Đọc câu trả lời ≥ 10 giây rồi bấm lại | Cộng `XP_REVIEW_ANSWER` | I |
| TC-GAM-05 | Câu hỏi đầu tiên | Nhận `FIRST_QUESTION` đúng 1 lần | I |
| TC-GAM-06 | Đạt 7 ngày liên tiếp | Nhận `STREAK_7` | I |
| TC-GAM-07 | Đạt 50 câu luyện tập với ≥ 80% đúng | Nhận `QUIZ_MASTER` | I |
| TC-GAM-08 | Xem leaderboard | Trả về danh sách, `isCurrentUser` đúng với dòng của mình | I |
| TC-GAM-09 | `totalXp` lệch với `SUM(xp_events)` | Phát hiện được (test bất biến) | I |
| TC-GAM-10 | 2 thiết bị, cùng tài khoản | Leaderboard mỗi máy khác nhau → **xác nhận giới hạn MVP** | M |

### FR10 — Notifications

| ID | Điều kiện | Kết quả mong đợi | Mức |
|---|---|---|---|
| TC-NOT-01 | Bookmark 1 câu, quá hạn 3 ngày | Tạo `REVIEW_REMINDER` | I |
| TC-NOT-02 | Sai cùng một lỗi 3 lần | Tạo `MISTAKE_ALERT` | I |
| TC-NOT-03 | Không hoạt động 3 ngày | Tạo `DAILY_SUMMARY` | I |
| TC-NOT-04 | Bấm 1 thông báo | `isRead=true`, badge giảm 1 | I |
| TC-NOT-05 | Bấm thông báo có `deepLink` | Mở đúng `QuestionDetailScreen` | I |

---

## 4. Ma trận truy vết — nhóm phi chức năng

### N1 — Security & Safety

| ID | Điều kiện | Kết quả mong đợi | Mức |
|---|---|---|---|
| TC-SEC-01 | Mở file DB bằng trình soạn text | **Không** thấy plaintext mật khẩu; chỉ thấy `passwordHash` + `passwordSalt` | I |
| TC-SEC-02 | Mở file DB | Cột `questionText`/`answerText` đã mã hoá qua `CryptoBox` | I |
| TC-SEC-03 | `CryptoBox.encrypt` → `decrypt` | Khôi phục đúng nguyên văn, kể cả dấu tiếng Việt | U |
| TC-SEC-04 | `decrypt` với sai IV | Ném lỗi, không trả plaintext sai | U |
| TC-SEC-05 | Đọc file ảnh trên máy không root | Nội dung ảnh không đọc được ⇒ đã mã hoá | I |
| TC-SEC-06 | Bật 2FA, nhập mã đúng trong 30s | Đăng nhập thành công | I |
| TC-SEC-07 | Nhập mã sai 3 lần | Từ chối, không tạo phiên | I |
| TC-SEC-08 | Dán nội dung lạm dụng vào câu hỏi (test bằng từ khoá đen) | `AbuseDetector` chặn, không gửi lên AI, không tốn tiền | I |
| TC-SEC-09 | Gửi 20 câu giống nhau trong 1 phút | Chặn spam ở câu thứ N | I |
| TC-SEC-10 | File `local.properties` | Có `.gitignore`, `git status` không hiện file này | M |

### N2 — Offline Access

| ID | Điều kiện | Kết quả mong đợi | Mức |
|---|---|---|---|
| TC-OFF-01 | Tắt mạng, mở app | App mở, 5 tab vẫn dùng được | I |
| TC-OFF-02 | Tắt mạng, mở lịch sử | Đọc được câu hỏi **và** câu trả lời đã lưu | I |
| TC-OFF-03 | Tắt mạng, hỏi câu mới | Xếp vào `PendingSyncQueue`, báo rõ sẽ gửi sau | I |
| TC-OFF-04 | Bật lại mạng | Hàng đợi tự drain, câu hỏi được gửi, không gửi trùng | I |
| TC-OFF-05 | Tắt mạng giữa chừng lúc đang gọi AI | Retry an toàn, không tạo 2 bản ghi trùng nhau | I |

### N3 — Data Privacy (GDPR / COPPA)

| ID | Điều kiện | Kết quả mong đợi | Mức |
|---|---|---|---|
| TC-PRV-01 | Bấm "Xoá dữ liệu của tôi" | Xoá hết `question_answers`, `quiz_items`, `xp_events`, `user_badges`, `notifications` của user đó | I |
| TC-PRV-02 | Xoá dữ liệu | Giữ lại `users` + `profiles` (tài khoản vẫn dùng được) | I |
| TC-PRV-03 | `exportAll(userId)` | Xuất JSON chứa đủ dữ liệu của user đó, **không** của user khác | I |
| TC-PRV-04 | Kiểm tra dữ liệu thu thập | **Không** có cột ngày sinh, địa chỉ, vị trí, tên trường | I |
| TC-PRV-05 | API key nằm trong `BuildConfig` | Không xuất hiện trong logcat ở bản release | I |
| TC-PRV-06 | Bản AndroidManifest | Không khai báo quyền `ACCESS_FINE_LOCATION`, không `READ_CONTACTS` | I |

### NFR chung

| ID | Điều kiện | Ngưỡng chấp nhận | Mức |
|---|---|---|---|
| TC-NFR-01 | Mở app từ icon đến màn hình "Hỏi AI" | ≤ 1,5 s trên emulator | I |
| TC-NFR-02 | Gửi câu không cache, đợi AI trả lời | ≤ 8 s (p95) | I |
| TC-NFR-03 | Lịch sử 1.000 câu, mở tab Lịch sử | ≤ 1 s, không ANR | I |
| TC-NFR-04 | Dashboard với 5.000 câu hỏi | ≤ 1 s | I |
| TC-NFR-05 | Quay vòng 5 tab 50 lần | Không rò rỉ bộ nhớ (heap ổn định) | I |
| TC-NFR-06 | Xoay màn hình dọc↔ngang | Không crash, giữ đúng trạng thái nhập | I |
| TC-NFR-07 | Chữ trên nút ở cỡ chữ hệ thống lớn nhất | Không bị cắt, không chồng lấn | I |
| TC-NFR-08 | `./gradlew test` trên máy sạch | Tất cả unit test xanh, < 30 s | M |
| TC-NFR-09 | Tổng binary debug | < 35 MB (hiện 30,8 MB) | M |

---

## 5. Cấu hình thiết bị test

| Thiết bị | Dùng để |
|---|---|
| Emulator Android 13 (API 33), cấu hình thấp nhất | Test chức năng, đo hiệu năng (đây là `minSdk`) |
| Emulator Android 15 (API 35) | Test tương thích |
| Máy thật Android 13 | Test camera (chụp ảnh bài tập) và hiệu năng thực tế |
| Máy không có mạng (hoặc bật chế độ máy bay) | Test offline (N2) |

**Điều kiện bắt buộc:** tất cả ca đo hiệu năng phải chạy trên emulator cấu hình **thấp nhất**, không phải máy mạnh — nếu không con số trong báo cáo không có ý nghĩa.

---

## 6. Quy trình báo lỗi

Mỗi lỗi phải có đủ:

1. **Mã lỗi** theo dạng `BUG-<phase>-<số>` (vd. `BUG-P2-003`).
2. Bản chất: **mô tả** (không phải nguyên nhân).
3. **Bước tái hiện** — người khác phải làm theo được mà không cần hỏi lại.
4. **Kết quả thực tế** vs **kết quả mong đợi**.
5. **Mức độ nghiêm trọng** (bảng mục 7).
6. Ảnh chụp màn hình hoặc logcat.
7. Ca hồi quy tương ứng (bắt buộc trước khi đóng).

---

## 7. Mức độ nghiêm trọng

| Mức | Định nghĩa | Ví dụ | Hạn xử lý |
|---|---|---|---|
| **S1 — Blocking** | App không khởi động, hoặc rò rỉ dữ liệu/mật khẩu | Crash khi mở; hash mật khẩu lộ trong DB | Trong 24 giờ |
| **S2 — Critical** | Hết tính năng chính, không có workaround | Hỏi câu mới luôn lỗi | Trong 2 ngày |
| **S3 — Major** | Mất tính năng phụ, có workaround | Bookmark xoá mất danh sách | Trước sprint kế tiếp |
| **S4 — Minor** | Lỗi hiển thị, không mất chức năng | Sai lề một nhãn | Khi có thời gian |

**Điều kiện kết thúc phase (definition of done):** không còn lỗi S1/S2 mở; test đạt tỉ lệ pass **≥ 95%** cho các ca tự động của phase đó.

---

## 8. Ước lượng khối lượng

| Nhóm | Số ca tự động | Số ca thủ công |
|---|---|---|
| FR1 + N1 bảo mật | 25 | 5 |
| FR2 | 4 | 3 |
| FR3–FR5 (core) | 18 | 6 |
| FR6 | 6 | 2 |
| FR7 | 7 | 1 |
| FR8 | 5 | 2 |
| FR9 | 8 | 2 |
| FR10 | 5 | 0 |
| N2 Offline | 4 | 1 |
| N3 Privacy | 5 | 2 |
| NFR hiệu năng | 3 | 6 |
| **Tổng** | **~90** | **~30** |

**Ước lượng công:** ~90 ca tự động × 20 phút viết + sửa = **30 giờ**; ~30 ca thủ công × 10 phút chạy = **5 giờ**. Rải đều theo 13 phase code còn lại thì khoảng **2,5 giờ/phase** — khả thi cho 14 tuần, và **nằm ngay trong kế hoạch dự án** mà P2 phải trình bày.

---

## 9. Rủi ro của kế hoạch kiểm thử

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| Emulator chạy chậm, team trì hoãn test tới cuối | Cao | Bắt buộc viết test cùng lúc viết code từng phase, không dồn cuối dự án |
| Không có API key Gemini khi demo | Cao | `FakeAiClient` dùng fixture ⇒ app vẫn chạy và test được không cần key |
| Đo hiệu năng trên máy mạnh cho số liệu đẹp rồi bị hỏi | Trung bình | Quy định emulator cấu hình thấp nhất ở mục 5 |
| Test UI Compose dễ vỡ khi sửa layout | Trung bình | Chỉ test hành vi (click được, text hiện đúng), không test vị trí pixel |
| 2 người cùng viết test trùng nhau | Thấp | Chia theo nhóm FR ở mục 3, mỗi người một nửa |

---

## 10. Liên hệ với các tài liệu khác

| Tài liệu | Vai trò |
|---|---|
| `docs/PART1_M1_SOFTWARE_DESIGN.md` mục 4 | Schema Room mà ca kiểm thử DAO dựa vào |
| `docs/PART1_M1_SOFTWARE_DESIGN.md` mục 1 | Bảng truy vết FR → phase code |
| `docs/uml/04_activity_ask_flow.puml` | Nhánh trong ca kiểm thử |
| `docs/uml/08_state_question.puml` | Trạng thái trong ca kiểm thử |
| `docs/uml/09_erd.puml` | Ràng buộc khoá ngoại mà ca kiểm thử dữ liệu dựa vào |
| `docs/PHASE_2_STEP_BY_STEP_CODE.md` *(sẽ tạo)* | Sinh `FakeAiClient` và ca test DAO đầu tiên |
