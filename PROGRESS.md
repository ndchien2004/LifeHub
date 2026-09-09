# Tiến độ dự án LifeHub

| Phase | Trạng thái | Ngày xong | Ghi chú |
|---|---|---|---|
| 0 — Hạ tầng & khung sườn | ✅ Xong | 2026-09-08 | 46 backend test + 14 frontend test PASS |
| 1 — Task & Project | ✅ Xong | 2026-09-08 | Đã merge vào `main`; bảng này lúc đó chưa được cập nhật |
| 2 — Calendar & Reminder | ✅ Xong | 2026-09-08 | 240 backend test + 61 frontend test PASS |
| 3 — Finance | ✅ Xong | 2026-09-08 | 304 backend test + 85 frontend test PASS. Đã sửa lỗi ngân sách trên danh mục con (xem "Lỗi đã sửa sau bàn giao") |
| 4 — AI Layer | ✅ Xong | 2026-09-09 | 438 backend test + 107 frontend test PASS |
| 5 — Insight, Report, Import | ⏸ Chờ | | |
| 6 — Đóng gói & phát hành | ⏸ Chờ | | |

---

## Lỗi đã sửa sau bàn giao

### Phase 4

| Ngày | Triệu chứng | Nguyên nhân | Cách sửa |
|---|---|---|---|
| 2026-09-09 | Lưu API key, xóa API key hoặc đổi múi giờ có thể để lại **hai** tiến trình backend cùng chạy, hai JVM cùng ghi `data/lifehub.db` | `BackendManager.restartNow()` hạ cờ `shuttingDown` ngay trong cùng tick với `child.kill()`. Sự kiện `exit` chỉ tới ở tick sau, lúc đó `handleExit()` thấy cờ đã hạ và mã thoát khác 0 (trên Windows `kill()` là `TerminateProcess`, mã thoát 1 chứ không phải 0) nên tưởng backend vừa crash và cho chạy chính sách khởi động lại — spawn thêm một backend thứ hai trên port khác. Đường này chạy mỗi lần user dùng đúng ba tính năng Phase 4 vừa thêm | Tách `stopAndWait()`: giữ cờ `shuttingDown` bật suốt thời gian chờ, lắng nghe `exit` một lần rồi mới hạ cờ và gọi `start()`. Có hạn chờ 5 giây kèm `SIGKILL` để không treo màn hình Cài đặt nếu tiến trình cũ không chịu thoát. Thêm bộ test đầu tiên cho `electron/` (`electron/backendManager.test.ts`, 8 case) — đã kiểm chứng test **thất bại** đúng lỗi cũ khi gỡ bản vá: `spawn` bị gọi 3 lần thay vì 2 |
| 2026-09-09 | `SettingService` lưu giá trị chưa cắt khoảng trắng | `validate()` cắt trước khi kiểm, nhưng `write()` lưu chuỗi gốc. `" DARK "` qua được validate rồi lưu nguyên, làm mọi phép so sánh `equals("DARK")` phía sau trượt — kể cả phép so sánh `useThemeSync.ts` dùng để khôi phục giao diện lúc khởi động | `write()` lưu `value.trim()`. Thêm test `SettingsApiIT.trimsValuesBeforeStoringThem`, đã kiểm chứng thất bại khi gỡ bản vá |

### Phase 3

