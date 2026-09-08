# 01 — Software Requirements Specification (SRS)

**Dự án:** LifeHub — Personal Productivity & Finance Desktop App
**Phiên bản:** 1.0
**Ngày:** 2026-09-04
**Chuẩn tham chiếu:** IEEE 830 (rút gọn cho dự án cá nhân)

---

## 1. Giới thiệu

### 1.1. Mục đích

Tài liệu này đặc tả đầy đủ yêu cầu cho LifeHub — ứng dụng desktop offline-first chạy trên Windows/macOS, giúp một cá nhân quản lý công việc, lịch hẹn, nhắc nhở và chi tiêu, với lớp AI hỗ trợ nhập liệu bằng ngôn ngữ tự nhiên.

### 1.2. Phạm vi

**Trong phạm vi:**
- Quản lý task, project, tag
- Lịch, sự kiện lặp lại, nhắc hẹn qua native notification
- Quản lý giao dịch thu/chi, ví, danh mục, ngân sách
- AI parse ngôn ngữ tự nhiên → task/event/giao dịch
- AI tự phân loại danh mục chi tiêu
- Báo cáo, biểu đồ, insight định kỳ
- Export PDF/Excel, backup tự động

**Ngoài phạm vi (v1.0):**
- Đa người dùng, đăng nhập, phân quyền
- Đồng bộ cloud, đồng bộ đa thiết bị
- Ứng dụng mobile
- Kết nối trực tiếp API ngân hàng
- Chia sẻ / cộng tác

### 1.3. Định nghĩa và viết tắt

| Thuật ngữ | Nghĩa |
|---|---|
| Task | Một việc cần làm, có thể có deadline |
| Project | Nhóm các task liên quan |
| Event | Sự kiện có mốc thời gian bắt đầu/kết thúc trên lịch |
| Reminder | Cấu hình nhắc trước một event hoặc task |
| Transaction | Một giao dịch thu hoặc chi |
| Wallet | Nguồn tiền (tiền mặt, tài khoản ngân hàng, ví điện tử) |
| Budget | Hạn mức chi cho một danh mục trong một chu kỳ |
| Insight | Nhận xét do AI sinh ra từ dữ liệu người dùng |
| RRULE | Chuỗi mô tả quy luật lặp theo chuẩn RFC 5545 |
| NLP Input | Ô nhập liệu tự do để AI phân tích |

### 1.4. Đối tượng người dùng

Một người dùng duy nhất — chủ sở hữu máy tính. Là developer, quen với công cụ kỹ thuật, ưu tiên tốc độ nhập liệu và tính chính xác của dữ liệu hơn là giao diện hoa mỹ.

---

## 2. Mô tả tổng quan

### 2.1. Bối cảnh sản phẩm

LifeHub là ứng dụng độc lập (standalone), không phụ thuộc server ngoài, trừ khi gọi AI API. Dữ liệu lưu hoàn toàn trên máy trong file SQLite.

### 2.2. Ràng buộc thiết kế

| ID | Ràng buộc |
|---|---|
| CON-01 | Ứng dụng phải chạy được hoàn toàn offline, trừ tính năng AI |
| CON-02 | Toàn bộ dữ liệu lưu local, không upload lên bất kỳ server nào ngoài nội dung gửi cho AI API |
| CON-03 | Không yêu cầu cài đặt Java, PostgreSQL hay Docker riêng — installer phải đóng gói đủ runtime |
| CON-04 | Tổng dung lượng installer ≤ 350 MB |
| CON-05 | Chỉ một người dùng, không có màn hình đăng nhập |

### 2.3. Giả định và phụ thuộc

- Máy người dùng có RAM ≥ 8 GB
- Người dùng tự cung cấp API key của nhà cung cấp LLM
- Khi không có API key, mọi tính năng AI ẩn đi hoặc chuyển sang chế độ rule-based

---

## 3. Yêu cầu chức năng

> Ký hiệu độ ưu tiên: **M** = Must have, **S** = Should have, **C** = Could have

### 3.1. Module Task (FR-TSK)

