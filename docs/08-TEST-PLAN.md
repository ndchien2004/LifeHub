# 08 — Test Plan

---

## 1. Chiến lược test

### Kim tự tháp test

```
        ╱╲          Manual UAT (user thực hiện)
       ╱  ╲         → mỗi phase có 1 file kịch bản riêng
      ╱────╲
     ╱      ╲       Integration Test (~25%)
    ╱        ╲      → MockMvc + SQLite in-memory
   ╱──────────╲
  ╱            ╲    Unit Test (~70%)
 ╱______________╲   → JUnit 5 + Mockito + AssertJ
```

### Ngưỡng bắt buộc

| Hạng mục | Ngưỡng |
|---|---|
| Coverage package `domain` | ≥ 80% |
| Coverage package `application` | ≥ 75% |
| Coverage package `api` | ≥ 60% |
| Coverage package `infrastructure` | ≥ 50% |
| Test phải PASS 100% trước khi báo cáo phase | Bắt buộc |

Không được đạt coverage bằng cách viết test rỗng hoặc test getter/setter. Test phải kiểm tra hành vi nghiệp vụ thật.

---

## 2. Cấu hình môi trường test

**`src/test/resources/application-test.yml`:**

```yaml
spring:
  datasource:
    url: jdbc:sqlite:file::memory:?cache=shared
    driver-class-name: org.sqlite.JDBC
  jpa:
    database-platform: org.hibernate.community.dialect.SQLiteDialect
    hibernate:
      ddl-auto: none
  flyway:
    enabled: true
    locations: classpath:db/migration
app:
  token: test-token
  ai:
    enabled: false
```

**Quy tắc:**
- Mỗi test class dùng database riêng biệt, không chia sẻ trạng thái
- Dùng `@Transactional` + rollback tự động cho integration test
- Không bao giờ gọi API AI thật trong test — luôn mock `AiClient`
- Test thời gian dùng `Clock` inject được, không dùng `LocalDateTime.now()` trực tiếp trong service

---

## 3. Danh sách test case bắt buộc theo phase

### Phase 0

| ID | Loại | Nội dung | Kỳ vọng |
|---|---|---|---|
| T0-01 | Integration | Khởi động Spring context | Không exception |
| T0-02 | Integration | GET /bootstrap có token đúng | 200 + settings |
| T0-03 | Integration | GET /bootstrap không có token | 401 + code UNAUTHORIZED |
| T0-04 | Integration | GET /bootstrap token sai | 401 |
| T0-05 | Integration | Flyway migrate | Bảng `setting` tồn tại, `flyway_schema_history` có 1 dòng |
| T0-06 | Unit | GlobalExceptionHandler map exception | Đúng HTTP status và error code |
| T0-07 | Unit | Sinh UUID v7 | Tăng dần theo thời gian, không trùng qua 10.000 lần |

### Phase 1

| ID | Loại | Nội dung | Kỳ vọng |
|---|---|---|---|
| T1-01 | Unit | Tạo task title rỗng | Ném ValidationException |
| T1-02 | Unit | Chuyển status sang DONE | `completed_at` được ghi |
| T1-03 | Unit | Chuyển DONE về TODO | `completed_at` bị xóa về null |
| T1-04 | Unit | Tạo subtask của subtask | Ném DomainException |
| T1-05 | Unit | Task quá hạn | `isOverdue = true` khi `due_at < now` và status ≠ DONE |
| T1-06 | Unit | Task DONE dù quá hạn | `isOverdue = false` |
| T1-07 | Unit | Specification lọc nhiều điều kiện | Query sinh ra đúng |
| T1-08 | Integration | CRUD task đầy đủ | Mỗi bước trả đúng status code |
| T1-09 | Integration | Soft delete rồi restore | Task quay lại danh sách |
| T1-10 | Integration | Xóa project có task | Task còn tồn tại, `project_id` = null |
| T1-11 | Integration | Tạo tag trùng tên | 409 CONFLICT |
| T1-12 | Integration | 1.000 task, load trang đầu | ≤ 500 ms |
| T1-13 | Frontend | Form validate title rỗng | Hiện lỗi inline, không gọi API |
| T1-14 | Frontend | Kéo thả Kanban | Gọi đúng endpoint status |

