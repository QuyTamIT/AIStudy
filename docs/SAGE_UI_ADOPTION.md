# Deep Sage UI — Ask, Login, Register

Ngày triển khai: 2026-10-21. Chỉ thay đổi giao diện ba màn hình; kế thừa hệ thống của Answer trong [DESIGN.md](../DESIGN.md).

## Kết quả

- **Ask:** ô soạn câu hỏi lớn, focus/error outline rõ, CTA Deep Sage, gợi ý dạng hàng phẳng có icon môn học và mũi tên. Lỗi nằm cạnh vùng nhập; trạng thái chờ có spinner và thông báo. Bàn phím và phần cuộn được xử lý trong Fragment, không thay đổi Main hoặc Bottom Navigation.
- **Login:** branding sách mở, tiêu đề phân cấp rõ, form mở không bọc card, trường email/password có nhãn, autofill, nút hiện/ẩn mật khẩu và lỗi inline.
- **Register:** cùng bố cục và thành phần với Login, thêm tên và helper mật khẩu; IME Next/Done, form cuộn và kích thước linh hoạt.
- Shared theme kế thừa màu semantic light/night của Answer. Không thêm gradient, shadow, framework, thư viện font hoặc dependency.

## Các file thay đổi trong lần triển khai này

Sáu file hiện có được chỉnh:

| File | Thay đổi |
|---|---|
| `app/src/main/java/com/aistudy/aistudy/ask/AskFragment.kt` | Theme cục bộ, focus/IME, hiển thị lỗi và loading |
| `app/src/main/java/com/aistudy/aistudy/auth/LoginActivity.kt` | Áp dụng theme và nối các trạng thái form vào UI dùng chung |
| `app/src/main/java/com/aistudy/aistudy/auth/RegisterActivity.kt` | Theme, trạng thái form, helper/autofill mật khẩu |
| `app/src/main/res/layout/fragment_ask.xml` | Bố cục Ask mới |
| `app/src/main/res/layout/auth_activity_login.xml` | Bố cục Login mới |
| `app/src/main/res/layout/auth_activity_register.xml` | Bố cục Register mới |

Các file mới:

- `app/src/main/java/com/aistudy/aistudy/auth/AuthFormUi.kt`
- `app/src/main/res/values/study_ui.xml`
- `app/src/main/res/color/study_control_foreground.xml`
- `app/src/main/res/color/study_input_stroke.xml`
- `app/src/main/res/color/study_row_background.xml`
- `app/src/main/res/color/study_suggestion_foreground.xml`
- `app/src/main/res/drawable/ic_study_arrow.xml`
- `app/src/main/res/drawable/ic_study_code.xml`
- `app/src/main/res/drawable/ic_study_error.xml`
- `app/src/main/res/drawable/ic_study_language.xml`
- `app/src/main/res/drawable/ic_study_math.xml`
- `app/src/main/res/drawable/ic_study_science.xml`
- `app/src/main/res/layout/include_study_auth_loading.xml`
- `app/src/main/res/layout/include_study_auth_status.xml`
- `app/src/main/res/layout/include_study_brand.xml`
- `app/src/main/res/layout/include_study_email.xml`
- `app/src/main/res/layout/include_study_name.xml`
- `app/src/main/res/layout/include_study_password.xml`

Tài liệu: cập nhật `DESIGN.md`, thêm báo cáo này. Các thay đổi đã có trước lần triển khai không nằm trong danh sách trên.

## Build và kiểm tra

**`assembleDebug`: BUILD SUCCESSFUL.** Build được kiểm tra lần lượt sau Ask, Login và Register. Bản kết hợp cuối thành công trong 54 giây. APK: `app/build/outputs/apk/debug/app-debug.apk`.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
.\gradlew.bat '-Porg.gradle.java.installations.paths=C:\Program Files\Java\jdk-25' '-Porg.gradle.java.installations.auto-detect=false' --no-daemon assembleDebug --console=plain
```

- Đối chiếu với snapshot trước khi chỉnh: thân hàm `login()` và `register()` giữ nguyên; Gemini worker, transaction Room, XP, prompt, parser và pending-answer navigation giữ nguyên. Các source main khác, bao gồm Answer và database, khớp hash ban đầu.
- Xem trên emulator: Ask/Login light và dark, lỗi rỗng cạnh trường nhập, Ask ở chiều rộng tablet, Register 320dp với font 200% và bàn phím QWERTY đầy đủ. Các ảnh mặc định Ask/Login dùng toolbar nhập liệu nổi của hệ điều hành.
- Ảnh kiểm tra ở thư mục ignored `build/sage-ui-review/round1/`. Helper kiểm tra nằm trong `build/sage-ui-before/`, không được đưa vào source ứng dụng.
- Chưa xác minh: TalkBack speech, giao diện loading khi chạy request thực, lỗi/helper ở font 200%, và thao tác cuộn tới CTA khi bàn phím đầy đủ mở trên cả ba màn.
- Không gọi Gemini, không đăng nhập/đăng ký thành công để kiểm tra giao diện. Phiên và thiết lập emulator được sao lưu/khôi phục sau preview.
- Không chạy unit test hoặc Instrumented Test trong lần triển khai này. Không commit/push. Không triển khai Phase 5 trở đi.