| Ngày | Triệu chứng | Nguyên nhân | Cách sửa |
|---|---|---|---|
| 2026-09-08 | Tạo ngân sách cho **danh mục con** trả 500 "Đã xảy ra lỗi không mong muốn"; thử lại lần hai thì báo CONFLICT "Danh mục này đã có ngân sách cùng chu kỳ" | `LazyInitializationException` khi map response: `FinanceMapper.toRef` đọc tên **danh mục cha** để dựng nhãn "Ăn uống › Cà phê", nhưng không có gì nạp `category.parent` trước khi transaction đóng (`open-in-view` tắt). Bản ghi **đã commit xong** rồi mapper mới nổ — nên ngân sách thực ra đã được tạo, và lần thử thứ hai đụng ràng buộc trùng. Mọi test Phase 3 đều đặt ngân sách trên danh mục **gốc** nên không lần nào đi vào nhánh này | `BudgetService.statusOf` nạp sẵn danh mục và danh mục cha khi session còn mở (giống `TransactionWriter.hydrate`); các truy vấn của `SpringDataBudgetRepository` thêm `LEFT JOIN FETCH c.parent` và `findById` dùng truy vấn có fetch. Thêm test hồi quy `FinanceApiIT.supportsABudgetOnASubCategory` — đã kiểm chứng test này **thất bại** đúng lỗi cũ khi gỡ bản vá |
| 2026-09-08 | Khi lưu thất bại, console có unhandled promise rejection | Các dialog tài chính gọi `void submit()` / `handleSubmit(async …)` mà không bắt lỗi, trong khi `mutateAsync` reject khi request hỏng | Bọc `try/catch` ở cả 5 form tài chính: giữ dialog mở và nguyên dữ liệu đã nhập, thông báo lỗi vẫn do `onError` của mutation hiển thị. Thêm test `TransactionFormDialog` cho nhánh lưu thất bại |

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
- [x] ~~**`displayZone` được phân giải một lần lúc khởi động**~~ — xử lý ở Phase 4 (A4-11):
  `PUT /settings` trả thêm cờ `requiresRestart`, và màn hình Cài đặt gọi IPC `app:restart-backend`
  để khởi động lại tiến trình Java. Bean vẫn chỉ đọc setting một lần; điều đổi là user không còn
  phải tự đoán ra rằng cần khởi động lại.
- [ ] **Thời gian khởi động backend ~5,0 giây khi chạy lạnh** (đo trên máy dev, JVM chưa warm).
  NFR-PERF-01 là ≤ 6 giây tổng. Còn biên nhưng hẹp — cần đo lại ở Phase 6 với JRE rút gọn.
- [ ] **Bundle renderer đã vượt 500 kB** (995 kB, gzip 284 kB sau Phase 4) sau khi thêm Recharts. Với app
  desktop nạp từ `file://` thì không có chi phí mạng, nên chưa ảnh hưởng NFR-PERF-01, nhưng nên
  tách chunk cho phần biểu đồ ở Phase 6.
- [ ] **Backup trước migration chưa tự động** (AGENTS.md §3.3 mục 3). `BackupService` thuộc Phase 5
  (PROGRESS mục B-2). Ở Phase 3 đã sao lưu thủ công `data/lifehub.db` sang `data/backups/` trước
  khi chạy `V4`/`V5`, và UAT Phase 3 nhắc user làm việc tương tự.
- [ ] **Ví chưa có thao tác sắp xếp lại trên giao diện.** Cột `sort_order` có trong schema và API
  `PATCH /wallets/{id}` nhận được, nhưng màn hình Ví chưa có kéo thả — danh sách xếp theo
  `sort_order` rồi `created_at`.
- [ ] **Ngân sách 5 giây có thể quá chặt với model mặc định.** `04-ARCHITECTURE.md` §7 và UC-09 E1
  chốt timeout 5 giây cho một lần parse tương tác, còn mặc định đã chọn là `claude-opus-5` (A4-04).
  Client đặt `effort = LOW` để giảm độ trễ, nhưng trên mạng chậm vẫn có thể chạm trần và rơi xuống
  bộ luật ngoại tuyến. Đây là suy giảm êm chứ không phải lỗi — user đổi sang model nhanh hơn trong
  Cài đặt là xử lý được. Cần đo thật ở Phase 6 rồi cân nhắc nới timeout (phải xin duyệt vì con số
  nằm trong tài liệu đã chốt).

- [ ] **FR-SYS-09 còn thiếu ô "thư mục backup" trên màn hình Cài đặt.** `01-SRS.md` liệt kê năm mục
  cho màn hình này: tiền tệ, ngày bắt đầu tuần, timezone, API key, **thư mục backup**. Bốn mục đầu
  đã có ở Phase 4; mục thứ năm hoãn sang Phase 5 để đi cùng `BackupService` — backend đã nhận và
  validate `backup.dir` cùng `backup.keep_count` qua `PUT /settings`, nhưng chưa có gì đọc hai khóa
  đó, nên một ô chọn thư mục lúc này sẽ là một ô không có tác dụng gì. Đây là **khoảng trống thật**
  so với FR-SYS-09 trong phạm vi Phase 4.

