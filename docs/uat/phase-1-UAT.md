# UAT — Phase 1: Task & Project

**Cách chạy ứng dụng:**

```bash
cd D:\LifeHub
npm run dev
```

Sau khi cửa sổ mở, bấm **Công việc** ở sidebar bên trái.

**Thời gian ước tính:** ~25 phút

---

## UAT-1-01: Tạo task đầy đủ thuộc tính

**Mục đích:** Kiểm tra FR-TSK-01, FR-TSK-05, FR-TSK-06

**Chuẩn bị:** Vào **Dự án & Nhãn**, tạo dự án `LifeHub` (màu tím) và nhãn `gấp` (màu đỏ). Quay lại **Công việc**.

**Các bước:**
1. Bấm **Thêm task**
2. Nhập tiêu đề `Hoàn thiện tài liệu SRS`
3. Nhập mô tả `Viết đủ use case và ERD`
4. Chọn độ ưu tiên **Cao**
5. Chọn ngày đến hạn là ngày mai
6. Chọn dự án `LifeHub`
7. Nhập ước lượng `180`
8. Bấm vào chip nhãn `gấp`
9. Bấm **Lưu**

**Kết quả mong đợi:**
- Con trỏ đặt sẵn ở ô tiêu đề ngay khi form mở
- Sau khi lưu, form đóng và task xuất hiện trong danh sách
- Task hiển thị: badge **Cao**, chấm màu tím kèm chữ `LifeHub`, chip đỏ `gấp`, ngày đến hạn
- Có toast "Đã tạo task"

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-1-02: Form chặn tiêu đề rỗng, cảnh báo ngày quá khứ

**Mục đích:** Kiểm tra UC-01 luồng ngoại lệ E1 và E2

**Các bước:**
1. Bấm **Thêm task**
2. Không nhập gì, bấm **Lưu**
3. Quan sát
4. Nhập tiêu đề `Task thử`, rồi chọn ngày đến hạn là **hôm qua**
5. Quan sát vùng dưới ô ngày
6. Bấm **Lưu**

**Kết quả mong đợi:**
- Bước 3: hiện lỗi đỏ **"Tiêu đề không được để trống"** ngay dưới ô tiêu đề, form **không đóng**, viền ô tiêu đề chuyển đỏ
- Bước 5: hiện dòng chữ vàng **"Ngày đến hạn đã qua, bạn có chắc không?"**
- Bước 6: vẫn **lưu được** — đây là cảnh báo, không phải chặn

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-1-03: Task quá hạn hiển thị nổi bật

**Mục đích:** Kiểm tra FR-TSK-12

**Chuẩn bị:** Ứng dụng đang mở ở màn hình Công việc

**Các bước:**
1. Bấm **Thêm task**
2. Nhập tiêu đề `Task thử quá hạn`
3. Chọn ngày đến hạn là ngày hôm qua
4. Bấm **Lưu**
5. Quan sát task trong danh sách
6. Đổi trạng thái task sang **Hoàn thành** bằng ô chọn bên phải
7. Quan sát lại

**Kết quả mong đợi:**
- Ở bước 3, hệ thống hiện cảnh báo "Ngày đến hạn đã qua" nhưng vẫn cho lưu
- Ở bước 5, task có **viền đỏ**, ngày đến hạn màu đỏ và có **icon tam giác cảnh báo**
- Ở bước 7, sau khi hoàn thành, task **không còn** dấu hiệu quá hạn nào, tiêu đề bị gạch ngang

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-1-04: Subtask chỉ một cấp

**Mục đích:** Kiểm tra FR-TSK-11

**Các bước:**
1. Đưa chuột vào một task bất kỳ trong danh sách
2. Bấm icon **+** hiện ra ở góc phải task
3. Nhập tiêu đề `Bước con thứ nhất`, bấm **Lưu**
4. Quan sát danh sách
5. Đưa chuột vào **subtask vừa tạo** và quan sát các icon hiện ra

**Kết quả mong đợi:**
- Subtask hiện thụt vào bên trong, có đường kẻ dọc bên trái
- Task cha hiện số đếm dạng `0/1` kèm icon cây thư mục
- Có mũi tên thu gọn / mở rộng ở bên trái task cha, bấm vào đóng mở được
- Ở bước 5, subtask **không có** icon **+** — không thể tạo subtask cho subtask

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-1-05: Kanban kéo thả đổi trạng thái

