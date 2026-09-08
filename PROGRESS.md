# Tiến độ dự án LifeHub

| Phase | Trạng thái | Ngày xong | Ghi chú |
|---|---|---|---|
| 0 — Hạ tầng & khung sườn | ✅ Xong | 2026-09-08 | 46 backend test + 14 frontend test PASS |
| 1 — Task & Project | ✅ Xong | 2026-09-08 | Đã merge vào `main`; bảng này lúc đó chưa được cập nhật |
| 2 — Calendar & Reminder | ✅ Xong | 2026-09-08 | 240 backend test + 61 frontend test PASS |
| 3 — Finance | ⏸ Chờ | | |
| 4 — AI Layer | ⏸ Chờ | | |
| 5 — Insight, Report, Import | ⏸ Chờ | | |
| 6 — Đóng gói & phát hành | ⏸ Chờ | | |

---

## Nợ kỹ thuật

- [ ] **Task lặp lại chưa có giao diện** (FR-TSK-13, ưu tiên C). Backend đầy đủ: cột `task.rrule`,
  validate quy luật, hoàn thành task lặp sinh instance kế tiếp và tiêu thụ dần `COUNT`. Form task
  chưa có ô chọn quy luật lặp — bộ dựng RRULE đã có sẵn ở `features/calendar/components/RecurrenceBuilder.tsx`,
  chỉ cần gắn vào `TaskFormDialog`. UAT Phase 2 kiểm mục này qua API (UAT-2-14).
- [ ] **Xóa chuỗi lặp với scope THIS_AND_FOLLOWING** hiện xóa cả chuỗi. `06-API-SPEC.md` §5 chỉ
  định nghĩa `DELETE /events/{id}` (cả chuỗi) và `DELETE /events/{id}/occurrences/{...}` (một lần),
  không có endpoint cắt-rồi-xóa. Hộp thoại phạm vi nói rõ từng lựa chọn làm gì nên user không bị
  bất ngờ, nhưng đây là khoảng trống thật so với FR-CAL-04.
- [ ] **`task.actual_minutes` không có chủ** (M-14) — cột nằm trong schema chốt nhưng chưa có
  FR hay use case nào dùng (ghi chú gốc: "pomodoro, phase sau"). Sẽ tạo ở `V2` đúng schema,
  không expose ra API cho tới khi có yêu cầu thật.
- [ ] **NFR-SEC-05 (mã hóa DB bằng SQLCipher)** chưa xếp vào phase nào và không tương thích với
  driver `org.xerial:sqlite-jdbc` đang chốt. Coi là ngoài phạm vi v1.0 (M-16).
- [ ] **Cảnh báo `npm audit` ở devDependencies**: `extract-zip` (qua `electron`) và `esbuild`
  (qua `vite@5`). Dependency chạy thật: 0 lỗ hổng. Không sửa vì `npm audit fix --force` sẽ nâng
  Vite/Electron vượt phiên bản đã chốt trong `04-ARCHITECTURE.md` §2.
- [ ] **`displayZone` được phân giải một lần lúc khởi động** (`TimeConfig`). Đổi `app.timezone`
  trong Settings ở Phase 4 sẽ phải làm mới bean này hoặc khởi động lại backend. Phase 2 dùng nó ở
  thêm hai chỗ (`ReminderService` dựng nội dung thông báo, `TaskService` tính mốc lặp kế tiếp) nên
  phạm vi ảnh hưởng rộng hơn trước.
- [ ] **Thời gian khởi động backend ~5,0 giây khi chạy lạnh** (đo trên máy dev, JVM chưa warm).
  NFR-PERF-01 là ≤ 6 giây tổng. Còn biên nhưng hẹp — cần đo lại ở Phase 6 với JRE rút gọn.

---

## Giả định đã đặt

### Phase 0

