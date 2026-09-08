# 05 — Sequence Diagrams

Tài liệu này mô tả chi tiết các luồng phức tạp nhất. Agent phải implement đúng thứ tự và điều kiện rẽ nhánh mô tả ở đây.

---

## SD-01 — Ghi giao dịch chi tiêu (UC-06)

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant R as React
    participant C as TransactionController
    participant S as TransactionService
    participant BS as BudgetService
    participant WB as WalletBalanceCalculator
    participant DB as SQLite

    U->>R: Nhập số tiền, danh mục, ví, ghi chú
    R->>R: Zod validate phía client
    U->>R: Bấm "Lưu"
    R->>C: POST /api/v1/transactions
    C->>C: Bean Validation trên DTO
    C->>S: create(CreateTransactionCommand)

    activate S
    S->>S: Kiểm tra amount > 0
    S->>DB: Tìm wallet theo id
    alt Ví không tồn tại hoặc đã xóa
        DB-->>S: empty
        S-->>C: throw NotFoundException
        C-->>R: 404 {code: NOT_FOUND}
        R-->>U: Toast lỗi "Ví không tồn tại"
    else Ví hợp lệ
        DB-->>S: Wallet
        alt type = TRANSFER
            S->>S: Kiểm tra to_wallet_id khác wallet_id
            S->>S: Bỏ qua kiểm tra category
        else type = INCOME hoặc EXPENSE
            S->>DB: Tìm category theo id
            S->>S: Kiểm tra category.type khớp transaction.type
        end

        S->>DB: BEGIN TRANSACTION
        S->>DB: INSERT INTO transaction
        S->>DB: COMMIT
        DB-->>S: OK

        S->>WB: recalculate(walletId)
        WB->>DB: SELECT tính tổng theo công thức số dư
        DB-->>WB: balance
        WB-->>S: newBalance

        S->>BS: checkBudgetThreshold(categoryId, occurredAt)
        BS->>DB: Lấy budget active của danh mục
        BS->>DB: Tính tổng chi trong chu kỳ hiện tại
        DB-->>BS: spentAmount
        BS->>BS: usage = spent / limit
        alt usage >= 1.0
            BS-->>S: BudgetAlert(EXCEEDED, usage)
        else usage >= 0.8
            BS-->>S: BudgetAlert(WARNING, usage)
        else
            BS-->>S: null
        end

        S-->>C: TransactionResult{transaction, newBalance, budgetAlert}
    end
    deactivate S

    C-->>R: 201 Created + body
    R->>R: Invalidate query cache: transactions, wallets, budgets, dashboard
    R-->>U: Đóng form, hiện giao dịch mới ở đầu danh sách
    opt Có budgetAlert
        R-->>U: Banner cảnh báo ngân sách
    end
```

**Điểm cần chú ý khi implement:**
- Bước tính lại số dư và kiểm ngân sách nằm **ngoài** DB transaction ghi giao dịch. Nếu chúng lỗi, giao dịch vẫn phải được lưu thành công.
- Không tính lại số dư của tất cả các ví, chỉ ví bị ảnh hưởng (và ví đích nếu là TRANSFER).

---

## SD-02 — Nhập bằng ngôn ngữ tự nhiên có fallback (UC-09)

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant R as CommandPalette
    participant C as AiController
    participant S as NlParseService
    participant PB as PromptBuilder
    participant AC as ClaudeAiClient
    participant LLM as LLM API
    participant JS as JsonSanitizer
    participant RB as RuleBasedParser
    participant LOG as AiParseLogRepo

    U->>R: Ctrl+Space, gõ "ăn trưa cơm gà 45k với team"
    U->>R: Enter
    R->>C: POST /api/v1/ai/parse {text}
    C->>S: parse(text)

    activate S
    S->>S: Kiểm tra setting ai.enabled
    alt AI bị tắt hoặc chưa có API key
        S->>RB: parse(text)
        RB-->>S: ParseResult(source=RULE)
    else AI được bật
        S->>S: Thu thập context: now, timezone,<br/>danh sách category, wallet,<br/>30 giao dịch gần nhất
        S->>PB: build("nl-parse", text, context)
        PB-->>S: prompt

        S->>AC: complete(prompt, timeout=5s)
        AC->>LLM: POST /messages (JSON mode)

        alt Thành công trong 5s
            LLM-->>AC: raw response
            AC->>JS: sanitize(raw)
            JS->>JS: Bỏ ```json fence, trim, bỏ ký tự điều khiển
            JS-->>AC: cleanJson
            AC->>AC: Jackson parse + Bean Validation

            alt JSON hợp lệ theo schema
                AC-->>S: ParseResult(source=AI)
            else JSON sai schema
                AC->>LLM: Retry 1 lần với prompt<br/>nhấn mạnh "CHỈ trả JSON"
                alt Retry thành công
                    LLM-->>AC: raw2
                    AC-->>S: ParseResult(source=AI)
                else Retry thất bại
                    AC-->>S: throw AiInvalidResponseException
                    S->>RB: parse(text)
                    RB-->>S: ParseResult(source=RULE)
                end
            end
        else Timeout / lỗi mạng / 401 / 429
            LLM-->>AC: error
            AC-->>S: throw AiUnavailableException(errorCode)
            S->>RB: parse(text)
            RB-->>S: ParseResult(source=RULE)
        end
    end

    S->>LOG: save(AiParseLog{...})
    S-->>C: ParseResult
    deactivate S

    C-->>R: 200 {intent, entities, fieldConfidence, source}

    alt intent = UNKNOWN
        R-->>U: "Mình chưa hiểu ý bạn" + 3 nút chọn loại
    else intent xác định
        R->>R: Map sang form tương ứng
        R->>R: Trường có confidence < 0.6 → để trống + highlight
        R-->>U: Hiện form ĐÃ ĐIỀN SẴN
        opt source = RULE
            R-->>U: Banner "Đang dùng chế độ offline"
        end
        U->>R: Rà soát, sửa nếu cần
        alt User bấm "Xác nhận"
            R->>C: POST /api/v1/transactions (hoặc /tasks, /events)
            Note over R,C: Đi tiếp theo SD-01
        else User nhấn Esc
            R->>R: Đóng, KHÔNG ghi gì vào DB
        end
    end