**Mục đích:** Kiểm tra FR-TSK-07 và tiêu chí "Kéo thả trên Kanban đổi trạng thái và lưu vào DB"

**Chuẩn bị:** Có ít nhất 3 task

**Các bước:**
1. Bấm nút **Kanban** ở góc trên bên phải
2. Quan sát 4 cột: Cần làm, Đang làm, Hoàn thành, Đã hủy
3. Kéo một task từ cột **Cần làm** sang cột **Đang làm**
4. Bấm nút **Danh sách** rồi bấm lại **Kanban**
5. Đóng hẳn ứng dụng, mở lại, vào Kanban

**Kết quả mong đợi:**
- Card đi theo con trỏ khi kéo, cột đích sáng lên khi thả vào được
- Task chuyển cột **ngay lập tức**, không có độ trễ chờ mạng
- Số đếm trên đầu mỗi cột cập nhật đúng
- Ở bước 4 và 5, task vẫn ở cột mới — nghĩa là đã lưu xuống database

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-1-06: Lọc kết hợp nhiều điều kiện

**Mục đích:** Kiểm tra FR-TSK-08, FR-TSK-09, FR-TSK-10

**Chuẩn bị:** Có ít nhất 5 task, thuộc 2 dự án khác nhau, độ ưu tiên khác nhau

**Các bước:**
1. Ở thanh lọc, chọn dự án `LifeHub`
2. Bấm chip trạng thái **Cần làm**
3. Bấm chip độ ưu tiên **Cao**
4. Quan sát số task ở dòng mô tả dưới tiêu đề "Công việc"
5. Gõ một từ khóa vào ô **Tìm kiếm**
6. Đổi ô **Sắp xếp** sang "Ngày đến hạn gần nhất"
7. Bấm nút **Xóa bộ lọc**

**Kết quả mong đợi:**
- Mỗi lần thêm điều kiện, danh sách **thu hẹp lại** (các điều kiện cộng dồn với nhau, không thay thế nhau)
- Ô tìm kiếm có **độ trễ nhẹ** rồi mới lọc — không gọi lại sau từng ký tự
- Nút "Xóa bộ lọc" hiện số điều kiện đang bật, bấm vào thì mọi thứ trở lại đầy đủ
- Sắp xếp đổi đúng thứ tự hiển thị

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-1-07: Xóa task và Hoàn tác trong 5 giây

**Mục đích:** Kiểm tra FR-TSK-03 và NFR-USE-04

**Các bước:**
1. Đưa chuột vào một task, bấm icon **thùng rác**
2. Quan sát góc dưới bên phải màn hình
3. **Trong vòng 5 giây**, bấm nút **Hoàn tác**
4. Quan sát danh sách
5. Xóa một task khác, lần này **chờ quá 5 giây** không bấm gì

**Kết quả mong đợi:**
- Task biến mất khỏi danh sách ngay
- Toast hiện ra ghi `Đã xóa "<tên task>"` kèm nút **Hoàn tác**
- Bước 3–4: task **quay lại đúng vị trí cũ**, giữ nguyên mọi thuộc tính (nhãn, dự án, trạng thái)
- Bước 5: toast tự biến mất sau 5 giây, task không quay lại

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-1-08: Xóa dự án thì task bên trong vẫn còn

**Mục đích:** Kiểm tra FR-PRJ-02 và tiêu chí "Xóa project thì task bên trong vẫn còn, project_id thành null"

**Chuẩn bị:** Dự án `LifeHub` đang có ít nhất 2 task

**Các bước:**
1. Vào màn hình **Dự án & Nhãn**
2. Quan sát thanh tiến độ của `LifeHub`
3. Bấm icon **thùng rác** ở dòng `LifeHub`
4. Đọc kỹ nội dung hộp thoại xác nhận rồi bấm OK
5. Quay lại màn hình **Công việc**

**Kết quả mong đợi:**
- Bước 2: thanh tiến độ hiện đúng tỉ lệ, bên phải ghi dạng `1/4 · 25%`
- Bước 4: hộp thoại nói rõ **số task sẽ được giữ lại và chuyển về "không dự án"**
- Bước 5: các task đó **vẫn còn trong danh sách**, chỉ mất chấm màu và tên dự án
- Bộ lọc dự án không còn `LifeHub` nữa

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-1-09: Quản lý nhãn

**Mục đích:** Kiểm tra FR-PRJ-04, FR-TSK-06

