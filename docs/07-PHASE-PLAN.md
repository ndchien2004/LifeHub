# 07 — Phase Plan (File điều phối chính)

> **Agent:** Đây là file bạn quay lại đọc mỗi khi nhận lệnh `START PHASE <n>`.
> Luật dừng và mẫu báo cáo nằm ở `AGENTS.md` §2.

---

## Tổng quan

| Phase | Tên | Kết quả bàn giao | Ước lượng |
|---|---|---|---|
| 0 | Hạ tầng & khung sườn | App chạy được, hiện "Hello", DB kết nối OK | 1–2 ngày |
| 1 | Task & Project | Quản lý công việc dùng được hàng ngày | 3–4 ngày |
| 2 | Calendar & Reminder | Lịch + thông báo hệ điều hành | 3–4 ngày |
| 3 | Finance | Sổ thu chi + ngân sách + biểu đồ | 4–5 ngày |
| 4 | AI Layer | Nhập liệu bằng ngôn ngữ tự nhiên | 3–4 ngày |
| 5 | Insight, Report, Import | Báo cáo, export, backup | 3–4 ngày |
| 6 | Đóng gói & phát hành | File cài đặt .exe / .dmg | 2–3 ngày |

Mỗi phase là một lát cắt dọc: đủ backend + frontend + test để user bấm vào dùng thật, không phải bản demo giả.

---

## PHASE 0 — Hạ tầng & khung sườn

### Truy vết yêu cầu
FR-SYS-06 (theme sáng/tối). Các FR khác: không có — phase này là hạ tầng.

### Mục tiêu
Dựng bộ khung chạy được từ đầu đến cuối: Electron mở cửa sổ → spawn backend Java → React gọi được API → đọc ghi được SQLite.

### Phạm vi

**Backend**
- Khởi tạo Maven project, Spring Boot 3.3, Java 21
- Cấu hình SQLite + Hibernate community dialect + Flyway
- `V1__init_core.sql`: bảng `setting`, các pragma bắt buộc (`03-DATA-MODEL.md` §5)
- `BaseEntity` với id UUID v7, `created_at`, `updated_at`
- `GlobalExceptionHandler` + `ApiResponse` + bảng `ErrorCode` (`04-ARCHITECTURE.md` §8)
- Filter kiểm `X-App-Token`
- Endpoint `/actuator/health` và `/api/v1/bootstrap` (chỉ trả settings)
- Cấu hình logging ra file, xoay vòng theo ngày

**Frontend**
- Vite + React 18 + TS + Tailwind + shadcn/ui
- `apiClient.ts` tự động gắn header token
- TanStack Query provider
- Layout khung: sidebar + main area
- Trang tạm hiển thị kết quả gọi `/bootstrap`
- Theme sáng/tối (FR-SYS-06) — lưu lựa chọn trong `localStorage` của renderer.
  Phase 4 sẽ chuyển sang `setting/app.theme` khi có endpoint `/settings`

**Electron**
- `main.ts`: tìm port trống, sinh token, spawn tiến trình Java
- `backendManager.ts`: health check polling, retry khi crash (NFR-REL-02)
- `preload.ts` với `contextIsolation: true`
- Splash screen trong lúc chờ backend
- IPC `app:get-backend-info`

**Hạ tầng chung**
- `.gitignore`, `README.md`, `PROGRESS.md`
- Script `npm run dev` chạy song song cả 3 tiến trình
- Cấu trúc thư mục đúng `04-ARCHITECTURE.md` §4

### Tiêu chí chấp nhận
- [ ] `npm run dev` khởi động app, cửa sổ hiện lên
- [ ] Splash screen chuyển sang giao diện chính trong ≤ 6 giây (NFR-PERF-01)
- [ ] Giao diện hiển thị dữ liệu lấy từ `/bootstrap`
- [ ] File `data/lifehub.db` được tạo, có bảng `flyway_schema_history` và `setting`
- [ ] Gọi API thiếu header token → trả 401
- [ ] Kill tiến trình Java bằng tay → Electron tự spawn lại, có toast báo
- [ ] Chuyển theme sáng/tối hoạt động
- [ ] `logs/lifehub.log` có nội dung

### Test
- Unit: `ErrorCode` mapping, token filter
- Integration: `@SpringBootTest` khởi động context với SQLite in-memory, gọi `/bootstrap` trả 200
- Manual: `docs/uat/phase-0-UAT.md`

---

## PHASE 1 — Task & Project

### Mục tiêu
Module quản lý công việc hoàn chỉnh, user dùng được thật ngay sau phase này.

