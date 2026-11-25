---
name: AIStudy
description: Premium EdTech Design System — Deep Forest Green + Warm Neutral with Editorial Learning Layouts.
---

# AIStudy Design System — Premium EdTech Edition

Approved design direction: **Deep Forest Green + Warm Neutral Editorial Learning Workspace**.
Built natively with Kotlin, Android Views/XML, AppCompat, and Material Components (Material 3).

The auth language control sits beside the AIStudy brand in a shared header for sign-in
and registration. Use a sage pill, globe icon, native language name, and down chevron,
with a 48dp touch target and semantic colors for light and dark modes. Reuse the same
picker in profile settings. Android stores the selection across app launches.

---

## 1. Design Philosophy & Vision

AIStudy is designed as a calm, focused, scholarly, and human-centered educational workspace for students. 
Unlike typical AI wrappers filled with gimmicky neon gradients and noisy AI chatbots, AIStudy treats learning with dignity, clarity, and precision:
- **Clarity over Clutter:** Clear visual hierarchy, generous white space, and deliberate typography that guides the learner's attention through complex problem-solving.
- **Editorial Structure:** Step-by-step solutions are structured like a premium textbook: numbered path indicators, isolated mathematical formulas, dedicated syntax-highlighted code blocks, and an undeniable conclusion box.
- **Warmth & Encouragement:** Gentle forest-green hues inspire focus and endurance, accented by warm amber gold for learning streaks, stars, and XP progression.
- **Native Android Excellence:** Full edge-to-edge support, dark mode symmetry, accessible touch targets (≥48dp), responsive columns (scaling gracefully from 320dp phones to tablets), and seamless keyboard management.

---

## 2. Color Palette & Tokens

### Semantic Color Matrix

| Semantic Role | Token Name | Light Mode | Dark Mode | Purpose |
|---|---|---|---|---|
| **Background** | `@color/answer_background` | `#F8F9F6` (Warm Paper) | `#121915` (Deep Forest Night) | Page and canvas context |
| **Surface** | `@color/answer_surface` | `#FFFFFF` (Crisp White) | `#1B2520` (Forest Slate) | Cards, inputs, sheets |
| **Primary** | `@color/answer_primary` | `#234B36` (Deep Forest) | `#A9CBB0` (Sage Bloom) | Main buttons, active tabs, highlights |
| **On Primary** | `@color/answer_on_primary` | `#FFFFFF` | `#16281D` | Text/icons on primary surfaces |
| **Primary Soft** | `@color/answer_result_surface` | `#EAF2EC` (Sage Tint) | `#25382B` (Muted Moss) | Result cards, active chips, badges |
| **Foreground (Text)** | `@color/answer_foreground` | `#18251E` (Ink Forest) | `#E6EDE8` (Pale Mist) | Primary headings, body copy |
| **Secondary Text** | `@color/answer_foreground_secondary` | `#586A5F` (Muted Sage) | `#A3B4A9` (Soft Mist) | Subtitles, timestamps, metadata |
| **Outline / Border** | `@color/answer_divider` | `#DFE6E0` (Hairline Sage) | `#2E3D33` (Deep Slate Border) | 1dp card borders, dividers, outlines |
| **Control Outline** | `@color/answer_outline` | `#76877B` | `#7E9384` | Form borders, secondary buttons |
| **Streak & XP Accent** | `@color/streak` | `#D97706` (Warm Amber) | `#F59E0B` (Luminous Amber) | Streaks, stars, XP points, medals |
| **Streak Soft** | `@color/streak_soft` | `#FEF3C7` (Amber Tint) | `#382914` (Dark Amber Surface) | Streak pill background |
| **Error** | `@color/answer_error` | `#C5221F` (Crimson) | `#F2B8B5` (Soft Coral) | Error banners, invalid input borders |
| **Error Surface** | `@color/answer_error_surface` | `#FCE8E6` | `#441412` | Error message background |

### Subject Identity Accents

| Subject | Accent Color | Soft Background | Icon / Glyph |
|---|---|---|---|
| **Toán học (Math)** | `#234B36` (Forest) | `#EAF2EC` | `ic_study_math` (∑ / π) |
| **Khoa học (Science)** | `#0D9488` (Teal) | `#E6FFFA` | `ic_study_science` (Atom) |
| **Lập trình (Code)** | `#2563EB` (Azure) | `#EFF6FF` | `ic_study_code` (Terminal `</>`) |
| **Lịch sử (History)** | `#D97706` (Amber) | `#FEF3C7` | `ic_answer_book` (Scroll) |
| **Ngoại ngữ (Language)** | `#7C3AED` (Violet) | `#F5F3FF` | `ic_study_language` (Globe / A) |
| **Khác (General)** | `#4B5563` (Slate) | `#F3F4F6` | `ic_sparkles` (Sparkle) |

