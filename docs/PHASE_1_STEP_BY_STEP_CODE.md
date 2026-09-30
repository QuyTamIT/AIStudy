# Phase 1: Main shell — Khung ứng dụng (Compose)

> Tài liệu này giữ đúng format của `Phase 0.pdf` để dùng song song.
> **Trạng thái: kế hoạch, chưa áp dụng. Chờ duyệt trước khi viết code.**

---

## Mục tiêu Phase 1

Dựng khung chạy được của app "AI Study Mentor": một `MainActivity` duy nhất chứa `Scaffold` với thanh điều hướng dưới (bottom navigation) gồm 5 mục, mỗi mục là một màn hình placeholder. Chưa có database, chưa có AI, chưa có logic.

## Điều kiện trước khi làm

- Phase 0 hoàn thành: Gradle sync OK, KSP chạy được, `assembleDebug` BUILD SUCCESSFUL.
- Cấu hình hiện tại: AGP 9.3.3, Gradle 9.5.0, Kotlin 2.2.10 (built-in), compileSdk/targetSdk 37, minSdk 33, Compose BOM 2026.02.01, Room 2.7.2, KSP 2.3.12.
- **Quyết định kiến trúc đã chốt: giữ Jetpack Compose** (xem Step 1).

## Kết quả sau Phase 1

- App mở ra hiện 1 màn hình trắng có tiêu đề "Hỏi AI", kèm thanh 5 mục ở đáy.
- Bấm mỗi mục chuyển đúng màn hình tương ứng, quay lại mục cũ giữ đúng trạng thái.
- `assembleDebug` BUILD SUCCESSFUL.
- Nền tảng navigation đã xong để các phase sau chỉ cần thêm route và logic.

## Tổng quan các file sẽ chạm

| File | Loại | Việc |
|---|---|---|
| `gradle/libs.versions.toml` | Sửa | Thêm version + library `navigation-compose` |
| `app/build.gradle.kts` | Sửa | Thêm `implementation(libs.androidx.navigation.compose)` |
| `app/src/main/res/values/strings.xml` | Sửa | Thêm label 5 tab + text placeholder |
| `app/src/main/java/com/aistudy/aistudy/MainActivity.kt` | Sửa | Gọi `MainShell()` thay vì `Greeting()` |
| `.../ui/theme/Color.kt` | Sửa | Bảng màu mới cho app học tập |
| `.../ui/theme/Theme.kt` | Sửa | Tắt dynamic color mặc định (giữ màu thương hiệu ổn định) |
| `.../ui/shell/Destinations.kt` | Tạo | `object Routes` + danh sách 5 tab |
| `.../ui/shell/AppNavHost.kt` | Tạo | `NavHost` + 5 route placeholder |
| `.../ui/shell/MainShell.kt` | Tạo | `Scaffold` + `NavigationBar` + điều hướng tab |
| `.../ui/components/SectionPlaceholder.kt` | Tạo | Màn hình trống dùng chung |
| `.../ui/screens/ask/AskScreen.kt` | Tạo | Màn hình Hỏi AI |
| `.../ui/screens/history/HistoryScreen.kt` | Tạo | Màn hình Lịch sử |
| `.../ui/screens/practice/PracticeScreen.kt` | Tạo | Màn hình Luyện tập |
| `.../ui/screens/progress/ProgressScreen.kt` | Tạo | Màn hình Tiến độ |
| `.../ui/screens/profile/ProfileScreen.kt` | Tạo | Màn hình Hồ sơ |

---

## Step 1) Giữ Compose — giải thích lựa chọn

Bảng quy ước ở `Phase 0` viết bằng View (`lateinit var tvTitle: TextView`, `btnSubmit.setOnClickListener`). Nhưng dự án đã cài sẵn Compose (`buildFeatures { compose = true }`, plugin `kotlin-compose`, `MainActivity` dùng `setContent`). Bỏ Compose đi tốn công hơn giữ, nên **giữ Compose** và bổ sung bộ quy ước Compose ở Step 10.

Hai điều kiện còn thiếu nếu đi theo hướng này:

| Cần thêm | Vì sao |
|---|---|
| `androidx.navigation:navigation-compose` | Điều hướng nhiều màn hình. Hiện chỉ có 1 màn hình nên chưa cần, nhưng từ Phase 2 trở đi sẽ cần auth gate và deep link tới chi tiết câu hỏi |
| Bảng màu riêng | `Color.kt` hiện dùng mẫu tím mặc định của Android Studio |

---

## Step 2) Thêm navigation-compose vào `libs.versions.toml`

Trong `[versions]`, thêm dòng cuối:

```toml
navigation = "2.10.2"
```

Trong `[libraries]`, thêm dòng cuối:

```toml
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
```

> `2.10.2` là bản stable mới nhất trên Google Maven, tương thích compileSdk 37.

## Step 3) Khai báo dependency ở `app/build.gradle.kts`

Trong `dependencies`, thêm ngay dưới `implementation(libs.room.runtime)`:

```kotlin
implementation(libs.androidx.navigation.compose)
```

---

## Step 4) Bảng màu — sửa `ui/theme/Color.kt`

Giữ nguyên cấu trúc `val ... = Color(0xFF...)`, chỉ đổi giá trị:

```kotlin
package com.aistudy.aistudy.ui.theme

import androidx.compose.ui.graphics.Color

val Indigo80 = Color(0xFFBBC3FF)
val IndigoGrey80 = Color(0xFFC5C4DD)
val Amber80 = Color(0xFFF7B955)

val Indigo40 = Color(0xFF4A4EA8)
val IndigoGrey40 = Color(0xFF5D5C71)
val Amber40 = Color(0xFF8B5000)
```

## Step 5) Theme — sửa `ui/theme/Theme.kt`

Chỉ đổi **một dòng**: đặt `dynamicColor` mặc định là `false`.

```kotlin
@Composable
fun AIStudyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
```

Phần còn lại giữ nguyên (`darkColorScheme(primary = Indigo80, secondary = IndigoGrey80, tertiary = Amber80)`, `lightColorScheme(... Indigo40 ...)`).

**Vì sao tắt:** dynamic color lấy màu từ wallpaper của thiết bị. Mỗi máy một màu → ảnh chụp màn hình trong report Part 2 không đồng nhất, và giảng viên khó so sánh giữa các nhóm. Tắt đi thì màu thương hiệu cố định.

---

## Step 6) Định nghĩa route — tạo `ui/shell/Destinations.kt`

```kotlin
package com.aistudy.aistudy.ui.shell

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import com.aistudy.aistudy.R

data class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
)

object Routes {
    const val ARG_QUESTION_ID = "questionId"

    const val ASK = "ask"
    const val HISTORY = "history"
    const val PRACTICE = "practice"
    const val PROGRESS = "progress"
    const val PROFILE = "profile"

    const val QUESTION_DETAIL = "question/{$ARG_QUESTION_ID}"

    fun questionDetail(id: Long): String = "question/$id"
}

val TOP_LEVEL_DESTINATIONS = listOf(
    TopLevelDestination(Routes.ASK, R.string.nav_ask, Icons.Filled.Home),
    TopLevelDestination(Routes.HISTORY, R.string.nav_history, Icons.Filled.List),
    TopLevelDestination(Routes.PRACTICE, R.string.nav_practice, Icons.Filled.Star),
    TopLevelDestination(Routes.PROGRESS, R.string.nav_progress, Icons.Filled.Refresh),
    TopLevelDestination(Routes.PROFILE, R.string.nav_profile, Icons.Filled.Person)
)
```

**5 tab này lấy từ đâu** — bám theo nhóm yêu cầu trong đề Assignment:

| Tab | Route | Nhóm yêu cầu trong đề |
|---|---|---|
| Hỏi AI | `ask` | AI Question Submission + AI Answer Generation |
| Lịch sử | `history` | Question History & Personal Library |
| Luyện tập | `practice` | AI-Generated Practice Questions |
| Tiến độ | `progress` | Progress Tracking |
| Hồ sơ | `profile` | User Registration + Onboarding + Gamification |

> **Về icon:** Compose chỉ có sẵn bộ icon lõi (`Home`, `List`, `Star`, `Refresh`, `Person`) nên ngữ nghĩa hơi chung chung. Nếu muốn icon đẹp hơn (`Psychology`, `Quiz`, `Insights`) thì thêm `androidx.compose.material:material-icons-extended`, nhưng nó nặng và làm chậm build. Đề xuất: giữ bộ lõi, đủ dùng cho assignment.

