# UAT — Phase 3: Finance

**Cách chạy ứng dụng:**

```bash
cd D:\LifeHub
npm run dev
```

Sau khi cửa sổ mở, bấm **Tài chính** ở sidebar bên trái.

**Thời gian ước tính:** ~40 phút
**Mẹo chung:**
- Ở màn hình Tài chính, bấm phím **N** để mở nhanh form thêm giao dịch.
- Trong form, **Ctrl+Enter** để lưu, **Esc** để đóng.
- Màn hình Tài chính có 6 thẻ: **Giao dịch · Báo cáo · Ngân sách · Ví · Danh mục · Định kỳ**.

> **Lưu ý trước khi test:** lần khởi động đầu tiên sau Phase 3 sẽ chạy migration `V4` và `V5`
> trên file `data/lifehub.db`. Nếu bạn có dữ liệu Phase 1–2 muốn giữ, hãy sao chép file đó
> sang chỗ khác trước khi chạy. (Tính năng backup tự động thuộc Phase 5.)

---

## UAT-3-01: Bộ danh mục mặc định tiếng Việt được tạo sẵn

**Mục đích:** Kiểm tra FR-FIN-02, FR-FIN-03

**Các bước:**
1. Mở **Tài chính** → thẻ **Danh mục**
2. Xem danh sách ở chế độ **Danh mục chi**
3. Bấm **Danh mục thu**
4. Thử bấm nút thùng rác trên danh mục `Ăn uống`

**Kết quả mong đợi:**
- Danh mục chi có đủ 9 nhóm: Ăn uống, Di chuyển, Nhà ở, Mua sắm, Sức khỏe, Giải trí, Học tập, Subscription, Khác
- `Ăn uống` có 3 danh mục con: Ăn ngoài, Đi chợ, Cà phê — hiển thị thụt vào bên trong
- Danh mục thu có 5 mục: Lương, Thưởng, Freelance, Đầu tư, Khác
- Mỗi danh mục có một chấm màu riêng
- Danh mục hệ thống có biểu tượng ổ khóa và **không có nút xóa**

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-3-02: Tạo ví và xem tổng tài sản

**Mục đích:** Kiểm tra FR-FIN-01, FR-FIN-07

**Các bước:**
1. Vào thẻ **Ví**. Nếu chưa có ví nào, bấm **Tạo ví đầu tiên**
2. Tạo ví `Tiền mặt`, loại **Tiền mặt**, số dư ban đầu gõ `5000000`
3. Bấm **Lưu**
4. Tạo tiếp ví `Vietcombank`, loại **Ngân hàng**, số dư ban đầu `20000000`
5. Tạo tiếp ví `Thẻ tín dụng`, loại **Thẻ tín dụng**, số dư ban đầu `3000000`, **tích ô "Đang nợ (số dư âm)"**

**Kết quả mong đợi:**
- Khi gõ `5000000` ô số dư hiển thị ngay `5.000.000`
- Ví `Tiền mặt` là ví đầu tiên nên tự động có dấu sao "ví mặc định"
- Thẻ tín dụng hiển thị số dư **-3.000.000 ₫** bằng màu đỏ
- Ô **Tổng tài sản** phía trên bằng `22.000.000 ₫` (5 + 20 − 3 triệu)

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-3-03: Ô nhập tiền tự định dạng khi gõ

**Mục đích:** Kiểm tra FR-FIN-06, tiêu chí "gõ 1500000 hiện 1.500.000"

**Các bước:**
1. Bấm phím **N** (hoặc nút **Thêm giao dịch**)
2. Gõ từng chữ số vào ô **Số tiền**: `1`, `5`, `0`, `0`, `0`, `0`, `0`
3. Xóa hết, thử dán chuỗi `1.500.000 ₫` vào ô đó
4. Xóa hết, thử gõ `45,5` rồi `abc`

**Kết quả mong đợi:**
- Sau mỗi phím, ô hiển thị lần lượt: `1` → `15` → `150` → `1.500` → `15.000` → `150.000` → `1.500.000`
- Dán `1.500.000 ₫` vẫn ra `1.500.000`
- `45,5` thành `455` (không có phần thập phân), `abc` không nhập được gì
- Ô rỗng thì để trống chứ không tự thành `0`

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-3-04: Ghi giao dịch chi, số dư ví giảm đúng

