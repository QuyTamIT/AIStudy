# Bàn giao chỉnh sửa Đợt 1–2

## Phạm vi và trạng thái

- Nhánh local: `dev_nguyen`; baseline Part 1: `b2c659dd9aac8088eac47d9c5b71a2ae4f3a0063`.
- Giữ Room schema/version 8, giao diện và các luồng hiện có. Không đổi backend hoặc triển khai hàng đợi offline.
- Thay đổi đang nằm trong working tree; chưa commit, chưa push.
- Kiểm thử thiết bị chỉ trên AVD cách ly `AIStudy_Test`, Android API 37. Database migration là fixture riêng có tên ngẫu nhiên; test DAO dùng Room in-memory. Không thử migration trên database học tập thật.
- Không gọi Gemini thật trong các kiểm thử này.

## Những mục đã triển khai

| Mục | Nội dung | Bằng chứng kiểm thử |
|---|---|---|
| CS01 | Mật khẩu mới dùng PBKDF2-HMAC-SHA256, salt riêng và định dạng có phiên bản. Giữ xác minh plaintext/SHA-256 cũ; nâng cấp sau khi xác thực bằng cập nhật có điều kiện. Không nhận chuỗi hash đang lưu làm mật khẩu. | `PasswordHasherTest`, `LegacyPasswordUpgradeInstrumentedTest`, `AuthFlowInstrumentedTest` |
| CS11 | Test auth theo lỗi inline, đăng ký tự đăng nhập, onboarding và hộp thoại xác nhận đăng xuất; cập nhật fake DAO theo contract mới. | Đăng ký, lỗi nhập liệu, trùng email, mật khẩu sai, đăng nhập, onboarding/recreation, đăng xuất, back stack và mở lại app |
| CS07 | Đọc/sửa lịch sử theo ID và owner; kiểm tra lại phiên tại caller/callback. Ghi nhận ôn tập có compare-and-set cùng transaction XP để tránh cập nhật lặp. | `DataOwnershipInstrumentedTest`: sai owner không đọc/sửa; callback đổi tài khoản không lưu; query Quiz kiểm tra owner và hoàn tất một lần |
| CS08 | Restore Quiz kiểm tra owner và dữ liệu phiên; giữ câu hiện tại, lựa chọn nháp, đáp án đã chấm, feedback và nút thao tác. Restore lỗi không tự gọi sinh Quiz mới. Hoàn tất phiên có điều kiện trong transaction XP. | `QuizRestoreInstrumentedTest`: recreation trước/sau chấm, câu tiếp, kết quả và XP không lặp; chặn tài khoản khác |
| CS03 (ảnh) | Copy tối đa 20 MiB; phát hiện định dạng từ bytes thay vì tin MIME/suffix; sampling giới hạn cả hai chiều; payload tối đa 2 MiB, JPEG trả đúng `image/jpeg`. Dùng decoder chung cho Ask/Answer. Draft nằm trong cache, tách ảnh lịch sử; dọn bản copy dở, ảnh nháp bị thay/gỡ/hủy. Chỉ lưu ảnh lâu dài khi chuẩn bị lưu đáp án; lỗi lưu dọn bản mới chưa tham chiếu. | `ImagePreparationInstrumentedTest`: thường/lớn/dài/rộng/hỏng, MIME không khớp suffix, copy quá giới hạn và bảo vệ ảnh ngoài draft. `ImageOnlyInputInstrumentedTest`: provider copy/preview, recreation, câu hỏi chỉ có ảnh qua validation, không gửi mạng/không cộng XP |
| CS09 | Dùng cùng factory mở DB cho ứng dụng và fixture, đăng ký đủ migration 1→8. Bỏ destructive fallback; phiên bản không được hỗ trợ báo lỗi thay vì tạo lại DB. Quy định backup đồng bộ như bên dưới. | `ProfileMigrationInstrumentedTest`: 7 fixture độc lập v1–v7 lên v8, giữ các dữ liệu có ở từng version và mở lại; test phiên bản không hỗ trợ giữ tài khoản sau khi mở thất bại |
| CS10 | Đổi `android:tint` sang `app:tint` và thêm namespace ở hai layout được chỉ định. | `lintDebug` không còn `UseAppTint`; build thành công |