### Phase 2

| ID | Loại | Nội dung | Kỳ vọng |
|---|---|---|---|
| T2-01 | Unit | Expand `FREQ=DAILY;COUNT=5` | Đúng 5 instance |
| T2-02 | Unit | Expand `FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=...` | Chỉ thứ 2 và thứ 4 trong khoảng |
| T2-03 | Unit | Expand `FREQ=MONTHLY;BYMONTHDAY=31` | Bỏ qua tháng không có ngày 31 |
| T2-04 | Unit | Expand RRULE vô hạn | Dừng ở 500 instance, không treo |
| T2-05 | Unit | Expand với exception cancelled | Instance đó bị loại khỏi kết quả |
| T2-06 | Unit | Expand với exception sửa giờ | Instance dùng giờ mới |
| T2-07 | Unit | Split series (THIS_AND_FOLLOWING) | Master có UNTIL đúng, event mới bắt đầu đúng |
| T2-08 | Unit | Validate end_at ≤ start_at | Ném ValidationException |
| T2-09 | Unit | Tính `trigger_at` từ `offset_minutes` | Đúng phép trừ, đúng timezone |
| T2-10 | Unit | Scheduler với clock giả | Reminder đúng hạn được bắn, chưa đến hạn thì không |
| T2-11 | Unit | Snooze | Reminder cũ SNOOZED, reminder mới PENDING đúng thời điểm |
| T2-12 | Unit | Reminder quá 24h | Chuyển EXPIRED |
| T2-13 | Integration | Tạo event lặp rồi query theo khoảng | Số instance đúng |
| T2-14 | Integration | Xóa 1 instance | Tạo exception, các instance khác còn |
| T2-15 | Integration | Lịch 200 instance | ≤ 500 ms |

### Phase 3

| ID | Loại | Nội dung | Kỳ vọng |
|---|---|---|---|
| T3-01 | Unit | `Money` từ số âm | Ném IllegalArgumentException |
| T3-02 | Unit | `Money` không có constructor nhận double | Kiểm bằng reflection |
| T3-03 | Unit | Số dư ví chỉ có INCOME | initial + tổng thu |
| T3-04 | Unit | Số dư ví hỗn hợp mọi loại giao dịch | Đúng công thức đầy đủ |
| T3-05 | Unit | TRANSFER ảnh hưởng 2 ví | Nguồn giảm, đích tăng cùng số tiền |
| T3-06 | Unit | TRANSFER cùng một ví | Ném ValidationException |
| T3-07 | Unit | Giao dịch INCOME với danh mục EXPENSE | Ném ValidationException |
| T3-08 | Unit | Budget MONTHLY, giao dịch ngày cuối tháng | Tính vào đúng chu kỳ |
| T3-09 | Unit | Budget WEEKLY, tuần bắt đầu thứ 2 | Biên chu kỳ đúng |
| T3-10 | Unit | Usage = 0.79 / 0.80 / 0.99 / 1.00 / 1.5 | Level lần lượt null/WARNING/WARNING/EXCEEDED/EXCEEDED |
| T3-11 | Integration | 1.000 giao dịch ngẫu nhiên | Tổng số dư khớp tuyệt đối, không sai 1 đồng |
| T3-12 | Integration | Lỗi khi ghi giao dịch | Rollback sạch, không có bản ghi một phần |
| T3-13 | Integration | Sửa số tiền giao dịch | Số dư tính lại đúng |
| T3-14 | Integration | 5.000 giao dịch, load trang đầu | ≤ 500 ms |
| T3-15 | Integration | Summary groupBy CATEGORY | Tổng các nhóm = tổng chi trong khoảng |
| T3-16 | Frontend | Gõ `1500000` vào ô tiền | Hiển thị `1.500.000`, gửi lên API là `1500000` |

