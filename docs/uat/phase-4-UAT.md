# Phase 4 — Kịch bản UAT (AI Layer)

> Mục tiêu: xác nhận nhập liệu bằng tiếng Việt tự nhiên hoạt động, có fallback khi mất mạng,
> và **AI không bao giờ ghi thẳng vào database**.
>
> Thời gian ước tính: 35–45 phút. 22 case.

---

## Chuẩn bị

1. **Đóng hẳn ứng dụng nếu đang mở** (menu tray → Thoát). Backend cũ giữ file jar nên bản build
   mới sẽ không ghi đè được.
2. Sao lưu thủ công trước khi chạy migration `V6`:
   ```bash
   cp data/lifehub.db "data/backups/lifehub-$(date +%Y%m%d-%H%M%S).db"
   ```
   (Backup tự động là việc của Phase 5 — xem PROGRESS.md, mục B-2.)
3. Build và chạy:
   ```bash
   npm run build
   npm run dev
   ```
4. Chuẩn bị sẵn một **API key Anthropic** (dạng `sk-ant-…`). Nếu chưa có, vẫn làm được toàn bộ
   phần ngoại tuyến — các case đánh dấu *(cần key)* thì bỏ qua và ghi rõ vào kết quả.
5. Nếu đây là lần đầu dùng app: vào **Tài chính → Ví** tạo ít nhất một ví, để form giao dịch có
   chỗ điền sẵn.

---

## A. Màn hình Cài đặt (FR-SYS-09, FR-AI-09, FR-AI-12)

### UAT-4-01 — Mục "Cài đặt" đã mở khóa
1. Nhìn thanh bên trái.
2. **Kỳ vọng:** mục **Cài đặt** không còn nhãn "P4" và bấm vào được.

### UAT-4-02 — Đổi giao diện sáng/tối được lưu vào database
1. Vào **Cài đặt → Chung**, đổi **Chủ đề** sang `Tối`.
2. Giao diện đổi màu ngay.
3. Đóng hẳn app rồi mở lại.
4. **Kỳ vọng:** app mở lại ở chế độ tối.
5. Kiểm tra bằng SQL (tùy chọn):
   ```bash
   sqlite3 data/lifehub.db "SELECT value FROM setting WHERE key='app.theme';"
   ```
   **Kỳ vọng:** trả về `DARK` — lựa chọn đã chuyển từ `localStorage` sang bảng `setting`
   (PROGRESS.md, mục C-2).

### UAT-4-03 — Đổi tiền tệ và ngày bắt đầu tuần
1. Đổi **Tiền tệ** sang `USD`, rồi đổi lại `VND`.
2. Đổi **Ngày bắt đầu tuần** sang `Chủ nhật`, rồi đổi lại `Thứ 2`.
3. **Kỳ vọng:** mỗi lần đổi hiện toast "Đã lưu cài đặt", không có lỗi trong console.

### UAT-4-04 — Đổi múi giờ khởi động lại dịch vụ nền
1. Đổi **Múi giờ** sang `Asia/Tokyo`.
2. **Kỳ vọng:** toast "Đã lưu. Đang khởi động lại dịch vụ nền để áp dụng…", màn hình nạp lại
   dữ liệu sau vài giây, app vẫn dùng được bình thường.
3. Vào **Lịch**, kiểm tra giờ của một sự kiện đã có: giờ hiển thị dịch đúng 2 tiếng so với trước.
4. Đổi lại `Asia/Ho_Chi_Minh`.

### UAT-4-05 — Nhập API key *(cần key)*
1. Vào **Cài đặt → AI**.
2. Dán API key vào ô, bấm **Lưu**.
3. **Kỳ vọng:**
   - Ô nhập bị che (dạng `••••`), bấm icon con mắt mới hiện.
   - Toast "Đã lưu API key vào kho bảo mật của hệ điều hành".
   - Ô nhập được xóa trắng sau khi lưu.
   - Dòng trạng thái đổi thành "Sẵn sàng, đang dùng claude-opus-5".