| # | Giả định | Lý do |
|---|---|---|
| A0-01 | Entity domain mang annotation JPA (`jakarta.persistence`), repository **interface** ở `domain` còn implement Spring Data ở `infrastructure` | `04-ARCHITECTURE.md` §3 nói domain "không phụ thuộc framework", nhưng `03-DATA-MODEL.md` §2.8 lại hướng dẫn đặt `@Table` ngay trên entity domain. Diễn giải: Jakarta Persistence là đặc tả chuẩn, không phải Spring; luật thực sự cần giữ là "controller không inject repository" và "infrastructure implement interface của domain" — cả hai đều được tuân thủ |
| A0-02 | Pragma SQLite đặt bằng tham số trên `spring.datasource.url`, **không** đặt trong `V1__init_core.sql` | Flyway bọc mỗi migration trong một transaction, mà `PRAGMA journal_mode = WAL` không chạy được bên trong transaction. Đặt trên URL còn có lợi thế áp dụng cho cả kết nối Flyway mở trước khi Spring context sẵn sàng. `SqliteConfig` đọc lại và **ném lỗi** nếu `foreign_keys` tắt |
| A0-03 | Filter token chỉ bảo vệ `/api/v1/**`; `/actuator/health` để mở | Electron poll health trong lúc khởi động như một liveness probe thuần túy, endpoint không lộ dữ liệu người dùng. Đã chốt ở mục M-5 phiên rà soát |
| A0-04 | Không truyền `app.token` thì backend **tự sinh** token ngẫu nhiên và ghi WARN ra log | Giữ endpoint luôn đóng. Không bao giờ chạy với token rỗng, nhưng vẫn cho phép chạy `java -jar` thủ công khi debug |
| A0-05 | `/bootstrap` trả đủ 4 trường ngay từ Phase 0: `settings` (có dữ liệu), `aiConfigured=false`, `missedReminders=[]`, `dashboard=null` | Phase 0 chỉ yêu cầu settings, nhưng giữ nguyên hình dạng response từ đầu để frontend không phải sửa contract ở Phase 2/3/4 |
| A0-06 | Integration test đặt tên `*IT.java` và cấu hình Surefire chạy cùng unit test | `08-TEST-PLAN.md` §7 quy định `./mvnw test` chạy toàn bộ; Surefire mặc định bỏ qua `*IT` |
| A0-07 | jsdom 25 trên Node 26 không có `localStorage`, phải shim trong `src/test/setup.ts` | Lỗi môi trường test, không phải lỗi ứng dụng — renderer Electron thật luôn có `localStorage`. Shim đặt ở tầng test để code production không phải mang workaround |
| A0-08 | Mỗi integration test class dùng một **file** SQLite tạm riêng (`TestDatabase.freshUrl()`) | Xem mục B-3 bên dưới |
| A0-09 | Backend cấu hình CORS cho `/api/v1/**` với origin `http://127.0.0.1:5173`, `http://localhost:5173` và `null` (trang `file://` của bản đóng gói); filter token **miễn trừ request preflight** | Renderer và backend luôn khác cổng nên mọi lời gọi đều là cross-origin, và header `X-App-Token` khiến trình duyệt gửi preflight `OPTIONS` trước. Theo đặc tả, preflight không mang header tùy chỉnh, nên nếu filter chặn nó thì request thật không bao giờ được gửi. Tài liệu không nhắc tới CORS ở đâu cả. Token vẫn là rào chắn thật: preflight chỉ lộ chính sách CORS, không lộ dữ liệu |
| A0-10 | Mỗi lần backend restart sinh **port và token mới**, đẩy xuống renderer qua `app:backend-restarted` | Port cũ có thể còn ở TIME_WAIT; token gắn với vòng đời tiến trình nó xác thực nên không có lý do sống lâu hơn tiến trình đó |
| A0-11 | Vite dev server ghim vào `127.0.0.1` | Mặc định trên Windows Vite chỉ bind `[::1]` (IPv6), khiến `wait-on`, Electron loader và `connect-src` trong CSP — đều dùng 127.0.0.1 — không kết nối được |

### Phase 2

