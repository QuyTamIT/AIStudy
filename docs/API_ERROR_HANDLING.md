# Cải tiến thông báo lỗi API — mục 1 và 2

Ngày thực hiện: 16/11/2026. Phạm vi: thống nhất thông báo lỗi Gemini trên mọi
luồng và làm rõ lỗi hạn mức HTTP 429.

## 1. Dùng chung cách phân loại lỗi

Trước đây Ask và More topics có hai bản ánh xạ lỗi riêng. Quiz và Learning
extras thường chỉ báo “Could not generate quiz/tips”, dù nguyên nhân là key,
billing, timeout hay quota.

Thêm `AiErrorMessages.message(error, fallback)` để tất cả các luồng dùng chung
resource cho cùng một lỗi API. Hàm chỉ trả về ID chuỗi đã định nghĩa trong app,
không đưa thông báo thô của provider lên giao diện.

| File | Mục được sửa | Cải tiến |
|---|---|---|
| `app/src/main/java/com/aistudy/aistudy/ask/AiErrorMessages.kt` | File mới, `message()` | Một nơi xử lý key sai/hết hạn, quyền/API bị chặn, billing, model, HTTP, quota, timeout, mạng và phản hồi API/JSON không hợp lệ. |
| `app/src/main/java/com/aistudy/aistudy/ask/AskFragment.kt` | `errorMessage()` | Bỏ bảng ánh xạ API trùng lặp, gọi mapper chung. Vẫn giữ lỗi phiên, kiểm tra nội dung và lỗi lưu riêng. Câu hỏi/ảnh vẫn được giữ khi gửi thất bại. |
| `app/src/main/java/com/aistudy/aistudy/answer/AnswerViewModel.kt` | `errorMessage()` | Phân loại Answer cũ và More topics dùng mapper chung; vẫn giữ thông báo nội dung thay đổi, thiếu key, không đủ chủ đề mới và lỗi lưu. |
| `app/src/main/java/com/aistudy/aistudy/answer/AnswerActivity.kt` | Khối `catch` của `loadExtras()` | Simplify, Alternative, Concepts, Mistakes và Follow-ups hiển thị đúng lỗi API trong vùng nội dung và Toast thay vì luôn báo lỗi tips chung. |
| `app/src/main/java/com/aistudy/aistudy/quiz/QuizViewModel.kt` | `runOperation()`, kiểm tra key trong `generateQuiz()` | Quiz chọn đúng thông báo API; thiếu key dùng cùng thông báo với Ask/Answer. Lỗi nghiệp vụ/lưu dữ liệu chưa có phân loại cụ thể vẫn dùng fallback của Quiz. |
| `app/src/main/java/com/aistudy/aistudy/quiz/QuizActivity.kt`, `app/src/main/res/layout/activity_quiz.xml` | Hiển thị lỗi tạo Quiz | Khi tạo Quiz thất bại, màn hình dừng vòng chờ và giữ thông báo đầy đủ cùng nút Back. |
| `app/src/main/res/values/strings_quiz.xml` | Xóa `quiz_no_api_key` | Bỏ chuỗi cũ dành cho dev vì Quiz đã dùng `ask_error_key_missing`; không còn hướng dẫn chỉnh `local.properties` trên luồng này. |

Ví dụ: nếu Google từ chối xác thực bằng HTTP 401, Ask, More topics, Learning
extras và Quiz đều chọn `ask_error_key_invalid`. Không còn trường hợp cùng lỗi
đó nhưng Quiz chỉ hiện “Could not generate quiz”.

Các lỗi phiên đăng nhập, thay đổi nội dung, validation nghiệp vụ và lưu dữ liệu
vẫn được phân biệt theo từng tính năng. Không biến mọi exception thành lỗi API.

## 2. Phân biệt gửi quá nhanh và hết quota

HTTP 429 tự nó chưa đủ để biết giới hạn nào đã vượt. Bộ xử lý mới đọc chi tiết
`google.rpc.QuotaFailure` trong `error.details`, sử dụng `quotaId`, `quotaMetric`
và `quotaValue`. Không phân tích các câu tự do trong `error.message` hoặc
`description`; không suy ra loại quota từ `Retry-After`/`RetryInfo`.