## Chính sách dữ liệu và backup

- Tắt backup tự động bằng `allowBackup=false` và exclude dữ liệu trong cả cloud-backup/device-transfer rules.
- DB, ảnh và phiên đăng nhập đều không được backup/transfer tự động: tránh khôi phục một phần DB–ảnh hoặc khôi phục phiên đăng nhập cũ.
- Cập nhật APK không xóa dữ liệu đang cài. Gỡ ứng dụng/xóa dữ liệu vẫn mất dữ liệu local; bản sửa này không bổ sung tính năng xuất/sao lưu thủ công.
- Thiếu migration không còn tự tạo lại DB. Cần cung cấp migration phù hợp thay vì xóa dữ liệu để mở được app.

## Kết quả đã chạy

Lượt xác nhận cuối: báo cáo instrumentation timestamp `2026-11-11T02:19:27` (UTC); máy host ghi nhận `2026-11-11T09:19:57+07:00` khi đọc kết quả.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug :app:assembleDebug --offline --no-configuration-cache --continue --console=plain
git diff --check
```

| Kiểm tra | Kết quả |
|---|---|
| Gradle | `BUILD SUCCESSFUL`, exit 0 |
| JVM unit test | 68 tests, 0 failures, 0 errors, 0 skipped |
| Android instrumentation | 23 tests, 0 failures, 0 errors, 0 skipped |
| Lint | 0 errors/fatal; 134 warnings; không còn `UseAppTint` |
| APK debug | Tạo thành công |
| `git diff --check` | Exit 0 |

Phân nhóm 23 test thiết bị: auth 1; data ownership 3; example/package 1; image-only input 1; image preparation 4; legacy password 3; migration 8; Quiz restore 2. Số lượng test tự động này không tương đương việc toàn bộ TC-01–TC-26 của assignment đã được thực thi.

Lint offline ban đầu thiếu `androidx.graphics:graphics-shapes-desktop:1.0.1`; đã chạy `lintDebug` có mạng để tải artifact thiếu, không thay dependency/version. Sau đó lệnh xác nhận tổng ở trên chạy offline thành công. Các warning còn lại gồm unused resources, hardcoded text, overdraw và các nhóm khác; chưa mở rộng phạm vi để xử lý toàn bộ warning.

Báo cáo Gradle trong thư mục build:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/androidTests/connected/debug/index.html`
- `app/build/reports/lint-results-debug.html`

Log lệnh xác nhận tổng nằm trong scratch của phiên: `C:\Users\CHI NGUYEN\.pi-desktop\scratch\61f73da8-9d2a-48b3-b309-28d9c0716a10\final-all-checks.txt`.

## APK

- Đường dẫn: `app/build/outputs/apk/debug/app-debug.apk`
- Kích thước: 17,972,473 bytes.
- SHA-256: `5B3B8578B5C9A0A16D47E7F208F2FF08CEF46DAE99D57DC002200FD01AA8414E`.

## Giới hạn kiểm chứng và rà soát

- Chưa chạy thiết bị API tối thiểu 33 hoặc thiết bị vật lý.
- Chưa kiểm chứng gọi AI end-to-end, chất lượng đáp án, UI picker/camera của mọi thiết bị hoặc backup/transfer thực tế qua cloud/OEM. Test image-only dừng ở guard trước mạng; test provider dùng handler thực qua reflection, không tự động hóa ứng dụng gallery bên ngoài.
- Fixture migration là SQL dựng độc lập theo schema/migration đang có, không phải bản sao dữ liệu người dùng hoặc artifact APK lịch sử.
- Đã đọc trực tiếp diff production và helper ảnh ở lượt cuối. Rà soát độc lập bằng subagent không chạy được vì công cụ từ chối model; không coi đó là review đã hoàn thành.
- CS02 (gateway), CS04–CS06 và thay đổi schema/hàng đợi lớn không nằm trong đợt đã triển khai này.