### Truy vết yêu cầu
FR-TSK-01 → FR-TSK-12, FR-PRJ-01 → FR-PRJ-04

### Phạm vi

**Backend**
- `V2__task_module.sql`: `project`, `task`, `tag`, `task_tag` + toàn bộ index
- Domain: `Task`, `Project`, `Tag`, enum `TaskStatus`, `Priority`
- Repository interface ở `domain`, implement JPA ở `infrastructure`
- `TaskService` (ghi) và `TaskQueryService` (đọc, có lọc động bằng Specification)
- `TaskController`, `ProjectController`, `TagController` theo `06-API-SPEC.md` §3–4
- Ràng buộc nghiệp vụ: subtask không có subtask, ghi `completed_at` khi chuyển DONE
- Soft delete + restore

**Frontend**
- Màn hình Tasks: chuyển đổi giữa List view và Kanban view
- Kanban kéo thả đổi trạng thái (dnd-kit), gọi `PATCH /tasks/{id}/status`
- Form tạo/sửa task (React Hook Form + Zod)
- Thanh lọc: project, tag, trạng thái, độ ưu tiên, khoảng ngày
- Ô tìm kiếm có debounce
- Màn hình Projects với thanh tiến độ
- Quản lý tag (chọn màu)
- Task quá hạn hiển thị màu đỏ + icon (FR-TSK-12)
- Toast "Hoàn tác" 5 giây sau khi xóa (NFR-USE-04)
- Phím tắt: `N` tạo task, `Ctrl+Enter` lưu, `Esc` đóng, `/` focus ô tìm kiếm

### Tiêu chí chấp nhận
- [ ] CRUD đầy đủ cho task, project, tag
- [ ] Kéo thả trên Kanban đổi trạng thái và lưu vào DB
- [ ] Lọc kết hợp nhiều điều kiện cùng lúc cho kết quả đúng
- [ ] Task có `due_at` trong quá khứ và chưa DONE thì hiển thị nổi bật
- [ ] Xóa task rồi bấm "Hoàn tác" trong 5 giây thì task quay lại
- [ ] Tạo subtask được, nhưng không tạo được subtask của subtask
- [ ] Đổi trạng thái sang DONE thì `completed_at` được ghi
- [ ] Xóa project thì task bên trong vẫn còn, `project_id` thành null
- [ ] Danh sách 1.000 task load ≤ 500 ms

### Test
- Unit: `TaskService` (chuyển trạng thái, ràng buộc subtask), `TaskSpecification` (lọc)
- Integration: đủ luồng CRUD qua MockMvc, kiểm tra ràng buộc DB
- Frontend: Vitest cho form validation, hook lọc
- Coverage tối thiểu 75% ở `application` và `domain`
- Manual: `docs/uat/phase-1-UAT.md`

---

## PHASE 2 — Calendar & Reminder

### Mục tiêu
Lịch có sự kiện lặp lại và thông báo hệ điều hành hoạt động thật.

### Truy vết yêu cầu
FR-CAL-01 → FR-CAL-11, FR-TSK-13, FR-SYS-07

### Phạm vi

**Backend**
- `V3__calendar_module.sql`: `event`, `event_exception`, `reminder`
- `RecurrenceExpander` dùng ical4j, có giới hạn 500 instance (SD-04)
- `EventService` với 3 scope sửa: THIS_ONLY / THIS_AND_FOLLOWING / ALL (SD-05)
- `ReminderService`: sinh reminder cho các instance trong 90 ngày tới
- `ReminderScheduler` chạy `@Scheduled(fixedRate = 30000)` (SD-03)
- `ReminderSseEmitter`: endpoint SSE `/events/stream`
- Endpoint reminder bị lỡ, snooze, dismiss
- Phát hiện xung đột lịch

**Frontend**
- Lịch 3 chế độ: tháng / tuần / ngày
- Form event có bộ dựng RRULE trực quan, hiện mô tả tiếng Việt
- Dialog chọn scope khi sửa instance của chuỗi lặp
- Cấu hình reminder trong form event
- Modal "Bạn đã bỏ lỡ N nhắc hẹn" khi khởi động (UC-05)
- Task có due date hiển thị trên lịch, kiểu dáng khác event
- Cảnh báo xung đột lịch

**Electron**
- Nhận SSE, hiển thị native notification có 2 nút hành động
- System tray: icon, menu chuột phải, đóng cửa sổ thì thu nhỏ xuống tray (FR-SYS-07)
- Xử lý click và action trên notification
- Phát hiện quyền notification bị chặn, fallback in-app toast

