# Ask: kiểm tra lỗi nhập liệu trên emulator

Ngày kiểm tra: 2026-10-21. Package: `com.aistudy.aistudy`.

## Kết luận từ bằng chứng

Lỗi chặn nhập liệu tái hiện được ở tầng focus cửa sổ của Android trên emulator:

```text
mCurrentFocus=Window{... Application Not Responding: system}
mImeInputTarget=null
mCurClient=null
mServedView=null
FocusedWindows: <none>
Reason: Input dispatching timed out (Application does not have a focused window).
```

Ảnh màn hình vẫn hiện Ask nhưng UI hierarchy lại chứa hộp thoại
`Process system isn't responding`, với các nút `Close app` và `Wait`.
Sau khi thử nhập bằng ADB, Android tiếp tục ghi nhận ANR của AIStudy với lý do
không có cửa sổ được focus. Điều này phân biệt trường hợp quan sát được với
EditText bị đặt `keyListener=null` hoặc `enabled=false` trong source.

Đã thực hiện `adb reboot` để khởi động lại Android, giữ dữ liệu ứng dụng.
Sau reboot, cửa sổ ứng dụng nhận focus trở lại và không còn ANR tại các lần
kiểm tra tiếp theo. Chưa xác định được tác nhân ban đầu gây System ANR;
chưa có bằng chứng đủ để kết luận snapshot, RAM hay NotificationShade là tác nhân đó.

Không sửa source ứng dụng để bù cho trạng thái focus lỗi của hệ thống.

## Kiểm tra source và runtime

| Thành phần | Kết quả |
| --- | --- |
| `fragment_ask.xml` | EditText chuẩn, `textMultiLine\|textCapSentences`; không đặt `enabled=false`, `editable=false`, giới hạn ký tự hoặc bộ lọc chữ số. |
| `focusable` | Runtime UI hierarchy: `true`. |
| `focusableInTouchMode` | Source không ghi đè mặc định EditText. Chọn gợi ý bằng touch rồi `requestFocus()` trả focus về EditText thành công. |
| `enabled` | Runtime `true` khi rảnh. `setLoading()` vô hiệu hóa cả ô nhập, nút gửi và gợi ý trong lúc chờ Gemini; đây là hành vi có chủ đích. |
| `inputType` | Runtime EditorInfo: `0x224001`, class text, có cờ multiline và viết hoa đầu câu. |
| `keyListener` | Không có lệnh gán null hoặc thay key listener trong source. Nhập, Backspace, di chuyển con trỏ và sửa giữa chuỗi hoạt động ở runtime. |
| `textIsSelectable` | Không đặt trên ô Ask, dùng mặc định EditText. Các chỗ đặt `true` thuộc màn Answer. |
| Container, Activity, theme | Không phát hiện chặn focus con, chặn key/touch hay cờ cửa sổ `FLAG_NOT_FOCUSABLE` / `FLAG_ALT_FOCUSABLE_IM`. MainActivity dùng `adjustResize`. |
| InputMethodManager / IME | Không có mã tự restart, ẩn hoặc khóa IME trong Ask. Sau khôi phục, `mCurClient` trỏ vào AIStudy và `mServedView` / `mServedInputConnection` trỏ vào `app:id/edit_question`. |
| Bàn phím AVD | `Medium_Phone`, API 37, `hw.keyboard=yes`; Android nhận các thiết bị bàn phím QWERTY. IME mặc định là Gboard. |
| Hiển thị bàn phím ảo | `show_ime_with_hard_keyboard=0`, giữ nguyên. Giá trị này điều khiển việc hiện bàn phím ảo khi có bàn phím cứng, không chứng minh EditText bị khóa. |
| APK thực tế | SHA-256 APK cài trên emulator trùng APK `app-debug.apk` vừa build: `79a5532f5330a420f37178bd03d55079c6eed97c20660848ede7b829d2bb6ee9`. |

Trong lúc kiểm tra, ứng dụng chuyển về Sign in. Người dùng đã đăng nhập lại
và mở Ask trước khi thực hiện các kiểm tra nhập liệu bên dưới.

## Kết quả kiểm tra trên emulator

Các thao tác bàn phím tự động bên dưới dùng `adb shell input keyboard`.
Chúng kiểm tra đường nhận key của Android và EditText; bước chuyển phím từ
bàn phím máy tính qua cửa sổ Android Studio cần kiểm tra riêng bằng tay.