- [ ] **`ai.weekly_insight_cron` chưa có ai đọc.** Khóa nằm trong danh sách setting ghi được và đã
  validate bằng `CronExpression`, nhưng `InsightScheduler` thuộc Phase 5. Cố ý: khóa đã có sẵn từ
  `V1` nên không phát sinh migration khi tới phase đó.

- [ ] **`GET /transactions/summary` gom nhóm trong bộ nhớ.** Bắt buộc vì SQLite không có hàm ngày
  giờ hiểu múi giờ (xem giả định A3-07). An toàn với cửa sổ thời gian mà giao diện đang dùng
  (tối đa 1 năm), nhưng nếu Phase 5 cần báo cáo nhiều năm thì phải tính lại bằng SQL hoặc bảng
  snapshot theo tháng như 03-DATA-MODEL.md §2.6 gợi ý.

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

### Phase 3

| # | Giả định | Lý do |
|---|---|---|
| A3-01 | `Money` là value object ở **API của entity**, còn cột `amount` / `limit_amount` map thành `long` thuần, không dùng `AttributeConverter` | Cột phải là số nguyên trần để `SUM`, `CASE` và so sánh khoảng chạy thẳng trong SQL — đó chính là thứ giúp số dư tính động và NFR-PERF-03 khả thi. Value object vẫn là kiểu duy nhất mà service, command và test chạm tới, nên ràng buộc "không âm, không số thực" (T3-01, T3-02) không hề bị nới |
| A3-02 | Chu kỳ ngân sách **bám lịch** (thứ 2 / ngày 1 / 1-1) chứ không bước từ `start_date` | `03-DATA-MODEL.md` §2.9 gọi `start_date` là "mốc bắt đầu chu kỳ đầu tiên" nhưng không nói chu kỳ sau tính thế nào. Nếu bước từ `start_date`, ngân sách tạo ngày 17 sẽ báo "chu kỳ này" cho khoảng kết thúc ngày 16 tháng sau — không khớp sao kê ngân hàng, không khớp bảng lương, cũng không khớp ô "chi tháng này" của dashboard. `start_date` giữ nguyên ý nghĩa "ngày hạn mức có hiệu lực". Mốc thứ 2 theo đúng mặc định `app.week_start` (T3-09) |
| A3-03 | Chi ở **danh mục con** tính vào ngân sách của **danh mục cha** | FR-FIN-08 không nói. Nếu không cộng, người dùng nào chịu khó phân loại 2 cấp sẽ thấy mọi ngân sách cấp cha luôn bằng 0 — tức là tính năng phân cấp và tính năng ngân sách triệt tiêu lẫn nhau. Bộ lọc `categoryIds` cũng mở rộng theo cùng quy tắc |
| A3-04 | Thêm nhóm endpoint `/recurring-rules` (GET, POST, PATCH, DELETE) và `POST /recurring-rules/run` | FR-FIN-13 thuộc phạm vi Phase 3 nhưng `06-API-SPEC.md` §7 dừng ở budget, không có endpoint nào cho giao dịch định kỳ. Đường dẫn theo đúng quy ước của các resource còn lại. `/run` để user ép chạy từ giao diện thay vì phải khởi động lại app |
| A3-05 | Tách `TransactionWriter` (có `@Transactional`) khỏi `TransactionService` (không có) | SD-01 ghi rõ bước tính lại số dư và kiểm ngân sách nằm **ngoài** transaction ghi giao dịch. Nếu cả luồng dùng chung một phương thức `@Transactional`, một lỗi lúc dựng banner cảnh báo sẽ âm thầm rollback chính bản ghi user vừa nhập — đúng hậu quả mà sơ đồ cảnh báo |
| A3-06 | Giao dịch `TRANSFER` **không tính** vào `totalIncome`, `totalExpense` và biểu đồ | Chuyển tiền giữa hai ví của chính mình không phải thu cũng không phải chi. Nếu tính, tổng hai chiều đều phồng lên và biểu đồ tròn sẽ lệch với danh sách giao dịch ngay bên cạnh — vi phạm tiêu chí chấp nhận của chính phase này |
| A3-07 | `GET /transactions/summary` gom nhóm **trong bộ nhớ**, không gom bằng SQL | Gom theo tuần/tháng nghĩa là chia theo ngày **giờ địa phương**, mà SQLite lưu mốc thời gian dưới dạng epoch milli và không có hàm ngày giờ hiểu múi giờ. Làm trong SQL đồng nghĩa với việc viết lại lịch bằng phép toán chuỗi và sai ở đúng các mốc biên. Khoảng thời gian do người gọi giới hạn nên số dòng tỉ lệ với cửa sổ đang vẽ |
| A3-08 | Response ghi giao dịch có thêm `toWalletBalance`; `GET /wallets` trả `{wallets, totalAssets}` thay vì mảng trần | `06-API-SPEC.md` §7 chỉ vẽ `walletBalance`, nhưng một giao dịch chuyển khoản đổi số dư của **hai** ví. `totalAssets` là con số màn hình Ví bắt buộc hiển thị (FR-FIN-01) và tính ở backend thì không thể lệch với danh sách. Cả hai đều là bổ sung thuần túy, không đổi trường nào đã đặc tả |
| A3-09 | `PATCH`, `DELETE` và `POST /transactions/{id}/restore` trả **cùng hình dạng** với `POST /transactions` | Đặc tả chỉ vẽ response của `POST`. Sửa hay xóa cũng làm số dư đổi đúng như khi tạo, nên trả cùng khối dữ liệu giúp giao diện dùng chung một đường xử lý thay vì ba |
| A3-10 | `wallet.initial_balance` cho phép **âm**, còn `Money` thì không | Thẻ tín dụng bắt đầu ở trạng thái đang nợ. Số dư là đại lượng có dấu; số tiền giao dịch là độ lớn, dấu nằm ở `type`. Vì vậy `initial_balance` và số dư tính ra là `long`, không bao giờ là `Money` |
| A3-11 | Ví **đầu tiên** tự động thành ví mặc định | UC-06 bước 2 yêu cầu form điền sẵn ví. Nếu không ví nào là mặc định, người dùng mới phải chọn ví thủ công ở mọi giao dịch cho tới khi họ tự phát hiện ra ô "đặt làm mặc định" |
| A3-12 | Không xóa được danh mục **đang có danh mục con** (ngoài hai lý do đã ghi ở `06-API-SPEC.md`) | Khóa ngoại là `ON DELETE RESTRICT`, nên nếu không chặn ở tầng nghiệp vụ thì user sẽ nhận lỗi ràng buộc thô thay vì một câu tiếng Việt nói rõ phải làm gì |
| A3-13 | `V5` dùng id hằng số **hình dạng UUID v7** và ghi mốc thời gian bằng epoch milli | Seed không chạy qua `IdGenerator` được. Tiền tố thời gian cố định trong quá khứ giúp danh mục hệ thống luôn đứng trước danh mục user tự tạo khi sắp theo id. Epoch milli là đúng định dạng Hibernate ghi xuống SQLite (đã kiểm chứng trên chính `data/lifehub.db`) — dùng `CURRENT_TIMESTAMP` sẽ trộn hai định dạng trong một cột |
| A3-14 | Chuỗi lặp của `recurring_rule` neo vào `template.occurredAt`, và `COUNT` dùng hết thì quy luật **tự tắt** | Bảng chỉ có sáu cột theo ERD (mục C-3), không có chỗ lưu mốc bắt đầu riêng. Neo lại vào ngày chạy gần nhất sẽ khởi động lại `COUNT` sau mỗi lần sinh và biến "lặp 6 lần" thành chuỗi vô tận. Tắt quy luật đã cạn giúp scheduler không phải xét lại một bản ghi không bao giờ chạy nữa |
| A3-15 | Job sinh giao dịch định kỳ chạy **lúc khởi động** và mỗi giờ, có bù các lần đã lỡ | Đây là app desktop, phần lớn thời gian ở trạng thái đóng — thời điểm mở app mới là cơ hội thực tế để bù một kỳ tiền nhà đã tới hạn. Nhịp mỗi giờ chỉ để phủ trường hợp app mở qua nửa đêm |
| A3-16 | `LocalDate` lưu dạng chuỗi ISO qua `LocalDateConverter` (`autoApply`) | Áp dụng mục M-21. Driver mặc định ghi giá trị thời gian thành epoch milli, vô nghĩa với một giá trị không có giờ và không có múi giờ. Chuỗi ISO sắp xếp đúng thứ tự nên truy vấn khoảng trên `next_run_date` vẫn chạy trong SQL |
| A3-17 | Số liệu dashboard đi kèm `GET /bootstrap`, không có endpoint riêng | `06-API-SPEC.md` §2 vốn mô tả bootstrap là "trả settings, reminder bị lỡ, số liệu dashboard trong 1 lần gọi". Mọi thao tác tài chính invalidate luôn query này nên bảng số liệu không bị cũ |
| A3-18 | Bootstrap **hạ cấp** thành `dashboard: null` khi tổng hợp số liệu lỗi | Đây là lời gọi mà cả ứng dụng chờ lúc khởi động. Không mở được app chỉ vì không cộng được vài con số tổng hợp là cái giá sai; giao diện đã có sẵn nhánh hiển thị khi thiếu dashboard |
| A3-19 | Danh sách giao dịch tải thêm bằng **nút "Tải thêm"**, không tự nạp khi cuộn | Phase plan ghi "infinite scroll". Nút cho kết quả tương đương về số request nhưng dùng được bằng bàn phím, không nuốt mất thanh cuộn, và không nạp thêm ngoài ý muốn khi user chỉ đang lướt tìm một dòng |
| A3-20 | Thêm `recharts` vào `package.json` | `04-ARCHITECTURE.md` §2 đã chốt Recharts là thư viện biểu đồ nhưng gói chưa từng được cài (Phase 0–2 không có biểu đồ nào) |