---

## Step 7) Màn hình trống dùng chung — tạo `ui/components/SectionPlaceholder.kt`

```kotlin
package com.aistudy.aistudy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aistudy.aistudy.R
import com.aistudy.aistudy.ui.theme.AIStudyTheme

@Composable
fun SectionPlaceholder(
    titleRes: Int,
    bodyRes: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SectionPlaceholderPreview() {
    AIStudyTheme {
        SectionPlaceholder(R.string.ask_placeholder_title, R.string.ask_placeholder_body)
    }
}
```

---

## Step 8) Tạo 5 file màn hình

Mỗi file đặt trong thư mục riêng theo tên màn hình, chỉ gọi `SectionPlaceholder`. Lấy `AskScreen.kt` làm mẫu, 4 file kia đổi tên package + hàm + 2 hằng string:

```kotlin
package com.aistudy.aistudy.ui.screens.ask

import androidx.compose.runtime.Composable
import com.aistudy.aistudy.R
import com.aistudy.aistudy.ui.components.SectionPlaceholder

@Composable
fun AskScreen() {
    SectionPlaceholder(
        titleRes = R.string.ask_placeholder_title,
        bodyRes = R.string.ask_placeholder_body
    )
}
```

| File | Hàm |
|---|---|
| `ui/screens/ask/AskScreen.kt` | `AskScreen()` |
| `ui/screens/history/HistoryScreen.kt` | `HistoryScreen()` |
| `ui/screens/practice/PracticeScreen.kt` | `PracticeScreen()` |
| `ui/screens/progress/ProgressScreen.kt` | `ProgressScreen()` |
| `ui/screens/profile/ProfileScreen.kt` | `ProfileScreen()` |

---

## Step 9) NavHost — tạo `ui/shell/AppNavHost.kt`

```kotlin
package com.aistudy.aistudy.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.aistudy.aistudy.ui.screens.ask.AskScreen
import com.aistudy.aistudy.ui.screens.history.HistoryScreen
import com.aistudy.aistudy.ui.screens.practice.PracticeScreen
import com.aistudy.aistudy.ui.screens.profile.ProfileScreen
import com.aistudy.aistudy.ui.screens.progress.ProgressScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.ASK,
        modifier = modifier
    ) {
        composable(Routes.ASK) { AskScreen() }
        composable(Routes.HISTORY) { HistoryScreen() }
        composable(Routes.PRACTICE) { PracticeScreen() }
        composable(Routes.PROGRESS) { ProgressScreen() }
        composable(Routes.PROFILE) { ProfileScreen() }
    }
}
```

Route `Routes.QUESTION_DETAIL` đã khai báo ở Step 6 nhưng **chưa** đăng ký `composable(...)` — cố ý để trống cho tới Phase 8 (màn hình chi tiết câu hỏi). Khai báo trước để các phase sau không phải sửa lại `Destinations.kt`.

---

## Step 10) Main shell — tạo `ui/shell/MainShell.kt`

```kotlin
package com.aistudy.aistudy.ui.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

@Composable
fun MainShell() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                TOP_LEVEL_DESTINATIONS.forEach { destination ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == destination.route } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.navigateToTopLevel(destination) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
```

**Vì sao `navigateToTopLevel` phải có 3 dòng đó** — bỏ điều hướng sẽ hỏng:
- `popUpTo(startDestination) { saveState = true }` + `restoreState = true`: giữ riêng stack của từng tab. Bấm Hỏi → Lịch sử → Hỏi thì Lịch sử phải quay về đúng chỗ đang xem, không bị reset.
- `launchSingleTop = true`: bấm tab đang chọn không tạo thêm một bản sao màn hình.

---

## Step 11) Sửa `MainActivity.kt`

Xoá hết nội dung cũ, thay bằng:

```kotlin
package com.aistudy.aistudy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aistudy.aistudy.ui.shell.MainShell
import com.aistudy.aistudy.ui.theme.AIStudyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIStudyTheme {
                MainShell()
            }
        }
    }
}
```

