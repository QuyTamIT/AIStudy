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