### Phase 4

| ID | Loại | Nội dung | Kỳ vọng |
|---|---|---|---|
| T4-01 | Unit | Sanitize JSON có ```json fence | JSON sạch |
| T4-02 | Unit | Sanitize JSON có text thừa trước và sau | Lấy đúng phần JSON |
| T4-03 | Unit | Sanitize chuỗi không phải JSON | Ném InvalidJsonException |
| T4-04 | Unit | Sanitize JSON có BOM | Parse được |
| T4-05 | Unit | RuleBased `45k` | 45.000 |
| T4-06 | Unit | RuleBased `1tr2` | 1.200.000 |
| T4-07 | Unit | RuleBased `2 triệu rưỡi` | 2.500.000 |
| T4-08 | Unit | RuleBased `3 trăm rưỡi` | 350.000 |
| T4-09 | Unit | RuleBased `500` (không hậu tố) | 500.000 |
| T4-10 | Unit | RuleBased `1.500.000đ` | 1.500.000 |
| T4-11 | Unit | RuleBased `thứ 5 tuần sau` | Đúng ngày, tính từ clock giả |
| T4-12 | Unit | RuleBased `cuối tháng` | Ngày cuối tháng hiện tại |
| T4-13 | Unit | RuleBased `2h chiều` | 14:00 |
| T4-14 | Unit | RuleBased nhận diện INCOME từ `lương` | type = INCOME |
| T4-15 | Unit | RuleBased 30 câu tiếng Việt mẫu | Bảng kỳ vọng riêng, đạt ≥ 80% đúng |
| T4-16 | Unit | Validate JSON thiếu trường bắt buộc | Ném SchemaValidationException |
| T4-17 | Integration | AiClient mock timeout | Fallback sang RuleBased, `source = RULE` |
| T4-18 | Integration | AiClient mock trả JSON sai schema | Retry 1 lần, vẫn sai thì fallback |
| T4-19 | Integration | AiClient mock trả 401 | error code AUTH, fallback |
| T4-20 | Integration | Mọi lần gọi AI | Có đúng 1 bản ghi trong `ai_parse_log` |
| T4-21 | **Bảo mật** | Quét code: `NlParseService` không gọi `TransactionService.create` | Không tìm thấy tham chiếu |
| T4-22 | **Bảo mật** | Grep `data/` và `logs/` tìm API key | Không xuất hiện plaintext |
| T4-23 | Integration | AI tắt trong settings | `/ai/parse` trả rule-based, không gọi API |

### Phase 5

| ID | Loại | Nội dung | Kỳ vọng |
|---|---|---|---|
| T5-01 | Unit | Aggregator khi tuần trước không có dữ liệu | Không chia cho 0, trả null thay vì Infinity |
| T5-02 | Unit | Aggregator tính % thay đổi | Đúng công thức |
| T5-03 | Unit | Insight khi < 7 ngày dữ liệu | Trả rỗng, không gọi AI |
| T5-04 | Unit | Import parse ngày `dd/MM/yyyy` và `yyyy-MM-dd` | Cả hai đúng |
| T5-05 | Unit | Import parse số tiền `1,500,000` và `1.500.000` | Cả hai ra 1500000 |
| T5-06 | Unit | Phát hiện trùng: cùng tiền, lệch 1 ngày | Đánh dấu nghi trùng |
| T5-07 | Unit | Phát hiện trùng: cùng tiền, lệch 3 ngày | Không đánh dấu |
| T5-08 | Integration | Import 500 dòng | Tất cả được ghi, đúng số lượng |
| T5-09 | Integration | Import lỗi ở dòng 250 | Rollback, DB không có dòng nào |
| T5-10 | Integration | Import file UTF-8 có dấu tiếng Việt | Ghi chú không lỗi font |
| T5-11 | Integration | Export Excel | File mở được bằng POI, công thức tồn tại |
| T5-12 | Integration | Export PDF | File hợp lệ, chứa ký tự tiếng Việt |
| T5-13 | Integration | Backup rồi restore | Dữ liệu khôi phục nguyên vẹn |
| T5-14 | Integration | Backup lần thứ 31 | File cũ nhất bị xóa, còn đúng 30 |

---

## 4. Mẫu file UAT cho user

Agent tạo file này ở `docs/uat/phase-<n>-UAT.md` sau mỗi phase, theo đúng mẫu:

```markdown
# UAT — Phase <n>: <Tên phase>

