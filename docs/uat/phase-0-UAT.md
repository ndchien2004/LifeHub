# UAT — Phase 0: Hạ tầng & khung sườn

**Cách chạy ứng dụng:**

```bash
cd D:\LifeHub
npm install       # chỉ cần chạy lần đầu
npm run dev
```

Lệnh `npm run dev` làm 3 việc theo thứ tự: build `backend/target/lifehub-backend.jar`,
khởi động Vite dev server ở cổng 5173, rồi mở Electron. Electron tự tìm một cổng trống,
sinh token ngẫu nhiên và spawn tiến trình Java.

**Yêu cầu môi trường:** JDK 21 (biến `JAVA_HOME` trỏ đúng), Node.js 20+.

**Thời gian ước tính:** ~15 phút

---

## UAT-0-01: Ứng dụng khởi động được từ đầu đến cuối

**Mục đích:** Kiểm tra tiêu chí "npm run dev khởi động app" và NFR-PERF-01 (≤ 6 giây)

**Chuẩn bị:** Đóng hết instance LifeHub đang chạy. Mở đồng hồ bấm giờ.

**Các bước:**
1. Chạy `npm run dev`
2. Bấm giờ từ lúc **cửa sổ splash hiện ra**
3. Quan sát splash screen "Đang khởi động dịch vụ nền…"
4. Dừng giờ khi cửa sổ chính hiện và đọc được nội dung

**Kết quả mong đợi:**
- Splash screen hiện gần như tức thì, có thanh chạy
- Splash tự đóng, cửa sổ chính LifeHub mở ra
- Thời gian từ splash tới giao diện đọc được **≤ 6 giây**
- Giao diện có sidebar bên trái và trang "Tổng quan" bên phải

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Thời gian đo được:** ______ giây
**Ghi chú của bạn:** _______________

---

## UAT-0-02: Giao diện hiển thị dữ liệu lấy từ `/bootstrap`

**Mục đích:** Kiểm tra tiêu chí "Giao diện hiển thị dữ liệu lấy từ /bootstrap" — chứng minh
chuỗi renderer → HTTP có token → Spring → SQLite thông suốt

**Chuẩn bị:** Ứng dụng đang mở ở trang Tổng quan

**Các bước:**
1. Nhìn thẻ "Kết nối thành công"
2. Nhìn thẻ "Cấu hình đọc từ database"
3. Đếm số dòng trong bảng cấu hình
4. Đối chiếu vài giá trị

**Kết quả mong đợi:**
- Thẻ "Kết nối thành công" hiện dấu tích
- Bảng cấu hình có đúng **8 dòng**
- `app.theme` = `SYSTEM`, `app.currency` = `VND`, `app.timezone` = `Asia/Ho_Chi_Minh`,
  `app.week_start` = `MONDAY`, `backup.keep_count` = `30`
- Thẻ "Chưa có ở phase này" ghi: nhắc hẹn bị lỡ **0**, số liệu tổng quan **chưa có**,
  AI đã cấu hình **chưa**

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-0-03: Chuyển theme sáng / tối (FR-SYS-06)

**Mục đích:** Kiểm tra FR-SYS-06

**Chuẩn bị:** Ứng dụng đang mở

**Các bước:**
1. Nhìn xuống góc dưới sidebar, thấy 3 nút: mặt trời, mặt trăng, màn hình
2. Bấm nút **mặt trăng**
3. Bấm nút **mặt trời**
4. Bấm nút **màn hình** (theo hệ thống)
5. Đổi theme của Windows (Cài đặt → Cá nhân hóa → Màu → chọn Tối / Sáng) trong khi app vẫn mở
6. Đóng hẳn app rồi mở lại bằng `npm run dev`

**Kết quả mong đợi:**
- Bước 2: toàn bộ giao diện chuyển sang nền tối ngay lập tức, chữ vẫn đọc rõ
- Bước 3: chuyển lại nền sáng ngay lập tức
- Bước 4: giao diện khớp với thiết lập hiện tại của Windows
- Bước 5: app **tự đổi theo** mà không cần khởi động lại
- Bước 6: lựa chọn ở bước 4 vẫn được nhớ

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-0-04: Database được tạo đúng chỗ, đúng cấu trúc

**Mục đích:** Kiểm tra tiêu chí "File data/lifehub.db được tạo, có bảng flyway_schema_history và setting"

**Chuẩn bị:** Đã chạy app ít nhất một lần

**Các bước:**
1. Mở thư mục `D:\LifeHub\data`
2. Liệt kê các file trong đó
3. (Tùy chọn, nếu bạn có công cụ SQLite) mở `lifehub.db` và chạy:
   `SELECT name FROM sqlite_master WHERE type='table';`

**Kết quả mong đợi:**
- Có file `lifehub.db`
- Có thêm `lifehub.db-wal` và `lifehub.db-shm` — đây là bằng chứng WAL mode đang bật (NFR-REL-04)
- Nếu mở được DB: thấy bảng `setting` và `flyway_schema_history`
- Bảng `setting` có 8 dòng, `flyway_schema_history` có đúng 1 dòng (version 1)

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-0-05: Gọi API thiếu token bị từ chối (NFR-SEC-04)