### UAT-4-06 — Kiểm tra kết nối *(cần key)*
1. Bấm **Kiểm tra kết nối**.
2. **Kỳ vọng:** toast "Kết nối thành công. Model đang dùng: …" trong vòng vài giây.

### UAT-4-07 — API key sai báo lỗi rõ ràng, không lộ stack trace
1. Xóa key hiện tại (**Xóa key**), rồi nhập một chuỗi rác, ví dụ `sk-ant-sai-hoan-toan`.
2. Bấm **Kiểm tra kết nối**.
3. **Kỳ vọng:**
   - Thông báo tiếng Việt kiểu "API key không hợp lệ hoặc đã bị thu hồi".
   - **KHÔNG** có chữ `Exception`, `at com.lifehub…` hay bất kỳ stack trace nào trên màn hình.
4. Nhập lại key đúng (hoặc xóa key nếu bạn không có).

### UAT-4-08 — Chọn model
1. Đổi **Model** sang `Claude Haiku 4.5`.
2. **Kỳ vọng:** lưu thành công; dòng trạng thái đổi theo.
3. Đổi lại về `Claude Opus 5` (hoặc giữ Haiku nếu bạn thấy nhanh hơn — nó ít rơi vào chế độ
   ngoại tuyến hơn khi mạng chậm).

---

## B. Nhập liệu bằng ngôn ngữ tự nhiên (FR-AI-01 → FR-AI-06)

> Các case B chạy **có API key**. Nếu bạn không có key, kết quả vẫn ra nhưng do bộ luật ngoại tuyến
> sinh, kèm banner vàng — khi đó hãy so với phần D thay vì phần này.

### UAT-4-09 — Mở ô nhập nhanh từ mọi màn hình
1. Đứng ở màn hình **Tổng quan**, nhấn `Ctrl` + `Space`.
2. **Kỳ vọng:** hộp "Nhập nhanh" hiện lên, con trỏ đã nằm sẵn trong ô nhập.
3. Nhấn `Esc`, sang màn hình **Lịch**, nhấn `Ctrl` + `Space` lần nữa — vẫn mở được.

### UAT-4-10 — `ăn trưa cơm gà 45k với team`
1. `Ctrl` + `Space`, gõ `ăn trưa cơm gà 45k với team`, nhấn `Enter`.
2. **Kỳ vọng:**
   - Form **Thêm giao dịch** mở ra, đã điền sẵn.
   - Số tiền: `45.000`.
   - Danh mục: **Ăn uống** (hoặc một danh mục con của nó).
   - Nhãn "Số tiền" và "Danh mục" có badge tím kiểu `AI 99%`.

### UAT-4-11 — `1tr2 tiền nhà`
1. `Ctrl` + `Space`, gõ `1tr2 tiền nhà`, `Enter`.
2. **Kỳ vọng:** số tiền `1.200.000`.

### UAT-4-12 — `2 triệu rưỡi`
1. `Ctrl` + `Space`, gõ `nhận lương 2 triệu rưỡi`, `Enter`.
2. **Kỳ vọng:** số tiền `2.500.000`, loại giao dịch là **Thu**, danh mục **Lương**.

### UAT-4-13 — `họp review sprint thứ 5 tuần sau 2h chiều nhắc trước 15 phút`
1. `Ctrl` + `Space`, gõ nguyên câu trên, `Enter`.
2. **Kỳ vọng:**
   - Form **Thêm sự kiện** mở ra.
   - Tiêu đề: đại ý "Họp review sprint" (không lặp lại cả câu).
   - Bắt đầu: đúng **thứ 5 của tuần kế tiếp**, lúc **14:00**.
   - Kết thúc: 15:00.
   - Mục nhắc hẹn **15 phút trước** đã được chọn sẵn.

### UAT-4-14 — `mua quà sinh nhật mẹ`
1. `Ctrl` + `Space`, gõ `mua quà sinh nhật mẹ`, `Enter`.
2. **Kỳ vọng:** form **Thêm task** mở ra, tiêu đề `mua quà sinh nhật mẹ`.