Hàm `Greeting` và `GreetingPreview` cũ bị xoá. `enableEdgeToEdge()` giữ nguyên — `Scaffold` + `NavigationBar` sẽ tự tính inset, nội dung không bị che.

---

## Step 12) Sửa `res/values/strings.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">AI Study Mentor</string>

    <string name="nav_ask">Hỏi AI</string>
    <string name="nav_history">Lịch sử</string>
    <string name="nav_practice">Luyện tập</string>
    <string name="nav_progress">Tiến độ</string>
    <string name="nav_profile">Hồ sơ</string>

    <string name="ask_placeholder_title">Hỏi AI</string>
    <string name="ask_placeholder_body">Đặt câu hỏi bằng chữ hoặc chụp ảnh bài tập. Tính năng này được triển khai ở Phase 5.</string>

    <string name="history_placeholder_title">Lịch sử</string>
    <string name="history_placeholder_body">Tất cả câu hỏi và câu trả lời sẽ được lưu tự động tại đây.</string>

    <string name="practice_placeholder_title">Luyện tập</string>
    <string name="practice_placeholder_body">AI sẽ sinh câu hỏi trắc nghiệm và bài tập từ các câu hỏi bạn đã hỏi.</string>

    <string name="progress_placeholder_title">Tiến độ</string>
    <string name="progress_placeholder_body">Thống kê số câu hỏi, môn hay hỏi, tỉ lệ chính xác và thời gian ôn tập.</string>

    <string name="profile_placeholder_title">Hồ sơ</string>
    <string name="profile_placeholder_body">Cấp học, môn quan tâm, phong cách giải thích, XP và huy hiệu.</string>
