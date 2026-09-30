# ROADMAP — AI Study Mentor (Unit 22 Application Development)

> Tài liệu này được sinh ra sau khi đọc `Assignment part 1.docx` và `Assignment part 2.docx`.
> **Trạng thái: kế hoạch. Chưa viết code nào.**

---

## 1. Phát hiện quan trọng nhất: Part 1 KHÔNG cần code

Đây là điểm mấu chốt quyết định toàn bộ thứ tự công việc.

| | Part 1 | Part 2 |
|---|---|---|
| Tên đề | *Produce a software **design** document and **research** tools* | *Evaluate performance... and **produce a functional** business application* |
| LO | LO1 (design doc), LO2 (research tools) | LO3 (plan + produce app), LO4 (evaluate) |
| Cần viết code? | **KHÔNG** | **CÓ** |
| Cần bàn giao | Slide trình bày + report 1500–2000 từ | Report 2000–2500 từ + software package + slide |
| Deadline constraint | — | MVP 14 tuần (theo scenario) |

`Phase 0.pdf` mà bạn đang theo là **kế hoạch xây app** — tức là nằm ở **Part 2, Activity 2 (P5 + M4)**. Nó cần thiết, nhưng **không phải thứ nộp cho Part 1**.

Hệ quả: nếu tiếp tục đi hết 14 phase code trước, bạn sẽ trễ phần Part 1 (nộp trước) mà không có gì để nộp.

---

## 2. Bài phải nộp cho Part 1 (chi tiết hoá từ brief)

### Activity 1 — P1 + P2 (~30 slide, trình bày)
- **P1**: Problem statement + user requirements + system requirements; stakeholders; khó khăn BrightPath gặp; các lựa chọn giải pháp (desktop vs mobile).
- **P2**: Rủi ro của dự án + chiến lược quản lý rủi ro (~5 slide).

### Activity 2 — P3 + M2 (~400 từ)
- **P3**: Nghiên cứu SDLC model (ưu/nhược) + công cụ phát triển (Android Studio, MS Project…).
- **M2**: Biện minh vì sao chọn SDLC model và bộ công cụ đó.

### Activity 3 — M1 (~400 từ, không tính diagram)
- Phân tích bài toán → đề xuất lời giải: requirements, system analysis, system design, coding, testing, implementation.
- **OOAD → software design document + UML diagrams (use case, class, …)**.
- **Test plan**.

### Activity 4 — D1 (~400 từ, **mỗi thành viên tự làm**)
- So sánh SDLC model đã chọn vs. các model khác.
- So sánh bộ công cụ đã chọn vs. các bộ khác.

### Yêu cầu hình thức
- Calibri 12, giãn dòng 1.5; margin trái 1.25 cm, phải 1 cm, trên 1 cm, dưới 1 cm; đánh số trang.
- **Harvard referencing** — trích dẫn trong bài + danh mục nguồn cuối bài.
- Bìa = Assignment front sheet 1.
- Nộp PDF lên `cms.btec.edu.vn`.
- Tài liệu khuyến nghị dùng LaTeX.

---

## 3. Phase 0 đã làm xong — ứng với điều gì trong bài

| Việc đã làm | Giá trị cho assignment |
|---|---|
| Chọn Android Studio + Kotlin + AGP 9.3.3 | Bằng chứng "bộ công cụ đã chọn" cho **P3 / M2** |
| Room + KSP (KSP 2.3.12, Room 2.7.2) | Công cụ sinh code DB, giải thích được trong M2 |
| `assembleDebug` thành công, APK 29.6 MB | Bằng chứng toolchain dùng được → dùng lại cho **P5** |

---

## 4. Khoảng trống hiện tại

Project hiện có **đúng 4 file Kotlin** (3 file theme Compose mặc định + `MainActivity` template `Greeting`).
**0 entity, 0 DAO, 0 Activity thật, 0 màn hình.**

Brief liệt kê 10 nhóm yêu cầu chức năng + 6 nhóm không chức năng. Bảng đối chiếu:

| # | Nhóm yêu cầu (brief) | Hiện trạng | Tên entity lớ giải pháp | Gợi ý trong `Phase 0.pdf` |
|---|---|---|---|---|
| 1 | User Registration & Authentication | ❌ | `User` | `User` entity, `UserDao`, `PREFS_AUTH` |
| 2 | Onboarding (level, subjects, style) | ❌ | `Profile` | `Profile`, `ProfileDefaults` |
| 3 | AI Question Submission (text + ảnh) | ❌ | `QuestionAnswer` | `QuestionAnswer` |
| 4 | AI Answer Generation | ❌ | `QuestionAnswer` | |
| 5 | Answer Improvement & Learning Support | ❌ | `QuestionAnswer` | |
| 6 | Question History & Personal Library | ❌ | `QuestionAnswer` | `getAllByUserId()` |
| 7 | AI-Generated Practice Questions | ❌ | `QuizItem` | **`QuizItem`**, cột `isCorrect` |
| 8 | Progress Tracking / dashboard | ❌ | (aggregate query) | `maxOf/minOf` |
| 9 | Gamification (XP, level, badge) | ❌ | `XpRules` | **`XpRules`** |
| 10 | Notifications | ❌ | | |
| N1 | Security (mật khẩu mạnh, mã hoá, 2FA) | ❌ | | |
| N2 | Offline access | ❌ | | |
| N3 | Data privacy (GDPR, COPPA) | ❌ | | |