| # | Giả định | Lý do |
|---|---|---|
| A2-01 | Reminder **không lưu** instance nào nó thuộc về; giá trị đó suy ra bằng `trigger_at + offset_minutes` | `03-DATA-MODEL.md` §2.5 không có cột cho việc này, nhưng một event lặp cần một reminder cho mỗi lần lặp, và thông báo phải nêu đúng giờ của lần đó. Phép suy là chính xác tuyệt đối vì `trigger_at` vốn được tính từ đúng hai đại lượng ấy. Nhờ vậy schema đã chốt không phải thêm cột |
| A2-02 | Khi hoãn, reminder mới **tính lại** `offset_minutes` theo mốc sự kiện gốc, và thời điểm bắn được làm tròn về phút gần nhất | Giữ nguyên offset cũ sẽ khiến thông báo nêu giờ họp trôi thêm đúng bằng thời gian hoãn sau mỗi lần. `offset_minutes` là số nguyên phút nên phải làm tròn — lệch dưới 30 giây so với yêu cầu, vẫn nằm trong chính chu kỳ quét 30 giây của scheduler (NFR-PERF-06). Offset âm là hợp lệ khi sự kiện đã bắt đầu |
| A2-03 | Danh sách "khoảng nhắc đã cấu hình" của một event = các `offset_minutes` **thuộc sáu giá trị FR-CAL-06 cho phép** | Bản ghi sinh ra do hoãn mang offset lẻ (ví dụ 1437 phút) nên tự động bị loại; nếu không, form sửa event sẽ hiện thêm một mục nhắc mà user chưa từng chọn |
| A2-04 | `GET /events` trả **một danh sách phẳng** gồm cả event và hạn chót task, phân biệt bằng trường `kind` | `06-API-SPEC.md` §5 mô tả endpoint là "danh sách instance" và có tham số `includeTasks`, nhưng mẫu response chỉ vẽ hình dạng của event. Thêm `kind` là bổ sung thuần túy, giữ nguyên mọi trường đã đặc tả, và chính nó là tín hiệu để lịch vẽ task khác kiểu theo FR-CAL-10 |
| A2-05 | Thêm hai trường bổ sung vào item của `GET /events`: `hasConflict` (FR-CAL-11) và `description` | Cảnh báo xung đột là yêu cầu bắt buộc nhưng đặc tả response không có chỗ mang thông tin đó; tính lại ở frontend sẽ sai vì frontend chỉ thấy đúng cửa sổ đang mở |
| A2-06 | `GET /events?from=..&to=..` giới hạn cửa sổ tối đa **400 ngày** | Không có giới hạn thì một request duy nhất có thể bung hàng chục nghìn instance. 400 ngày đủ rộng cho mọi chế độ xem của FR-CAL-02 |
| A2-07 | Reminder được sinh sẵn cho các instance trong **90 ngày tới** và làm mới mỗi giờ (kèm một lần lúc khởi động) | Phase plan chốt chân trời 90 ngày nhưng không nói ai đẩy nó đi tiếp. Không có job này, một chuỗi hàng tuần sẽ lặng lẽ ngừng nhắc sau ba tháng vì bản ghi đơn giản là không tồn tại |
| A2-08 | `@EnableScheduling` bị tắt ở profile `test`; các scheduler vẫn là bean và test gọi thẳng với `Clock` cố định | Nếu để bật, một nhịp quét 30 giây có thể chen vào giữa một assertion và đổi dữ liệu bên dưới nó |
| A2-09 | Electron main giữ kết nối SSE và bắn native notification; renderer chỉ nhận bản sao qua IPC | Cửa sổ có thể đang thu nhỏ dưới tray đúng lúc reminder tới — chính là tình huống FR-CAL-07 nhắm tới — nên renderer không thể là nơi chịu trách nhiệm hiển thị |
| A2-10 | Nút "Hoãn"/"Đã xong" trên notification gọi thẳng REST từ main process, không đi qua renderer | Cùng lý do A2-09: renderer có thể không tồn tại tại thời điểm user bấm |
| A2-11 | Kênh `notification:permission` trả `granted`/`denied` dựa trên `Notification.isSupported()` | Electron không expose trạng thái quyền thật của hệ điều hành. Điều renderer cần quyết định là "có nên hiện toast thay thế không", và tín hiệu này đủ để trả lời |
| A2-12 | Đóng cửa sổ **ẩn** xuống tray; chỉ menu tray → Thoát hoặc lệnh thoát của hệ điều hành mới kết thúc tiến trình | FR-SYS-07. `before-quit` cũng đặt cờ thoát để Cmd+Q và lệnh tắt máy không bị kẹt vô hạn |
| A2-13 | Lịch coi tuần bắt đầu từ **thứ 2** | Đúng mặc định `app.week_start = MONDAY` ở `03-DATA-MODEL.md` §2.11. Phase 4 sẽ đọc từ setting |
| A2-14 | Sự kiện `all_day` không tính vào phát hiện xung đột | Một ngày nghỉ lễ chồng lên mọi cuộc họp trong ngày là đúng về mặt thời gian nhưng vô nghĩa với user, và sẽ khiến cảnh báo xung đột kêu liên tục |
| A2-15 | Hoàn thành task lặp sinh instance kế tiếp và **giảm `COUNT` đi một**; task đã xong giữ nguyên làm bản ghi của lần chạy đó | FR-TSK-13 không nói `COUNT` xử lý thế nào. Giữ nguyên `COUNT` sẽ biến "lặp 3 lần" thành chuỗi vô tận |

---

## Quyết định thay đổi so với tài liệu