| ID | Yêu cầu | Ưu tiên | Phase |
|---|---|---|---|
| FR-TSK-01 | Người dùng tạo task với: tiêu đề (bắt buộc), mô tả, độ ưu tiên (LOW/MEDIUM/HIGH/URGENT), ngày đến hạn, ước lượng thời gian (phút) | M | 1 |
| FR-TSK-02 | Người dùng sửa mọi thuộc tính của task | M | 1 |
| FR-TSK-03 | Người dùng xóa task (soft delete), có thể khôi phục trong 30 ngày | M | 1 |
| FR-TSK-04 | Task có trạng thái: TODO, IN_PROGRESS, DONE, CANCELLED. Chuyển trạng thái ghi lại thời điểm | M | 1 |
| FR-TSK-05 | Người dùng gán task vào một project | M | 1 |
| FR-TSK-06 | Người dùng gắn nhiều tag cho một task | S | 1 |
| FR-TSK-07 | Hiển thị task ở 2 chế độ: danh sách và Kanban theo trạng thái | M | 1 |
| FR-TSK-08 | Lọc task theo: project, tag, trạng thái, độ ưu tiên, khoảng ngày đến hạn | M | 1 |
| FR-TSK-09 | Sắp xếp task theo: ngày đến hạn, độ ưu tiên, ngày tạo | M | 1 |
| FR-TSK-10 | Tìm kiếm task theo từ khóa trong tiêu đề và mô tả | S | 1 |
| FR-TSK-11 | Task có thể có subtask (một cấp, không lồng sâu hơn) | S | 1 |
| FR-TSK-12 | Task quá hạn phải được đánh dấu trực quan khác biệt | M | 1 |
| FR-TSK-13 | Task lặp lại theo RRULE, hoàn thành task lặp sẽ sinh instance kế tiếp | C | 2 |

### 3.2. Module Project & Tag (FR-PRJ)

| ID | Yêu cầu | Ưu tiên | Phase |
|---|---|---|---|
| FR-PRJ-01 | CRUD project với: tên, màu, mô tả, trạng thái (ACTIVE/ARCHIVED) | M | 1 |
| FR-PRJ-02 | Xóa project không xóa task bên trong, task chuyển về "không project" | M | 1 |
| FR-PRJ-03 | Hiển thị tiến độ project: % task DONE trên tổng task | S | 1 |
| FR-PRJ-04 | CRUD tag với tên và màu, tên tag là duy nhất | S | 1 |

### 3.3. Module Calendar & Reminder (FR-CAL)

| ID | Yêu cầu | Ưu tiên | Phase |
|---|---|---|---|
| FR-CAL-01 | CRUD event với: tiêu đề, mô tả, thời gian bắt đầu, thời gian kết thúc, địa điểm, cờ cả ngày | M | 2 |
| FR-CAL-02 | Xem lịch ở 3 chế độ: tháng, tuần, ngày | M | 2 |
| FR-CAL-03 | Event lặp lại theo RRULE (DAILY, WEEKLY, MONTHLY, YEARLY + INTERVAL + BYDAY + UNTIL/COUNT) | M | 2 |
| FR-CAL-04 | Sửa event lặp: chọn "chỉ lần này" hoặc "lần này và các lần sau" | S | 2 |
| FR-CAL-05 | Liên kết một event với một task | S | 2 |
| FR-CAL-06 | Tạo nhiều reminder cho một event, mỗi reminder là khoảng thời gian trước (0, 5, 15, 30, 60, 1440 phút) | M | 2 |
| FR-CAL-07 | Reminder kích hoạt native OS notification, kể cả khi app thu nhỏ xuống system tray | M | 2 |
| FR-CAL-08 | Notification có nút "Hoãn 10 phút" và "Đã xong" | S | 2 |
| FR-CAL-09 | Nếu app đóng lúc reminder đến hạn, khi mở lại phải hiện tổng hợp các reminder bị lỡ trong 24h qua | M | 2 |
| FR-CAL-10 | Task có ngày đến hạn cũng hiển thị trên lịch dưới dạng khác biệt với event | S | 2 |
| FR-CAL-11 | Phát hiện và cảnh báo xung đột lịch khi 2 event trùng thời gian | C | 2 |

### 3.4. Module Finance (FR-FIN)