**Mục đích:** Kiểm tra tiêu chí "Gọi API thiếu header token → trả 401"

**Chuẩn bị:** Ứng dụng đang chạy. Cần biết cổng backend — nhìn dòng
`[main] backend ready on 127.0.0.1:XXXXX` trong cửa sổ terminal đang chạy `npm run dev`.

**Các bước:**
1. Mở một terminal khác
2. Chạy (thay `XXXXX` bằng cổng thật):
   ```bash
   curl -i http://127.0.0.1:XXXXX/api/v1/bootstrap
   ```
3. Chạy tiếp:
   ```bash
   curl -i -H "X-App-Token: token-bay-ba" http://127.0.0.1:XXXXX/api/v1/bootstrap
   ```
4. Chạy tiếp:
   ```bash
   curl -i http://127.0.0.1:XXXXX/actuator/health
   ```

**Kết quả mong đợi:**
- Bước 2: `HTTP/1.1 401`, body có `"code":"UNAUTHORIZED"` và một `traceId`
- Bước 3: cũng `HTTP/1.1 401` — token sai bị từ chối y như không có token
- Bước 4: `HTTP/1.1 200` với `"status":"UP"` — health mở có chủ ý để Electron poll lúc khởi động
- **Không có** stack trace hay tên class Java nào trong body lỗi (NFR-USE-03)

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-0-06: Backend chết thì Electron tự dựng lại (NFR-REL-02)

**Mục đích:** Kiểm tra tiêu chí "Kill tiến trình Java bằng tay → Electron tự spawn lại"

**Chuẩn bị:** Ứng dụng đang chạy bình thường, đang mở trang Tổng quan

**Các bước:**
1. Mở Task Manager (Ctrl+Shift+Esc), tab Details
2. Tìm tiến trình `java.exe` (hoặc `javaw.exe`)
3. Bấm **End task** để giết nó
4. Quan sát cửa sổ terminal đang chạy `npm run dev` và cửa sổ ứng dụng
5. Chờ khoảng 5–10 giây

**Kết quả mong đợi:**
- Terminal in ra dòng dạng `[backend] exited with code ..., restart attempt 1/3 in 2000ms`
- Một tiến trình `java.exe` mới xuất hiện trong Task Manager
- Cửa sổ ứng dụng tự tải lại và hiện lại đầy đủ dữ liệu cấu hình
- Ứng dụng **không** bị treo, **không** hiện màn hình trắng vĩnh viễn

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-0-07: Log ghi ra file

**Mục đích:** Kiểm tra tiêu chí "logs/lifehub.log có nội dung" (NFR-MNT-04)

**Chuẩn bị:** Đã chạy app ít nhất một lần

**Các bước:**
1. Mở file `D:\LifeHub\logs\lifehub.log` bằng Notepad hoặc VS Code
2. Tìm dòng chứa `SQLite pragmas`
3. Tìm dòng chứa `Started LifeHubApplication`
4. Nếu bạn đã làm UAT-0-05, tìm dòng chứa `Rejected GET /api/v1/bootstrap`

**Kết quả mong đợi:**
- File tồn tại và có nội dung
- Dòng pragma đọc được: `{journal_mode=wal, foreign_keys=1, busy_timeout=5000, synchronous=1}`
  — `foreign_keys=1` là quan trọng nhất, nếu tắt thì mọi ràng buộc khóa ngoại sẽ bị bỏ qua âm thầm
- Có dòng `Started LifeHubApplication in ... seconds`
- Nếu đã làm UAT-0-05: có dòng WARN ghi lại request bị từ chối, kèm `traceId` **khớp với
  traceId mà curl trả về**

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-0-08: Test tự động chạy sạch

**Mục đích:** Xác nhận lưới an toàn cho các phase sau đã hoạt động

**Các bước:**
1. Đóng ứng dụng
2. Chạy `npm test` ở thư mục gốc

**Kết quả mong đợi:**
- Backend: `Tests run: 46, Failures: 0, Errors: 0` và `BUILD SUCCESS`
- Frontend: `Test Files 3 passed`, `Tests 14 passed`
- Không có test nào bị skip

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## Tổng kết

| Case | Nội dung | Đạt | Không đạt |
|---|---|---|---|
| UAT-0-01 | Ứng dụng khởi động ≤ 6 giây | ☐ | ☐ |
| UAT-0-02 | Hiển thị dữ liệu từ `/bootstrap` | ☐ | ☐ |
| UAT-0-03 | Theme sáng / tối (FR-SYS-06) | ☐ | ☐ |
| UAT-0-04 | Database và WAL được tạo đúng | ☐ | ☐ |
| UAT-0-05 | Thiếu / sai token → 401 | ☐ | ☐ |
| UAT-0-06 | Backend chết thì tự dựng lại | ☐ | ☐ |
| UAT-0-07 | Log ghi ra file | ☐ | ☐ |
| UAT-0-08 | Test tự động chạy sạch | ☐ | ☐ |

**Nếu có case không đạt**, gõ lệnh:
`FIX PHASE 0: <mô tả vấn đề>`

**Nếu tất cả đạt**, gõ lệnh:
`START PHASE 1`