**Các bước:**
1. Vào **Dự án & Nhãn**, phần **Nhãn**
2. Tạo nhãn tên `học tập`, chọn màu xanh lá
3. Thử tạo lại nhãn cũng tên `học tập`
4. Thử tạo nhãn tên `HỌC TẬP` (viết hoa)
5. Bấm vào ô màu tròn bên trái một nhãn, chọn màu khác
6. Xóa một nhãn đang được dùng bởi ít nhất một task
7. Quay lại **Công việc**

**Kết quả mong đợi:**
- Bước 3 và 4: hiện toast lỗi **"Tên nhãn đã tồn tại"** — trùng tên không phân biệt hoa thường, kể cả với chữ có dấu
- Số bên cạnh mỗi nhãn là số task đang dùng nhãn đó
- Bước 5: màu đổi ngay, và đổi cả trên các task đang gắn nhãn đó
- Bước 6: hộp thoại nói rõ nhãn sẽ bị gỡ khỏi bao nhiêu task
- Bước 7: các task đó **vẫn còn**, chỉ mất chip nhãn

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-1-10: Phím tắt

**Mục đích:** Kiểm tra NFR-USE-01

**Các bước:**
1. Ở màn hình Công việc, **không** đang gõ ở ô nào, nhấn phím `N`
2. Nhấn `Esc`
3. Nhấn phím `/`
4. Gõ vài chữ vào ô tìm kiếm, kiểm tra chữ `n` có làm mở form không
5. Nhấn `N` lần nữa để mở form, nhập tiêu đề, nhấn `Ctrl+Enter`

**Kết quả mong đợi:**
- Bước 1: form **Thêm task** mở ra
- Bước 2: form đóng lại
- Bước 3: con trỏ nhảy vào ô **Tìm kiếm**
- Bước 4: gõ chữ `n` trong ô tìm kiếm **không** mở form — phím tắt bị tắt khi đang nhập liệu
- Bước 5: task được lưu mà không cần bấm nút

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-1-11: Hiệu năng với 1.000 task

**Mục đích:** Kiểm tra tiêu chí "Danh sách 1.000 task load ≤ 500 ms"

> Test tự động **T1-12** đã kiểm điều này. Case thủ công này chỉ để bạn cảm nhận thực tế
> nếu muốn; có thể bỏ qua.

**Các bước:**
1. Chạy `npm run backend:test` và tìm dòng kết quả của `TaskPerformanceIT`
2. (Tùy chọn) Tự tạo nhiều task rồi cuộn danh sách

**Kết quả mong đợi:**
- `TaskPerformanceIT` PASS — trang đầu 50 dòng trong 1.000 task trả về trong ≤ 500 ms
- Cuộn danh sách mượt, không giật

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-1-12: Test tự động chạy sạch

**Các bước:**
1. Đóng ứng dụng
2. Chạy `npm test`

**Kết quả mong đợi:**
- Backend: `Tests run: 137, Failures: 0, Errors: 0` và `BUILD SUCCESS`
- Frontend: `Test Files 5 passed`, `Tests 28 passed`

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## Tổng kết

| Case | Nội dung | Đạt | Không đạt |
|---|---|---|---|
| UAT-1-01 | Tạo task đầy đủ thuộc tính | ☐ | ☐ |
| UAT-1-02 | Chặn tiêu đề rỗng, cảnh báo ngày quá khứ | ☐ | ☐ |
| UAT-1-03 | Task quá hạn hiển thị nổi bật | ☐ | ☐ |
| UAT-1-04 | Subtask chỉ một cấp | ☐ | ☐ |
| UAT-1-05 | Kanban kéo thả đổi trạng thái | ☐ | ☐ |
| UAT-1-06 | Lọc kết hợp nhiều điều kiện | ☐ | ☐ |
| UAT-1-07 | Xóa task và Hoàn tác trong 5 giây | ☐ | ☐ |
| UAT-1-08 | Xóa dự án thì task vẫn còn | ☐ | ☐ |
| UAT-1-09 | Quản lý nhãn | ☐ | ☐ |
| UAT-1-10 | Phím tắt | ☐ | ☐ |
| UAT-1-11 | Hiệu năng 1.000 task | ☐ | ☐ |
| UAT-1-12 | Test tự động chạy sạch | ☐ | ☐ |

**Nếu có case không đạt**, gõ lệnh:
`FIX PHASE 1: <mô tả vấn đề>`

**Nếu tất cả đạt**, gõ lệnh:
`START PHASE 2`