> Tên entity trong bảng không phải tôi bịa — chúng xuất hiện trong bảng quy ước Kotlin của `Phase 0.pdf` (`object XpRules`, `object ProfileDefaults`, entity `QuizItem`, `companion object { const val PREFS_AUTH = "auth" }`, `fun findByEmail(email: String): User?`). Nghĩa là kế hoạch phase gốc đã map sẵn 1-1 với các nhóm yêu cầu trên.

---

## 5. Việc cần làm tiếp — đề xuất thứ tự

### BƯỚC A (làm ngay, không cần code) → nộp được Part 1

> **Trạng thái 2026-09-30: A1 và A2 ĐÃ XONG.** Còn A3 (viết report) và A4 (slide) là việc của bạn.

| Việc | Trạng thái | Sản phẩm |
|---|---|---|
| **A1. Chốt kiến trúc + UML** | ✅ Xong 30/09 | `PART1_M1_SOFTWARE_DESIGN.md` (schema Room 7 bảng, 12 quyết định D-1..D-12) + `uml/` **13 diagram** kèm PNG: 2 use case, 5 class, 2 activity, 1 component, 1 deployment, 1 state, 1 ERD |
| **A2. Test plan** | ✅ Xong 30/09 | `PART1_M1_TEST_PLAN.md` — ~90 ca tự động + 30 ca thủ công, truy vết đủ 10 FR + 3 NFR, 4 tầng test, 4 mức nghiêm trọng, ước lượng 35 giờ |
| **A3. Viết report Part 1** | ⬜ Chưa làm | Việc của bạn. Khung: P1 → P2 → P3+M2 → M1 → D1. Dùng 2 file `PART1_M1_*` làm nguyên liệu cho M1 |
| **A4. Slide ~30 trang** | ⬜ Chưa làm | Việc của bạn — Activity 1 (P1 + P2) |

**A1 — những gì đã chốt** (thay cho phần dấu hỏi ở mục 6):
- 4 tầng `ui` → `domain` → `data` + `platform`, **1 Gradle module**, không DI framework.
- `domain/` không import `androidx.room`/`android` ⇒ XP, similarity, hash test bằng JUnit thuần, không cần emulator.
- **Không có backend** (local-first). Leaderboard chỉ trên thiết bị — ghi rõ giới hạn này vào design doc và nêu ở P2 như rủi ro đã biết.
- Gọi AI bằng `HttpURLConnection` + `org.json`, **không** Retrofit/OkHttp (D-5): build không cần mạng, không xung đột version với AGP 9.3.3.
- Room schema **7 bảng**: `users`, `profiles`, `question_answers`, `quiz_items`, `xp_events`, `user_badges`, `notifications`.
- **AI Cost Management**: `questionHash` (SHA-256) + Jaccard ≥ 0.85 + cờ `servedFromCache`.

> **Nếu bạn đổi ý về 6.3 hoặc 6.4 thì phải sửa lại diagram** — cụ thể 6.3 (credential: đã chọn EncryptedSharedPreferences) và 6.4 (backend: đã chọn không có).

**A3. Viết report Part 1** theo khung: P1 (problem + requirements) → P2 (risk) → P3+M2 (tools & SDLC) → M1 (design doc + UML) → D1 (đánh giá so sánh).

**A4. Slide trình bày ~30 slide.**

> **Lưu ý về D1:** brief ghi rõ *"The following part each team member must be done individually"*. Với 2 người thì ~400 từ/người — phần này **không chia nhau được**, mỗi người tự viết và tự bảo vệ.

### BƯỚC B (sau khi nộp Part 1) → tiếp tục code, phục vụ Part 2

