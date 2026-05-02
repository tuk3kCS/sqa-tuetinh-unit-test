"""
Unit tests cho jwt_utils - lấy user ID từ JWT claims.
"""
import pytest
from unittest.mock import patch


# ============================================================
# GET CURRENT USER ID
# ============================================================

# Test Case ID: TC_AI_TestJwtUtils_get_current_user_id_001
# Test Objective: Trả userId khi JWT claims hợp lệ
# Input: JWT claims chứa userId=42
# Expected Output: 42
# Notes: Mock get_jwt() trả claims
@patch("app.utils.jwt_utils.get_jwt", return_value={"userId": 42, "sub": "user@test.com"})
def test_get_current_user_id_valid(mock_jwt):
    """Trả userId từ JWT claims."""
    from app.utils.jwt_utils import get_current_user_id
    result = get_current_user_id()
    assert result == 42


# Test Case ID: TC_AI_TestJwtUtils_get_current_user_id_002
# Test Objective: Trả None khi JWT claims không chứa userId
# Input: JWT claims rỗng hoặc thiếu userId
# Expected Output: None
# Notes: Kiểm tra xử lý khi userId không có
@patch("app.utils.jwt_utils.get_jwt", return_value={"sub": "user@test.com"})
def test_get_current_user_id_missing_user_id(mock_jwt):
    """Trả None khi claims thiếu userId."""
    from app.utils.jwt_utils import get_current_user_id
    result = get_current_user_id()
    assert result is None


# Test Case ID: TC_AI_TestJwtUtils_get_current_user_id_003
# Test Objective: Trả None khi JWT claims hoàn toàn rỗng
# Input: JWT claims = {}
# Expected Output: None
# Notes: Edge case - claims rỗng
@patch("app.utils.jwt_utils.get_jwt", return_value={})
def test_get_current_user_id_empty_claims(mock_jwt):
    """Trả None khi claims rỗng."""
    from app.utils.jwt_utils import get_current_user_id
    result = get_current_user_id()
    assert result is None


# Test Case ID: TC_AI_TestJwtUtils_get_current_user_id_004
# Test Objective: Trả userId khi userId = 0 (falsy nhưng hợp lệ)
# Input: JWT claims chứa userId=0
# Expected Output: None (vì 0 là falsy → not user_id = True)
# Notes: Kiểm tra edge case với giá trị 0
@patch("app.utils.jwt_utils.get_jwt", return_value={"userId": 0})
def test_get_current_user_id_zero(mock_jwt):
    """userId=0 bị coi là falsy → trả None."""
    from app.utils.jwt_utils import get_current_user_id
    result = get_current_user_id()
    # Trong code: if not user_id: return None → 0 là falsy
    assert result is None