### Tiêu chí chấp nhận
- [ ] Tạo event lặp hàng tuần, các instance hiển thị đúng trên lịch tháng
- [ ] Sửa một instance với scope THIS_ONLY chỉ đổi instance đó
- [ ] Sửa với scope THIS_AND_FOLLOWING cắt chuỗi cũ và tạo chuỗi mới đúng
- [ ] Xóa một instance tạo bản ghi exception cancelled, các instance khác còn nguyên
- [ ] Đặt reminder trước 1 phút, đóng cửa sổ xuống tray, notification vẫn hiện
- [ ] Bấm "Hoãn 10 phút" tạo reminder mới đúng thời điểm
- [ ] Tắt app, để reminder quá hạn, mở lại thì hiện modal reminder bị lỡ
- [ ] Độ trễ notification so với giờ hẹn ≤ 30 giây
- [ ] Lịch tháng có 200 instance render ≤ 500 ms
- [ ] Bấm nút X thu nhỏ xuống tray thay vì thoát

### Test
- Unit: `RecurrenceExpander` với các RRULE điển hình và biên (DAILY INTERVAL=3, WEEKLY BYDAY=MO,WE, MONTHLY BYMONTHDAY=31 với tháng 2, UNTIL, COUNT), logic split series
- Integration: luồng tạo event lặp → query theo khoảng → có exception
- Test scheduler: dùng clock giả, xác nhận reminder được bắn đúng thời điểm
- Manual: `docs/uat/phase-2-UAT.md`

---

## PHASE 3 — Finance

### Mục tiêu
Sổ thu chi hoàn chỉnh với ngân sách và biểu đồ.

### Truy vết yêu cầu
FR-FIN-01 → FR-FIN-13, FR-SYS-01

### Phạm vi

**Backend**
- `V4__finance_module.sql` (gồm cả `recurring_rule`) + `V5__seed_categories.sql`
- Value Object `Money` bọc `long`, cấm khởi tạo từ `double`
- `WalletBalanceCalculator` tính động theo công thức ở `03-DATA-MODEL.md` §2.6
- `TransactionService` theo đúng SD-01
- `BudgetService`: tính chu kỳ hiện tại theo period, ngưỡng cảnh báo 80% và 100%
- `TransactionQueryService` với summary groupBy
- Giao dịch định kỳ: `RecurringRule` + job sinh giao dịch (FR-FIN-13)
- Endpoint dashboard đầy đủ

**Frontend**
- Màn hình Transactions: danh sách nhóm theo ngày, có infinite scroll
- Form giao dịch, ô số tiền tự định dạng dấu chấm phân cách nghìn khi gõ
- Form đổi giao diện khi chọn TRANSFER
- Quản lý ví, hiển thị số dư và tổng tài sản
- Quản lý danh mục dạng cây 2 cấp, chọn icon và màu
- Màn hình ngân sách: thanh tiến độ, đổi màu theo ngưỡng
- Biểu đồ tròn cơ cấu chi tiêu (Recharts)
- Biểu đồ đường xu hướng thu/chi
- Bộ lọc nâng cao
- Dashboard: task hôm nay, event sắp tới, chi tiêu tháng, cảnh báo ngân sách

### Tiêu chí chấp nhận
- [ ] Tạo giao dịch chi, số dư ví giảm đúng số tiền
- [ ] Tạo giao dịch chuyển khoản, ví nguồn giảm và ví đích tăng
- [ ] Không tạo được giao dịch chuyển khoản với cùng một ví
- [ ] Sửa số tiền giao dịch, số dư tính lại đúng
- [ ] Xóa giao dịch, số dư khôi phục đúng
- [ ] Vượt 80% ngân sách hiện cảnh báo vàng, vượt 100% hiện cảnh báo đỏ
- [ ] Biểu đồ tròn khớp với tổng trong danh sách giao dịch cùng khoảng thời gian
- [ ] Ô nhập tiền hiển thị `1.500.000` khi gõ `1500000`
- [ ] Danh sách 5.000 giao dịch load ≤ 500 ms (NFR-PERF-03)
- [ ] Số tiền không bao giờ có sai số thập phân (kiểm bằng test cộng 1.000 giao dịch lẻ)

### Test
- Unit: `Money` (không nhận double, không âm), `WalletBalanceCalculator` (mọi tổ hợp loại giao dịch), `BudgetService` (tính chu kỳ cho WEEKLY/MONTHLY/YEARLY, biên đầu và cuối chu kỳ)
- Integration: SD-01 đầy đủ kể cả nhánh rollback
- Test đặc biệt: tạo 1.000 giao dịch ngẫu nhiên, tổng số dư tính ra phải khớp tuyệt đối
- Manual: `docs/uat/phase-3-UAT.md`

