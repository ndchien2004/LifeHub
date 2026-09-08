# 06 — REST API Specification

**Base URL:** `http://127.0.0.1:{port}/api/v1`
**Header bắt buộc mọi request:** `X-App-Token: {token}`
**Content-Type:** `application/json; charset=UTF-8`

---

## 1. Quy ước chung

### Định dạng response thành công

```json
{ "success": true, "data": { } }
```

Với danh sách có phân trang:

```json
{
  "success": true,
  "data": {
    "items": [],
    "page": 0,
    "size": 50,
    "totalItems": 1234,
    "totalPages": 25
  }
}
```

### Định dạng response lỗi

```json
{
  "success": false,
  "error": { "code": "VALIDATION_ERROR", "message": "...", "field": "amount", "traceId": "01J..." }
}
```

### Quy ước tham số

| Tham số | Kiểu | Ghi chú |
|---|---|---|
| `page` | int | Bắt đầu từ 0, mặc định 0 |
| `size` | int | Mặc định 50, tối đa 200 |
| `sort` | string | Dạng `field,asc` hoặc `field,desc` |
| Ngày giờ | string | ISO-8601 có offset: `2026-09-04T14:30:00+07:00` |
| Số tiền | number | Số nguyên, đơn vị đồng |

---

## 2. Bootstrap & System

| Method | Path | Mô tả | Phase |
|---|---|---|---|
| GET | `/actuator/health` | Health check cho Electron | 0 |
| GET | `/bootstrap` | Trả settings, reminder bị lỡ, số liệu dashboard trong 1 lần gọi | 0 |
| GET | `/settings` | Lấy toàn bộ setting | 4 |
| PUT | `/settings` | Cập nhật nhiều setting cùng lúc | 4 |
| GET | `/events/stream` | SSE stream cho reminder | 2 |

**`GET /bootstrap` response:**

```json
{
  "success": true,
  "data": {
    "settings": { "app.theme": "SYSTEM", "ai.enabled": true },
    "aiConfigured": true,
    "missedReminders": [
      { "id": "...", "title": "Họp review", "triggerAt": "...", "type": "EVENT", "refId": "..." }
    ],
    "dashboard": {
      "todayTasks": 5,
      "overdueTasks": 2,
      "upcomingEvents": 3,
      "monthExpense": 4250000,
      "monthIncome": 15000000,
      "budgetAlerts": [{ "categoryName": "Ăn uống", "usage": 0.92 }]
    }
  }
}
```

---

## 3. Task API (Phase 1)

| Method | Path | Mô tả |
|---|---|---|
| GET | `/tasks` | Danh sách task, có lọc và phân trang |
| GET | `/tasks/{id}` | Chi tiết một task kèm subtask |
| POST | `/tasks` | Tạo task |
| PATCH | `/tasks/{id}` | Cập nhật một phần |
| PATCH | `/tasks/{id}/status` | Đổi trạng thái riêng (tối ưu cho kéo thả Kanban) |
| DELETE | `/tasks/{id}` | Soft delete |
| POST | `/tasks/{id}/restore` | Khôi phục task đã xóa |
| PATCH | `/tasks/reorder` | Cập nhật sort_order hàng loạt |

**Query params cho `GET /tasks`:**

`projectId`, `tagIds` (CSV), `status` (CSV), `priority` (CSV), `dueFrom`, `dueTo`, `q` (từ khóa), `includeDeleted` (bool), `page`, `size`, `sort`

**`POST /tasks` request:**

```json
{
  "title": "Hoàn thiện SRS",
  "description": "Viết đủ use case và ERD",
  "priority": "HIGH",
  "dueAt": "2026-09-10T17:00:00+07:00",
  "projectId": "01J...",
  "tagIds": ["01J...", "01J..."],
  "estimateMinutes": 180,
  "parentId": null
}
```

**Response 201:**

```json
{
  "success": true,
  "data": {
    "id": "01J...",
    "title": "Hoàn thiện SRS",
    "status": "TODO",
    "priority": "HIGH",
    "dueAt": "2026-09-10T17:00:00+07:00",
    "isOverdue": false,
    "project": { "id": "01J...", "name": "LifeHub", "color": "#6366f1" },
    "tags": [{ "id": "01J...", "name": "docs", "color": "#10b981" }],
    "subtaskCount": 0,
    "completedSubtaskCount": 0,
    "createdAt": "2026-09-04T10:00:00+07:00"
  }
}
```

---

## 4. Project & Tag API (Phase 1)