</resources>
```

> `app_name` đổi từ `AI Study` → `AI Study Mentor` cho khớp tên trong đề bài.

---

## Step 13) Build & kiểm tra

1. **File → Sync Project with Gradle Files**
2. Chạy:
   ```
   .\gradlew.bat :app:assembleDebug
   ```
3. Cài lên emulator và mở:
   ```
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   adb shell am start -n com.aistudy.aistudy/.MainActivity
   ```
4. **Kết quả mong đợi:** màn hình giữa hiện "Hỏi AI", dưới cùng có 5 mục `Hỏi AI · Lịch sử · Luyện tập · Tiến độ · Hồ sơ`. Bấm mỗi mục, nội dung giữa đổi theo.

> **Lưu ý môi trường máy này:** thư mục có dấu tiếng Việt nên `.\gradlew.bat` chạy trực tiếp sẽ lỗi `Unable to access jarfile`. Dùng:
> ```
> subst R: "<đường dẫn tới AI Study>"
> R:\gradlew.bat :app:assembleDebug
> ```

---

## Bảng lỗi thường gặp

| Triệu chứng | Nguyên nhân | Cách xử lý |
|---|---|---|
| `Unresolved reference: navigation` | Chưa thêm library vào `libs.versions.toml` | Kiểm tra đã khai báo `androidx-navigation-compose` chưa |
| `Unresolved reference: navigationCompose` | Đã thêm alias nhưng chưa khai báo `implementation` | Thêm dòng dependency ở Step 3 |
| `Unresolved reference: Icons` | Thiếu import `androidx.compose.material.icons.Icons` | Bổ sung import |
| `Cannot find a parameter with this name: currentBackStackEntryAsState` | Sai package | Đúng là `androidx.navigation.compose.currentBackStackEntryAsState` |
| `Unresolved reference: findStartDestination` | Thiếu import | Thêm `androidx.navigation.NavGraph.Companion.findStartDestination` |
| `Unresolved reference: stringResource` | Thiếu import | Thêm `androidx.compose.ui.res.stringResource` |
| App chạy nhưng không thấy thanh 5 mục | Chưa gọi `MainShell()` trong `setContent` | Kiểm tra lại `MainActivity.kt` |
| `@StringRes` báo lỗi | Thiếu `androidx.annotation` | Nên đã có sẵn qua `core-ktx`; nếu không thì bỏ annotation |
| Icon không hiện | Chuỗi rỗng trong tên màu | Bấm mỗi tab xem màu nào đang chọn |

---

## Bổ sung: quy ước Compose cho toàn dự án

Bảng quy ước ở `Phase 0` viết cho View. Vì đã chốt giữ Compose, dùng bảng sau **thay cho** phần View trong `Phase 0`. Phần Room vẫn giữ nguyên.

| Thành phần | Cách viết |
|---|---|
| Activity | `ComponentActivity` + `enableEdgeToEdge()` + `setContent { }` |
| Nội dung màn hình | `@Composable fun XxxScreen()` — không tham số khi chưa cần |
| Màn hình rỗng | `SectionPlaceholder(titleRes, bodyRes)` |
| Chuỗi hiển thị | Luôn qua `stringResource(R.string.…)`, **không hardcode** trong Compose |
| Định tuyến | Route là hằng trong `object Routes`; **không** viết chuỗi thô trong `composable()` |
| Thêm tab mới | Thêm vào `object Routes` + `TOP_LEVEL_DESTINATIONS` + `composable()` trong `AppNavHost` |
| Chuyển màn hình | `navController.navigate(Routes.questionDetail(id))` |
| Quay lại | `navController.popBackStack()` |
| View trong `onCreate` | Không dùng — Compose tự quản lý vòng đời qua `rememberSaveable` |
| Trạng thái tạm | `var x by rememberSaveable { mutableStateOf(...) }` |
| Style chữ | `MaterialTheme.typography.…` — không set `textSize` thủ công |
| Màu | `MaterialTheme.colorScheme.…` — không hardcode `Color(0xFF…)` trong màn hình |
| Layout | `Modifier` luôn là tham số đầu tiên, mặc định `= Modifier` |
| Xử lý 1 lần | `LaunchedEffect(Unit) { … }` |
| Việc nặng | `rememberCoroutineScope()` + `launch { }`, không block luồng chính |
| Kiểm tra rỗng | `itemsEmpty` / `if (list.isEmpty()) …` trước khi vẽ `LazyColumn` |

**Quy tắc bất di bất dịch:** không được trộn hai mốc thời gian — hoặc bỏ Compose, hoặc bỏ View. Phase 0 còn sót `TextView`/`setOnClickListener` trong bảng quy ước là thừa từ dự án cũ, bảng trên là bản chính thức.

---

## Checklist hoàn thành Phase 1

- [ ] `libs.versions.toml` có `navigation = "2.10.2"` và `androidx-navigation-compose`
- [ ] `app/build.gradle.kts` có `implementation(libs.androidx.navigation.compose)`
- [ ] `strings.xml` có label 5 tab và 5 cặp placeholder
- [ ] `ui/shell/Destinations.kt` tồn tại, có `object Routes` + `TOP_LEVEL_DESTINATIONS`
- [ ] `ui/shell/AppNavHost.kt` đăng ký 5 route
- [ ] `ui/shell/MainShell.kt` có `Scaffold` + `NavigationBar` 5 item
- [ ] 5 file màn hình trong `ui/screens/...`
- [ ] `MainActivity.kt` gọi `MainShell()`, đã xoá `Greeting`
- [ ] `Theme.kt` đặt `dynamicColor = false`
- [ ] `assembleDebug` BUILD SUCCESSFUL
- [ ] Chạy lên máy thấy 5 tab, bấm chuyển màn hình đúng

**Tiếp theo: Phase 2 — Schema Room** (`docs/PHASE_2_STEP_BY_STEP_CODE.md`): entity `User`, `Profile`, `QuestionAnswer`, `QuizItem`, `AppDatabase`, các DAO, và `Room.databaseBuilder` singleton. Đây sẽ là lần đầu `app/build/generated/ksp/` sinh ra file thật.

---

## Ghi chú liên quan bài Assignment

Phase này là **Part 2, Activity 2 (P5)** — dựng app thật. Không đóng góp gì cho Part 1.

Với **Part 1, Activity 3 (M1)**, Phase 1 cấp dữ liệu cho **class diagram**: 5 màn hình ↔ 5 tab trong `TOP_LEVEL_DESTINATIONS` là cơ sở vẽ package/class diagram. Nên nếu giảng viên hỏi "vì sao chọn 5 mục này", câu trả lời là ánh xạ 1-1 với 5 nhóm yêu cầu chức năng trong đề (bảng ở Step 6).
