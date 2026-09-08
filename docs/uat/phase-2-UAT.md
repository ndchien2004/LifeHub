# UAT — Phase 2: Calendar & Reminder

**Cách chạy ứng dụng:**

```bash
cd D:\LifeHub
npm run dev
```

Sau khi cửa sổ mở, bấm **Lịch** ở sidebar bên trái.

**Thời gian ước tính:** ~35 phút (trong đó có 2 case phải chờ đồng hồ chạy tới)

**Mẹo chung:**
- Bấm **N** ở màn hình Lịch để mở nhanh form thêm sự kiện, **T** để nhảy về hôm nay.
- Nháy đúp vào một ô trống trên lưới cũng mở form với thời gian điền sẵn.

---

## UAT-2-01: Tạo sự kiện đơn và xem trên cả 3 chế độ

**Mục đích:** Kiểm tra FR-CAL-01, FR-CAL-02

**Các bước:**
1. Bấm **Thêm sự kiện**
2. Nhập tiêu đề `Khám sức khỏe định kỳ`
3. Đặt bắt đầu là **ngày mai 08:00**, kết thúc **ngày mai 09:00**
4. Nhập địa điểm `Bệnh viện Quận 1`
5. Nhập mô tả `Mang theo sổ khám cũ`
6. Bấm **Tạo sự kiện**
7. Lần lượt bấm **Tháng**, **Tuần**, **Ngày** ở góc phải
8. Ở chế độ **Tuần** và **Ngày**, dùng chuột cuộn lên xuống cột giờ

**Kết quả mong đợi:**
- Con trỏ đặt sẵn ở ô tiêu đề khi form mở
- Có toast "Đã tạo sự kiện"
- Chế độ **Tháng**: ô ngày mai có một khối màu ghi `08:00 Khám sức khỏe định kỳ`
- Chế độ **Tuần**: khối nằm đúng cột ngày mai, kéo dài đúng 1 giờ trên thang giờ
- Chế độ **Ngày**: khối chiếm đúng khoảng 08:00–09:00
- Ở chế độ Tuần và Ngày, khung nhìn mở sẵn quanh giờ hành chính chứ không phải 00:00

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-2-02: Form chặn thời gian ngược và tiêu đề rỗng

**Mục đích:** Kiểm tra NFR-USE-03 và ràng buộc `end_at > start_at`

**Các bước:**
1. Bấm **Thêm sự kiện**, không nhập gì, bấm **Tạo sự kiện**
2. Nhập tiêu đề `Test`, đặt bắt đầu **10:00 ngày mai**, kết thúc **09:00 ngày mai**
3. Bấm **Tạo sự kiện**
4. Bấm **Esc**

**Kết quả mong đợi:**
- Bước 1: hiện lỗi đỏ ngay dưới ô tiêu đề `Tiêu đề không được để trống`
- Bước 3: hiện lỗi `Thời gian kết thúc phải sau thời gian bắt đầu` dưới ô Kết thúc
- Không có toast lỗi kiểu "đã xảy ra lỗi", không có mã lỗi kỹ thuật nào hiện ra
- Bước 4: form đóng, **không có sự kiện nào được tạo** trên lịch

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-2-03: Sự kiện lặp hàng tuần hiện đúng trên lịch tháng

**Mục đích:** Kiểm tra FR-CAL-03

**Các bước:**
1. Bấm **Thêm sự kiện**
2. Nhập tiêu đề `Standup nhóm`
3. Đặt bắt đầu **thứ 2 tuần sau 09:00**, kết thúc **09:30** cùng ngày
4. Ở mục **Lặp lại**, chọn **Hằng tuần**
5. Quan sát dòng mô tả màu xám ngay dưới các ô lặp
6. Bấm các nút **T2** và **T4**
7. Quan sát lại dòng mô tả
8. Ở **Kết thúc**, chọn **Sau số lần**, nhập `6`
9. Bấm **Tạo sự kiện**, chuyển về chế độ **Tháng**

**Kết quả mong đợi:**
- Sau bước 4, dòng mô tả ghi `Hằng tuần`
- Sau bước 6, dòng mô tả ghi `Hằng tuần vào thứ 2, thứ 4`
- Sau bước 8, dòng mô tả ghi `Hằng tuần vào thứ 2, thứ 4, 6 lần`
- Trên lịch tháng có đúng 6 khối `Standup nhóm`, chỉ rơi vào các thứ 2 và thứ 4
- Mỗi khối có icon vòng lặp nhỏ ở bên phải

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-2-04: Sửa một lần duy nhất trong chuỗi (THIS_ONLY)