---

## PHASE 4 — AI Layer

### Mục tiêu
Nhập liệu bằng tiếng Việt tự nhiên, có fallback hoạt động khi mất mạng.

### Truy vết yêu cầu
FR-AI-01 → FR-AI-10, FR-AI-12, FR-SYS-09

### Phạm vi

**Backend**
- `V6__ai_module.sql`: `ai_parse_log`
- Interface `AiClient` ở `domain`, implement `ClaudeAiClient` ở `infrastructure`
- `PromptBuilder` nạp template từ `resources/prompts/`
- `JsonSanitizer`: strip markdown fence, ký tự điều khiển, BOM
- Validate schema JSON (`04-ARCHITECTURE.md` §7)
- `RuleBasedParser` đầy đủ theo bảng đặc tả `04-ARCHITECTURE.md` §7
- `NlParseService` theo SD-02, bao gồm retry 1 lần và fallback
- `CategorySuggestService` với debounce phía client và cache kết quả khớp chính xác
- Ghi log mọi lần gọi, dọn log cũ hơn 90 ngày
- Endpoint `/settings` đầy đủ

**Frontend**
- Command palette `Ctrl+Space` mở ở mọi màn hình
- Form kết quả parse: trường do AI suy ra có badge, confidence thấp thì để trống + highlight
- Banner khi đang ở chế độ rule-based
- Chip gợi ý danh mục dưới ô danh mục trong form giao dịch
- Màn hình Settings: nhập API key (lưu qua IPC `secure:set-api-key`), chọn model, bật/tắt AI, tiền tệ, timezone, ngày bắt đầu tuần
- Nút "Kiểm tra kết nối"
- Màn hình xem log AI (phục vụ debug)

**Electron**
- IPC `secure:set-api-key` / `secure:has-api-key` dùng `safeStorage`
- Truyền API key cho backend qua biến môi trường lúc spawn

### Tiêu chí chấp nhận
- [ ] Gõ `ăn trưa cơm gà 45k với team` → form giao dịch điền sẵn 45.000, danh mục Ăn uống
- [ ] Gõ `1tr2 tiền nhà` → 1.200.000
- [ ] Gõ `2 triệu rưỡi` → 2.500.000
- [ ] Gõ `họp review sprint thứ 5 tuần sau 2h chiều nhắc trước 15 phút` → form event với đúng ngày, giờ và reminder
- [ ] Gõ `mua quà sinh nhật mẹ` → form task
- [ ] Ngắt mạng rồi thử lại → fallback rule-based hoạt động, có banner báo, không crash
- [ ] Nhập API key sai → thông báo lỗi rõ ràng, không hiện stack trace
- [ ] **Bấm Esc ở form kết quả → KHÔNG có bản ghi nào được tạo trong DB** (kiểm bằng query trực tiếp)
- [ ] Mọi lần gọi AI đều có bản ghi trong `ai_parse_log`
- [ ] Tắt AI trong Settings → command palette ẩn hoặc chuyển hoàn toàn sang rule-based
- [ ] API key không xuất hiện dạng plaintext trong bất kỳ file nào (grep toàn bộ `data/` và `logs/`)

### Test
- Unit: `JsonSanitizer` (JSON có fence, có text thừa trước/sau, có BOM), `RuleBasedParser` với ít nhất 30 câu tiếng Việt mẫu
- Unit: `PromptBuilder` thay biến đúng
- Integration: mock `AiClient` trả JSON hợp lệ / sai schema / timeout, xác nhận cả 3 nhánh của SD-02
- **Test bảo mật bắt buộc:** xác nhận không có đường code nào từ `NlParseService` gọi được `TransactionService.create()`
- Manual: `docs/uat/phase-4-UAT.md`

---

## PHASE 5 — Insight, Report, Import, Backup

### Mục tiêu
Hoàn thiện các tính năng bổ trợ và an toàn dữ liệu.

### Truy vết yêu cầu
FR-AI-11, FR-FIN-14, FR-FIN-15, FR-SYS-02 → FR-SYS-05

### Phạm vi