```

**Quy tắc tuyệt đối:** Không có đường nào từ `NlParseService` dẫn thẳng tới `TransactionService.create()`. Việc tạo bản ghi chỉ xảy ra khi frontend gửi request mới sau khi user bấm xác nhận.

---

## SD-03 — Scheduler bắn reminder (UC-04)

```mermaid
sequenceDiagram
    autonumber
    participant SCH as ReminderScheduler
    participant REPO as ReminderRepository
    participant SSE as ReminderSseEmitter
    participant EM as Electron Main
    participant OS as OS Notification
    participant U as User
    participant C as ReminderController

    loop Mỗi 30 giây
        SCH->>REPO: findDue(status=PENDING, trigger_at <= now)
        REPO-->>SCH: List<Reminder>

        loop Với mỗi reminder
            SCH->>REPO: Nạp event hoặc task liên quan
            SCH->>REPO: UPDATE status=FIRED, fired_at=now
            SCH->>SSE: emit("reminder.fired", payload)
            SSE-->>EM: SSE event
            EM->>OS: new Notification(title, body, actions)
            OS-->>U: Hiện thông báo hệ thống
        end
    end

    alt User bấm vào thân notification
        U->>OS: click
        OS-->>EM: 'click'
        EM->>EM: window.show() + focus()
        EM->>EM: IPC "navigate" tới event/task
    else User bấm "Hoãn 10 phút"
        U->>OS: action 'snooze'
        OS-->>EM: 'action', index 0
        EM->>C: POST /api/v1/reminders/{id}/snooze {minutes:10}
        C->>REPO: UPDATE reminder cũ status=SNOOZED
        C->>REPO: INSERT reminder mới trigger_at=now+10m, PENDING
    else User bấm "Đã xong"
        U->>OS: action 'done'
        OS-->>EM: 'action', index 1
        EM->>C: POST /api/v1/reminders/{id}/dismiss
        C->>REPO: UPDATE status=DISMISSED
        opt Reminder gắn với task
            C->>REPO: UPDATE task status=DONE, completed_at=now
        end
    else Không tương tác
        Note over OS: Tự ẩn theo hành vi OS<br/>Trạng thái giữ FIRED
    end