**Mục đích:** Kiểm tra FR-CAL-04, SD-05

**Chuẩn bị:** dùng chuỗi `Standup nhóm` từ UAT-2-03.

**Các bước:**
1. Bấm vào khối `Standup nhóm` **lần thứ hai** trong chuỗi
2. Trong hộp thoại chi tiết, bấm **Sửa**
3. Đổi tiêu đề thành `Standup nhóm (dời chiều)`
4. Đổi giờ bắt đầu thành **15:00**, kết thúc **15:30** cùng ngày đó
5. Bấm **Lưu thay đổi**
6. Hộp thoại **Áp dụng thay đổi cho...** hiện ra — chọn **Chỉ lần này**, bấm **Áp dụng**

**Kết quả mong đợi:**
- Hộp thoại phạm vi hiện ra trước khi có bất kỳ thay đổi nào được lưu
- Mỗi lựa chọn có một dòng giải thích ngắn bên dưới
- Sau khi áp dụng: **chỉ** lần thứ hai đổi tên và chuyển sang 15:00
- Các lần còn lại vẫn là `Standup nhóm` lúc 09:00
- Mở lại lần vừa sửa: dòng lặp có ghi chú `(lần này đã được sửa riêng)`

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-2-05: Sửa lần này và các lần sau (THIS_AND_FOLLOWING)

**Mục đích:** Kiểm tra FR-CAL-04 nhánh cắt chuỗi

**Các bước:**
1. Bấm vào khối `Standup nhóm` **lần thứ tư** trong chuỗi
2. Bấm **Sửa**, đổi tiêu đề thành `Standup nhóm mở rộng`, đổi địa điểm thành `Phòng họp lớn`
3. Bấm **Lưu thay đổi**
4. Chọn **Lần này và các lần sau**, bấm **Áp dụng**

**Kết quả mong đợi:**
- Ba lần đầu vẫn tên cũ `Standup nhóm`
- Từ lần thứ tư trở đi đổi thành `Standup nhóm mở rộng`, địa điểm `Phòng họp lớn`
- **Tổng số lần không đổi** — vẫn đúng 6 khối, không mất và không nhân đôi
- Mở chi tiết một khối cũ và một khối mới: cả hai vẫn hiện là sự kiện lặp

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-2-06: Xóa một lần trong chuỗi

**Mục đích:** Kiểm tra FR-CAL-04, tiêu chí "xóa 1 instance tạo exception cancelled"

**Các bước:**
1. Bấm vào khối `Standup nhóm` **lần thứ nhất**
2. Bấm **Xóa**
3. Chọn **Chỉ lần này**, bấm **Xóa**
4. Chuyển sang tháng khác rồi quay lại (để lịch tải lại từ backend)

**Kết quả mong đợi:**
- Chỉ lần thứ nhất biến mất
- Các lần còn lại vẫn nguyên vẹn, kể cả sau khi tải lại
- Có toast "Đã xóa lần này khỏi chuỗi lặp"

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-2-07: Cảnh báo trùng lịch

**Mục đích:** Kiểm tra FR-CAL-11

**Các bước:**
1. Tạo sự kiện `Họp kế hoạch`, **ngày kia 14:00 – 15:00**
2. Bấm **Thêm sự kiện**, nhập tiêu đề `Phỏng vấn ứng viên`
3. Đặt **ngày kia 14:30 – 15:30**
4. Chờ khoảng một giây, quan sát form
5. Bấm **Tạo sự kiện**
6. Xem lịch ở chế độ **Ngày** của ngày kia

**Kết quả mong đợi:**
- Bước 4: trong form hiện khung cảnh báo đỏ `Khoảng thời gian này đã có lịch khác`, liệt kê `Họp kế hoạch`
- Có dòng `Bạn vẫn có thể lưu nếu trùng lịch là có chủ ý.`
- Bước 5: **vẫn lưu được** — cảnh báo không chặn
- Bước 6: cả hai khối đều có viền đỏ và icon tam giác cảnh báo

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-2-08: Task có hạn chót hiện trên lịch, khác kiểu với event

**Mục đích:** Kiểm tra FR-CAL-10

**Các bước:**
1. Vào **Công việc**, tạo task `Nộp báo cáo quý` với ngày đến hạn là **ngày kia 17:00**
2. Quay lại **Lịch**, xem chế độ **Ngày** của ngày kia
3. Bấm vào mục `Nộp báo cáo quý`
4. Bỏ tick ô **Hiện task đến hạn** ở thanh trên

