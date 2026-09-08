# LifeHub

Ứng dụng desktop offline-first quản lý công việc, lịch hẹn và chi tiêu cá nhân,
có lớp AI hỗ trợ nhập liệu bằng tiếng Việt tự nhiên.

Dữ liệu lưu hoàn toàn trên máy trong một file SQLite. Chỉ nội dung gửi cho AI parse
mới rời khỏi thiết bị.

---

## Yêu cầu môi trường

| Thành phần | Phiên bản | Ghi chú |
|---|---|---|
| JDK | 21 (LTS) | `JAVA_HOME` phải trỏ đúng vào JDK 21 |
| Node.js | 20 trở lên | |
| Maven | không cần cài | dùng Maven Wrapper `backend/mvnw` |

---

## Chạy ứng dụng

```bash
npm install     # lần đầu; tự cài luôn cho frontend
npm run dev
```

`npm run dev` chạy tuần tự: build `backend/target/lifehub-backend.jar` → khởi động Vite
dev server ở cổng 5173 → mở Electron. Electron tự tìm một cổng trống trong khoảng
49152–65535, sinh token ngẫu nhiên cho phiên đó, rồi spawn tiến trình Java.

---

## Các lệnh khác

| Lệnh | Việc |
|---|---|
| `npm test` | Chạy toàn bộ test backend và frontend |
| `npm run backend:test` | Chỉ test backend (JUnit 5 + MockMvc) |
| `npm run frontend:test` | Chỉ test frontend (Vitest) |
| `npm run typecheck` | Kiểm tra kiểu TypeScript cho cả renderer và Electron |
| `npm run build` | Build cả 3 tiến trình ở chế độ production |
| `npm run backend:build` | Chỉ build lại jar backend |

Báo cáo coverage backend: `backend/target/site/jacoco/index.html`

---

## Cấu trúc thư mục

```
LifeHub/
├─ AGENTS.md          giao thức thực thi cho AI agent — đọc đầu tiên
├─ PROGRESS.md        tiến độ, giả định đã đặt, quyết định sai lệch so với tài liệu
├─ docs/              đặc tả 01→08 và kịch bản UAT từng phase
├─ backend/           Spring Boot 3.3 / Java 21 — api, application, domain, infrastructure
├─ frontend/          React 18 + TypeScript + Vite + Tailwind + shadcn/ui
├─ electron/          main process, preload, quản lý vòng đời tiến trình backend
├─ data/              (không commit) lifehub.db và backups/
└─ logs/              (không commit) lifehub.log, xoay vòng theo ngày
```

---

## Trạng thái hiện tại

**Phase 0 — Hạ tầng & khung sườn: xong.**
**Phase 1 — Task & Project: xong.**
**Phase 2 — Calendar & Reminder: xong.**
**Phase 3 — Finance: xong.**

Đang chạy được:

- **Hạ tầng** — Electron spawn backend, health check, splash screen, tự khởi động lại khi
  backend chết, SQLite + Flyway, filter token, `/bootstrap`, theme sáng/tối, log ra file.
- **Công việc** — CRUD task/project/nhãn, danh sách và Kanban kéo thả, lọc và tìm kiếm,
  subtask một cấp, xóa mềm kèm hoàn tác.
- **Lịch** — xem theo tháng/tuần/ngày, sự kiện lặp theo RRULE với bộ dựng trực quan, sửa hoặc
  xóa một lần riêng lẻ trong chuỗi, cảnh báo trùng giờ, task đến hạn hiển thị ngay trên lịch.
- **Nhắc hẹn** — thông báo hệ điều hành đẩy qua SSE, hoãn và tắt ngay trên thông báo, modal
  tổng hợp nhắc hẹn bị lỡ khi mở lại app, thu nhỏ xuống system tray thay vì thoát.
- **Tài chính** — sổ thu chi với ví, danh mục 2 cấp tiếng Việt tạo sẵn, chuyển khoản giữa hai ví,
  số dư ví tính động từ sổ nên không bao giờ lệch, ngân sách theo tuần/tháng/năm có cảnh báo ở
  mốc 80% và 100%, biểu đồ tròn cơ cấu chi tiêu và biểu đồ đường xu hướng thu/chi, bộ lọc nâng
  cao, giao dịch định kỳ tự sinh và bù các lần bị lỡ.
- **Tổng quan** — dashboard gộp việc hôm nay, việc quá hạn, sự kiện sắp tới, thu chi tháng này
  và các ngân sách sắp vượt.

Xem `PROGRESS.md` để biết tiến độ đầy đủ và `docs/07-PHASE-PLAN.md` để biết phase kế tiếp.

---

## Quy trình làm việc với AI agent

Agent đọc `AGENTS.md` trước tiên và làm việc theo **phase**, mỗi lần đúng một phase.
Xong một phase thì dừng lại, không tự chạy tiếp.

```
START PHASE <n>              bắt đầu phase n
FIX PHASE <n>: <mô tả lỗi>   sửa lỗi trong phase n
REDO PHASE <n>               làm lại phase n từ đầu
```

Sau mỗi phase, test thủ công theo `docs/uat/phase-<n>-UAT.md` rồi ra lệnh tiếp theo.