| Thao tác | Kết quả |
| --- | --- |
| Nhập chuỗi mới | `AIStudy test 123` xuất hiện nguyên vẹn trong `edit_question`, `focused=true`. |
| Ctrl+A rồi Backspace | Xóa nội dung cũ, UI chỉ còn hint `Type a question you want to understand…`. |
| Sửa giữa chuỗi | Di chuyển trái, Backspace, chèn `9`: kết quả chính xác `AIStudy test 193`. |
| Copy / xóa / paste | Ctrl+A, Ctrl+C, Backspace, Ctrl+V khôi phục nguyên vẹn `AIStudy test 193`. |
| Bốn câu hỏi mẫu | Chạm từng nút; nội dung ô nhập khớp nguyên vẹn nhãn nút, EditText nhận focus lại. |
| Get an answer với câu mới | Ô nhập trước gửi và `tv_question` ở Answer đều chính xác là `What is 7 plus 5? Answer briefly.`. Gemini trả `12`; Logcat ghi `saved qaId=6`. |
| Bàn phím máy tính qua Android Studio | Chờ xác nhận thủ công. |
| Build | `.\gradlew.bat assembleDebug`: `BUILD SUCCESSFUL`, 37 tasks up-to-date. |

Đã khôi phục nguyên vẹn bản nháp ban đầu sau kiểm tra. Lần gửi câu thử đi qua
luồng Gemini hiện có và tạo bản ghi câu hỏi/câu trả lời bằng hoạt động bình thường
của ứng dụng.

Không chạy instrumented test. Không sửa API key, source/schema Room,
Authentication hoặc source của các Phase khác. Không commit, push hoặc merge.

## Kiểm tra lại khi gặp lỗi

Trong PowerShell tại thư mục project:

```powershell
$adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adbPath devices -l
& $adbPath shell dumpsys window | Select-String 'mCurrentFocus=|mImeInputTarget='
& $adbPath shell dumpsys input_method | Select-String 'mCurClient=|mServedView='
& $adbPath shell dumpsys activity lastanr
```

Nếu focus đang ở System ANR / NotificationShade hoặc InputDispatcher không có
focused window, đóng lớp hệ thống đó hoặc dùng `adb reboot`, đợi Android khởi
động xong rồi mở lại AIStudy. Nếu tái diễn khi mở lại AVD từ Quick Boot,
dùng Device Manager → menu của AVD → **Cold Boot Now** để kiểm tra mà vẫn giữ
dữ liệu. Không dùng Wipe Data, `pm clear` hoặc gỡ ứng dụng để xử lý lỗi focus này.

Nếu `hw.keyboard` bị tắt, bật **Enable Keyboard Input** trong cấu hình AVD.
Nếu cần bàn phím ảo cùng bàn phím máy tính, bật **Show virtual keyboard** trong
cài đặt Physical keyboard của Android; ghi lại giá trị cũ để có thể khôi phục.

Kiểm tra thủ công: click trực tiếp ô Your question, gõ câu mới, Ctrl+A,
Backspace, dán một đoạn tiếng Việt nhiều dòng, sửa ở giữa đoạn, thử gợi ý rồi
sửa tiếp. Nhấn Get an answer và đối chiếu câu hỏi ở màn Answer. Nếu ADB nhập
được mà bàn phím máy tính không nhập được, kiểm tra focus cửa sổ Running Devices
và thử AVD ở cửa sổ riêng để phân biệt đường chuyển phím của Android Studio.

## Nguồn và bằng chứng

