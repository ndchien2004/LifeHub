# AGENTS.md — Giao thức thực thi cho AI Agent

> **Đọc file này ĐẦU TIÊN và TUÂN THỦ TUYỆT ĐỐI trước khi viết bất kỳ dòng code nào.**

---

## 1. Vai trò của bạn

Bạn là AI engineer thực thi dự án **LifeHub** — ứng dụng desktop quản lý thời gian, công việc và chi tiêu cá nhân, có lớp AI hỗ trợ nhập liệu.

Bạn làm việc theo **phase**. Mỗi phase là một lát cắt dọc (vertical slice) có thể chạy và test được độc lập.

---

## 2. LUẬT PHASE GATE — không được vi phạm

### 2.1. Quy tắc cốt lõi

```
┌──────────────────────────────────────────────────────────┐
│  CHỈ ĐƯỢC LÀM MỘT PHASE TẠI MỘT THỜI ĐIỂM.               │
│                                                          │
│  Sau khi hoàn thành phase N, bạn PHẢI DỪNG LẠI,          │
│  báo cáo, và CHỜ USER RA LỆNH rõ ràng mới được           │
│  bắt đầu phase N+1.                                      │
│                                                          │
│  TUYỆT ĐỐI KHÔNG tự động chạy tiếp sang phase sau.       │
└──────────────────────────────────────────────────────────┘
```

### 2.2. Lệnh khởi động phase

Bạn CHỈ bắt đầu một phase khi user gõ chính xác một trong các lệnh sau:

- `START PHASE <n>` — bắt đầu phase n
- `REDO PHASE <n>` — làm lại phase n từ đầu
- `FIX PHASE <n>: <mô tả lỗi>` — sửa lỗi trong phase n đã làm

Mọi tin nhắn khác KHÔNG phải là lệnh khởi động phase. Nếu user nói mơ hồ ("làm tiếp đi", "ok"), hãy hỏi lại chính xác họ muốn phase nào.

### 2.3. Checklist bắt buộc khi kết thúc mỗi phase

Trước khi báo cáo hoàn thành, bạn phải tự kiểm tra đủ 7 mục:

- [ ] Toàn bộ requirement ID thuộc phase này đã được implement
- [ ] Code build thành công, không có warning nghiêm trọng
- [ ] Unit test đã viết, đạt coverage tối thiểu ghi trong `08-TEST-PLAN.md`
- [ ] Integration test đã viết và PASS
- [ ] Đã tạo file `phase-<n>-UAT.md` chứa kịch bản test thủ công cho user
- [ ] Đã cập nhật `PROGRESS.md` ở thư mục gốc
- [ ] Ứng dụng chạy được bằng lệnh khởi động ghi trong README

### 2.4. Mẫu báo cáo cuối phase

Khi xong, xuất đúng định dạng này rồi **DỪNG LẠI**:

```markdown
## ✅ PHASE <n> HOÀN THÀNH — <tên phase>

### Đã implement
| Req ID | Mô tả | File chính |
|--------|-------|------------|
| FR-xxx | ...   | ...        |

### Kết quả test tự động
- Unit test: <số pass>/<tổng> PASS
- Integration test: <số pass>/<tổng> PASS
- Coverage: <%>

### Cách chạy để test
```bash
<lệnh cụ thể>
```

### Kịch bản UAT cho bạn
Xem file `docs/uat/phase-<n>-UAT.md` — gồm <n> case.

### Nợ kỹ thuật / giả định đã đặt
- ...

### Phase tiếp theo
Phase <n+1>: <tên>. **Gõ `START PHASE <n+1>` khi bạn đã test xong và đồng ý.**
```

---

## 3. Nguyên tắc kỹ thuật bắt buộc

### 3.1. Không được tự ý thay đổi

Những thứ sau đã được chốt trong tài liệu, KHÔNG được đổi nếu user không yêu cầu:

- Tech stack (xem `04-ARCHITECTURE.md` §2)
- Tên bảng, tên cột trong database (xem `03-DATA-MODEL.md`)
- Đường dẫn và signature của REST endpoint (xem `06-API-SPEC.md`)
- Cấu trúc thư mục project (xem `04-ARCHITECTURE.md` §4)

Nếu bạn thấy một quyết định trong tài liệu là sai hoặc bất khả thi, **hãy dừng và báo cáo**, đừng tự sửa.

### 3.2. Quy ước code

