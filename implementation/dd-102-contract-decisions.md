# Quyết định contract DD-102

## Refresh request và response

`POST /v1/auth/refresh` nhận `{ "refreshToken": "..." }` mà không cần access JWT. Bản thân refresh token là bearer credential, cho phép client khôi phục khi access token ngắn hạn hết hạn. Token được truyền trong JSON thay vì cookie, nên API hiện tại không cần cơ chế CSRF dành cho cookie.

Mỗi lần gọi thành công trả cùng cấu trúc token response như login, rotate refresh token và kéo dài hạn của session mới theo refresh TTL đã cấu hình. Response của login và refresh có `Cache-Control: no-store` và `Pragma: no-cache`.

## Rotation và reuse

Refresh token chỉ dùng một lần. Rotation tạo row mới cùng `family_id`, ghi ID row mới vào `replaced_by_id` của row cũ và ghi `last_used_at`. Database bảo đảm replacement session tồn tại và không thể bị nhiều row cùng tham chiếu như replacement.

Gửi lại token đã rotate được coi là reuse, kể cả khi token đó đã hết hạn. Reuse thu hồi mọi row trong family và trả `401 AUTH_REFRESH_REUSE_DETECTED`. Token không tồn tại, hết hạn, đã thu hồi hoặc thuộc user không còn đủ điều kiện trả `401 AUTH_INVALID_REFRESH_TOKEN`.

## Xử lý đồng thời và ranh giới transaction

Refresh dùng pessimistic write lock khi chọn session. Các request đồng thời dùng cùng token được tuần tự hóa: request đầu rotate; request sau thấy `replaced_by_id`, thu hồi family rồi thất bại. Reuse exception được cấu hình để không rollback thao tác thu hồi family.

Ký JWT và tạo refresh token là thao tác local, diễn ra trong transaction ngắn này. Không gọi network hoặc broker khi đang giữ database lock. Caller chỉ nhìn thấy token response sau commit.

## Logout

`POST /v1/auth/logout` nhận cùng refresh-token request và thu hồi toàn bộ family của token được gửi. Logout trả `204` cho token đã biết, không biết, đã rotate hoặc đã thu hồi. Cách này làm logout idempotent và không tiết lộ token có tồn tại hay không.