- [Android Developers: Handle input method visibility](https://developer.android.com/develop/ui/views/touch-and-input/keyboard-input/visibility)
- [Android Developers: EditText](https://developer.android.com/reference/android/widget/EditText)
- [Android Developers: Emulator command line, snapshot và user data](https://developer.android.com/studio/run/emulator-commandline)
- Log, window/input/IME dumps và UI hierarchy trong thư mục tạm của phiên kiểm tra:
  `%TEMP%\AIStudy-input-debug-20261021`.

## Metrics AI Đợt 1 — 2026-11-13

- Bản debug ghi JSON bằng tag Logcat `AIStudyAI`; bản release không phát metrics.
  Lọc bằng `adb -s <thiết-bị-đã-xác-minh> logcat -s AIStudyAI:D "*:S"`.
  Không cần bật request thật để chạy các test fixture.
- `taskId` ngẫu nhiên liên kết tác vụ; retry giữ taskId và tăng ordinal/attemptId.
  `attempt_end` ghi HTTP status, finish/block reason đã lọc, usage và latency.
  `validation` phân biệt valid/invalid/not_run; `task_end` phân biệt kết quả lưu,
  session_changed, content_changed, cancellation và request/response lỗi.
- `attemptLatencyMs` đo một attempt; `generationLatencyMs` gồm retry/backoff;
  `taskLatencyMs` kết thúc sau validation/persistence, không phải thời gian render UI.
- Usage `null` là unknown, không phải 0. `state=partial` nghĩa là chỉ quan sát
  được một phần; số 0 chỉ được ghi khi API trả số 0. Không cộng totalTokenCount
  vào các thành phần input/output/thinking để tránh đếm trùng.
- Metrics không lưu prompt, ảnh, đáp án, raw response/error body, exception message,
  user/QA ID, URI, đường dẫn local hoặc key. Không có database/dashboard metrics.
- Payload có root systemInstruction và schema cho từng luồng. Quiz mới chỉ lưu
  batch đủ bốn câu hợp lệ; Extras validate đủ năm nhóm trước ghi cache. Cache
  legacy từng nhóm và session Quiz cũ vẫn đọc được; Room vẫn version 8.
- Kiểm chứng offline: 102 unit tests, 0 failures/errors/skipped; lintDebug và
  assembleDebug thành công (131 lint warnings). Hai lớp DataOwnershipInstrumentedTest
  và QuizRestoreInstrumentedTest chạy 12 tests, 0 failures trên AVD cách ly
  AIStudy_Test; các đường generation dùng fake.
- Gemini thật ngày 2026-11-14, qua ứng dụng debug trên AIStudy_Test với tài khoản
  demo riêng: ASK_TEXT bị HTTP 403, reason=http_error; 1 attempt, generation 1.393 ms,
  task 1.436 ms, validation=not_run, outcome=request_failed; toàn bộ usage unknown.
  Không có subtype đã nhận diện để kết luận riêng lỗi hạn chế key, quyền hay billing.
  Dừng gọi thật theo điều kiện lỗi quyền; chưa chạy ảnh, Extras/cache hoặc Quiz thật.
- Chưa đo chi phí, chất lượng ngôn ngữ hoặc mức tiết kiệm. Key trong APK và request
  trùng do lifecycle chưa được xử lý.

## Demo Gemini thật sau thay key — 2026-11-14

### Phạm vi và bản cài

- Bắt đầu thu metrics lúc `2026-11-14T05:44:53.318053+00:00`; không xóa Logcat.
  Lọc taskId mới, tách hoàn toàn phiên HTTP 403 cũ và 135 token chẩn đoán bên ngoài.
- Theo thông tin bàn giao, key đã được người dùng thay và Codex đã build APK mới.
  Phiên này không đọc/in key, không gọi script chẩn đoán và không build lại.
  Cài bằng `adb install -r -t`, không uninstall/wipe/reset dữ liệu.
- APK debug có thời điểm file `2026-11-14T05:31:30.7089199Z`; SHA256 local và
  bản APK đã cài trên thiết bị khớp:
  `d49594e2a7a990ff9a713a8c463c6a9a540ddf538251bbd3315fecf8b5e95d57`.
- Source vẫn ở `dev_nguyen`, commit `b3fcd23`; giữ model `gemini-3.6-flash`,
  thinking, generation config, timeout, retry, schema, prompt và xử lý ảnh.
- AVD cách ly `AIStudy_Test`, serial `emulator-5560`, được xác minh trước các
  thao tác adb. Tạo tài khoản demo mới qua UI; kiểm tra chỉ đọc Room xác nhận
  ban đầu có 0 đáp án và 0 phiên Quiz của tài khoản đó.
- Không sửa database thiết bị bằng công cụ. Kiểm tra persistence dùng bản sao
  tạm của database/WAL, mở SQLite read-only; xóa bản sao sau khi đọc.
- Toàn bộ bằng chứng mới ở `%TEMP%\aistudy-gemini-demo-newkey-dh59th0u\`.
  `AIStudyAI-session.json` chứa metrics đã lọc; `verification.json` chứa kết quả
  đối chiếu. Chỉ chụp dữ liệu demo tổng hợp, không lưu raw response/error body.

### Kết quả bốn ca

| Ca | Kết quả thực tế | Bằng chứng trong thư mục tạm |
|---|---|---|
| ASK_TEXT | Pass: nhập đúng câu hỏi tiếng Việt qua UI, gửi một lần; nghiệm x = 6, hai bước hợp lý, subject math, validation valid và lưu đúng owner. Mở lại từ Library không thêm AI event. | `01-text-input.png`, `01-text-result.png`, `01-text-reopened.png` |
| ASK_IMAGE | Pass: đọc fixture cũ trước khi dùng, đề 3x - 7 = 14, nghiệm x = 7. Chọn qua Photo Picker, ô hỏi trống; nội dung giải tiếng Việt dù lịch sử giữ placeholder tiếng Anh. Subject math, validation valid, ảnh lưu khớp fixture và mở lại được. | `02-image-only-input.png`, `02-image-solution.png`, `02-image-reopened.png` |
| Extras/cache | Pass: một generation sinh đủ năm nhóm; schema đúng kiểu, nội dung không rỗng, tiếng Việt và phù hợp phương trình. Đọc từng nhóm, chuyển lại Simplify, rời/mở lại đáp án và đọc sau process restore đều không thêm AI event. Các field lời giải gốc không đổi. | `03-extras-simplify.png`, `03-extras-alternative.png`, `03-extras-concepts.png`, `03-extras-mistakes.png`, `03-extras-followUps.png`, `03-extras-persistent-cache-display.png`, `03-extras-cache-after-process-restore.png` |
| Quiz math | Pass: một session, bốn item kiểu mcq/short/blank/mcq, toàn batch valid trước khi lưu; sourceQaIds=[2,1] đúng hai đáp án demo cùng owner. Trả lời đủ, chủ ý nhập sai câu 3 để kiểm tra nhánh chấm sai; hoàn thành 3/4, cộng 50 XP đúng một lần. | `04-quiz-first-item.png`, `04-quiz-item1-feedback.png`, `04-quiz-item2-feedback.png`, `04-quiz-item3-incorrect-feedback.png`, `04-quiz-item4-feedback.png`, `04-quiz-result.png`, `04-quiz-result-restored.png` |

- Extras lưu đúng hai string `simplify`/`alternative`; `concepts` có 3 string,
  `mistakes` có 2 string, `followUps` có 2 string; không có field ngoài contract.
  Sau khi Extras hoàn tất, các thao tác đọc cache trước Quiz giữ nguyên 3 task /
  4 attempts. Đây là không gọi API, không phải API trả usage=0.
- MCQ đều có bốn lựa chọn không rỗng, khác nhau sau normalization production,
  correctAnswer khớp đúng một lựa chọn; short/blank có options=[].
  Câu hỏi/giải thích tiếng Việt, cùng dạng phương trình bậc nhất với hai nguồn.
- Sau màn kết quả: về Home, dừng process app nền bằng `am kill`, mở lại task từ
  Recents. PID mới khôi phục đúng session đã hoàn thành; Room vẫn một session,
  bốn item, score 3/4, XP không đổi và không có generation mới.
  Extras cũng đọc lại được từ cache sau lần process restore này.

### Metrics quan sát

TaskId dưới đây là ID ngẫu nhiên; attemptId đầy đủ bằng `<taskId>:<ordinal>`,
được lưu nguyên trong `AIStudyAI-session.json`.

| Feature | taskId | Attempt ordinal: HTTP / finishReason / latency ms |
|---|---|---|
| ASK_TEXT | `089bc0c9-710b-480a-bea3-33c60b3b3696` | 1: 503 / null / 19441; 2: 200 / STOP / 11895 |
| ASK_IMAGE | `b7ccd048-7519-4857-84e3-728686d15011` | 1: 200 / STOP / 4985 |
| LEARNING_EXTRAS | `62443750-97ec-459d-9759-9eaf772425a5` | 1: 200 / STOP / 6590 |
| QUIZ | `6a53d466-9a30-4694-a1f9-68144e120a3f` | 1: 200 / STOP / 8351 |

| Feature | Attempts | promptTokenCount | candidatesTokenCount | thoughtsTokenCount | totalTokenCount | Generation ms | Task ms |
|---|---:|---:|---:|---:|---:|---:|---:|
| ASK_TEXT | 2 | 714 | 148 | 411 | 1273 | 32338 | 32362 |
| ASK_IMAGE | 1 | 1766 | 160 | 489 | 2415 | 4986 | 5008 |
| LEARNING_EXTRAS | 1 | 558 | 265 | 671 | 1494 | 6590 | 6592 |
| QUIZ | 1 | 568 | 370 | 1002 | 1940 | 8351 | 8361 |

- Các token trong bảng là số API cung cấp ở bốn response HTTP 200. Attempt 503
  của ASK_TEXT có reason=http_error, finish/block reason null, toàn bộ usage unknown.
  Không suy ra attempt lỗi đó đã hoặc chưa bị tính phí.
- Tổng usage quan sát từ bốn response 200: prompt=3606, candidates=943,
  thoughts=2573, total=7122. Không cộng total vào các thành phần và không cộng
  135 token chẩn đoán bên ngoài; không coi đây là tổng usage đầy đủ của 5 attempts.
- cachedContentTokenCount và toolUsePromptTokenCount đều unknown/null trong mọi
  attempt; không thay bằng 0. Bốn response 200 có serviceTier=standard, state=partial.
  Riêng ASK_IMAGE có modality details IMAGE=1075 và TEXT=691 token do API trả.
- Mỗi task có đúng một validation=valid và một task_end=success; retry ASK_TEXT
  giữ taskId và đổi attemptId. Không thấy classification, More, repair hay task trùng.
  Tổng ngân sách đã dùng: 4/4 tác vụ generation chủ động, 5/12 HTTP attempts.

### Lỗi/lưu ý và giới hạn kết luận

- ASK_TEXT gặp HTTP 503 ở attempt đầu, tự retry theo chính sách hiện có rồi 200.
  Nguyên nhân máy chủ sâu hơn chưa được xác minh; không gửi thêm request chẩn đoán.
  Không tái hiện lỗi xác thực/quyền/billing/quota trong bốn tác vụ app của phiên này.
- Lưu ý hiển thị không chặn demo: công thức còn LaTeX thô như `$x$`, `\frac` và
  `\implies`; không phải lỗi dấu tiếng Việt hay JSON. Tái hiện bằng hai đáp án và
  Quiz ở các ảnh trên. `AnswerActivity.showAnswer()` dòng 307–310 gán expr vào
  TextView; `QuizActivity.bindCurrentItem()` dòng 236 và `renderFeedback()` dòng
  213 cũng gán text trực tiếp. Hướng sửa nhỏ để review sau: thống nhất prompt
  dùng công thức plain-text/Unicode phù hợp renderer hiện có nếu chưa cần LaTeX.
  Chưa thay prompt, renderer hay source trong phiên này.
- Không chạy lại 102 unit tests, 12 instrumentation tests fake, lint hoặc build:
  đó vẫn là bằng chứng cũ; không có source/test thay đổi. Kiểm chứng mới là cài
  APK mới và thao tác UI với Gemini thật, đối chiếu metrics và persistence.
- Bốn luồng đủ chạy demo trong lần kiểm tra này, với lưu ý trình bày công thức.
  Chưa đo chi phí tiền, tỷ lệ tiết kiệm, tải đồng thời hoặc độ ổn định dài hạn.
  Một lần restore không chứng minh đã giải quyết request trùng do lifecycle;
  key trong APK vẫn ngoài phạm vi.
- Chỉ cập nhật tài liệu kết quả này trong workspace; không sửa source/config,
  không commit/amend/push/merge/deploy. Bốn file ngoài commit Đợt 1 được bảo toàn.
  AVD do phiên tự khởi động đã được dừng; xác nhận emulator-5560 ngắt kết nối.

## Kiểm thử UI trực tiếp bằng Codex — 2026-11-14

### Phương pháp và phạm vi

- Đây là phiên thao tác UI mới, không dùng ảnh/metrics của phiên demo trước để
  thay thế kết quả. Khởi động AVD cách ly `AIStudy_Test`, cửa sổ hiển thị,
  serial `emulator-5560`, màn hình 1080 × 2400, density 420 dpi.
- Dùng SDK/system image đã cài sẵn, không tải package hoặc cập nhật dependency.
  Lúc đầu Emulator lỗi nạp DLL trong môi trường ứng dụng desktop; khởi động
  lại bằng cơ chế desktop process của Windows với software renderer, tắt Vulkan
  cho tiến trình thử nghiệm. Không sửa SDK, AVD cá nhân hoặc cấu hình project.
- Cài APK debug hiện có bằng `install -r -t`; SHA256:
  `d49594e2a7a990ff9a713a8c463c6a9a540ddf538251bbd3315fecf8b5e95d57`.
  Source vẫn `dev_nguyen`, commit `b3fcd23`; không build/test suite lại.
- Tạo tài khoản demo mới qua màn Register; chọn High school, Math, Step by step
  trong Onboarding. Thao tác bằng touch/key qua ADB và clipboard Unicode qua
  cửa sổ emulator; quan sát screenshot mới và UI hierarchy sau mỗi bước.
- Không gọi Gemini bằng curl/script hoặc gọi repository trực tiếp. Không sửa
  SharedPreferences/database để bỏ qua UI. Chỉ đọc bản sao database/WAL sau
  thao tác để đối chiếu persistence/XP; bản sao tạm được xóa sau khi đọc.
- Màn hướng dẫn stylus của Gboard xuất hiện ở lần nhập đầu, che ứng dụng và
  nhận phím thay cho ô đăng ký; đóng bằng Cancel trên UI rồi nhập bình thường.
  Không quy kết trạng thái hướng dẫn hệ thống này là lỗi EditText của AIStudy.
- Bằng chứng mới tại `%TEMP%\aistudy-ui-live-qs55u477\`: screenshot PNG,
  hierarchy XML, `metrics.json`, `metrics-after-cache.json`,
  `metrics-summary.json`, `persistence-verification.json`, baseline/hash.
- Video thao tác mới (H.264, 540 × 1200): `01-text-navigation.mp4` (~120 giây),
  `03-extras-navigation.mp4` (~122 giây), `04-quiz-and-restore.mp4` (~180 giây).
  Video là các đoạn ghi màn hình, không bao phủ toàn bộ thời gian phiên.

### Các ca thực hiện trên giao diện

| Ca | Thao tác và kết quả thực tế | Bằng chứng mới |
|---|---|---|
| Nhập rỗng | Bấm Get an answer khi không có chữ/ảnh: hiện “Enter a question or attach an image.”, không gọi AI. | `01-empty-validation.png` |
| Photo Picker | Mở rồi Back để hủy; chọn ảnh → Done → preview → Remove → chọn lại. Preview mất sau Remove, ảnh chọn lại được; không gọi AI trong các bước này. | `01-picker-open.png`, `01-picker-dismissed.png`, `02-image-preview.png`, `02-image-removed.png`, `02-image-before-send.png` |
| ASK_TEXT | Dán đúng câu hỏi tiếng Việt `2x + 5 = 17`, đóng bàn phím, gửi một lần. Loading khóa input/các nút gửi, sau đó mở Answer: x = 6, hai bước đúng, subject Math, tiếng Việt đúng dấu. Cuộn đến Final answer; Back → History → mở lại thành công, không gọi AI thêm. | `01-text-ready.png`, `01-text-loading.png`, `01-text-complete.png`, `01-text-final.png`, `01-history.png`, `01-text-reopened.png` |
| ASK_IMAGE | Đã xem fixture `3x - 7 = 14` trước khi gửi. Chọn qua Photo Picker, ô hỏi trống. Gửi một lần: x = 7, giải bằng tiếng Việt, ảnh và đáp án mở lại được từ History. | `02-image-before-send.png`, `02-image-loading.png`, `02-image-complete.png`, `02-image-solution.png`, `03-image-reopened.png` |
| Extras/cache | Bấm Simplify, chờ xong rồi mở Alternative, Concepts, Mistakes; vuốt hàng nút sang Follow-ups và mở nhóm đó. Cả năm nhóm có nội dung tiếng Việt phù hợp. Back → History → cùng đáp án → Simplify đọc ngay cache; tổng vẫn 3 task/3 attempts. Sau process restore, Simplify vẫn khớp nội dung trước đó và không gọi AI thêm. | `03-extras-loading.png`, `03-extras-simplify.png`, `03-alternative.png`, `03-concepts.png`, `03-mistakes.png`, `03-followups.png`, `03-cache-reopened.png`, `05-cached-simplify.png` |
| Quiz | Bấm Practice một lần. Đọc và tự giải câu hỏi trên UI; chọn x = 4 cho `4x - 3 = 13`, nhập 3 cho `5x + 4 = 19`, cố ý nhập 0 ở câu điền số cần cộng trong `2x - 8 = 10` (đúng là 8), chọn x = 7 cho `3x + 1 = 22`. Check/Next hoạt động, câu 3 báo Incorrect + Correct answer: 8; kết thúc Score 3/4, +50 XP. | `04-quiz-loading.png`, `04-quiz-first.png`, `04-q1-feedback.png`, `04-q2-feedback.png`, `04-q3-feedback.png`, `04-q4-feedback.png`, `04-quiz-result.png` |
| Khôi phục Quiz | Home Android → kill process app đang ở nền → Recents → bấm thẻ AIStudy. Mở lại đúng kết quả 3/4, +50 XP; Done trở về Answer, cache vẫn dùng được. Không bấm Practice lần hai. | `04-recents.png`, `04-result-restored.png`, `05-return-answer.png`, `05-cached-simplify.png` |

- Đối chiếu read-only: đúng 2 QuestionAnswer, 1 QuizSession, 4 QuizItem;
  sourceQaIds=[2,1] khớp hai đáp án demo cùng owner. Quiz completed=true, score=3.
- XP trước chấm Quiz là 120, sau hoàn thành và process restore là 170: tăng 50
  đúng một lần. Đây là đối chiếu dữ liệu sau các thao tác UI, không dùng để
  lấy đáp án thay cho việc giải câu hỏi trên màn hình.
- Extras lưu hai chuỗi `simplify`/`alternative` không rỗng và ba mảng
  `concepts`/`mistakes`/`followUps`, mỗi mảng có 3 chuỗi không rỗng.
- Toàn phiên: 4/4 tác vụ chủ động, 4/12 HTTP attempts, mỗi task đúng một
  validation=valid và task_end=success. Không thấy classification, More, repair,
  retry hoặc task trùng trong phiên. Mọi response đều HTTP 200 / STOP.

### Metrics riêng của phiên UI mới

| Feature | taskId (attemptId = taskId:1) | Input | Candidates | Thinking | Total | Attempt ms | Generation ms | Task ms |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| ASK_TEXT | `72d333cd-7c9c-409d-9354-ed5559fe1807` | 724 | 183 | 427 | 1334 | 35107 | 35107 | 35141 |
| ASK_IMAGE | `edd7bc8a-a0af-4950-834e-3ebc138e2cf4` | 1776 | 167 | 428 | 2371 | 26991 | 26991 | 27026 |
| LEARNING_EXTRAS | `4e9e234b-de11-49c3-ab9e-451e61a864ff` | 564 | 402 | 782 | 1748 | 12228 | 12228 | 12231 |
| QUIZ | `da4953fc-2b06-478c-8de7-3b1ef8866134` | 564 | 342 | 1499 | 2405 | 20207 | 20208 | 20222 |
| Tổng quan sát | | 3628 | 1094 | 3136 | 7858 | | | |

- cachedContentTokenCount/toolUsePromptTokenCount đều null/unknown; không thay
  bằng 0. ASK_IMAGE gồm TEXT=701, IMAGE=1075 input tokens theo API.
- Tổng chỉ thuộc bốn response mới; không gộp 135 token chẩn đoán hoặc 7122
  token của phiên trước, không cộng total vào các thành phần. Đọc cache là
  không gọi API, không phải API báo 0 token. Chưa đo tiền hoặc tỷ lệ tiết kiệm.

### Lỗi giao diện và giới hạn

1. **Công thức LaTeX hiện thô, ảnh hưởng khả năng đọc.** Answer/body/Extras/Quiz
   còn `$x$`, `\frac`, `\implies`. Thấy ở `01-text-final.png`,
   `02-image-solution.png`, `03-followups.png`, `04-quiz-first.png`.
   `AnswerActivity.showAnswer()` gán expr trực tiếp tại dòng 308;
   `AnswerBodyRenderer` không render toán; `QuizActivity` gán prompt/options/
   explanation trực tiếp vào TextView. Cần thống nhất định dạng AI phù hợp
   renderer hoặc giải pháp render toán trong yêu cầu sửa sau; chưa sửa ở đây.
2. **Nhãn Camera/Gallery bị ngắt giữa từ.** `02-image-before-send.png` hiển thị
   Cam/era, Gall/ery. Trong `fragment_ask.xml` dòng 96/107, hai nút width=0dp,
   weight=1; nút Send dòng 118 wrap_content chiếm phần lớn hàng. Trên màn hình
   đã kiểm tra, phần chiều rộng còn lại không đủ nhãn và padding. Thao tác
   Gallery vẫn hoạt động; đây là lỗi bố trí UI, không phải lỗi Gemini.
3. **Hỏi lại quyền thông báo sau restore.** Đã chọn Don't allow ở lần đầu;
   sau khôi phục Quiz và Back khỏi Answer, MainActivity hỏi lại quyền. Ảnh
   `05-library-end.png`; `MainActivity.onCreate()` gọi
   `requestNotificationPermissionIfNeeded()` và yêu cầu lại nếu chưa được cấp.
   Đây là lưu ý UX thuộc luồng Android/notifications, chưa sửa hoặc mở rộng
   trách nhiệm AI. Bước mở Home cuối phiên không được thực hiện qua hộp thoại này.

- Bốn luồng AI hoạt động trên UI trong lần thử mới, với các lưu ý hiển thị trên.
  Chưa kiểm tra camera thật, đăng nhập lại riêng, offline, xoay màn hình,
  nhiều kích thước máy hoặc tải đồng thời. Một lần restore không chứng minh
  đã xử lý triệt để request trùng do lifecycle.
- Không chạy lại unit/instrumentation/lint/build; không sửa source, key, model,
  prompt, schema, config hoặc dependency. Chỉ thêm kết quả vào tài liệu này.
  Các file thay đổi có sẵn được đối chiếu hash và bảo toàn. Không commit/push/
  merge/deploy. Đã dừng AVD do phiên tự mở, `adb devices -l` không còn thiết bị.

## Sửa ba lỗi UI sau kiểm thử — 2026-11-14

### Thay đổi

- `AnswerPrompts.commonInstruction` yêu cầu công thức plain-text/Unicode ngoài
  vùng code cho mọi luồng sinh nội dung; giữ nguyên model, schema, retry,
  timeout, thinking và key. Không thêm dependency hoặc migration.
- `StudyMathText` xử lý trình bày công thức cũ: bỏ delimiter toán được nhận
  diện, đổi `\frac{a}{b}` thành `(a)/(b)` và các toán tử phổ biến thành Unicode.
  Dùng tại Answer body/title/expr/finalAnswer/Extras và các trường hiển thị Quiz.
  Không ghi lại câu trả lời/cache trong Room; không đổi dữ liệu chấm điểm.
- Code fences, inline code (kể cả double backticks), đường dẫn và ký hiệu tiền
  tệ được bảo toàn. LaTeX chưa hỗ trợ được giữ nguyên thay vì bỏ ký hiệu làm
  sai nghĩa; đây không phải engine render LaTeX đầy đủ.
- `QuizActivity` giữ option gốc trong RadioButton.tag để chọn, khôi phục và
  chấm theo giá trị raw dù text hiển thị đã được định dạng.
- `fragment_ask.xml`: Camera/Gallery chia đều hàng đầu, Send full-width hàng
  thứ hai; giữ IDs/hành vi/font scaling, không ép nhỏ chữ hoặc cắt nhãn.
- `MainActivity` ghi cờ đã yêu cầu quyền thông báo vào prefs riêng trước khi
  mở dialog. Không tự hỏi lại sau recreate/restart; tôn trọng trạng thái từ
  chối của bản cũ qua shouldShowRequestPermissionRationale. Người dùng vẫn
  có thể cấp quyền trong Android Settings. Không thay scheduling hoặc auth.

### Kiểm chứng mới

- 109 JVM unit tests: 0 failures/errors. Các regression mới gồm phân số lồng,
  delimiter, tiếng Việt, code, tiền tệ và bảo toàn biểu thức không hỗ trợ.
  Đã quan sát test thất bại trước khi sửa, gồm các ca currency và backticks.
- 13 Android instrumentation tests trên AIStudy_Test: đạt. Bao gồm regression
  mới `formattedMathOptionKeepsRawSelectionAcrossRecreationAndGradesCorrectly`,
  cùng kiểm tra Quiz restore/ownership/validation/XP hiện có. Không gọi Gemini
  trong các ca dùng fake này. Lỗi regex dấu ngoặc nhọn trên Android ICU đã được
  phát hiện khi chạy Android, sửa escape và chạy lại thành công trên bản cuối.
- `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` thành công trên
  source cuối; lint 0 errors/131 warnings. Không phải dùng lại báo cáo cũ.
- UI mới: mở lại đáp án ảnh cũ và cache Simplify từ Library; biểu thức hiện
  `x = (21)/(3) ⇒ x = 7`, văn bản/final answer/Extras không còn delimiter thô.
  Đối chiếu read-only xác nhận các bản ghi cũ vẫn giữ LaTeX nguyên gốc.
- Camera/Gallery đọc trọn từ ở 1080 × 2400 và 840 × 1960/font_scale=1.3.
  Đã khôi phục wm size mặc định và font_scale=1.0 sau kiểm tra.
- Thiết bị từng từ chối thông báo: nâng cấp APK, recreate do đổi cấu hình,
  trở về MainActivity và cold process restart đều không hiện lại dialog.
  Đã xác nhận cờ requested=true. Không chạy lại riêng ca first-install prompt.
- Đăng nhập lại tài khoản demo qua UI sau các fixture instrumentation. Chỉ
  gửi một bài thật `4x - 3 = 13` qua Ask để kiểm tra chỉ dẫn định dạng mới:
  HTTP 200/STOP, validation=valid, task_end=success, nghiệm x=4, hai bước
  tiếng Việt. Dữ liệu mới lưu cũng không chứa LaTeX, không chỉ được che ở UI.
- TaskId `2d8ba8ea-910e-45de-827f-abc9da015963`, attemptId taskId:1;
  1 tác vụ/1 HTTP attempt, input=818, candidates=153, thinking=809,
  total=1780. Attempt=14263 ms, generation=14265 ms, task=14292 ms.
  cachedContentTokenCount/toolUsePromptTokenCount null/unknown. Tách khỏi
  metrics các phiên cũ và test fake; không suy ra chi phí tiền.
- Không có FATAL EXCEPTION trong phiên UI cuối. Các ca đọc cache, recreate
  và restart không tạo thêm generation. Không chạy lại bốn generation demo
  hoặc tuyên bố mọi dạng toán học/thiết bị đều đã được kiểm tra.
- Bằng chứng tại `%TEMP%\aistudy-ui-fixes-larn2avh\`: `04-math-readable.png`,
  `04-extras-readable.png`, `05-ask-layout-fixed.png`,
  `05-ask-narrow-large-font.png`, `06-new-answer.png`,
  `07-restarted-no-permission-dialog.png`, `ui-fixes-verified.mp4`,
  `instrumentation-final.log`, `verified-checks.log`, `metrics.json`,
  `ui-verification.json` và baseline hashes.
- Thay đổi ở working tree dev_nguyen; chưa commit/push/merge/deploy. Bốn file
  ngoài commit Đợt 1 và local.properties được bảo toàn theo SHA256.
  AVD AIStudy_Test do phiên mở đã được dừng sau kiểm tra.