| ID | Yêu cầu | Ưu tiên | Phase |
|---|---|---|---|
| FR-FIN-01 | CRUD ví với: tên, loại (CASH/BANK/E_WALLET/CREDIT), số dư ban đầu, tiền tệ (mặc định VND) | M | 3 |
| FR-FIN-02 | CRUD danh mục thu/chi, hỗ trợ 2 cấp (danh mục cha - con), có icon và màu | M | 3 |
| FR-FIN-03 | Hệ thống tạo sẵn bộ danh mục mặc định tiếng Việt khi khởi tạo lần đầu | M | 3 |
| FR-FIN-04 | CRUD giao dịch với: loại (INCOME/EXPENSE/TRANSFER), số tiền, ví, danh mục, ngày giờ, ghi chú, tag | M | 3 |
| FR-FIN-05 | Giao dịch TRANSFER cần ví nguồn và ví đích, không cần danh mục | M | 3 |
| FR-FIN-06 | Số tiền lưu dạng số nguyên đơn vị đồng, không dùng số thực | M | 3 |
| FR-FIN-07 | Số dư ví tính động từ số dư ban đầu cộng/trừ các giao dịch, không lưu cứng | M | 3 |
| FR-FIN-08 | CRUD ngân sách: danh mục, hạn mức, chu kỳ (WEEKLY/MONTHLY/YEARLY), ngày bắt đầu | M | 3 |
| FR-FIN-09 | Hiển thị mức độ sử dụng ngân sách theo %, cảnh báo trực quan ở mốc 80% và 100% | M | 3 |
| FR-FIN-10 | Biểu đồ tròn cơ cấu chi tiêu theo danh mục trong khoảng thời gian chọn | M | 3 |
| FR-FIN-11 | Biểu đồ đường xu hướng thu/chi theo ngày/tuần/tháng | M | 3 |
| FR-FIN-12 | Lọc giao dịch theo: khoảng ngày, ví, danh mục, loại, khoảng số tiền, từ khóa | M | 3 |
| FR-FIN-13 | Giao dịch định kỳ (tiền nhà, subscription) tự sinh theo RRULE | C | 3 |
| FR-FIN-14 | Import giao dịch từ file CSV, cho phép ánh xạ cột thủ công | S | 5 |
| FR-FIN-15 | Phát hiện giao dịch trùng khi import (cùng ngày ± 1 ngày, cùng số tiền) và hỏi người dùng | S | 5 |

### 3.5. Module AI (FR-AI)

| ID | Yêu cầu | Ưu tiên | Phase |
|---|---|---|---|
| FR-AI-01 | Ô nhập liệu tự nhiên toàn cục (phím tắt `Ctrl+Space`), người dùng gõ tiếng Việt tự do | M | 4 |
| FR-AI-02 | AI phân loại ý định đầu vào thành: TASK, EVENT, TRANSACTION, hoặc UNKNOWN | M | 4 |
| FR-AI-03 | AI trích xuất thực thể cho TRANSACTION: số tiền, loại, danh mục, ví, ngày, ghi chú. Hiểu được cách viết tắt tiếng Việt: "45k", "1tr2", "2 triệu rưỡi" | M | 4 |
| FR-AI-04 | AI trích xuất thực thể cho TASK: tiêu đề, độ ưu tiên, ngày đến hạn từ diễn đạt tương đối ("thứ 5 tuần sau", "cuối tháng") | M | 4 |
| FR-AI-05 | AI trích xuất thực thể cho EVENT: tiêu đề, thời gian bắt đầu/kết thúc, địa điểm, reminder | M | 4 |
| FR-AI-06 | Kết quả AI hiển thị dưới dạng form đã điền sẵn, **người dùng bắt buộc xác nhận** trước khi lưu | M | 4 |
| FR-AI-07 | AI tự đề xuất danh mục cho giao dịch chưa có danh mục, dựa trên 30 giao dịch tương tự gần nhất của người dùng | S | 4 |
| FR-AI-08 | Khi API lỗi hoặc offline, hệ thống chuyển sang parser rule-based (regex số tiền + từ khóa danh mục) và báo rõ cho người dùng | M | 4 |
| FR-AI-09 | Người dùng nhập và lưu API key trong Settings, key được mã hóa bằng OS credential store | M | 4 |
| FR-AI-10 | Ghi log mọi lần gọi AI: thời điểm, loại yêu cầu, độ trễ, số token, kết quả | M | 4 |
| FR-AI-11 | Sinh insight hàng tuần: so sánh chi tiêu với tuần trước, danh mục vượt ngân sách, task trễ hạn lặp lại | S | 5 |
| FR-AI-12 | Người dùng có thể tắt hoàn toàn tính năng AI trong Settings | M | 4 |

### 3.6. Module Report & System (FR-SYS)

| ID | Yêu cầu | Ưu tiên | Phase |
|---|---|---|---|
| FR-SYS-01 | Dashboard tổng hợp: task hôm nay, event sắp tới, chi tiêu tháng này, tiến độ ngân sách | M | 3 |
| FR-SYS-02 | Export báo cáo chi tiêu ra Excel (.xlsx) có công thức tổng và biểu đồ | S | 5 |
| FR-SYS-03 | Export báo cáo tháng ra PDF định dạng chuyên nghiệp | S | 5 |
| FR-SYS-04 | Backup tự động file database mỗi ngày khi mở app, giữ 30 bản gần nhất | M | 5 |
| FR-SYS-05 | Restore từ file backup qua giao diện Settings | S | 5 |
| FR-SYS-06 | Chế độ sáng/tối, theo hệ thống hoặc chọn thủ công | S | 0 |
| FR-SYS-07 | Thu nhỏ xuống system tray thay vì thoát khi bấm nút đóng | M | 2 |
| FR-SYS-08 | Khởi động cùng hệ điều hành (tùy chọn) | C | 6 |
| FR-SYS-09 | Màn hình Settings: tiền tệ, ngày bắt đầu tuần, timezone, API key, thư mục backup | M | 4 |