| Phase | Nội dung | Có thể chứng minh cho bài |
|---|---|---|
| **1** | Main shell: activity chính, theme, navigation, edge-to-edge | P5: "app chạy được" |
| 2 | Schema Room: `User`, `Profile` + `AppDatabase` + DAO | P5 + M1 khớp UML |
| 3 | Register / Login / Logout (`PREFS_AUTH`) | FR1, N1 |
| 4 | Onboarding: chọn level / subjects / explanation style | FR2 |
| 5 | Hỏi bằng text + upload ảnh | FR3 |
| 6 | Tích hợp AI (Gemini / ChatGPT) + hiển thị câu trả lời | FR4 |
| 7 | Answer Improvement (giải thích rút gọn, cách khác, lỗi thường gặp…) | FR5 |
| 8 | Lịch sử + tìm kiếm + bookmark + nhóm theo môn | FR6 |
| 9 | Sinh câu hỏi luyện tập (`QuizItem`) + chấm điểm tức thì | FR7 |
| 10 | Dashboard thống kê | FR8 |
| 11 | Gamification (`XpRules`) | FR9 |
| 12 | Thông báo | FR10 |
| 13 | Mã hoá lưu trữ, kiểm tra mật khẩu, phát hiện lạm dụng | N1, N3 |
| 14 | Offline + sync | N2 |
| 15 | Đóng gói, test, viết report Part 2 | P5, M4, P6, M5, D2 |

Ghi chú về ràng buộc từ scenario — những thứ này **nên có trong design doc** vì giảm chi phí AI (brief nêu rõ "AI Cost Management"):
- Cache câu trả lời cho câu hỏi trùng → `QuestionAnswer` cần index theo nội dung/ hash
- Phát hiện câu hỏi tương tự và tái dùng kết quả

---

## 6. Các quyết định cần bạn chốt TRƯỚC khi viết code

### 6.1 Compose hay View truyền thống? (quan trọng nhất)

Mâu thuẫn hiện tại trong dự án:

| Nguồn | Nói gì |
|---|---|
| `Phase 0.pdf` — bảng quy ước | `private lateinit var tvTitle: TextView`, `btnSubmit.setOnClickListener { login() }`, `startActivity(Intent(this, MainActivity::class.java))` → **View** |
| Project thực tế | `buildFeatures { compose = true }`, plugin `kotlin-compose`, `setContent { AIStudyTheme { } }` → **Compose** |

Hai hướng cho class diagram khác nhau, và class diagram là M1. Phải chốt trước khi vẽ.

**Khuyến nghị: giữ Compose.** Project đã cài sẵn, `MainActivity` đã viết bằng Compose, không cần thêm dependency Material/AppCompat. Bỏ Compose đi tốn công hơn giữ. Nhưng nếu thầy yêu cầu View (vì tài liệu ghi thế), thì phải sửa cả `app/build.gradle.kts` + `themes.xml` + `MainActivity.kt`.

### 6.2 Nhà cung cấp AI
Brief nói "AI integration (ChatGPT, Google Gemini)". Chọn 1. Ảnh hưởng tới cấu trúc code, câu hỏi bảo mật API key, và chi phí.

### 6.3 Lưu trữ credential
- SharedPreferences (đơn giản, hợp `PREFS_AUTH` trong tài liệu)
- Hay EncryptedSharedPreferences (khớp yêu cầu "encrypted storage")

### 6.4 Backend có không?
Brief nói leaderboard "global or friends-based" → cần server. Nhưng "Limited budget" và team junior. Đề xuất: **local-first, leaderboard để placeholder** — ghi rõ vào design doc là giới hạn của MVP, nêu trong phần risk.

---

## 7. Rủi ro cần nêu trong P2

| Rủi ro | Mức | Giảm thiểu |
|---|---|---|
| Tên thư mục có dấu tiếng Việt → AGP chặn build, `gradlew.bat` hỏng | **Đã xảy ra** | Tạm dùng `android.overridePathCheck=true` + `subst R:`. Nên chuyển project sang path ASCII |
| Mất mạng → Gradle không tải được dependency | **Đã xảy ra** | Có cache sẵn Room/KSP. Bật mạng trước khi demo |
| Chi phí API AI vượt ngân sách | Cao | Cache + dedup câu hỏi (brief đã yêu cầu) |
| COPPA — app cho học sinh <13 tuổi | Cao | Cần nêu rõ trong design doc; không thu thập data tuổi trẻ |
| 14 tuần cho MVP, team junior | Trung bình | Agile/Scrum với sprint 1–2 tuần — cũng là câu trả lời cho M2 |
| Không đạt chất lượng ảnh chụp màn hình cho report | Thấp | Chụp dần theo từng phase, không để tới cuối |

---

## 8. Việc cần bạn quyết trước khi tôi bắt đầu

1. **Compose hay View?** (chặn A1 — không có quyết định này thì không vẽ được class diagram)
2. **Có cần tôi viết luôn phần report Part 1 không, hay chỉ phần thiết kế/UML/test plan?**
3. **Có cần tạo thư mục `docs/` trong project `AI Study` không?** Hiện `docs/` đặt cạnh `Phase 0.pdf`, ngoài project.
4. **Gemini hay ChatGPT?**

Sau khi bạn chốt, tôi sẽ viết `docs/PHASE_1_STEP_BY_STEP_CODE.md` đúng format tài liệu Phase 0 đang dùng, để bạn duyệt trước khi tôi đụng vào code.