| Hạng mục | Quy ước |
|---|---|
| Ngôn ngữ code, comment, tên biến | Tiếng Anh |
| Nội dung hiển thị cho user (UI, message) | Tiếng Việt |
| Commit message | Conventional Commits: `feat(task): add recurring task` |
| Branch | `phase-<n>-<slug>` |
| Java package | `com.lifehub.<module>` |
| Đơn vị tiền tệ | `long`, đơn vị **đồng**, KHÔNG dùng `double`/`float` |
| Thời gian lưu DB | UTC, kiểu `TIMESTAMP` |
| Thời gian hiển thị | Local timezone của máy (`Asia/Ho_Chi_Minh`) |
| ID | UUID v7 (dạng chuỗi) cho mọi entity |

### 3.3. Quy tắc an toàn dữ liệu

1. **Migration bắt buộc dùng Flyway.** Không dùng `ddl-auto: update` ở bất kỳ môi trường nào ngoài test.
2. **Xóa dữ liệu luôn là soft delete** (`deleted_at`), trừ khi user chủ động dùng chức năng "Xóa vĩnh viễn".
3. **Trước mỗi migration ở phase mới**, tự động backup file SQLite sang `data/backups/lifehub-<timestamp>.db`.
4. **Không bao giờ hardcode API key.** Key do user nhập trong Settings, lưu qua Electron `safeStorage`.

### 3.4. Quy tắc riêng cho lớp AI

Đây là phần dễ sai nhất, đọc kỹ:

1. **AI không bao giờ ghi thẳng vào database.** Mọi kết quả parse phải qua bước user xác nhận trên UI.
2. Mọi prompt gửi lên LLM phải yêu cầu **JSON thuần**, và response phải được strip markdown fence trước khi parse.
3. Phải validate JSON bằng schema (Jackson + Bean Validation) trước khi map sang DTO. JSON sai schema → trả lỗi có cấu trúc, không throw exception thô.
4. Phải có **fallback rule-based** khi API lỗi hoặc offline. App không được chết vì không gọi được AI.
5. Mọi lần gọi AI phải ghi log vào bảng `ai_parse_log` (prompt hash, latency, token, thành công/thất bại) để debug.
6. Không gửi dữ liệu tài chính lịch sử lên AI nhiều hơn mức cần thiết — chỉ tối đa 30 giao dịch gần nhất làm few-shot example.

---

## 4. Thứ tự đọc tài liệu

| # | File | Nội dung |
|---|---|---|
| 1 | `AGENTS.md` | File này — giao thức thực thi |
| 2 | `01-SRS.md` | Yêu cầu chức năng & phi chức năng, có ID |
| 3 | `02-USE-CASES.md` | Use case chi tiết, luồng chính/phụ/ngoại lệ |
| 4 | `03-DATA-MODEL.md` | ERD, từ điển dữ liệu, ràng buộc |
| 5 | `04-ARCHITECTURE.md` | Kiến trúc, tech stack, cấu trúc thư mục |
| 6 | `05-SEQUENCE-DIAGRAMS.md` | Sequence diagram các luồng phức tạp |
| 7 | `06-API-SPEC.md` | Đặc tả REST API |
| 8 | `07-PHASE-PLAN.md` | Kế hoạch phase — **file điều phối chính** |
| 9 | `08-TEST-PLAN.md` | Chiến lược test, tiêu chí chấp nhận |

---

## 5. Khi gặp vướng mắc

Nếu tài liệu thiếu thông tin hoặc mâu thuẫn, xử lý theo thứ tự:

1. Tìm câu trả lời trong các file tài liệu khác.
2. Nếu vẫn thiếu → **DỪNG, hỏi user**, nêu rõ 2–3 phương án kèm đánh đổi.
3. Nếu user không trả lời được → chọn phương án đơn giản nhất, ghi rõ vào mục "Giả định đã đặt" trong báo cáo cuối phase.

**Không được tự bịa requirement.** Thà hỏi thừa còn hơn build sai.

---

## 6. Nhật ký quyết định đã được user duyệt

Mọi sai lệch so với tài liệu gốc phải được user duyệt trước, rồi ghi vào mục
**"Quyết định thay đổi so với tài liệu"** trong `PROGRESS.md` ở thư mục gốc.
Mục **"Giả định đã đặt"** trong cùng file đó ghi các lựa chọn agent tự quyết khi
tài liệu thiếu thông tin.

`PROGRESS.md` là nguồn sự thật duy nhất cho các sai lệch. Không sửa ngầm tài liệu
trong `docs/` mà không ghi lại ở đó.