| Method | Path | Mô tả |
|---|---|---|
| GET | `/projects` | Danh sách, kèm số task và % hoàn thành |
| POST | `/projects` | Tạo |
| PATCH | `/projects/{id}` | Sửa |
| DELETE | `/projects/{id}` | Xóa, task bên trong chuyển về không project |
| GET | `/tags` | Danh sách tag kèm số lần dùng |
| POST | `/tags` | Tạo |
| PATCH | `/tags/{id}` | Sửa |
| DELETE | `/tags/{id}` | Xóa, gỡ khỏi mọi task/giao dịch |

---

## 5. Calendar API (Phase 2)

| Method | Path | Mô tả |
|---|---|---|
| GET | `/events` | Danh sách instance trong khoảng `from`–`to` (đã expand RRULE) |
| GET | `/events/{id}` | Chi tiết event master |
| POST | `/events` | Tạo event |
| PATCH | `/events/{id}` | Sửa toàn bộ chuỗi (scope = ALL) |
| PATCH | `/events/{id}/occurrences/{occurrenceStart}` | Sửa instance, body có `scope` |
| DELETE | `/events/{id}` | Xóa cả chuỗi |
| DELETE | `/events/{id}/occurrences/{occurrenceStart}` | Xóa một instance (tạo exception cancelled) |
| GET | `/events/conflicts` | Kiểm tra xung đột với khoảng thời gian cho trước |

**`GET /events` query:** `from` (bắt buộc), `to` (bắt buộc), `includeTasks` (bool, mặc định true)

**Response item:**

```json
{
  "eventId": "01J...",
  "occurrenceStart": "2026-09-15T14:00:00+07:00",
  "title": "Họp review sprint",
  "startAt": "2026-09-15T14:00:00+07:00",
  "endAt": "2026-09-15T15:00:00+07:00",
  "allDay": false,
  "location": "Phòng họp A",
  "isRecurring": true,
  "isException": false,
  "linkedTaskId": null,
  "reminders": [{ "id": "01J...", "offsetMinutes": 15 }]
}
```

**`PATCH /events/{id}/occurrences/{occurrenceStart}` body:**

```json
{
  "scope": "THIS_ONLY",
  "title": "Họp review sprint (dời)",
  "startAt": "2026-09-15T16:00:00+07:00",
  "endAt": "2026-09-15T17:00:00+07:00"
}
```

`scope` nhận: `THIS_ONLY` | `THIS_AND_FOLLOWING` | `ALL`

---

## 6. Reminder API (Phase 2)

| Method | Path | Mô tả |
|---|---|---|
| GET | `/reminders/missed` | Reminder PENDING quá hạn trong 24h |
| POST | `/reminders/{id}/snooze` | Hoãn, body `{minutes: 10}` |
| POST | `/reminders/{id}/dismiss` | Tắt, nếu gắn task thì đánh dấu DONE |
| POST | `/reminders/dismiss-all` | Tắt hàng loạt các reminder bị lỡ |

**SSE `GET /events/stream`** — định dạng sự kiện:

```
event: reminder.fired
data: {"reminderId":"01J...","title":"Họp review sprint","body":"14:00 · Phòng họp A","refType":"EVENT","refId":"01J..."}
```

---

## 7. Finance API (Phase 3)

### Wallet

| Method | Path | Mô tả |
|---|---|---|
| GET | `/wallets` | Danh sách kèm số dư tính động |
| POST | `/wallets` | Tạo |
| PATCH | `/wallets/{id}` | Sửa |
| DELETE | `/wallets/{id}` | Xóa, chỉ cho phép khi không có giao dịch |
| GET | `/wallets/{id}/balance` | Số dư tại một mốc thời gian (`asOf`) |

### Category

| Method | Path | Mô tả |
|---|---|---|
| GET | `/categories` | Danh sách dạng cây, lọc theo `type` |
| POST | `/categories` | Tạo |
| PATCH | `/categories/{id}` | Sửa |
| DELETE | `/categories/{id}` | Xóa, chặn nếu `is_system = true` hoặc còn giao dịch |

### Transaction

| Method | Path | Mô tả |
|---|---|---|
| GET | `/transactions` | Danh sách có lọc, phân trang |
| GET | `/transactions/{id}` | Chi tiết |
| POST | `/transactions` | Tạo |
| PATCH | `/transactions/{id}` | Sửa |
| DELETE | `/transactions/{id}` | Soft delete |
| GET | `/transactions/summary` | Tổng hợp theo danh mục / theo thời gian |

**Query cho `GET /transactions`:** `from`, `to`, `walletIds`, `categoryIds`, `type`, `minAmount`, `maxAmount`, `q`, `source`, `page`, `size`, `sort`

**`POST /transactions` request:**

```json
{
  "type": "EXPENSE",
  "amount": 45000,
  "walletId": "01J...",
  "categoryId": "01J...",
  "toWalletId": null,
  "note": "Cơm gà, ăn cùng team",
  "occurredAt": "2026-09-04T12:15:00+07:00",
  "tagIds": [],
  "source": "MANUAL",
  "aiConfidence": null
}
```

