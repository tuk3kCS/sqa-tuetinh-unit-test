-- Xóa dữ liệu phụ thuộc user/role trước khi test seeder (chạy transaction ISOLATED, commit).
SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM order_items WHERE 1=1;
DELETE FROM orders WHERE 1=1;
DELETE FROM users WHERE 1=1;
DELETE FROM roles WHERE 1=1;
SET FOREIGN_KEY_CHECKS = 1;
