# Phase 4: thêm chủ đề trong Answer

## Hành vi

- Gemini trả `answerType` (`solution`, `explanation`, `topic_list`) và
  `finalAnswerRedundant` qua JSON schema. UI không dò từ khóa của câu hỏi.
- Chỉ ẩn cả tiêu đề và nội dung Final answer khi loại là `topic_list` và
  metadata xác nhận phần đó lặp lại. Câu trả lời môn toán luôn giữ Final answer.
- Show more topics yêu cầu 3–5 mục mới với `topicId` đại diện cho khái niệm.
  Prompt gửi kèm toàn bộ mục cũ để tránh lặp ý. Client loại mục trùng ID, tiêu đề
  hoặc nội dung sau chuẩn hóa Unicode, chữ hoa/thường và dấu câu. Nếu còn ít hơn
  3 mục mới thì báo lỗi và giữ nguyên dữ liệu.
- Mỗi câu trả lời có tối đa 3 lần bổ sung thành công. Lỗi mạng/API, dữ liệu
  không hợp lệ và lỗi lưu không tiêu thụ lượt. GeminiClient vẫn dùng retry và
  xử lý Retry-After hiện có.
- Nội dung mới nối vào danh sách, số thứ tự tiếp tục từ số mục hiện tại.
  ViewModel giữ yêu cầu đang chạy qua thay đổi cấu hình màn hình.

## Lưu trữ không đổi schema

`QuestionAnswer.steps` tiếp tục là mảng JSON. Các phần tử vẫn có `title`,
`body`, `expr`; mục danh sách bổ sung thêm `topicId`. Phần tử đầu có metadata:

```json
{
  "_answer": {
    "version": 1,
    "answerType": "topic_list",
    "finalAnswerRedundant": true,
    "moreCount": 0
  }
}
```

Dữ liệu cũ không có metadata vẫn đọc được. Câu trả lời toán cũ giữ nguyên cách
hiển thị; các câu trả lời cũ khác được Gemini phân loại một lần khi mở và lưu
metadata vào chính bản ghi đó, không viết lại nội dung đã có. Nếu phân loại lỗi,
nội dung vẫn hiển thị và có nút thử lại.

DAO chỉ cập nhật `steps` với điều kiện khớp `id`, `userId` và JSON trước khi gửi
yêu cầu. Điều kiện so sánh ngăn hai màn hình ghi đè mục mới của nhau hoặc vượt
giới hạn 3 lượt. Không insert QuestionAnswer, không cập nhật profile/XP. Giữ nguyên
entity, database version 3 và các migration hiện có.

## Xác minh

Unit test trong `AnswerContentTest` và `AnswerRepositoryTest` kiểm tra ẩn/hiện
Final answer, dữ liệu cũ, metadata round trip, chống trùng, số lượt qua mở lại,
lỗi mạng, bảo vệ phiên tài khoản và cập nhật đồng thời. Không chạy Instrumented Test.

Build dùng task `assembleDebug`; unit test dùng `testDebugUnitTest`.
Nếu daemon chọn nhầm JRE của VS Code thiếu `jlink`, chạy bằng JDK Android Studio
trong tiến trình mới (chỉ là tùy chọn của lệnh, không sửa cấu hình project):

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest --offline --no-daemon --max-workers=2 --no-configuration-cache '-Pkotlin.compiler.execution.strategy=in-process' '-Dorg.gradle.java.installations.auto-detect=false' '-Dorg.gradle.java.installations.paths=C:\Program Files\Android\Android Studio\jbr' '-Dorg.gradle.java.home=C:\Program Files\Android\Android Studio\jbr'
```

Kiểm tra emulator: mở một câu trả lời danh sách, tải thêm, đối chiếu nội dung cũ
và đánh số; mở lại để xác nhận lưu số lượt. Đối chiếu số hàng QuestionAnswer và
XP trước/sau bổ sung. Với câu toán, kiểm tra Explanation và Final answer vẫn hiện.

Nguồn: [Gemini GenerationConfig và JSON schema](https://ai.google.dev/api/generate-content#v1beta.GenerationConfig),
[Android ViewModel và thay đổi cấu hình](https://developer.android.com/topic/libraries/architecture/viewmodel),
[Gradle Java toolchains](https://docs.gradle.org/current/userguide/toolchains.html).