**Kết quả mong đợi:**
- Task hiện trên lịch với **kiểu dáng khác hẳn** event: viền nét đứt, nền nhạt, có icon checklist
- Bấm vào nó mở hộp thoại chi tiết có dòng `Mục này là hạn chót của một task. Sửa nó ở màn hình Công việc.`
- Hộp thoại của task **không có** nút Sửa/Xóa
- Bước 4: mục task biến mất khỏi lịch, các event vẫn còn

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-2-09: Liên kết sự kiện với task

**Mục đích:** Kiểm tra FR-CAL-05

**Các bước:**
1. Bấm **Thêm sự kiện**, tiêu đề `Thuyết trình nội bộ`, thời gian tùy ý trong tuần này
2. Ở mục **Liên kết với task**, chọn `Nộp báo cáo quý`
3. Bấm **Tạo sự kiện**
4. Bấm vào khối vừa tạo

**Kết quả mong đợi:**
- Danh sách thả xuống chỉ liệt kê task chưa hoàn thành
- Hộp thoại chi tiết có dòng có icon mắt xích, ghi tên task `Nộp báo cáo quý`

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-2-10: Nhắc hẹn bắn ra thông báo hệ điều hành

**Mục đích:** Kiểm tra FR-CAL-06, FR-CAL-07, NFR-PERF-06

> ⏱ Case này phải chờ khoảng 1–2 phút.

**Các bước:**
1. Xem đồng hồ máy. Bấm **Thêm sự kiện**
2. Tiêu đề `Test nhắc hẹn`, địa điểm `Bàn làm việc`
3. Đặt bắt đầu là **thời điểm hiện tại + 6 phút**, kết thúc + 7 phút
4. Ở mục **Nhắc trước**, bấm chip **5 phút trước** (bỏ chọn chip 15 phút nếu đang bật)
5. Bấm **Tạo sự kiện**
6. **Bấm nút X đóng cửa sổ ứng dụng** (xem UAT-2-13 nếu chưa chắc chuyện gì xảy ra)
7. Chờ tới đúng phút thứ 6

**Kết quả mong đợi:**
- Đúng khoảng thời điểm bắt đầu trừ 5 phút, một thông báo hệ điều hành hiện ra ở góc màn hình
- Thông báo có tiêu đề `Test nhắc hẹn` và nội dung dạng `HH:mm · Bàn làm việc`
- **Độ trễ so với giờ hẹn không quá 30 giây**
- Thông báo hiện ra **dù cửa sổ ứng dụng đã đóng xuống tray**

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Độ trễ đo được:** _______ giây
**Ghi chú của bạn:** _______________

---

## UAT-2-11: Nút hành động trên thông báo

**Mục đích:** Kiểm tra FR-CAL-08, UC-04 luồng phụ 5a

> ⚠️ **Lưu ý về nền tảng:** hai nút **Hoãn 10 phút** / **Đã xong** trên thông báo chỉ hiển thị
> được trên macOS. Trên Windows, nút hành động cần AppUserModelID đã đăng ký và toast XML —
> chỉ có sau khi đóng gói ở Phase 6 (xem `PROGRESS.md`, quyết định B-5). Trên Windows hãy kiểm
> phần **Cách kiểm thay thế** bên dưới; endpoint và logic đã được implement đầy đủ.

**Các bước (macOS):**
1. Tạo một sự kiện nhắc trước 5 phút như UAT-2-10
2. Khi thông báo hiện ra, bấm nút **Hoãn 10 phút**
3. Chờ 10 phút

**Kết quả mong đợi (macOS):**
- Sau 10 phút, thông báo hiện lại
- Nội dung thông báo vẫn ghi **đúng giờ của sự kiện gốc**, không bị cộng dồn thêm 10 phút

**Cách kiểm thay thế (Windows):**
1. Tạo sự kiện có giờ bắt đầu **cách hiện tại 3 phút**, nhắc trước **15 phút** (tức là đã quá hạn ngay)
2. Đóng ứng dụng hoàn toàn: chuột phải icon tray → **Thoát**
3. Mở lại bằng `npm run dev`
4. Trong modal "Bạn đã bỏ lỡ..." (xem UAT-2-12), bấm **Nhắc lại sau 1 giờ** ở mục đó