**Mục đích:** Kiểm tra UC-06 luồng chính, FR-FIN-04, FR-FIN-07

**Các bước:**
1. Bấm **N**, giữ nguyên loại **Chi**
2. Số tiền `45000`
3. Ví: `Tiền mặt` (đã chọn sẵn vì là ví mặc định)
4. Danh mục: chọn `└ Cà phê`
5. Ghi chú: `Cà phê sáng với team`
6. Bấm **Lưu**
7. Sang thẻ **Ví**

**Kết quả mong đợi:**
- Ví `Tiền mặt` được chọn sẵn khi form vừa mở
- Có toast `Đã lưu giao dịch`
- Giao dịch xuất hiện ở đầu danh sách, nhóm dưới tiêu đề ngày hôm nay
- Dòng giao dịch ghi `Cà phê`, số tiền `−45.000 ₫` màu đỏ, dòng phụ có `Ví smoke`… ý là tên ví và ghi chú
- Tiêu đề ngày hiển thị tổng ròng của ngày đó
- Thẻ **Ví**: `Tiền mặt` còn `4.955.000 ₫`

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-3-05: Chuyển khoản giữa hai ví

**Mục đích:** Kiểm tra FR-FIN-05, UC-06 luồng phụ 5a

**Các bước:**
1. Bấm **N**, chọn loại **Chuyển khoản**
2. Quan sát form đổi hình dạng
3. Số tiền `2000000`, Ví nguồn `Vietcombank`, Ví đích `Tiền mặt`
4. Bấm **Lưu**
5. Sang thẻ **Ví**

**Kết quả mong đợi:**
- Khi chọn **Chuyển khoản**, ô **Danh mục** biến mất và ô **Ví đích** xuất hiện
- Nhãn ô ví đầu tiên đổi từ "Ví" thành "Ví nguồn"
- Sau khi lưu: `Vietcombank` giảm đúng 2.000.000, `Tiền mặt` tăng đúng 2.000.000
- **Tổng tài sản không đổi** so với trước khi chuyển
- Dòng giao dịch hiển thị `Vietcombank → Tiền mặt`, số tiền **không có dấu + hoặc −**

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-3-06: Chặn chuyển khoản cùng một ví và số tiền bằng 0

**Mục đích:** Kiểm tra UC-06 ngoại lệ E1 và E2

**Các bước:**
1. Bấm **N**, không nhập gì, bấm **Lưu**
2. Chọn loại **Chuyển khoản**, số tiền `100000`, Ví nguồn và Ví đích **cùng chọn** `Tiền mặt`
3. Bấm **Lưu**
4. Bấm **Esc**

**Kết quả mong đợi:**
- Bước 1: lỗi đỏ ngay dưới ô số tiền `Số tiền phải lớn hơn 0`
- Bước 3: lỗi đỏ dưới ô Ví đích `Ví nguồn và ví đích phải khác nhau`
- Cả hai trường hợp **không có giao dịch nào được tạo** (kiểm lại danh sách sau khi đóng form)
- Không có toast lỗi kỹ thuật, không có mã lỗi lạ hiện ra
- Bấm Esc đóng được form

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-3-07: Sửa số tiền thì số dư tính lại đúng

**Mục đích:** Kiểm tra FR-FIN-07, tiêu chí "sửa số tiền, số dư tính lại đúng"

**Các bước:**
1. Ghi lại số dư hiện tại của ví `Tiền mặt`
2. Về thẻ **Giao dịch**, rê chuột vào giao dịch `Cà phê sáng với team`, bấm nút bút chì
3. Đổi số tiền từ `45.000` thành `250000`
4. Bấm **Lưu**
5. Sang thẻ **Ví**

**Kết quả mong đợi:**
- Form mở ra đã điền sẵn đúng số tiền, ví, danh mục và ghi chú cũ
- Sau khi lưu, số dư ví `Tiền mặt` giảm thêm đúng **205.000 ₫** so với bước 1
- Danh sách giao dịch hiển thị `−250.000 ₫`

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-3-08: Xóa giao dịch và hoàn tác trong 5 giây

**Mục đích:** Kiểm tra NFR-USE-04, soft delete

