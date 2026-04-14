"""
Unit tests cho module app.utils.jwt_utils
Kiểm tra get_access_token và get_user_id_from_token.
"""
import pytest
from unittest.mock import MagicMock, patch

from app.utils.jwt_utils import get_access_token, get_user_id_from_token


# ============================================================
# get_access_token
# ============================================================

# Test Case ID: TC-FR-02-001tils_get_access_token_001
# Test Objective: Kiểm tra lấy token thành công từ Authorization header
# Input: Request với header Authorization: "Bearer abc123"
# Expected Output: "abc123"
# Notes: Happy path – header đúng format
def test_get_access_token_valid():
    request = MagicMock()
    request.headers.get.return_value = "Bearer abc123"
    result = get_access_token(request)
    assert result == "abc123"


# Test Case ID: TC-FR-02-001tils_get_access_token_002
# Test Objective: Kiểm tra raise ValueError khi thiếu Authorization header
# Input: Request không có Authorization header
# Expected Output: Raise ValueError "Authorization header missing"
# Notes: Nhánh not auth_header
def test_get_access_token_missing_header():
    request = MagicMock()
    request.headers.get.return_value = None
    with pytest.raises(ValueError, match="Authorization header missing"):
        get_access_token(request)


# Test Case ID: TC-FR-02-001tils_get_access_token_003
# Test Objective: Kiểm tra raise ValueError khi header sai format
# Input: Authorization: "InvalidFormat"
# Expected Output: Raise ValueError "Invalid Authorization header format"
# Notes: Nhánh len(parts) != 2
def test_get_access_token_malformed_header():
    request = MagicMock()
    request.headers.get.return_value = "InvalidFormat"
    with pytest.raises(ValueError, match="Invalid Authorization header format"):
        get_access_token(request)


# Test Case ID: TC-FR-02-001tils_get_access_token_004
# Test Objective: Kiểm tra raise ValueError khi prefix không phải "Bearer"
# Input: Authorization: "Token abc123"
# Expected Output: Raise ValueError "Invalid Authorization header format"
# Notes: Nhánh parts[0].lower() != "bearer"
def test_get_access_token_wrong_prefix():
    request = MagicMock()
    request.headers.get.return_value = "Token abc123"
    with pytest.raises(ValueError, match="Invalid Authorization header format"):
        get_access_token(request)


# Test Case ID: TC-FR-02-001tils_get_access_token_005
# Test Objective: Kiểm tra khi header có quá nhiều parts
# Input: Authorization: "Bearer token extra_part"
# Expected Output: Raise ValueError (len(parts) != 2)
# Notes: 3 parts → thất bại validation
def test_get_access_token_too_many_parts():
    request = MagicMock()
    request.headers.get.return_value = "Bearer token extra_part"
    with pytest.raises(ValueError, match="Invalid Authorization header format"):
        get_access_token(request)


# Test Case ID: TC-FR-02-001tils_get_access_token_006
# Test Objective: Kiểm tra case insensitive "BEARER" prefix
# Input: Authorization: "BEARER abc123"
# Expected Output: "abc123"
# Notes: parts[0].lower() == "bearer" → hỗ trợ case insensitive
def test_get_access_token_case_insensitive():
    request = MagicMock()
    request.headers.get.return_value = "BEARER abc123"
    result = get_access_token(request)
    assert result == "abc123"


# ============================================================
# get_user_id_from_token
# ============================================================

# Test Case ID: TC-FR-02-001tils_get_user_id_from_token_001
# Test Objective: Kiểm tra lấy userId từ JWT claims
# Input: JWT claims chứa {"userId": 42}
# Expected Output: 42
# Notes: Happy path – claims có key "userId"
@patch("app.utils.jwt_utils.get_jwt")
def test_get_user_id_from_token_valid(mock_get_jwt):
    mock_get_jwt.return_value = {"userId": 42, "sub": "test@test.com"}
    result = get_user_id_from_token()
    assert result == 42


# Test Case ID: TC-FR-02-001tils_get_user_id_from_token_002
# Test Objective: Kiểm tra khi claims không có key "userId"
# Input: JWT claims rỗng
# Expected Output: None (dict.get trả None khi key không tồn tại)
# Notes: claims.get("userId") → None
@patch("app.utils.jwt_utils.get_jwt")
def test_get_user_id_from_token_missing_claim(mock_get_jwt):
    mock_get_jwt.return_value = {"sub": "test@test.com"}
    result = get_user_id_from_token()
    assert result is None


# Test Case ID: TC-FR-02-001tils_get_user_id_from_token_003
# Test Objective: Kiểm tra khi userId là string (kiểu dữ liệu khác)
# Input: JWT claims {"userId": "123"}
# Expected Output: "123" (trả nguyên giá trị, không convert)
# Notes: Hàm chỉ lấy giá trị, không ép kiểu
@patch("app.utils.jwt_utils.get_jwt")
def test_get_user_id_from_token_string_value(mock_get_jwt):
    mock_get_jwt.return_value = {"userId": "123"}
    result = get_user_id_from_token()
    assert result == "123"