| Dữ liệu API trả về | Kết quả | Hành vi |
|---|---|---|
| Tất cả vi phạm xác định được là `PerMinute` / `per_minute` | `RATE_LIMITED` | Báo quá nhiều yêu cầu trong thời gian ngắn; giữ cơ chế tự thử lại có giới hạn. |
| Có vi phạm `PerDay` / `per_day` | `QUOTA_EXHAUSTED` | Báo quota đã hết/không khả dụng; không tự gửi lại ngay. |
| Có `quotaValue = 0` hợp lệ theo int64, kể cả `0.0`, `"0e0"` | `QUOTA_EXHAUSTED` | Đọc số nguyên chính xác, không phụ thuộc cách viết số/chuỗi. Quota không khả dụng, kể cả khi ID có `PerMinute`; không tự retry. Đây là hạn mức được cấp, không phải token đã dùng. |
| Có cả vi phạm phút và ngày/quota 0 | `QUOTA_EXHAUSTED` | Quota ngày/không khả dụng được ưu tiên, kể cả khi có gợi ý thử lại sớm. |
| Không đủ bằng chứng phân loại; `quotaValue` có mặt nhưng sai kiểu, không nguyên, âm hoặc vượt int64; trộn với vi phạm chưa biết | `UNKNOWN` + HTTP 429 | Không kết luận giới hạn phút là tạm thời từ giá trị hỏng. Quota ngày/zero đã biết ở một vi phạm khác vẫn được ưu tiên. Giữ ngân sách retry hiện có. |

`quotaValue` bị bỏ trống hoặc là JSON `null` được coi là chưa cung cấp giá trị;
không suy ra quota bằng 0. ID/metric theo phút vẫn có thể nhận diện được khi
giá trị không được cung cấp.

### File và mục thay đổi

| File | Mục được sửa | Cải tiến |
|---|---|---|
| `app/src/main/java/com/aistudy/aistudy/ask/GeminiClient.kt` | `ErrorReason`, `readRequestError()`, `quotaReason()` | Thêm `RATE_LIMITED` và `QUOTA_EXHAUSTED`; đọc chi tiết quota có cấu trúc, ưu tiên lý do key/quyền/billing rõ ràng. Chỉ phân loại quota từ metadata cho HTTP 429. |
| Cùng file, vá P2 sau review OMP | `parseQuotaValue()`, `quotaReason()`, `parseRetryAfter()` | Đọc quota bằng `BigDecimal.longValueExact()` và kiểm tra miền không âm; giá trị không hợp lệ không được coi là giới hạn tạm thời. Tính khoảng chờ HTTP-date bằng `Duration` giữa hai `Instant`, tránh cả tràn epoch mili giây và tràn phép trừ. |
| `app/src/main/java/com/aistudy/aistudy/ask/GeminiErrorJson.kt` — mới | `readGeminiErrorJson()` | Dùng `android.util.JsonReader.nextString()` để lấy nguyên token số trước khi Android làm tròn qua Double. Giữ BigDecimal chính xác; token quá lớn/không biểu diễn được bằng BigDecimal giữ dạng chuỗi để không làm mất các lý do lỗi khác trong cùng phản hồi. Chỉ áp dụng cho JSON lỗi HTTP. |
| Cùng file | Vòng retry trong `execute()`, `parseRetryDelay()` | Dừng retry cho quota cạn/không khả dụng. Đọc thêm `RetryInfo.retryDelay`; chọn thời gian lớn nhất giữa backoff, Retry-After và RetryInfo. Làm tròn lên mili giây để không retry sớm. |
| `app/src/main/java/com/aistudy/aistudy/ask/AiRequestTrace.kt` | Enum `Reason` | Log debug hiện có ghi rõ `rate_limited` hoặc `quota_exhausted` thay cho `http_error` chung khi nhận diện được. Không ghi raw metadata hay dữ liệu nhạy cảm. |
| `app/src/main/res/values/strings.xml` | `ask_error_quota`, hai chuỗi mới | Thông báo trung lập cho 429 chưa rõ; thông báo riêng cho giới hạn tạm thời và quota cạn/không khả dụng. |