**Các bước:**
1. Ghi lại số dư ví `Tiền mặt`
2. Rê chuột vào giao dịch bất kỳ, bấm nút thùng rác
3. Quan sát toast ở góc dưới phải, bấm **Hoàn tác** trong vòng 5 giây
4. Lặp lại bước 2, lần này **không bấm gì**, chờ toast tự tắt

**Kết quả mong đợi:**
- Bước 2: giao dịch biến mất khỏi danh sách, số dư ví tăng lại đúng bằng số tiền vừa xóa
- Bước 3: giao dịch quay lại đúng vị trí cũ, số dư trở về như bước 1
- Bước 4: sau khi toast tắt, giao dịch không quay lại nữa

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-3-09: Cảnh báo ngân sách ở mốc 80% và 100%

**Mục đích:** Kiểm tra FR-FIN-08, FR-FIN-09

**Các bước:**
1. Sang thẻ **Ngân sách**, bấm **Thêm ngân sách**
2. Danh mục `Ăn uống`, hạn mức `1000000`, chu kỳ **Hàng tháng**, bấm **Lưu**
3. Bấm **N**, tạo giao dịch chi `700000` vào danh mục `Ăn uống`
4. Quay lại thẻ **Ngân sách**, xem thanh tiến độ
5. Bấm **N**, tạo giao dịch chi `150000` vào danh mục **`└ Cà phê`** (danh mục con của Ăn uống)
6. Bấm **N**, tạo giao dịch chi `300000` vào danh mục `Ăn uống`

**Kết quả mong đợi:**
- Bước 3: **không** có cảnh báo (70% < 80%)
- Bước 4: thanh tiến độ **màu xanh lá**, ghi `700.000 ₫ / 1.000.000 ₫` và `70% · còn 300.000 ₫`
- Bước 5: hiện toast cảnh báo **vàng** kiểu "Sắp hết ngân sách…", thanh chuyển **màu vàng**, đạt `85%`
  → chứng minh chi ở **danh mục con** vẫn tính vào ngân sách của **danh mục cha**
- Bước 6: hiện toast cảnh báo **đỏ** "Vượt ngân sách…", thanh **màu đỏ**, ghi `115% · vượt 150.000 ₫`
- Dòng chu kỳ ghi đúng `01/<tháng này>` đến ngày cuối tháng này

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-3-09b: Ngân sách đặt trên danh mục con

**Mục đích:** Kiểm tra lỗi đã sửa sau bàn giao (xem `PROGRESS.md` → "Lỗi đã sửa sau bàn giao")

**Các bước:**
1. Vào thẻ **Ngân sách**, bấm **Thêm ngân sách**
2. Chọn danh mục **`└ Cà phê`** (danh mục **con**, có dấu `└` ở đầu)
3. Hạn mức `500000`, chu kỳ **Hàng tháng**, bấm **Lưu**
4. Bấm **Thêm ngân sách** lần nữa, chọn lại đúng `└ Cà phê` và chu kỳ **Hàng tháng**, bấm **Lưu**

**Kết quả mong đợi:**
- Bước 3: lưu thành công, **không** có thông báo "Đã xảy ra lỗi không mong muốn"
- Ngân sách mới hiện trong danh sách với tên `Cà phê`
- Bước 4: báo lỗi rõ ràng `Danh mục này đã có ngân sách cùng chu kỳ`, **dialog vẫn mở** và giữ nguyên
  những gì bạn đã chọn để sửa lại
- Đổi chu kỳ sang **Hàng tuần** rồi lưu thì được (ràng buộc chỉ tính theo từng chu kỳ)

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-3-10: Biểu đồ tròn khớp với danh sách giao dịch

**Mục đích:** Kiểm tra FR-FIN-10, tiêu chí "biểu đồ tròn khớp với tổng trong danh sách"

**Các bước:**
1. Sang thẻ **Báo cáo**, chọn khoảng **Tháng này**
2. Ghi lại con số ở ô **Tổng chi**
3. Rê chuột lên từng miếng của biểu đồ tròn, cộng nhẩm các số tiền
4. Sang thẻ **Giao dịch**, đặt bộ lọc: **Loại = Chi**, **Từ ngày = 01 tháng này**, **Đến ngày = hôm nay**
5. Cộng các số tiền trong danh sách (hoặc so với tổng ròng từng ngày)