**Response 201:**

```json
{
  "success": true,
  "data": {
    "transaction": { "id": "01J...", "amount": 45000, "...": "..." },
    "walletBalance": 3455000,
    "budgetAlert": { "categoryId": "01J...", "categoryName": "Ăn uống", "usage": 0.92, "level": "WARNING", "limitAmount": 3000000, "spentAmount": 2760000 }
  }
}
```

**`GET /transactions/summary` query:** `from`, `to`, `groupBy` (`CATEGORY`|`DAY`|`WEEK`|`MONTH`|`WALLET`), `type`

```json
{
  "success": true,
  "data": {
    "totalIncome": 15000000,
    "totalExpense": 8420000,
    "net": 6580000,
    "groups": [
      { "key": "01J...", "label": "Ăn uống", "color": "#f59e0b", "amount": 2760000, "percentage": 32.8, "transactionCount": 47 }
    ]
  }
}
```

### Budget

| Method | Path | Mô tả |
|---|---|---|
| GET | `/budgets` | Danh sách kèm mức sử dụng chu kỳ hiện tại |
| POST | `/budgets` | Tạo |
| PATCH | `/budgets/{id}` | Sửa |
| DELETE | `/budgets/{id}` | Xóa |

---

## 8. AI API (Phase 4)

| Method | Path | Mô tả |
|---|---|---|
| POST | `/ai/parse` | Phân tích câu tự nhiên |
| POST | `/ai/suggest-category` | Gợi ý danh mục từ ghi chú |
| GET | `/ai/status` | Trạng thái AI: đã cấu hình chưa, model nào, còn hoạt động không |
| POST | `/ai/test-connection` | Kiểm tra API key có hợp lệ |
| GET | `/ai/logs` | Xem log gọi AI (phục vụ debug) |

**`POST /ai/parse` request:** `{ "text": "ăn trưa cơm gà 45k với team" }`

**Response:**

```json
{
  "success": true,
  "data": {
    "intent": "TRANSACTION",
    "confidence": 0.94,
    "source": "AI",
    "transaction": {
      "type": "EXPENSE",
      "amount": 45000,
      "categoryId": "01J...",
      "categoryName": "Ăn uống",
      "walletId": "01J...",
      "note": "Cơm gà, ăn cùng team",
      "occurredAt": "2026-09-04T12:00:00+07:00",
      "fieldConfidence": { "amount": 0.99, "categoryName": 0.87, "occurredAt": 0.7 }
    },
    "task": null,
    "event": null,
    "warning": null
  }
}
```

Khi fallback: `"source": "RULE"`, `"warning": "AI_UNAVAILABLE"`.

**`POST /ai/suggest-category` request:** `{ "note": "trà sữa gongcha", "type": "EXPENSE" }`

**Response:** `{"suggestions": [{"categoryId": "...", "categoryName": "Cà phê", "confidence": 0.88}]}` — tối đa 3 mục.

---

## 9. Insight & Report API (Phase 5)

| Method | Path | Mô tả |
|---|---|---|
| GET | `/insights` | Danh sách insight, lọc `unread` |
| POST | `/insights/{id}/read` | Đánh dấu đã đọc |
| POST | `/insights/{id}/rate` | Đánh giá hữu ích, body `{rating: 1}` hoặc `{rating: -1}` |
| POST | `/insights/generate` | Sinh thủ công ngay (không chờ cron) |
| POST | `/reports/export/excel` | Xuất Excel, trả file binary |
| POST | `/reports/export/pdf` | Xuất PDF, trả file binary |
| POST | `/import/preview` | Xem trước import CSV |
| POST | `/import/commit` | Thực hiện import |
| POST | `/backup/create` | Tạo backup thủ công |
| GET | `/backup/list` | Danh sách file backup |
| POST | `/backup/restore` | Khôi phục, body `{fileName: "..."}` |

**Endpoint export trả về:** `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` hoặc `application/pdf`, kèm `Content-Disposition: attachment; filename="..."`.

---

## 10. Bảng truy vết endpoint → phase

| Phase | Nhóm endpoint |
|---|---|
| 0 | `/actuator/health`, `/bootstrap` (rút gọn) |
| 1 | `/tasks/*`, `/projects/*`, `/tags/*` |
| 2 | `/events/*`, `/reminders/*`, `/events/stream` |
| 3 | `/wallets/*`, `/categories/*`, `/transactions/*`, `/budgets/*` |
| 4 | `/ai/*`, `/settings` |
| 5 | `/insights/*`, `/reports/*`, `/import/*`, `/backup/*` |
| 6 | Không thêm endpoint mới |
