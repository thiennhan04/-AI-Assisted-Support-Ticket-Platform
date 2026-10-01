# Quyết định contract DD-101

## Ngữ cảnh tenant khi đăng nhập

`POST /v1/auth/login` nhận `tenantCode`, `email` và `password`. Email chỉ duy nhất trong phạm vi một tenant, vì vậy Identity phải xác định tenant code đã chuẩn hóa trước khi tìm user. Đây là endpoint duy nhất được phép nhận ngữ cảnh tra cứu tenant từ request chưa xác thực. API đã xác thực chỉ lấy tenant từ claim JWT `tid` đã được kiểm tra.

Tenant/user không tồn tại, bị suspended, locked, disabled hoặc password sai đều trả cùng response `401 AUTH_INVALID_CREDENTIALS`.

## Token response và ranh giới story

Login trả access JWT RS256, refresh token opaque, loại `Bearer`, access TTL tính bằng giây và thông tin tóm tắt user. DD-101 tạo refresh session đầu tiên và chỉ lưu SHA-256 hash của token. DD-102 chịu trách nhiệm rotation, phát hiện reuse và logout.

Access JWT có hiệu lực 15 phút và chứa `sub`, `tid`, `roles`, `iss`, `aud`, `jti`, `iat`, `exp`. Protected JWT header chứa `alg=RS256` và bắt buộc có `kid`.

## Ranh giới transaction

Việc kiểm tra password và tạo token diễn ra trước một write transaction ngắn. Transaction này lock và kiểm tra lại user, đặt lại số lần thất bại rồi lưu refresh session hash. Chỉ trả token sau khi commit. Cập nhật số lần thất bại dùng transaction độc lập để generic authentication exception không rollback thay đổi này.

## Bảo vệ login

Năm lần sai password liên tiếp tạm khóa user trong 15 phút. Login thành công đặt lại bộ đếm. `DISABLED` là trạng thái quản trị viên đặt rõ ràng. Redis giới hạn số lần thử theo SHA-256 hash của tenant/email đã chuẩn hóa và riêng theo remote IP đáng tin cậy. Mặc định local là 10 lần trong 15 phút; request bị giới hạn trả `429` kèm `Retry-After`.

## Persistence an toàn theo tenant

`user_role` và `refresh_session` có `tenant_id`. Composite foreign key ngăn role hoặc session tham chiếu user thuộc tenant khác. Mọi repository operation dùng bởi application code luôn kèm ngữ cảnh tenant.