**Kết quả mong đợi (Windows):**
- Mục đó biến mất khỏi danh sách bị lỡ
- Có toast "Đã hoãn nhắc hẹn"

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-2-12: Modal nhắc hẹn bị lỡ khi khởi động

**Mục đích:** Kiểm tra FR-CAL-09, UC-05

**Các bước:**
1. Tạo sự kiện `Họp đã lỡ`, bắt đầu **hiện tại + 3 phút**, nhắc trước **15 phút**
   (nhắc hẹn này quá hạn ngay lúc tạo)
2. Chuột phải icon LifeHub ở khay hệ thống → **Thoát**
3. Chạy lại `npm run dev`
4. Chờ cửa sổ chính hiện lên

**Kết quả mong đợi:**
- Ngay sau khi giao diện chính hiện, modal **"Bạn đã bỏ lỡ 1 nhắc hẹn"** bật lên
- Mục trong danh sách ghi tiêu đề `Họp đã lỡ` kèm thời điểm đáng lẽ phải nhắc
- Có đủ 3 nút: **Mở sự kiện**, **Nhắc lại sau 1 giờ**, **Bỏ qua**
- Bấm **Bỏ qua tất cả** thì modal đóng
- **Khởi động lại lần nữa: modal KHÔNG hiện lại** (nhắc hẹn đã được xử lý)

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-2-13: Thu nhỏ xuống system tray thay vì thoát

**Mục đích:** Kiểm tra FR-SYS-07

**Các bước:**
1. Với ứng dụng đang mở, bấm nút **X** ở góc phải trên cửa sổ
2. Nhìn khay hệ thống (góc phải dưới trên Windows, thanh menu trên macOS)
3. Mở Task Manager / Activity Monitor, tìm tiến trình `electron` và `java`
4. Bấm một lần vào icon LifeHub ở khay
5. Chuột phải icon ở khay, xem menu
6. Bấm **Thoát** trong menu đó
7. Kiểm lại Task Manager

**Kết quả mong đợi:**
- Bước 1–2: cửa sổ biến mất nhưng icon LifeHub vẫn nằm ở khay
- Bước 3: **cả hai tiến trình vẫn đang chạy** — đây là điều kiện để nhắc hẹn còn hoạt động
- Bước 4: cửa sổ hiện lại đúng trạng thái cũ
- Bước 5: menu có **Mở LifeHub** và **Thoát**
- Bước 6–7: cả hai tiến trình đều kết thúc, icon khay biến mất

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-2-14: Task lặp lại sinh instance kế tiếp

**Mục đích:** Kiểm tra FR-TSK-13

> Phase 2 mới nối RRULE vào task ở tầng backend; form task chưa có ô chọn quy luật lặp
> (xem "Nợ kỹ thuật" trong `PROGRESS.md`). Case này kiểm qua API.

**Các bước:**
1. Mở PowerShell **mới** (giữ ứng dụng đang chạy)
2. Lấy port và token: mở DevTools trong app (`Ctrl+Shift+I`) → tab Console → gõ
   `await window.lifehub.getBackendInfo()`
3. Thay `<PORT>` và `<TOKEN>` rồi chạy:

```powershell
$h = @{ 'X-App-Token' = '<TOKEN>'; 'Content-Type' = 'application/json; charset=utf-8' }
$b = '{"title":"Nop bao cao tuan","dueAt":"2026-10-02T17:00:00+07:00","rrule":"FREQ=WEEKLY;COUNT=3"}'
$t = Invoke-RestMethod -Uri "http://127.0.0.1:<PORT>/api/v1/tasks" -Method Post -Headers $h -Body $b
Invoke-RestMethod -Uri "http://127.0.0.1:<PORT>/api/v1/tasks/$($t.data.id)/status" -Method Patch -Headers $h -Body '{"status":"DONE"}'
```

4. Vào màn hình **Công việc**, tìm `Nop bao cao tuan`

**Kết quả mong đợi:**
- Có **hai** task cùng tên
- Task cũ: trạng thái **Xong**, hạn 02/10/2026
- Task mới: trạng thái **Cần làm**, hạn **09/10/2026** (đúng một tuần sau)
- Lặp lại thao tác `status: DONE` trên task mới sẽ sinh task thứ ba, và task thứ ba **không** sinh thêm nữa (COUNT=3 đã hết)

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-2-15: Hiệu năng — lịch tháng nhiều sự kiện

**Mục đích:** Kiểm tra tiêu chí "lịch tháng có 200 instance render ≤ 500 ms"