| Ngày | Mã | Thay đổi | Lý do | User duyệt |
|---|---|---|---|---|
| 2026-09-08 | B-1 | `recurring_rule` chuyển từ `V7` sang `V4__finance_module.sql`; `V7__insight_recurring.sql` đổi tên thành `V7__insight.sql` | `transaction.recurring_rule_id` là FK tạo trong `V4` (Phase 3) và FR-FIN-13 thuộc Phase 3 — với `PRAGMA foreign_keys = ON`, `V4` sẽ migrate thất bại | ✅ |
| 2026-09-08 | B-2 | Backup chạy qua Flyway callback `beforeMigrate` / `FlywayMigrationStrategy`, **không** qua `onApplicationReady` như SD-08 vẽ | Flyway chạy lúc khởi tạo context, trước `ApplicationReadyEvent`. Giữ đúng ý đồ "backup TRƯỚC migration", chỉ đổi cơ chế kích hoạt. Áp dụng ở Phase 5 | ✅ |
| 2026-09-08 | B-3 | Test dùng file SQLite tạm riêng cho mỗi class, thay cho `file::memory:?cache=shared` trong `08-TEST-PLAN.md` §2 | `cache=shared` là **một** database dùng chung cho cả JVM, mâu thuẫn trực tiếp với quy tắc "mỗi test class dùng database riêng biệt" ngay bên dưới nó. File thật còn kiểm luôn được đường đi của WAL và pragma | ✅ |
| 2026-09-08 | B-4 | Thêm `UNAUTHORIZED` → HTTP 401 vào bảng mã lỗi `04-ARCHITECTURE.md` §8 | T0-03 yêu cầu mã này, bảng gốc thiếu | ✅ |
| 2026-09-08 | B-5 | Nút hành động trên notification: implement đầy đủ endpoint + logic, nhưng UAT Phase 2 kiểm qua toast trong app | `actions` của Electron `Notification` chỉ chạy trên macOS; Windows cần `toastXml` + AppUserModelID đã đăng ký, không hoạt động ở chế độ dev chưa đóng gói. Xem lại ở Phase 6 | ✅ |
| 2026-09-08 | C-1 | FR-SYS-06 (theme) chuyển từ Phase 1 sang **Phase 0**; sửa ma trận truy vết `01-SRS.md` §5 | Phase plan vốn đã đặt việc này trong phạm vi Phase 0 và có tiêu chí chấp nhận cho nó, nhưng ma trận SRS lại ghi Phase 1 | ✅ |
| 2026-09-08 | C-2 | Phase 0 lưu lựa chọn theme trong `localStorage`; Phase 4 chuyển sang `setting/app.theme` | Endpoint `/settings` thuộc Phase 4, không kéo sớm chỉ để lưu một giá trị | ✅ |
| 2026-09-08 | C-16 | Mở rộng cấu trúc thư mục chốt: thêm `api/system`, `application/system`, `domain/system`, `application/report/DataAggregator`, `api/common/AppTokenFilter`, `domain/common/IdGenerator` | Cấu trúc gốc không có chỗ cho `ImportService`, `BackupService`, `SettingsController`, `DataAggregator` và các entity `Setting`/`Insight`/`RecurringRule` — đều được nhắc tới ở tài liệu khác | ✅ |
| 2026-09-08 | M-1 | `AGENTS.md` chuyển từ `docs/` ra thư mục gốc | Đúng như `README.md` và `04-ARCHITECTURE.md` §4 mô tả, và là nơi agent tự động đọc | ✅ |
| 2026-09-08 | M-3 | Thêm `com.fasterxml.uuid:java-uuid-generator` 5.1.0 vào tech stack | JDK không có bộ sinh UUID v7; T0-07 yêu cầu id có thứ tự theo thời gian | ✅ |
| 2026-09-08 | M-4 | Thêm `spring-boot-starter-actuator` vào tech stack | `/actuator/health` là tiêu chí chấp nhận của Phase 0 nhưng chưa có trong bảng stack | ✅ |
| 2026-09-08 | D-1 | Thư viện RRULE chốt là `org.mnode.ical4j:ical4j` 4.0.8 | `04-ARCHITECTURE.md` §2 ghi groupId là `com.github.ical4j`, không tồn tại trên Maven Central. Đây là cùng một thư viện, chỉ đúng tọa độ. Bản 4.x dùng `java.time` nên không phải chuyển đổi qua lớp ngày giờ riêng của ical4j | ✅ |
| 2026-09-08 | D-2 | `Patch<T>` chuyển từ `TaskCommands` sang `domain/common/Patch.java`; thêm `api/common/JsonPatchReader` | Module calendar cũng cần cả hai. Để nguyên chỗ cũ thì `application.calendar` phải phụ thuộc `application.task`, và ba controller sẽ mang ba bản sao của cùng một đoạn đọc JSON. `domain/common` đã có trong cấu trúc chốt nên không phát sinh thư mục mới | ✅ |

> Các mục C-3 → C-15, C-17 và M-5 → M-22 đã được duyệt và ghi trong phụ lục §6 của
> `03-DATA-MODEL.md` (sửa đổi schema) hoặc áp dụng trực tiếp ở phase tương ứng. Chúng sẽ được
> ghi lại vào bảng trên khi phase đó thực sự chạm tới.