### UAT-4-15 — Trường AI không chắc thì để trống và tô cảnh báo
1. `Ctrl` + `Space`, gõ một câu mơ hồ, ví dụ `trả 200k cho cái kia`, `Enter`.
2. **Kỳ vọng:** ô **Danh mục** để trống, có viền vàng và badge **Cần kiểm tra**
   (AI không đoán bừa — UC-09 luồng 10a).
3. Chọn tay một danh mục rồi bấm **Lưu** để kiểm tra vẫn lưu bình thường.

### UAT-4-16 — Câu không hiểu được thì hỏi lại
1. `Ctrl` + `Space`, gõ `zzz qwerty`, `Enter`.
2. **Kỳ vọng:** dòng chữ "Mình chưa hiểu ý bạn. Bạn muốn tạo gì?" kèm 3 nút
   **Giao dịch / Công việc / Sự kiện**.
3. Bấm **Công việc** → form task mở ra với tiêu đề `zzz qwerty`.

### UAT-4-17 — ⚠️ **Bấm Esc thì KHÔNG có bản ghi nào được tạo** (bắt buộc)
1. Đếm số giao dịch hiện có:
   ```bash
   sqlite3 data/lifehub.db "SELECT COUNT(*) FROM \"transaction\";"
   ```
   Ghi lại con số này.
2. `Ctrl` + `Space`, gõ `ăn tối 300k`, `Enter`.
3. Form giao dịch mở ra đã điền sẵn — **nhấn `Esc`**.
4. Lặp lại bước 2–3 thêm 2 lần với câu khác.
5. Đếm lại:
   ```bash
   sqlite3 data/lifehub.db "SELECT COUNT(*) FROM \"transaction\";"
   ```
6. **Kỳ vọng:** con số **không đổi**. Đây là tiêu chí quan trọng nhất của phase này —
   AI chỉ đề xuất, chỉ nút Lưu mới ghi.

### UAT-4-18 — Xác nhận thì mới lưu
1. `Ctrl` + `Space`, gõ `cà phê sáng 35k`, `Enter`, rồi bấm **Lưu**.
2. **Kỳ vọng:** toast "Đã lưu giao dịch", số dư ví giảm 35.000, giao dịch xuất hiện trong danh sách.

---

## C. Gợi ý danh mục (FR-AI-07, UC-10)

### UAT-4-19 — Chip gợi ý dưới ô danh mục
1. Vào **Tài chính**, bấm **Thêm giao dịch**.
2. Nhập số tiền `55.000`, **để trống danh mục**.
3. Gõ vào ô **Ghi chú**: `trà sữa gongcha`, rồi **dừng gõ**.
4. **Kỳ vọng:** sau khoảng 1 giây, dưới ô Danh mục hiện dòng "Gợi ý:" kèm tối đa 3 chip tím.
5. Bấm một chip → danh mục được điền vào ô.
6. **Kỳ vọng phụ:** khi ô Danh mục đã có giá trị, các chip biến mất.

### UAT-4-20 — Ghi chú trùng lịch sử thì không tốn lời gọi AI
1. Lưu giao dịch ở bước trên.
2. Mở form giao dịch mới, gõ đúng ghi chú `trà sữa gongcha` một lần nữa.
3. **Kỳ vọng:** chip gợi ý hiện gần như tức thì và trỏ đúng danh mục bạn vừa chọn.
4. Vào **Cài đặt → Nhật ký AI**: **không** có bản ghi `CATEGORY_SUGGEST` mới cho lần thứ hai
   (UC-10 ngoại lệ E2 — dùng lại lịch sử, tiết kiệm chi phí API).

---

## D. Chế độ ngoại tuyến và tắt AI (FR-AI-08, FR-AI-12)

