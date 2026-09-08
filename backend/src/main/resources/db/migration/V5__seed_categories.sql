-- V5 — Phase 3: bo danh muc thu/chi mac dinh tieng Viet (FR-FIN-03).
--
-- Danh sach lay dung 03-DATA-MODEL.md §2.7. Tat ca deu `is_system = 1` nen khong xoa duoc;
-- user van doi duoc ten, icon va mau.
--
-- Ve id: bang seed khong chay duoc qua IdGenerator nen id la hang so co dinh, van dung
-- hinh dang UUID v7 (nibble phien ban `7`, variant `8`). Tien to thoi gian dat o mot moc
-- co dinh trong qua khu de danh muc he thong luon dung truoc moi danh muc user tu tao khi
-- sap xep theo id.
--
-- Ve moc thoi gian: Hibernate luu Instant xuong SQLite duoi dang INTEGER milli-giay epoch
-- (kiem chung tren chinh file lifehub.db). Seed phai dung dinh dang do, khong duoc dung
-- CURRENT_TIMESTAMP — chuoi 'YYYY-MM-DD HH:MM:SS' se lam Hibernate doc ra sai kieu.
-- `icon` la ten component cua lucide-react, frontend tra cuu theo ten nay.

INSERT INTO category (id, parent_id, name, type, icon, color, is_system, sort_order, created_at, updated_at)
VALUES
    -- ---------- Chi (EXPENSE) ----------
    ('01900000-0000-7000-8000-00000000e001', NULL, 'Ăn uống',     'EXPENSE', 'UtensilsCrossed', '#f59e0b', 1, 10, strftime('%s','now') * 1000, strftime('%s','now') * 1000),
    ('01900000-0000-7000-8000-00000000e101', '01900000-0000-7000-8000-00000000e001', 'Ăn ngoài',      'EXPENSE', 'Utensils',       '#f59e0b', 1, 11, strftime('%s','now') * 1000, strftime('%s','now') * 1000),
    ('01900000-0000-7000-8000-00000000e102', '01900000-0000-7000-8000-00000000e001', 'Đi chợ',        'EXPENSE', 'ShoppingBasket', '#f59e0b', 1, 12, strftime('%s','now') * 1000, strftime('%s','now') * 1000),
    ('01900000-0000-7000-8000-00000000e103', '01900000-0000-7000-8000-00000000e001', 'Cà phê',        'EXPENSE', 'Coffee',         '#f59e0b', 1, 13, strftime('%s','now') * 1000, strftime('%s','now') * 1000),

    ('01900000-0000-7000-8000-00000000e002', NULL, 'Di chuyển',   'EXPENSE', 'Car',            '#3b82f6', 1, 20, strftime('%s','now') * 1000, strftime('%s','now') * 1000),
    ('01900000-0000-7000-8000-00000000e201', '01900000-0000-7000-8000-00000000e002', 'Xăng xe',       'EXPENSE', 'Fuel',           '#3b82f6', 1, 21, strftime('%s','now') * 1000, strftime('%s','now') * 1000),
    ('01900000-0000-7000-8000-00000000e202', '01900000-0000-7000-8000-00000000e002', 'Grab/Taxi',     'EXPENSE', 'CarTaxiFront',   '#3b82f6', 1, 22, strftime('%s','now') * 1000, strftime('%s','now') * 1000),
    ('01900000-0000-7000-8000-00000000e203', '01900000-0000-7000-8000-00000000e002', 'Gửi xe',        'EXPENSE', 'SquareParking',  '#3b82f6', 1, 23, strftime('%s','now') * 1000, strftime('%s','now') * 1000),

    ('01900000-0000-7000-8000-00000000e003', NULL, 'Nhà ở',       'EXPENSE', 'Home',           '#10b981', 1, 30, strftime('%s','now') * 1000, strftime('%s','now') * 1000),
    ('01900000-0000-7000-8000-00000000e301', '01900000-0000-7000-8000-00000000e003', 'Tiền nhà',      'EXPENSE', 'House',          '#10b981', 1, 31, strftime('%s','now') * 1000, strftime('%s','now') * 1000),
    ('01900000-0000-7000-8000-00000000e302', '01900000-0000-7000-8000-00000000e003', 'Điện nước',     'EXPENSE', 'Zap',            '#10b981', 1, 32, strftime('%s','now') * 1000, strftime('%s','now') * 1000),
    ('01900000-0000-7000-8000-00000000e303', '01900000-0000-7000-8000-00000000e003', 'Internet',      'EXPENSE', 'Wifi',           '#10b981', 1, 33, strftime('%s','now') * 1000, strftime('%s','now') * 1000),

    ('01900000-0000-7000-8000-00000000e004', NULL, 'Mua sắm',     'EXPENSE', 'ShoppingBag',    '#ec4899', 1, 40, strftime('%s','now') * 1000, strftime('%s','now') * 1000),
    ('01900000-0000-7000-8000-00000000e401', '01900000-0000-7000-8000-00000000e004', 'Quần áo',       'EXPENSE', 'Shirt',          '#ec4899', 1, 41, strftime('%s','now') * 1000, strftime('%s','now') * 1000),
    ('01900000-0000-7000-8000-00000000e402', '01900000-0000-7000-8000-00000000e004', 'Đồ điện tử',    'EXPENSE', 'Smartphone',     '#ec4899', 1, 42, strftime('%s','now') * 1000, strftime('%s','now') * 1000),
    ('01900000-0000-7000-8000-00000000e403', '01900000-0000-7000-8000-00000000e004', 'Đồ dùng',       'EXPENSE', 'Package',        '#ec4899', 1, 43, strftime('%s','now') * 1000, strftime('%s','now') * 1000),

    ('01900000-0000-7000-8000-00000000e005', NULL, 'Sức khỏe',    'EXPENSE', 'HeartPulse',     '#ef4444', 1, 50, strftime('%s','now') * 1000, strftime('%s','now') * 1000),
    ('01900000-0000-7000-8000-00000000e006', NULL, 'Giải trí',    'EXPENSE', 'Gamepad2',       '#8b5cf6', 1, 60, strftime('%s','now') * 1000, strftime('%s','now') * 1000),
    ('01900000-0000-7000-8000-00000000e007', NULL, 'Học tập',     'EXPENSE', 'GraduationCap',  '#06b6d4', 1, 70, strftime('%s','now') * 1000, strftime('%s','now') * 1000),
    ('01900000-0000-7000-8000-00000000e008', NULL, 'Subscription','EXPENSE', 'Repeat',         '#f97316', 1, 80, strftime('%s','now') * 1000, strftime('%s','now') * 1000),
    ('01900000-0000-7000-8000-00000000e009', NULL, 'Khác',        'EXPENSE', 'CircleEllipsis', '#94a3b8', 1, 90, strftime('%s','now') * 1000, strftime('%s','now') * 1000),

    -- ---------- Thu (INCOME) ----------
    ('01900000-0000-7000-8000-00000000a001', NULL, 'Lương',       'INCOME',  'Banknote',       '#22c55e', 1, 10, strftime('%s','now') * 1000, strftime('%s','now') * 1000),
    ('01900000-0000-7000-8000-00000000a002', NULL, 'Thưởng',      'INCOME',  'Gift',           '#14b8a6', 1, 20, strftime('%s','now') * 1000, strftime('%s','now') * 1000),
    ('01900000-0000-7000-8000-00000000a003', NULL, 'Freelance',   'INCOME',  'Laptop',         '#0ea5e9', 1, 30, strftime('%s','now') * 1000, strftime('%s','now') * 1000),
    ('01900000-0000-7000-8000-00000000a004', NULL, 'Đầu tư',      'INCOME',  'TrendingUp',     '#a855f7', 1, 40, strftime('%s','now') * 1000, strftime('%s','now') * 1000),
    ('01900000-0000-7000-8000-00000000a005', NULL, 'Khác',        'INCOME',  'CircleEllipsis', '#94a3b8', 1, 50, strftime('%s','now') * 1000, strftime('%s','now') * 1000);