**Kết quả mong đợi:**
- Tổng các miếng trong biểu đồ tròn **bằng đúng** ô **Tổng chi**
- Tổng đó **bằng đúng** tổng chi trong danh sách giao dịch cùng khoảng
- Miếng lớn nhất đứng đầu chú giải bên phải
- Màu mỗi miếng trùng với chấm màu của danh mục ở thẻ **Danh mục**
- **Giao dịch chuyển khoản không xuất hiện** trong biểu đồ và không cộng vào Tổng chi

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-3-11: Biểu đồ đường xu hướng thu/chi

**Mục đích:** Kiểm tra FR-FIN-11

**Các bước:**
1. Bấm **N**, tạo một giao dịch **Thu** `15000000`, danh mục `Lương`, ví `Vietcombank`
2. Sang thẻ **Báo cáo**
3. Đổi ô **Nhóm theo** lần lượt: **Theo ngày** → **Theo tuần** → **Theo tháng**
4. Đổi khoảng thời gian sang **90 ngày gần nhất**

**Kết quả mong đợi:**
- Có hai đường: **Chi** màu đỏ và **Thu** màu xanh lá, kèm chú giải
- Ô **Tổng thu** hiện `15.000.000 ₫`, **Chênh lệch** đổi theo
- Đổi "Nhóm theo" làm các điểm trên trục ngang gom lại/tách ra tương ứng
- Trục dọc ghi kiểu rút gọn (`4,3 tr`, `500 k`) chứ không phải số dài
- Rê chuột lên đường hiện tooltip có ngày và số tiền đầy đủ

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-3-12: Bộ lọc nâng cao

**Mục đích:** Kiểm tra FR-FIN-12

**Các bước:**
1. Sang thẻ **Giao dịch**
2. Gõ `cà phê` vào ô tìm kiếm, chờ khoảng nửa giây
3. Xóa ô tìm kiếm, chọn **Danh mục = Ăn uống** (danh mục **cha**)
4. Thêm điều kiện **Loại = Chi** và **Số tiền từ** `100000`
5. Bấm **Xóa bộ lọc**

**Kết quả mong đợi:**
- Bước 2: chỉ còn các giao dịch có ghi chú chứa "cà phê"; danh sách không nhấp nháy sau từng phím
- Bước 3: kết quả gồm **cả giao dịch thuộc danh mục con `Cà phê`**, không chỉ riêng `Ăn uống`
- Bước 4: các điều kiện cộng dồn với nhau (AND), số đếm "N giao dịch" ở trên cập nhật theo
- Bước 5: mọi ô lọc trở về mặc định và danh sách đầy đủ quay lại

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-3-13: Không xóa được ví còn giao dịch

**Mục đích:** Kiểm tra ràng buộc an toàn dữ liệu (mục C-6)

**Các bước:**
1. Sang thẻ **Ví**, rê chuột vào ví `Tiền mặt`, bấm nút thùng rác
2. Tạo một ví mới tên `Ví tạm`, không ghi giao dịch nào vào đó
3. Xóa `Ví tạm`

**Kết quả mong đợi:**
- Bước 1: hiện toast đỏ kiểu `Không xóa được ví vì còn N giao dịch…`, ví vẫn còn nguyên
- Bước 3: `Ví tạm` bị xóa bình thường, có toast `Đã xóa ví`
- Tổng tài sản cập nhật lại đúng

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-3-14: Giao dịch định kỳ

**Mục đích:** Kiểm tra FR-FIN-13

**Các bước:**
1. Sang thẻ **Định kỳ**, bấm **Thêm quy luật**
2. Số tiền `4500000`, ví `Vietcombank`, danh mục `└ Tiền nhà`, ghi chú `Tiền nhà hàng tháng`
3. Đặt **Bắt đầu từ** là **ngày hôm nay của 2 tháng trước** (ví dụ hôm nay 08/09 thì chọn 08/07)
4. Ở phần lặp lại, chọn **Hàng tháng**
5. Bấm **Lưu**
6. Bấm **Chạy ngay**
7. Sang thẻ **Giao dịch**