---

## 4. Yêu cầu phi chức năng

### 4.1. Hiệu năng (NFR-PERF)

| ID | Yêu cầu | Ngưỡng đo |
|---|---|---|
| NFR-PERF-01 | Thời gian khởi động app đến khi tương tác được | ≤ 6 giây |
| NFR-PERF-02 | Thời gian phản hồi thao tác CRUD | ≤ 200 ms (p95) |
| NFR-PERF-03 | Thời gian load danh sách 5.000 giao dịch có phân trang | ≤ 500 ms |
| NFR-PERF-04 | Thời gian phản hồi AI parse | ≤ 5 giây, có loading indicator |
| NFR-PERF-05 | RAM tiêu thụ khi idle | ≤ 600 MB |
| NFR-PERF-06 | Độ trễ hiển thị reminder so với giờ hẹn | ≤ 30 giây |

### 4.2. Độ tin cậy (NFR-REL)

| ID | Yêu cầu |
|---|---|
| NFR-REL-01 | Mất kết nối mạng không được làm crash app, chỉ vô hiệu hóa tính năng AI |
| NFR-REL-02 | Backend crash phải được Electron phát hiện và tự khởi động lại tối đa 3 lần |
| NFR-REL-03 | Ghi giao dịch phải nằm trong transaction DB, đảm bảo atomic |
| NFR-REL-04 | Đóng app đột ngột không được làm hỏng file SQLite (bật WAL mode) |

### 4.3. Bảo mật (NFR-SEC)

| ID | Yêu cầu |
|---|---|
| NFR-SEC-01 | API key không được lưu dạng plaintext trong file config hay database |
| NFR-SEC-02 | Backend chỉ bind vào `127.0.0.1`, không mở ra mạng ngoài |
| NFR-SEC-03 | Port backend chọn ngẫu nhiên trong khoảng động, truyền cho frontend qua IPC |
| NFR-SEC-04 | Backend yêu cầu một shared token sinh ngẫu nhiên mỗi lần khởi động, gửi kèm mọi request từ frontend |
| NFR-SEC-05 | Tùy chọn mã hóa file database bằng SQLCipher với mật khẩu người dùng đặt |

### 4.4. Khả năng bảo trì (NFR-MNT)

| ID | Yêu cầu |
|---|---|
| NFR-MNT-01 | Code tuân thủ kiến trúc phân lớp mô tả ở `04-ARCHITECTURE.md`, không gọi tắt xuyên lớp |
| NFR-MNT-02 | Unit test coverage cho package `application` và `domain` ≥ 75% |
| NFR-MNT-03 | Mọi thay đổi schema phải qua Flyway migration có đánh số |
| NFR-MNT-04 | Log ứng dụng ghi ra file, xoay vòng theo ngày, giữ 14 ngày |

### 4.5. Khả dụng (NFR-USE)

| ID | Yêu cầu |
|---|---|
| NFR-USE-01 | Mọi thao tác chính có phím tắt bàn phím |
| NFR-USE-02 | Toàn bộ nội dung UI bằng tiếng Việt, định dạng số theo chuẩn Việt Nam (dấu chấm phân cách nghìn) |
| NFR-USE-03 | Thông báo lỗi phải nêu rõ nguyên nhân và gợi ý cách xử lý, không hiện stack trace |
| NFR-USE-04 | Thao tác xóa phải có xác nhận và có thể hoàn tác trong 5 giây (toast "Hoàn tác") |

---

## 5. Ma trận truy vết yêu cầu → phase

| Phase | Nhóm requirement |
|---|---|
| 0 | Hạ tầng + FR-SYS-06 |
| 1 | FR-TSK-01→12, FR-PRJ-01→04 |
| 2 | FR-CAL-01→11, FR-TSK-13, FR-SYS-07 |
| 3 | FR-FIN-01→13, FR-SYS-01 |
| 4 | FR-AI-01→10, FR-AI-12, FR-SYS-09 |
| 5 | FR-AI-11, FR-FIN-14→15, FR-SYS-02→05 |
| 6 | FR-SYS-08, đóng gói và phát hành |