Giữ tối đa **2 lần retry** (tối đa 3 lần gọi gồm lần đầu), thời gian chờ mỗi lần
không quá **30 giây**. Nếu API yêu cầu đợi lâu hơn 30 giây, trả lỗi về giao diện
thay vì cắt thời gian chờ rồi gọi lại sớm. Đây là giới hạn mỗi lần chờ, không
phải tổng thời gian của tác vụ mạng.

Ngày `Retry-After` trong quá khứ cho khoảng chờ 0; ngày tương lai/khoảng cách
thời gian vượt miền mili giây của `Long` được chặn ở `Long.MAX_VALUE`. Lỗi vẫn
là `RequestException` với mã HTTP/reason gốc, và không tự retry sớm. Header ngày
sai cú pháp vẫn bị bỏ qua như trước.

### Chuỗi đang hiển thị

- `ask_error_rate_limit`: “Too many AI requests right now. Please wait a moment before trying again.”
- `ask_error_quota_exhausted`: “The AI quota is exhausted or unavailable. Try again after the quota resets, or contact support.”
- `ask_error_quota`: “An AI request limit was reached. Please try again later. If this continues, contact support.”

Giữ English theo ngôn ngữ giao diện hiện tại. Không hứa thời điểm reset chính
xác vì API có thể không cung cấp thông tin đó. Quota có tên/cửa sổ chưa được
nhận diện sẽ dùng thông báo chung.

## 3. Test và kết quả kiểm chứng

| File test | Bổ sung |
|---|---|
| `app/src/test/java/com/aistudy/aistudy/ask/GeminiClientTest.kt` | Phân loại phút/ngày/quota 0, độ ưu tiên, dữ liệu thiếu/hỏng, retry hints/budget, không lộ dữ liệu. Sau phản hồi OMP: mở rộng test zero với JSON thô giữ nguyên số thập phân/số mũ và thêm 5 test về int64, dữ liệu hỏng, precedence, HTTP-date rất xa và khoảng cách đồng hồ tràn số. Tổng suite: 25 test. |
| `app/src/test/java/com/aistudy/aistudy/ask/AiErrorMessagesTest.kt` | 5 test mới: toàn bộ nhóm lỗi và HTTP mapping, phân biệt timeout/mạng, phản hồi lỗi và giữ fallback riêng cho từng caller. |
| `app/src/androidTest/java/com/aistudy/aistudy/QuizRestoreInstrumentedTest.kt` | 2 test mới: Quiz hiển thị đúng lỗi mà không tạo session/items/XP; thiếu key không gọi API và dùng thông báo chung. |
| `app/src/androidTest/java/com/aistudy/aistudy/ask/GeminiErrorParsingInstrumentedTest.kt` — mới | 6 test cho parser Android: số rất nhỏ không được thành 0, phần lẻ không được làm tròn thành nguyên, zero/số nguyên hợp lệ, null, header ngày tràn và số mũ quá lớn không che mất quota ngày/zero/lỗi key. |

TDD ban đầu: test quota chạy trên mã cũ có 6 test thất bại đúng hành vi còn thiếu;
test mapper trước khi triển khai bảng ánh xạ có 5 test thất bại. OMP sau đó xác
nhận hai P2 mà lượt review ban đầu đã bỏ sót. Trong phiên vá P2, 5/25 test của
GeminiClient thất bại trước sửa và 25/25 đạt sau sửa. Kết quả chạy mới sau bản vá:

- **130 JVM unit test đạt**, 0 thất bại, 0 lỗi, 0 bỏ qua; gồm 30 test thuộc hai
  suite `GeminiClientTest` và `AiErrorMessagesTest`.