### UAT-4-21 — Ngắt mạng thì tự chuyển sang bộ luật, không crash
1. **Tắt Wi-Fi / rút mạng.**
2. `Ctrl` + `Space`, gõ `ăn trưa cơm gà 45k với team`, `Enter`.
3. **Kỳ vọng:**
   - Sau tối đa ~5 giây, form giao dịch vẫn mở ra với số tiền `45.000`.
   - Banner vàng ở đầu màn hình: "Đang dùng chế độ ngoại tuyến, kết quả có thể kém chính xác hơn
     — Không kết nối được dịch vụ AI".
   - App **không** treo, **không** hiện lỗi đỏ.
4. Thử thêm `1tr2 tiền nhà` và `2 triệu rưỡi` — số tiền vẫn đúng.
5. **Bật mạng lại.**

### UAT-4-22 — Tắt AI thì mọi câu chạy bằng bộ luật
1. Vào **Cài đặt → AI**, bỏ chọn **Bật phân tích bằng AI**.
2. `Ctrl` + `Space`.
3. **Kỳ vọng:** trong hộp nhập nhanh có dòng "AI đang tắt nên câu của bạn được phân tích bằng bộ
   luật ngoại tuyến. Bật lại trong Cài đặt."
4. Gõ `cà phê 45k`, `Enter` → vẫn ra form giao dịch với `45.000`.
5. Vào **Cài đặt → Nhật ký AI**: có bản ghi mới, cột "Kết quả" ghi **AI đang tắt**, cột Model trống
   — chứng minh không có lời gọi API nào được thực hiện.
6. Bật AI lại.

---

## E. Nhật ký và bảo mật (FR-AI-10, NFR-SEC-01)

### UAT-4-23 — Mọi lần gọi đều có bản ghi
1. Vào **Cài đặt → Nhật ký AI**.
2. **Kỳ vọng:** thấy các dòng tương ứng với những câu bạn đã gõ ở phần B, mới nhất trước, kèm
   thời điểm, độ trễ (ms), số token và tên model.
3. So sánh số dòng với số lần bạn nhấn `Enter` trong hộp nhập nhanh — phải khớp.

### UAT-4-24 — ⚠️ API key không nằm ở dạng thường trong bất kỳ file nào (bắt buộc)
1. Với API key thật đang được lưu, chạy (thay `sk-ant-abc123` bằng **10 ký tự đầu** key của bạn):
   ```bash
   grep -r "sk-ant-abc123" data/ logs/ ; echo "exit=$?"
   ```
2. **Kỳ vọng:** không có dòng kết quả nào (`exit=1`).
3. Kiểm tra thêm bảng `setting` không chứa key:
   ```bash
   sqlite3 data/lifehub.db "SELECT key FROM setting;"
   ```
   **Kỳ vọng:** không có khóa nào tên `ai.api_key` hay tương tự.
4. Key được cất ở `%APPDATA%/LifeHub/secure/ai-api-key.bin` (Windows) dưới dạng **đã mã hóa** bằng
   kho bảo mật của hệ điều hành — mở bằng Notepad sẽ thấy ký tự nhị phân, không đọc được.

---

## Ghi kết quả

| Case | Kết quả (PASS/FAIL) | Ghi chú |
|---|---|---|
| UAT-4-01 | | |
| UAT-4-02 | | |
| UAT-4-03 | | |
| UAT-4-04 | | |
| UAT-4-05 | | |
| UAT-4-06 | | |
| UAT-4-07 | | |
| UAT-4-08 | | |
| UAT-4-09 | | |
| UAT-4-10 | | |
| UAT-4-11 | | |
| UAT-4-12 | | |
| UAT-4-13 | | |
| UAT-4-14 | | |
| UAT-4-15 | | |
| UAT-4-16 | | |
| UAT-4-17 | | ⚠️ bắt buộc PASS |
| UAT-4-18 | | |
| UAT-4-19 | | |
| UAT-4-20 | | |
| UAT-4-21 | | |
| UAT-4-22 | | |
| UAT-4-23 | | |
| UAT-4-24 | | ⚠️ bắt buộc PASS |

Nếu có case FAIL, gõ `FIX PHASE 4: <mô tả lỗi>` để yêu cầu sửa.