**Cách chạy ứng dụng:**
```bash
<lệnh cụ thể>
```

**Thời gian ước tính:** ~<n> phút

---

## UAT-<n>-01: <Tên kịch bản>

**Mục đích:** Kiểm tra <yêu cầu FR-xxx>

**Chuẩn bị:** <trạng thái ban đầu cần có>

**Các bước:**
1. ...
2. ...
3. ...

**Kết quả mong đợi:**
- ...
- ...

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## Tổng kết

| Case | Đạt | Không đạt |
|---|---|---|
| UAT-<n>-01 | ☐ | ☐ |

**Nếu có case không đạt**, gõ lệnh:
`FIX PHASE <n>: <mô tả vấn đề>`

**Nếu tất cả đạt**, gõ lệnh:
`START PHASE <n+1>`
```

---

## 5. Kịch bản UAT mẫu (Phase 1)

Để agent hiểu mức độ chi tiết cần đạt:

```markdown
## UAT-1-03: Task quá hạn hiển thị nổi bật

**Mục đích:** Kiểm tra FR-TSK-12

**Chuẩn bị:** Ứng dụng đang mở ở màn hình Tasks

**Các bước:**
1. Bấm "Thêm task"
2. Nhập tiêu đề "Task thử quá hạn"
3. Chọn ngày đến hạn là ngày hôm qua
4. Bấm "Lưu"
5. Quan sát task trong danh sách
6. Đổi trạng thái task sang "Hoàn thành"
7. Quan sát lại

**Kết quả mong đợi:**
- Ở bước 3, hệ thống hiện cảnh báo "Ngày đến hạn đã qua" nhưng vẫn cho lưu
- Ở bước 5, task hiển thị với màu đỏ và icon cảnh báo
- Ở bước 7, sau khi hoàn thành, task KHÔNG còn hiển thị màu đỏ nữa

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
```

---

## 6. Test hiệu năng

Chạy ở cuối Phase 3 và Phase 6.

| ID | Chỉ tiêu | Cách đo | Ngưỡng |
|---|---|---|---|
| P-01 | Khởi động app | Bấm icon → giao diện tương tác được | ≤ 6 s |
| P-02 | CRUD p95 | 100 request liên tiếp, lấy p95 | ≤ 200 ms |
| P-03 | Load 5.000 giao dịch | Seed dữ liệu rồi mở màn hình | ≤ 500 ms |
| P-04 | AI parse | 10 lần gọi, lấy trung bình | ≤ 5 s |
| P-05 | RAM idle | Task Manager sau 5 phút không thao tác | ≤ 600 MB |
| P-06 | Độ trễ reminder | Đặt reminder, đo bằng đồng hồ | ≤ 30 s |

**Script seed dữ liệu test:** agent phải viết `backend/src/test/java/.../DataSeeder.java` có thể chạy độc lập để sinh 5.000 giao dịch, 1.000 task, 200 event phục vụ đo hiệu năng.

---

## 7. Test hồi quy

Trước khi bắt đầu mỗi phase mới, agent chạy lại toàn bộ test của các phase trước. Nếu có test cũ fail, **phải sửa trước khi làm tính năng mới**, và báo cáo trong phần "Nợ kỹ thuật".

```bash
cd backend && ./mvnw test
cd frontend && npm run test
```