### Phase 4

| # | Giả định | Lý do |
|---|---|---|
| A4-01 | `AiClient` chỉ là **cổng truyền tải** (prompt vào, text ra, kèm số token); dựng prompt, làm sạch JSON, validate schema và **thử lại một lần** đều nằm ở tầng trên | SD-02 vẽ sanitize/validate bên trong `ClaudeAiClient`, nhưng T4-18 yêu cầu *"AiClient mock trả JSON sai schema → retry 1 lần"* — muốn kiểm chứng được số lần thử thì vòng lặp phải nằm **ngoài** client. Cách chia này cũng đúng danh sách 8 bước ở `04-ARCHITECTURE.md` §7, nơi "thử lại" là bước 6 chứ không phải một phần của bước 3 |
| A4-02 | Thêm ba cổng nữa ở `domain/ai`: `PromptTemplates`, `AiResponseReader`, `FallbackParser` | `04-ARCHITECTURE.md` §3 luật 2 bắt `infrastructure` implement interface do `domain` định nghĩa. `PromptBuilder`, `ParseResponseReader` và `RuleBasedParser` đều sống ở `infrastructure/ai` theo cấu trúc chốt, nên `NlParseService` (tầng `application`) chỉ được nhìn thấy chúng qua cổng |
| A4-03 | Template prompt là **một file cho mỗi tác vụ**, chia phần hệ thống và phần người dùng bằng dòng đánh dấu `=== USER ===` | Giữ đúng danh sách file ở `04-ARCHITECTURE.md` §4 (`nl-parse.txt`, `category-suggest.txt`) mà vẫn không nhúng một chữ nào của prompt vào mã Java |
| A4-04 | Model mặc định là `claude-opus-5`; Cài đặt cho chọn thêm Sonnet 5 và Haiku 4.5 | Tài liệu chỉ nói "tên model do user chọn" (`03-DATA-MODEL.md` §2.11), không nêu mặc định. Chọn model mạnh nhất làm mặc định và để việc hạ cấp cho user quyết định; ô chọn ghi rõ đánh đổi tốc độ. Xem mục nợ kỹ thuật về trần 5 giây |
| A4-05 | `output_config.effort = LOW` cho mọi lời gọi | Biến một câu tiếng Việt ngắn thành sáu trường không phải bài toán suy luận, còn ngân sách tương tác chỉ có 5 giây — để mức suy nghĩ sâu sẽ tiêu hết ngân sách rồi rơi xuống fallback |
| A4-06 | **Mọi** lời gọi `/ai/parse` đều ghi một dòng `ai_parse_log`, kể cả khi AI đang tắt (khi đó `success = 0`, `error_code = NULL`) | SD-02 đặt bước `save(AiParseLog)` **sau** cả khối `alt`, tức là nhánh "AI bị tắt" cũng đi qua. Nhờ vậy số dòng log luôn khớp số lần user nhấn Enter, và UAT kiểm được "không có lời gọi API nào" bằng cột `model` để trống |
| A4-07 | Số tiền dưới 1.000 mà **không có** ký hiệu tiền tệ thì nhân 1.000 — áp dụng **sau** khi đã nhân hệ số đơn vị | Đây là quy tắc duy nhất làm cả `500 → 500.000` (T4-09) lẫn `3 trăm rưỡi → 350.000` (T4-08) cùng đúng: `3 × 100 + 50 = 350`, vẫn dưới 1.000 nên thành 350.000. `1.500.000đ` có hậu tố `đ` nên giữ nguyên |
| A4-08 | Bộ luật ngoại tuyến **cắt** phần đã trở thành trường (ngày, giờ, nhắc hẹn, số tiền) ra khỏi tiêu đề | Để nguyên cả câu thì form sự kiện sẽ hiện "họp review sprint thứ 5 tuần sau 2h chiều nhắc trước 15 phút" ngay cạnh chính các ô đang giữ phần còn lại. Cắt được là nhờ `TextFolding` bỏ dấu mà **không đổi độ dài**, nên chỉ số tìm trên "thu 5" khớp đúng với "thứ 5" trong câu gốc |
| A4-09 | Kết quả parse hiển thị bằng **chính ba form tạo mới đã có**, chỉ thêm `defaults` và `aiConfidence`, thay vì dựng một form riêng | `04-ARCHITECTURE.md` §4 có nhắc `ParseResultForm.tsx`, và file đó vẫn tồn tại — nhưng nó điều phối chứ không dựng lại form. Dùng lại form thật nghĩa là đường AI và đường gõ tay có **chung** một bộ validate Zod và một mutation lưu, nên không thể tồn tại một lối tạo giao dịch thứ hai lỏng lẻo hơn |
| A4-10 | Frontend là nơi áp ngưỡng `confidence < 0.6` (để trống + tô cảnh báo); backend trả nguyên `fieldConfidence` | UC-09 luồng 10a mô tả đây là hành vi của giao diện. Backend giữ nguyên số liệu để màn hình Nhật ký AI còn xem được AI thực sự tự tin bao nhiêu |
| A4-11 | `PUT /settings` trả thêm cờ `requiresRestart`; đổi `app.timezone` khiến renderer gọi IPC khởi động lại backend | `TimeConfig.displayZone` chỉ đọc setting một lần lúc khởi động (nợ kỹ thuật từ Phase 0). Lựa chọn còn lại là để user tự phát hiện ra rằng thay đổi của họ không có tác dụng gì |
| A4-12 | API key cất ở `userData/secure/ai-api-key.bin` qua `safeStorage`, **ngoài** thư mục `data/` | `data/` là thứ được sao lưu và khôi phục (Phase 5). Một bản backup vô tình mang theo credential là đúng loại rò rỉ mà NFR-SEC-01 muốn chặn |
| A4-13 | Lưu hoặc xóa API key sẽ **khởi động lại tiến trình backend** | `03-DATA-MODEL.md` §2.11 chốt "backend nhận qua biến môi trường lúc spawn". Không có cách đưa biến môi trường mới vào một JVM đang chạy, và bịa ra một endpoint nhận secret qua HTTP thì đi ngược đúng lý do key không nằm trong database |
| A4-14 | Máy không có kho bảo mật (`safeStorage` không khả dụng) thì key chỉ sống trong bộ nhớ của phiên hiện tại, và giao diện nói rõ điều đó | Ghi ra file dạng thường vi phạm thẳng NFR-SEC-01. Từ chối hẳn thì chặn luôn một người dùng Linux hoàn toàn hợp lệ |
| A4-15 | `POST /ai/test-connection` **không** ghi `ai_parse_log` | Cột `request_type` chỉ nhận ba giá trị `NL_PARSE` / `CATEGORY_SUGGEST` / `WEEKLY_INSIGHT` (`03-DATA-MODEL.md` §2.10). Thử kết nối không phải một lần parse; nhét nó vào một trong ba loại kia sẽ làm sai số liệu của chính bảng log |
| A4-16 | `PUT /settings` chỉ nhận **danh sách khóa cho phép**, mỗi khóa kèm luật kiểm giá trị | Bảng `setting` là key-value trần. Không có danh sách trắng thì một lỗi gõ trong màn hình Cài đặt sẽ âm thầm tạo ra một setting không ai đọc, và user ngồi nhìn một giá trị không có tác dụng gì. `db.schema_version` cố ý **không** nằm trong danh sách — Flyway sở hữu nó |
| A4-17 | Gợi ý danh mục khớp lịch sử so sánh **toàn bộ ghi chú**, đã bỏ dấu và không phân biệt hoa thường | UC-10 ngoại lệ E2 nói "ghi chú trùng khớp". Khớp một phần là phỏng đoán, mà phỏng đoán là việc của model |
| A4-18 | `RuleBasedParser` không bao giờ ném lỗi; câu không hiểu được trả về `UNKNOWN` | Đây là nhánh chạy khi có thứ khác đã hỏng rồi. Một exception ở đây biến tính năng suy giảm thành tính năng gãy |
| A4-19 | "Thứ X" đứng một mình mà đã qua trong tuần này thì hiểu là tuần sau; "tuần sau" dịch cả tuần trước rồi mới chọn thứ | Không có quy tắc nào trong tài liệu. Cách này làm `thứ 5 tuần sau` rơi đúng thứ 5 của tuần kế tiếp (T4-11) thay vì tám ngày kể từ thứ 5 gần nhất |
| A4-21 | Validate schema JSON của AI bằng **kiểm tra tường minh trong `ParseResponseReader`**, không dùng Bean Validation trên một lớp DTO có annotation | AGENTS.md §3.4 mục 3 ghi "Jackson + Bean Validation". Sai lệch có chủ đích: schema ở `04-ARCHITECTURE.md` §7 là một **union** — đúng một trong ba object được điền, tùy theo `intent` — mà Bean Validation không diễn đạt được ràng buộc liên trường đó nếu không có validator tự viết, tức là vẫn phải viết đúng phần logic này bằng tay. Quan trọng hơn: mức độ nghiêm ngặt ở đây **không đồng đều** theo thiết kế (thiếu số tiền thì từ chối cả câu trả lời, thiếu địa điểm thì bỏ qua — UC-09 E4), còn `@NotNull` thì hoặc bật hoặc tắt. Đánh đổi: sai schema báo lỗi ở dạng thông điệp chứ không phải danh sách `ConstraintViolation`; chưa thấy chỗ nào cần danh sách đó. T4-16 kiểm đủ các nhánh từ chối |
| A4-20 | Khoảng nhắc lạ được **làm tròn** về giá trị gần nhất trong sáu giá trị FR-CAL-06 cho phép (bộ luật), còn kết quả từ AI thì **loại hẳn** | Người viết "nhắc trước 20 phút" nhận được ô 15 phút — gần ý họ hơn là không có nhắc nào, và là thứ form thực sự hiển thị được. Với AI thì khác: prompt đã nêu đúng sáu giá trị hợp lệ, nên một giá trị ngoài danh sách là model làm sai chứ không phải user diễn đạt lạ |

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
| 2026-09-08 | B-1 (áp dụng) | `recurring_rule` đã được tạo trong `V4__finance_module.sql` ở Phase 3 đúng như quyết định trên | Thực thi quyết định đã duyệt | ✅ |
| 2026-09-08 | C-3, C-4, C-5, C-6, M-15, M-21 (áp dụng) | `V4` tạo `transaction_tag` và `recurring_rule` theo định nghĩa chốt; unique index của `category` đặt trên `COALESCE(parent_id, '')`; `wallet`, `category`, `budget` đều có `updated_at`, `budget` có `deleted_at`; `idx_wallet_name` là partial index; `start_date` / `next_run_date` / `last_run_date` lưu `TEXT` ISO | Áp dụng phụ lục §6 của `03-DATA-MODEL.md` khi tới đúng phase | ✅ |