- `:app:assembleDebug`: thành công.
- `:app:assembleDebugAndroidTest`: thành công; test Android mới đã biên dịch.
- `git diff --check`: đạt.
- Rà soát độc lập lại bản vá P2 sau khi bổ sung parser Android và xử lý số quá
  lớn: không có phát hiện cần sửa thêm trong phạm vi bản vá.
- **6 kiểm tra parser với mã SDK Android đạt trên host JVM**: dùng nguyên mã
  chức năng `JsonReader`, `JSONObject`, `JSONArray` từ SDK 37, chỉ thêm stub
  annotation để biên dịch độc lập. Đã tái hiện cách đọc cũ phân loại sai
  `1e-400` và `1.0000000000000001`, rồi chạy 6 phương thức regression với cách
  đọc mới. Không dùng thư viện JSON JVM để thay cho parser Android ở bước này.
- Không chạy được instrumentation trên thiết bị trong phiên vá P2: emulator
  thoát do QEMU thiếu thư viện native (`0xC0000135`). Build APK và các kiểm tra
  mã SDK trên host **không được coi là instrumentation đã chạy trên Android**.
- Không gọi API Gemini thật, không sử dụng/quay vòng key để tạo lỗi, không tiêu
  thụ quota cho việc kiểm chứng.

Theo báo cáo OMP người dùng cung cấp, trước phiên vá P2 đã chạy trên AVD API 37:
`QuizRestoreInstrumentedTest` 11/11 đạt, `DataOwnershipInstrumentedTest` 4/4 đạt,
`ImageOnlyInputInstrumentedTest` 1 đạt/1 thất bại vì Espresso không thấy đủ diện
tích nút Send để click. Ca thất bại dừng trước handler gửi. Đây là kết quả OMP
báo lại, không phải kết quả chạy mới trong phiên vá P2. Lỗi giao diện/test đó
nằm ngoài phạm vi hai bản vá P2 ban đầu.

Khi chuẩn bị merge vào `master`, Quiz đã chuyển lỗi tạo mới sang màn hình có
thể cuộn và bài kiểm thử gửi ảnh cuộn tới nút trước khi bấm. Trên AVD API 37,
42 bài instrumentation đạt. Ca Quiz cũng đạt ở cửa sổ 840 × 730, cỡ chữ 200%.

Lệnh kiểm chứng chính:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir="C:\Users\CHI NGUYEN\AndroidStudioProjects\AIStudy\build\jvm-tmp"'
.\gradlew.bat :app:testDebugUnitTest --rerun :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain
```

Bước kiểm tra mã SDK dùng công cụ tạm trong `build/api-p2-platform-harness/`,
không thay SDK, không thêm dependency và không thay cấu hình build của dự án.
`run-sdk-parser-checks.ps1` chạy lại được trong workspace hiện tại sau khi xuất
classpath bằng init script đi kèm với `--no-configuration-cache`. Đây là công
cụ xác minh cục bộ, không phải bài test được Gradle tự chạy trên thiết bị.

## 4. Giới hạn phạm vi

Không triển khai hệ thống thu thập lỗi release, mã lỗi hỗ trợ người dùng, dịch
toàn bộ giao diện hoặc thiết kế lại UI (các mục 3–5 của đề xuất trước).
Các test bổ sung ở trên nhằm xác minh hai mục đã được duyệt.

Không đổi model, prompt, DB, XP, điều hướng, layout hay chính sách log
debug/release. Các thay đổi Manifest và cấu hình backup có sẵn trước tác vụ
được giữ nguyên. Chưa commit hoặc push.

Thiết kế và kế hoạch thực hiện:

- `docs/superpowers/specs/2026-11-16-api-errors-design.md`
- `docs/superpowers/plans/2026-11-16-api-errors.md`

Tài liệu kỹ thuật đối chiếu: [Gemini rate limits](https://aistudio.google.com/docs/rate-limits)
và [Google RPC error details](https://github.com/googleapis/googleapis/blob/master/google/rpc/error_details.proto).
Quy tắc đọc số nguyên/số mũ và JSON null được đối chiếu với
[ProtoJSON](https://protobuf.dev/programming-guides/json/).