---

## 3. Typography Hierarchy

Using Android system sans-serif family for natural Vietnamese diacritics and system `monospace` for formulas and code blocks.

| Role | Size | Line Height | Weight | Usage |
|---|---|---|---|---|
| **Hero Display** | 32sp | 40sp | Bold (700) / Semibold (600) | Welcome titles, Hero greeting, Auth headline |
| **Page / Question Title** | 24–28sp | 32–36sp | Semibold (600) | Main question in Answer, Screen titles |
| **Section Header** | 20sp | 28sp | Semibold (600) | Dashboard sections, Explanation header |
| **Step / Card Title** | 17–18sp | 26sp | Semibold (600) | Step titles, Game names, Quiz prompts |
| **Body (Reading)** | 15–16sp | 24–26sp | Regular (400) | Explanations, step prose, descriptions |
| **Formulas (Math)** | 15sp | 24sp | Regular Monospace | Equations, identities, arithmetic steps |
| **Code Blocks** | 14sp | 22sp | Regular Monospace | Syntax blocks, preserves indentation & spaces |
| **Metric Value** | 32–36sp | 40–44sp | Bold (700, tabular numerals) | XP totals, score counter, streak count |
| **Labels & Badges** | 12–13sp | 18sp | Bold / Medium (600/500) | Subject pills, difficulty tags, button labels |
| **Micro / Caption** | 11sp | 16sp | Regular / Medium | Timestamps, helper hints, sub-labels |

---

## 4. Spacing, Geometry & Shadows

### Spacing Scale
- `space_1`: **4dp** (Icon gaps, tag padding)
- `space_2`: **8dp** (Tight element spacing, subtitle margins)
- `space_3`: **12dp** (Card internal padding small, input inner gaps)
- `space_4`: **16dp** (Standard gutter, card padding, list item margin)
- `space_6`: **24dp** (Section gaps, hero padding)
- `space_8`: **32dp** (Major layout separation, bottom sheet gutters)
- `space_12`: **48dp** (Large header separation)

### Radii & Shapes
- **Hairline Pill (999dp):** Filter chips, streak badges, difficulty tags, status pills, floating bottom navigation bubble.
- **Card Large (16–20dp):** Dashboard cards, Question cards, Result card, Game cards.
- **Control Medium (12–14dp):** Primary buttons, input fields, step number badge, choice options.
- **Block Small (8dp):** Code blocks, expression containers, small image previews.

### Elevation & Borders
- Cards utilize a crisp **1dp hairline border** (`@color/answer_divider`) paired with subtle tonal depth (0dp to 2dp elevation) rather than muddy drop-shadows. This maintains high contrast and readability on OLED and IPS displays alike.

---

## 5. Screen-by-Screen Redesign Specifications

### A. Login & Register (`auth_activity_login.xml`, `auth_activity_register.xml`)
- **Brand Hero:** Elegant 52dp circular forest-green badge with open-book vector, "AIStudy" title, and inspiring subtitle: *"Nền tảng học tập thông minh cùng AI"*.
- **Form Card:** Clean single-column layout centered up to 480dp width. 
- **Inputs:** Outlined fields with 14dp radius, pleasant floating hint, lead icon, password reveal action, and inline validation error text.
- **Actions:** Commanding 52dp forest-green primary button with smooth press ripple, followed by a delicate divider and pill link to toggle between Sign In and Sign Up.

### B. Onboarding (`activity_onboarding.xml`, `OnboardingActivity.kt`)
- **Step Header:** Dynamic 3-segment pill progress indicator and counter (*"Bước 1 / 3: Chọn cấp học"*).
- **Choice Cards:**
  - *Cấp học (Level):* THCS, THPT, Đại học & Tự học — each in an interactive 16dp rounded card with custom glyph, title, description, and vibrant selection border.
  - *Môn học (Subjects):* Interactive multi-select grid with subject icons (Toán, Khoa học, Lập trình, Lịch sử, Ngoại ngữ, Khác).
  - *Phong cách (Style):* Ngắn gọn, Từng bước (Khuyên dùng), Toàn diện — with clear guidance on how AI will answer.
- **Bottom Navigation:** Smooth "Quay lại" and "Tiếp tục / Bắt đầu học" buttons.