```

**Yêu cầu hiệu năng:** Chu kỳ 30 giây đảm bảo NFR-PERF-06 (độ trễ ≤ 30 giây). Query `findDue` phải dùng index `idx_reminder_pending`, không được quét toàn bảng.

---

## SD-04 — Sinh instance của event lặp lại

```mermaid
sequenceDiagram
    autonumber
    participant R as Calendar UI
    participant C as EventController
    participant Q as EventQueryService
    participant EXP as RecurrenceExpander
    participant REPO as EventRepository
    participant EXC as EventExceptionRepository

    R->>C: GET /api/v1/events?from=2026-09-01&to=2026-09-30
    C->>Q: findInRange(from, to)

    Q->>REPO: Lấy event không lặp giao với khoảng
    REPO-->>Q: List<Event> (rrule = null)

    Q->>REPO: Lấy event có rrule != null<br/>và start_at <= to
    REPO-->>Q: List<Event> (master)

    loop Với mỗi event master
        Q->>EXP: expand(event.rrule, event.start_at, from, to)
        EXP->>EXP: Parse RRULE (ical4j)
        EXP->>EXP: Sinh danh sách mốc bắt đầu trong [from, to]
        EXP->>EXP: Giới hạn tối đa 500 instance để tránh RRULE lỗi
        EXP-->>Q: List<LocalDateTime> occurrences

        Q->>EXC: findByEventId(event.id)
        EXC-->>Q: List<EventException>

        loop Với mỗi occurrence
            alt Có exception cancelled = true
                Q->>Q: Bỏ qua occurrence này
            else Có exception sửa nội dung
                Q->>Q: Tạo instance với giá trị đã ghi đè
            else Không có exception
                Q->>Q: Tạo instance từ template event master
            end
        end
    end

    Q->>Q: Gộp và sắp xếp theo start_at
    Q-->>C: List<EventInstanceDto>
    C-->>R: 200 + danh sách instance
    R->>R: Render lên lưới lịch
```

**Chi tiết quan trọng:**
- `EventInstanceDto` có 2 trường ID: `eventId` (của master) và `occurrenceStart` (mốc gốc của instance). Cặp này định danh duy nhất một instance. Frontend cần cả hai khi sửa/xóa.
- Giới hạn 500 instance là van an toàn chống RRULE vô hạn kiểu `FREQ=SECONDLY`.

---

## SD-05 — Sửa một instance của chuỗi lặp

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant R as React
    participant C as EventController
    participant S as EventService
    participant REPO as EventRepository
    participant EXC as EventExceptionRepository

    U->>R: Sửa instance ngày 15/09 của event lặp hàng tuần
    R-->>U: Dialog "Áp dụng thay đổi cho..."
    U->>R: Chọn phương án

    alt "Chỉ lần này"
        R->>C: PATCH /events/{eventId}/occurrences/{occurrenceStart}<br/>{scope: THIS_ONLY, ...}
        C->>S: updateSingleOccurrence(...)
        S->>EXC: Tìm exception theo (eventId, originalStart)
        alt Đã tồn tại
            S->>EXC: UPDATE các trường new_*
        else Chưa có
            S->>EXC: INSERT exception mới
        end
        S-->>C: OK
    else "Lần này và các lần sau"
        R->>C: PATCH ... {scope: THIS_AND_FOLLOWING, ...}
        C->>S: splitSeries(...)
        S->>REPO: Lấy event master
        S->>S: Thêm UNTIL = occurrenceStart - 1 giây<br/>vào RRULE của master
        S->>REPO: UPDATE master
        S->>S: Tạo event mới: start_at = occurrenceStart,<br/>RRULE giữ nguyên tần suất, bỏ UNTIL cũ
        S->>REPO: INSERT event mới
        S->>EXC: Chuyển các exception có<br/>original_start >= occurrenceStart sang event mới
        S-->>C: OK {newEventId}
    else "Tất cả các lần"
        R->>C: PATCH /events/{eventId} {scope: ALL, ...}
        C->>S: updateMaster(...)
        S->>REPO: UPDATE event master
        S-->>C: OK
    end

    C-->>R: 200
    R->>R: Invalidate cache lịch
    R-->>U: Lịch render lại
```

---

## SD-06 — Sinh insight hàng tuần (UC-11)

```mermaid
sequenceDiagram
    autonumber
    participant SCH as InsightScheduler
    participant S as InsightService
    participant AGG as DataAggregator
    participant DB as SQLite
    participant AC as ClaudeAiClient
    participant LLM as LLM API
    participant REPO as InsightRepository
    participant R as Dashboard

    Note over SCH: Cron 20:00 Chủ nhật<br/>hoặc chạy bù khi khởi động
    SCH->>S: generateWeeklyInsight()

    S->>REPO: Đã có insight cho tuần này chưa?
    alt Đã có
        REPO-->>S: có
        S-->>SCH: Bỏ qua
    else Chưa có
        S->>AGG: aggregate(weekStart, weekEnd)
        AGG->>DB: Tổng chi theo danh mục tuần này
        AGG->>DB: Tổng chi theo danh mục tuần trước
        AGG->>DB: Ngân sách và mức sử dụng
        AGG->>DB: Task hoàn thành / trễ hạn
        AGG->>DB: Task bị dời deadline >= 2 lần
        DB-->>AGG: dữ liệu thô
        AGG->>AGG: Tính % thay đổi, xếp hạng biến động
        AGG-->>S: WeeklySummary (đã tổng hợp, KHÔNG có giao dịch chi tiết)

        alt Không đủ 7 ngày dữ liệu
            S-->>SCH: Bỏ qua, không thông báo
        else Đủ dữ liệu
            S->>AC: complete(prompt insight + summary)
            AC->>LLM: POST /messages
            alt Thành công
                LLM-->>AC: JSON [{content, severity, module}]
                AC-->>S: List<InsightDto> (3–5 mục)
                S->>REPO: INSERT insight
                S-->>SCH: OK
            else Thất bại
                AC-->>S: exception
                S->>S: Sinh insight rule-based:<br/>danh mục vượt ngân sách,<br/>chênh lệch chi > 30% so tuần trước
                S->>REPO: INSERT insight (severity từ ngưỡng cố định)
            end
        end
    end

    Note over R: Lần mở app kế tiếp
    R->>REPO: GET /api/v1/insights?unread=true
    REPO-->>R: List<Insight>
    R->>R: Hiện badge trên Dashboard
```