**Kết quả mong đợi:**
- Sau bước 5, quy luật xuất hiện với mô tả tiếng Việt kiểu "Lặp hàng tháng" và ngày chạy kế tiếp
- Bước 6: toast báo `Đã sinh 3 giao dịch định kỳ` (bù cho 2 tháng trước + tháng này)
- Thẻ **Giao dịch**: có 3 giao dịch `Tiền nhà hàng tháng`, mỗi giao dịch cách nhau đúng 1 tháng,
  mỗi dòng có biểu tượng vòng lặp
- Số dư ví `Vietcombank` giảm đúng 3 × 4.500.000 ₫
- Bấm **Chạy ngay** lần nữa: toast báo `Không có giao dịch định kỳ nào tới hạn` (không sinh trùng)
- Nút tạm dừng (⏸) làm quy luật chuyển sang trạng thái **Đã dừng**

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-3-15: Dashboard tổng hợp

**Mục đích:** Kiểm tra FR-SYS-01

**Các bước:**
1. Bấm **Tổng quan** ở sidebar
2. Đối chiếu các con số với những gì bạn vừa tạo
3. Bấm vào ô **Chi tháng này**
4. Quay lại **Tổng quan**, bấm ô **Việc quá hạn**

**Kết quả mong đợi:**
- Có 4 ô đầu: Việc hôm nay, Việc quá hạn, Sự kiện 7 ngày tới, Còn lại tháng này
- **Thu tháng này** và **Chi tháng này** khớp với ô Tổng thu / Tổng chi ở thẻ **Báo cáo** (khoảng "Tháng này")
- Mục **Ngân sách cần chú ý** liệt kê danh mục `Ăn uống` kèm `%` và chữ "đã vượt hạn mức"
- Bước 3 nhảy sang màn hình **Tài chính**, bước 4 nhảy sang **Công việc**
- Nếu có việc quá hạn, con số đó hiển thị màu đỏ

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-3-16: Dữ liệu vẫn đúng sau khi khởi động lại

**Mục đích:** Kiểm tra tính bền vững của dữ liệu và số dư tính động

**Các bước:**
1. Ghi lại số dư từng ví và **Tổng tài sản**
2. Đóng hẳn ứng dụng (chuột phải icon ở khay hệ thống → **Thoát**)
3. Chạy lại `npm run dev`
4. Vào **Tài chính** → thẻ **Ví**

**Kết quả mong đợi:**
- Mọi ví, giao dịch, ngân sách và quy luật định kỳ còn nguyên
- Số dư từng ví và Tổng tài sản **giống hệt** bước 1, không lệch một đồng
- Ngân sách vẫn giữ đúng mức % của chu kỳ hiện tại

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## Tổng kết

| Case | Nội dung | Kết quả |
|---|---|---|
| UAT-3-01 | Danh mục mặc định tiếng Việt | ☐ |
| UAT-3-02 | Tạo ví, tổng tài sản | ☐ |
| UAT-3-03 | Ô nhập tiền tự định dạng | ☐ |
| UAT-3-04 | Ghi chi, số dư giảm đúng | ☐ |
| UAT-3-05 | Chuyển khoản hai ví | ☐ |
| UAT-3-06 | Chặn chuyển khoản cùng ví, số tiền 0 | ☐ |
| UAT-3-07 | Sửa số tiền, số dư tính lại | ☐ |
| UAT-3-08 | Xóa và hoàn tác 5 giây | ☐ |
| UAT-3-09 | Cảnh báo ngân sách 80% / 100% | ☐ |
| UAT-3-09b | Ngân sách trên danh mục con | ☐ |
| UAT-3-10 | Biểu đồ tròn khớp danh sách | ☐ |
| UAT-3-11 | Biểu đồ đường xu hướng | ☐ |
| UAT-3-12 | Bộ lọc nâng cao | ☐ |
| UAT-3-13 | Chặn xóa ví còn giao dịch | ☐ |
| UAT-3-14 | Giao dịch định kỳ | ☐ |
| UAT-3-15 | Dashboard tổng hợp | ☐ |
| UAT-3-16 | Dữ liệu bền sau khởi động lại | ☐ |

**Số case đạt:** ____ / 17

**Vấn đề phát hiện được:**

1. _______________
2. _______________
3. _______________