### C. Home Dashboard (`fragment_home.xml`, `include_home_journey.xml`, `include_home_continue.xml`)
- **Learner Header:** Greeting with student name (*"Chào bạn, Linh! ✨"*), current date, streak pill (🔥 3 ngày), and elegant rounded Logout button.
- **Hero Ask Banner:** Deep forest-green card with spark illustration, motivating copy (*"Bạn đang thắc mắc điều gì?"*), and a prominent *"Hỏi bài cùng AI ngay →"* action.
- **Learning Journey Metrics:** Two-column dashboard cards displaying Total Saved Questions and Accumulated XP with bold tabular numbers.
- **Continue Learning:** High-priority card displaying the latest answered question, subject pill, time, and one-tap *"Xem lại lời giải"*.

### D. Ask / AI Q&A (`fragment_ask.xml`, `AskFragment.kt`)
- **Question Composer:** High-end card container with 160dp minimum height, soft background, character count, and persistent focus border.
- **Multimodal Toolbar:** Camera and Gallery actions styled as refined outlined buttons.
- **Image Preview:** 12dp rounded thumbnail preview with floating remove button.
- **Send Action:** 52dp high-contrast primary button with clear sending state and polite accessibility live updates.
- **Curated Suggestions:** 4 horizontal/vertical prompt cards with subject icons (Toán học, Khoa học, Lập trình, Ngoại ngữ) that fill the composer on tap.

### E. Answer & Solution Reader (`activity_answer.xml`, `item_answer_*.xml`)
- **Sticky App Bar:** 48dp outlined Back button, screen title, Subject badge, and Difficulty tag (*Dễ / Trung bình / Khó*).
- **Question Card:** Elevated card displaying the full question text with high-contrast typography.
- **Step-by-Step Learning Rail:**
  - Numbered circular badges (`01`, `02`, `03`) connected by a continuous vertical hairline guide.
  - Step title in bold 18sp.
  - Step explanation prose in comfortable 15sp text.
  - Mathematical formulas in dedicated monospace container with horizontal scrolling and left accent line.
  - Code blocks in syntax-like monospace box with horizontal scrolling.
- **Final Conclusion Box:** Highlighted celebratory card with checkmark badge, bold conclusion header, and clear final answer.
- **Interactive Learning Actions:** Quick-action pills (*"Đơn giản hóa"*, *"Cách giải khác"*, *"Khái niệm cốt lõi"*, *"Lỗi thường gặp"*, *"Câu hỏi mở rộng"*) and *"Luyện tập trắc nghiệm"* button.

### F. Quiz Practice (`activity_quiz.xml`, `QuizActivity.kt`)
- **Top Progress Bar:** Question index tracker (e.g., *"Câu 2 / 5"*) with linear indicator.
- **Question Prompt:** High-contrast card with question text.
- **Selectable Choices:** 4 rich option cards (A, B, C, D) with instant selection state and correct/incorrect feedback animations with explanation.
- **Result Victory Screen:** Celebration layout with score, earned XP (+XP), and motivating feedback.

### G. History Library (`fragment_history.xml`, `item_history.xml`)
- **Search Bar:** 48dp rounded search pill with leading search icon.
- **Subject Filter Row:** Scrollable chips for All, Math, Science, Code, History, Language, and Starred.
- **Question Cards:** Refined cards featuring subject badges, truncated question text, formatted relative time, and interactive star bookmark action.

### H. Progress & Profile (`fragment_progress.xml`, `item_leaderboard_row.xml`)
- **Profile Header:** Student identity, level badge, and logout action.
- **XP Progression Hero:** Modern card displaying level rank, total XP, and progress bar toward next rank.
- **Stats Row:** Streak, Solved Questions, and Starred Answers.
- **Subject Mastery:** Progress bars per subject with percentage breakdown.
- **Leaderboard & Badges:** Top ranking table with gold/silver/bronze medals and unlocked badges showcase.

### I. Playground & Mini-Games (`fragment_playground.xml`, `SpeedMathActivity.kt`, `MemoryMatchActivity.kt`)
- **Game Hub:** Daily challenge banner and game cards for Speed Math and Memory Match.
- **Speed Math:** Countdown timer bar, arithmetic problem card, instant tap choice buttons, combo multiplier with flame.
- **Memory Match:** 3x4 card grid with card-flip states (front/back/matched).

### J. Curved Animated Bottom Navigation (`CurvedAnimatedBottomNav.kt`, `activity_main.xml`)
- Concave scooped notch with floating bubble indicator synchronized with the forest-green palette and dark mode.

---

## 6. Implementation & Verification Rules
- All existing features, database tables, Gemini API integrations, navigation routes, and business rules are strictly preserved.
- Verified with `.\gradlew assembleDebug` with 0 build errors.