**Quyền riêng tư:** Chỉ gửi số liệu đã tổng hợp lên AI (tổng theo danh mục, phần trăm thay đổi). Tuyệt đối không gửi từng giao dịch kèm ghi chú.

---

## SD-07 — Import CSV có chống trùng (UC-12)

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant R as React
    participant EM as Electron Main
    participant C as ImportController
    participant S as ImportService
    participant DB as SQLite

    U->>R: Bấm "Import CSV"
    R->>EM: IPC dialog:open-file
    EM-->>R: filePath
    R->>R: Đọc file, parse 5 dòng đầu (papaparse)
    R-->>U: Hiện bảng xem trước + form ánh xạ cột
    U->>R: Ánh xạ cột, chọn ví đích, định dạng ngày
    U->>R: Bấm "Kiểm tra"

    R->>C: POST /api/v1/import/preview {rows, mapping, walletId}
    C->>S: preview(...)

    loop Với mỗi dòng
        S->>S: Parse ngày theo định dạng đã chọn
        S->>S: Parse số tiền, bỏ dấu phân cách
        alt Dòng lỗi
            S->>S: Thêm vào danh sách INVALID kèm lý do
        else Dòng hợp lệ
            S->>S: Tính import_hash = sha256(ngày|sốtiền|mô tả chuẩn hóa)
            S->>DB: Tìm giao dịch cùng amount<br/>và occurred_at trong ±1 ngày
            alt Tìm thấy
                S->>S: Đánh dấu SUSPECTED_DUPLICATE + tham chiếu bản ghi cũ
            else Không tìm thấy
                S->>S: Đánh dấu READY
            end
        end
    end

    S-->>C: PreviewResult{ready: N, duplicates: M, invalid: K}
    C-->>R: 200
    R-->>U: Bảng tổng kết, dòng nghi trùng hiện song song với bản ghi cũ
    U->>R: Tick chọn giữ/bỏ từng dòng nghi trùng
    U->>R: Bấm "Import"

    R->>C: POST /api/v1/import/commit {selectedRows}
    C->>S: commit(...)
    S->>DB: BEGIN TRANSACTION
    loop Với mỗi dòng được chọn
        S->>DB: INSERT transaction (source=CSV_IMPORT, import_hash)
    end
    alt Tất cả thành công
        S->>DB: COMMIT
        S-->>C: ImportResult{imported: N}
    else Có lỗi
        S->>DB: ROLLBACK
        S-->>C: throw ImportFailedException
    end
    C-->>R: 200 hoặc 422
    R-->>U: Thông báo kết quả
```

---

## SD-08 — Backup tự động khi khởi động

```mermaid
sequenceDiagram
    autonumber
    participant APP as LifeHubApplication
    participant BS as BackupScheduler
    participant FS as FileSystem
    participant FW as Flyway

    APP->>BS: onApplicationReady()
    BS->>FS: Kiểm tra backup gần nhất
    alt Đã backup trong 24h qua
        BS-->>APP: Bỏ qua
    else Chưa backup hôm nay
        BS->>FS: Đóng mọi kết nối ghi, checkpoint WAL
        BS->>FS: Copy lifehub.db →<br/>backups/lifehub-yyyyMMdd-HHmmss.db
        BS->>FS: Đếm số file backup
        alt Nhiều hơn backup.keep_count
            BS->>FS: Xóa các file cũ nhất
        end
        BS-->>APP: OK
    end
    APP->>FW: migrate()
    Note over APP,FW: Backup LUÔN chạy TRƯỚC migration
```

**Thứ tự bắt buộc:** backup → migration → khởi động scheduler. Nếu migration hỏng dữ liệu, người dùng vẫn còn bản backup ngay trước đó.