**Các bước:**
1. Tạo 5 sự kiện lặp **Hằng ngày**, mỗi cái kết thúc **sau 40 lần**, giờ khác nhau
   (ví dụ 08:00, 10:00, 13:00, 15:00, 17:00), bắt đầu từ đầu tháng này
2. Chuyển sang chế độ **Tháng**
3. Mở DevTools (`Ctrl+Shift+I`) → tab **Network** → lọc `events?from=`
4. Bấm mũi tên **Kỳ sau** rồi **Kỳ trước** vài lần, xem cột **Time**

**Kết quả mong đợi:**
- Lưới hiện đủ các ngày, ô nào quá 3 mục thì hiện `+N mục khác`
- Bấm `+N mục khác` chuyển sang chế độ Ngày của đúng ngày đó
- Thời gian mỗi request `GET /events` **≤ 500 ms**
- Chuyển tháng không thấy lịch nhấp nháy trắng giữa hai lần tải

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Thời gian đo được:** _______ ms
**Ghi chú của bạn:** _______________

---

## UAT-2-16: Sự kiện cả ngày

**Mục đích:** Kiểm tra FR-CAL-01 (cờ cả ngày), quy ước M-13

**Các bước:**
1. Bấm **Thêm sự kiện**, tiêu đề `Nghỉ lễ`
2. Tick ô **Sự kiện cả ngày** — hai ô thời gian đổi thành chọn ngày
3. Chọn bắt đầu là một ngày trong tuần sau, kết thúc là **ngày kế tiếp**
4. Bấm **Tạo sự kiện**
5. Xem ở chế độ **Tuần**

**Kết quả mong đợi:**
- Ở chế độ Tuần, `Nghỉ lễ` nằm trong dải **Cả ngày** phía trên lưới giờ, không nằm trong cột giờ
- Ở chế độ Tháng, khối không hiển thị giờ ở đầu

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## UAT-2-17: Backend chết giữa chừng thì nhắc hẹn vẫn hồi phục

**Mục đích:** Kiểm tra NFR-REL-02 còn đúng sau khi thêm kênh SSE

**Các bước:**
1. Tạo sự kiện nhắc trước 5 phút, giờ bắt đầu **hiện tại + 6 phút**
2. Mở Task Manager, tìm tiến trình `java.exe` (Java(TM) Platform SE binary), **End task**
3. Quan sát ứng dụng
4. Chờ tới thời điểm nhắc

**Kết quả mong đợi:**
- Ứng dụng tự khởi động lại backend, có thông báo/toast cho biết
- Giao diện tải lại và hoạt động bình thường
- **Nhắc hẹn vẫn bắn đúng giờ** — kênh SSE đã tự nối lại vào cổng mới

**Kết quả thực tế:** ☐ Đạt   ☐ Không đạt
**Ghi chú của bạn:** _______________

---

## Tổng kết

| Case | Nội dung | Kết quả |
|---|---|---|
| UAT-2-01 | Tạo sự kiện, 3 chế độ xem | ☐ |
| UAT-2-02 | Form chặn dữ liệu sai | ☐ |
| UAT-2-03 | Sự kiện lặp + mô tả tiếng Việt | ☐ |
| UAT-2-04 | Sửa THIS_ONLY | ☐ |
| UAT-2-05 | Sửa THIS_AND_FOLLOWING | ☐ |
| UAT-2-06 | Xóa một instance | ☐ |
| UAT-2-07 | Cảnh báo trùng lịch | ☐ |
| UAT-2-08 | Task đến hạn trên lịch | ☐ |
| UAT-2-09 | Liên kết event ↔ task | ☐ |
| UAT-2-10 | Native notification | ☐ |
| UAT-2-11 | Nút hành động / hoãn | ☐ |
| UAT-2-12 | Modal nhắc hẹn bị lỡ | ☐ |
| UAT-2-13 | Thu nhỏ xuống tray | ☐ |
| UAT-2-14 | Task lặp lại | ☐ |
| UAT-2-15 | Hiệu năng lịch tháng | ☐ |
| UAT-2-16 | Sự kiện cả ngày | ☐ |
| UAT-2-17 | Backend crash + SSE nối lại | ☐ |

**Nếu có case không đạt:** ghi rõ số hiệu case và hiện tượng, rồi gõ

```
FIX PHASE 2: <mô tả lỗi>
```

**Nếu tất cả đều đạt:** gõ `START PHASE 3` để sang module Finance.