> **Phase 4 phát sinh hai mục cần user duyệt: D-3 và E-1 bên dưới.**
>
> ⚠️ **Ghi nhận sai sót quy trình:** mã của D-3 và E-1 đã được commit và merge vào `main` **trước**
> khi được duyệt, trái với AGENTS.md §6 ("duyệt trước rồi mới ghi"). Không viết thêm code cho hai
> mục này cho tới khi có quyết định; nếu bị bác thì phương án thay thế đã được trình bày kèm.

| Ngày | Mã | Thay đổi | Lý do | User duyệt |
|---|---|---|---|---|
| 2026-09-09 | D-3 | Thêm `com.anthropic:anthropic-java` 2.34.0 vào tech stack | `04-ARCHITECTURE.md` §2 chốt `ClaudeAiClient` nhưng bảng stack không có dòng nào cho client gọi LLM. Dùng SDK chính thức thay vì tự gọi REST: nó phân loại sẵn lỗi theo mã HTTP (401 → `UnauthorizedException`, 429 → `RateLimitException`), đúng thứ `NlParseService` cần để chọn `AiErrorCode` cho log và banner. Thêm khoảng 3 MB vào jar, không ảnh hưởng CON-04 | ⏳ **chờ duyệt** |
| 2026-09-09 | E-1 | `POST /ai/parse` trả thêm `transaction.walletName`, và hình dạng đầy đủ cho `task` / `event`; `GET /settings` trả `{settings, requiresRestart}` thay vì map trần | `06-API-SPEC.md` §8 chỉ vẽ response của giao dịch, không vẽ task và event. Các trường tên là **bổ sung thuần túy** (mọi trường đã đặc tả giữ nguyên) và cần thiết vì frontend phải hiện nhãn khi tên model trả về không khớp danh mục nào của user. `requiresRestart` là cách màn hình Cài đặt biết khi nào phải khởi động lại backend (A4-11) | ⏳ **chờ duyệt** |
| 2026-09-09 | C-18 | Mở rộng cấu trúc thư mục chốt và bảng kênh IPC: thêm `electron/secureStore.ts`, `electron/vitest.config.ts`, `electron/backendManager.test.ts`, và bốn kênh IPC `secure:set-api-key`, `secure:has-api-key`, `secure:clear-api-key`, `app:restart-backend` | `04-ARCHITECTURE.md` §4 liệt kê hết nội dung thư mục `electron/` và §6.3 liệt kê hết kênh IPC, nhưng cả hai đều được viết trước khi có FR-AI-09. `secure:set-api-key` và `secure:has-api-key` vốn đã được `07-PHASE-PLAN.md` (phạm vi Phase 4, mục Electron) nêu đích danh; `secure:clear-api-key` là mục **thật sự mới** — không xóa được key thì không rút lại được một credential đã lộ. `app:restart-backend` phục vụ A4-11. Cùng dạng mở rộng với C-16 | ⏳ **chờ duyệt** |
| 2026-09-09 | C-2 (áp dụng) | Theme chuyển từ `localStorage` sang `setting/app.theme` | Thực thi quyết định đã duyệt ở Phase 0, nay `/settings` đã tồn tại | ✅ |
| 2026-09-09 | V6 (áp dụng) | `V6__ai_module.sql` chỉ tạo `ai_parse_log`, đúng bảng migration ở `03-DATA-MODEL.md` §4 | Thực thi kế hoạch đã chốt | ✅ |

> Các mục C-3 → C-15, C-17 và M-5 → M-22 đã được duyệt và ghi trong phụ lục §6 của
> `03-DATA-MODEL.md` (sửa đổi schema) hoặc áp dụng trực tiếp ở phase tương ứng. Chúng sẽ được
> ghi lại vào bảng trên khi phase đó thực sự chạm tới.