**Backend**
- `V7__insight.sql` (chỉ bảng `insight`; `recurring_rule` đã tạo ở `V4`)
- `DataAggregator` + `InsightService` theo SD-06, có fallback rule-based
- `InsightScheduler` với cron cấu hình được, chạy bù khi lỡ
- `ImportService` theo SD-07: preview, chống trùng bằng `import_hash`, commit trong transaction
- `ExcelExporter` (Apache POI): sheet giao dịch, sheet tổng hợp có công thức, biểu đồ
- `PdfExporter`: báo cáo tháng, font hỗ trợ tiếng Việt (nhúng Roboto hoặc DejaVu Sans)
- `BackupScheduler` theo SD-08, chạy trước migration
- Chức năng restore từ backup

**Frontend**
- Màn hình Insights, badge số chưa đọc trên Dashboard
- Nút đánh giá hữu ích / không hữu ích
- Wizard import CSV: chọn file → ánh xạ cột → xem trước → xử lý trùng → xác nhận
- Nút export Excel / PDF với chọn khoảng thời gian
- Màn hình quản lý backup: danh sách, tạo mới, khôi phục

### Tiêu chí chấp nhận
- [ ] Insight sinh ra từ dữ liệu thật, nội dung đúng với số liệu
- [ ] Tắt AI vẫn sinh được insight rule-based
- [ ] Import file CSV 500 dòng thành công, dòng trùng được phát hiện đúng
- [ ] Import lỗi giữa chừng thì rollback sạch, không còn dòng nào được ghi
- [ ] File CSV có dấu tiếng Việt import không bị lỗi font
- [ ] File Excel mở được, công thức tính đúng, biểu đồ hiển thị
- [ ] File PDF hiển thị tiếng Việt đầy đủ dấu, không bị ô vuông
- [ ] Backup tự động tạo khi mở app lần đầu trong ngày
- [ ] Chỉ giữ đúng 30 file backup gần nhất
- [ ] Restore từ backup khôi phục đúng dữ liệu

### Test
- Unit: `DataAggregator` (tính % thay đổi, chia cho 0 khi tuần trước không có dữ liệu), `ImportService` (parse ngày nhiều định dạng, phát hiện trùng)
- Integration: import → rollback khi lỗi, export → mở lại file kiểm tra nội dung
- Manual: `docs/uat/phase-5-UAT.md`

---

## PHASE 6 — Đóng gói & phát hành

### Mục tiêu
Tạo file cài đặt chạy được trên máy sạch, không cần cài Java.

### Truy vết yêu cầu
FR-SYS-08, CON-03, CON-04

### Phạm vi
- Tạo JRE rút gọn bằng `jlink` với đúng các module cần thiết
- Cấu hình `electron-builder`: NSIS cho Windows, DMG cho macOS
- Nhúng JRE và `backend.jar` vào resource của app
- Xử lý đường dẫn khi đóng gói (`app.asar` không chứa được file thực thi, phải dùng `asarUnpack`)
- Đường dẫn thư mục `data/` trỏ về `app.getPath('userData')` khi ở chế độ production
- Icon ứng dụng, thông tin phiên bản
- Tùy chọn khởi động cùng hệ điều hành
- Auto-update (tùy chọn, có thể bỏ ở v1.0)
- Tài liệu hướng dẫn cài đặt và sử dụng

### Tiêu chí chấp nhận
- [ ] Chạy `npm run build` tạo ra file `.exe` (Windows) hoặc `.dmg` (macOS)
- [ ] Cài trên **máy chưa từng cài Java** và chạy được
- [ ] Dung lượng installer ≤ 350 MB (CON-04)
- [ ] Dữ liệu lưu ở `userData`, không nằm trong thư mục cài đặt
- [ ] Gỡ cài đặt không xóa dữ liệu người dùng
- [ ] Bật tùy chọn khởi động cùng hệ điều hành hoạt động
- [ ] RAM khi idle ≤ 600 MB (NFR-PERF-05)
- [ ] Thời gian khởi động lần đầu ≤ 6 giây

### Test
- Test thủ công trên máy ảo sạch (Windows 10 và 11)
- Kiểm tra hiệu năng bằng Task Manager
- Manual: `docs/uat/phase-6-UAT.md`

---

## Mẫu file `PROGRESS.md`

Agent cập nhật file này sau mỗi phase:

```markdown
# Tiến độ dự án LifeHub

| Phase | Trạng thái | Ngày xong | Ghi chú |
|---|---|---|---|
| 0 | ✅ Xong | 2026-09-05 | |
| 1 | 🔄 Đang làm | | |
| 2 | ⏸ Chờ | | |

## Nợ kỹ thuật
- [ ] ...

## Giả định đã đặt
- ...

## Quyết định thay đổi so với tài liệu
| Ngày | Thay đổi | Lý do | User duyệt |
|---|---|---|---|
```
