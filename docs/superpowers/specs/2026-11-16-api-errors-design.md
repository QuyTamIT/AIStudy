# Thống nhất lỗi Gemini và phân loại hạn mức

Phạm vi đã được người dùng duyệt: mục 1 (thông báo lỗi chung cho mọi luồng AI)
và mục 2 (phân biệt giới hạn tạm thời với hết quota). Thực hiện trên checkout
hiện tại; giữ nguyên các thay đổi Manifest/backup có trước phiên này.

## Thiết kế

- Thêm `ask/AiErrorMessages.kt`: đổi exception API/mạng/phản hồi thành string
  resource. Ask, phân loại Answer/More topics, Learning extras và Quiz đều gọi
  hàm này. Lỗi phiên, nội dung thay đổi và lỗi lưu vẫn giữ xử lý riêng của từng luồng.
- `GeminiClient.readRequestError()` đọc `google.rpc.QuotaFailure` có cấu trúc:
  `quotaId`/`quotaMetric` cho biết per-minute hoặc per-day; `quotaValue = 0`
  cho biết không có quota khả dụng. Không đoán từ `error.message`/`description`.
- Sau review OMP: quotaValue được đọc chính xác theo số nguyên int64 không âm,
  hỗ trợ số/chuỗi dạng thập phân nguyên và số mũ. Giá trị có mặt nhưng không
  hợp lệ ngăn kết luận RATE_LIMITED; absent/null là chưa cung cấp, không phải 0.
- JSON lỗi dùng JsonReader để lấy token số nguyên gốc, tránh JSONObject của
  Android làm tròn qua Double. Token ngoài Double/BigDecimal được giữ dạng
  chuỗi, không loại bỏ toàn bộ envelope hoặc các lý do quota/quyền khác.
- HTTP 429 có vi phạm per-day hoặc quota bằng 0 -> `QUOTA_EXHAUSTED`.
  Nếu tất cả vi phạm xác định được là per-minute -> `RATE_LIMITED`.
  Thiếu, hỏng hoặc có vi phạm chưa biết -> `UNKNOWN`, thông báo trung lập.
  Quota dài hạn/bằng 0 được ưu tiên hơn giới hạn phút và gợi ý retry.
- Không tự retry khi đã nhận diện quota cạn/không khả dụng. Giới hạn tạm thời,
  429 chưa xác định và lỗi máy chủ giữ ngân sách tối đa 2 lần retry.
- Đọc thêm `google.rpc.RetryInfo.retryDelay`; lấy thời gian chờ lớn nhất giữa
  backoff, Retry-After và RetryInfo. Nếu vượt trần 30 giây, trả lỗi ngay thay vì
  gửi lại sớm hơn hướng dẫn của server. Không suy ra loại quota từ thời gian chờ.
- HTTP-date trong Retry-After dùng Duration giữa các Instant để tránh tràn số;
  ngày đã qua -> 0, khoảng chờ dương vượt Long -> Long.MAX_VALUE. Luôn bảo toàn
  RequestException/mã HTTP/reason gốc khi header có ngày rất xa.
- Bổ sung hai reason an toàn vào trace hiện có. Không ghi raw error body,
  metric, project, key hoặc câu hỏi; không thay đổi chính sách log debug/release.
- Giữ ngôn ngữ giao diện hiện có (English), không thêm dependency hoặc đổi DB,
  model, prompt, layout, navigation, XP hay luồng lưu dữ liệu.

## Hành vi người dùng

| Trường hợp | Thông báo |
|---|---|
| Per-minute | Too many AI requests right now. Please wait a moment before trying again. |
| Per-day / quota 0 | The AI quota is exhausted or unavailable. Try again after the quota resets, or contact support. |
| 429 chưa rõ loại | An AI request limit was reached. Please try again later. If this continues, contact support. |

Thông báo thiếu key dùng chung `ask_error_key_missing`, kể cả Quiz. Các lỗi
key sai/hết hạn, quyền, billing, model, timeout, mạng và phản hồi không hợp lệ
dùng cùng resource trên mọi luồng. Learning extras hiển thị lỗi cụ thể ngay
trong vùng nội dung và Toast. Lỗi tạo Quiz hiển thị đầy đủ trên màn hình cùng
nút Back. Lỗi khôi phục phiên đã lưu vẫn đóng màn Quiz.

## Kiểm chứng

- Test HTTP giả lập qua `GeminiClient.execute()`: phân loại, độ ưu tiên, quota 0,
  dữ liệu hỏng/thiếu, retry budget/delay và không lộ dữ liệu nhạy cảm.
- Unit test mapper: key/quyền/billing/HTTP/429/mạng/phản hồi và fallback riêng.
- Kiểm tra integration Quiz hiện có: lỗi schema vẫn không tạo dữ liệu/XP.
- Chạy toàn bộ JVM test mới, build debug và build APK instrumentation.
- Nếu có emulator sẵn, chạy test integration được chọn; không gọi Gemini thật.

## Tài liệu đối chiếu

- [Gemini rate limits](https://aistudio.google.com/docs/rate-limits): RPM, TPM,
  RPD là các chiều hạn mức riêng; HTTP 429 không tự nó xác định chiều đã vượt.
- [Google RPC error details](https://github.com/googleapis/googleapis/blob/master/google/rpc/error_details.proto):
  QuotaFailure/Violation và RetryInfo. Tên quota có thể đổi; trường hợp chưa nhận
  diện được luôn rơi về thông báo trung lập, không hứa thời gian reset cụ thể.
