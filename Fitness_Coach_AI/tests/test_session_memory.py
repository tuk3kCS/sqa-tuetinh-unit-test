"""
Unit tests cho module app.memory.session_memory
Kiểm tra get_session_memory và update_session_memory.
"""
import time
import pytest
from unittest.mock import patch

from app.memory.session_memory import (
    get_session_memory,
    update_session_memory,
    _session_store,
    SESSION_TTL_SECONDS,
)


@pytest.fixture(autouse=True)
def clear_session_store():
    """Xóa session store trước và sau mỗi test."""
    _session_store.clear()
    yield
    _session_store.clear()


# ============================================================
# get_session_memory
# ============================================================

# Test Case ID: TC_FITNESS_SessionMemory_get_session_memory_001
# Test Objective: Kiểm tra lấy session mới (chưa tồn tại)
# Input: user_id="new_user" (chưa có trong store)
# Expected Output: Dict rỗng {}
# Notes: Nhánh not session → return {}
def test_get_session_memory_new_session():
    result = get_session_memory("new_user")
    assert result == {}


# Test Case ID: TC_FITNESS_SessionMemory_get_session_memory_002
# Test Objective: Kiểm tra lấy session đã tồn tại và chưa hết hạn
# Input: user_id đã có session data
# Expected Output: Dict chứa data đã lưu
# Notes: Session mới tạo → updated_at gần hiện tại → chưa expired
def test_get_session_memory_existing_valid():
    update_session_memory("user1", {"chat_history": [{"role": "user", "content": "hi"}]})
    result = get_session_memory("user1")
    assert "chat_history" in result
    assert len(result["chat_history"]) == 1


# Test Case ID: TC_FITNESS_SessionMemory_get_session_memory_003
# Test Objective: Kiểm tra session hết hạn → trả {} và xóa khỏi store
# Input: Session có updated_at quá TTL (30 phút)
# Expected Output: Dict rỗng {}
# Notes: Nhánh _now() - session["updated_at"] > SESSION_TTL_SECONDS
@patch("app.memory.session_memory._now")
def test_get_session_memory_expired(mock_now):
    mock_now.return_value = 1000.0
    update_session_memory("user2", {"intent": "chat"})

    mock_now.return_value = 1000.0 + SESSION_TTL_SECONDS + 1
    result = get_session_memory("user2")
    assert result == {}
    assert "user2" not in _session_store


# Test Case ID: TC_FITNESS_SessionMemory_get_session_memory_004
# Test Objective: Kiểm tra session đúng biên hết hạn (TTL chính xác)
# Input: Thời gian trôi qua đúng bằng SESSION_TTL_SECONDS
# Expected Output: Dict rỗng {} (vì > là strict, nhưng bằng thì không expired)
# Notes: Edge case – exactly at TTL boundary → NOT expired (dùng >)
@patch("app.memory.session_memory._now")
def test_get_session_memory_at_ttl_boundary(mock_now):
    mock_now.return_value = 2000.0
    update_session_memory("user3", {"data": "test"})

    mock_now.return_value = 2000.0 + SESSION_TTL_SECONDS
    result = get_session_memory("user3")
    assert result == {"data": "test"}


# ============================================================
# update_session_memory
# ============================================================

# Test Case ID: TC_FITNESS_SessionMemory_update_session_memory_001
# Test Objective: Kiểm tra cập nhật session mới
# Input: user_id="user5", data mới
# Expected Output: Session được lưu vào store
# Notes: INSERT mới vào _session_store
def test_update_session_memory_new():
    update_session_memory("user5", {"chat_history": [], "last_intent": "chat"})
    assert "user5" in _session_store
    assert _session_store["user5"]["data"]["last_intent"] == "chat"


# Test Case ID: TC_FITNESS_SessionMemory_update_session_memory_002
# Test Objective: Kiểm tra cập nhật session đã tồn tại (ghi đè data)
# Input: Session đã tồn tại → update với data mới
# Expected Output: Data mới ghi đè data cũ hoàn toàn
# Notes: Hàm ghi đè toàn bộ data (không merge)
def test_update_session_memory_overwrite():
    update_session_memory("user6", {"old": True})
    update_session_memory("user6", {"new": True})
    assert _session_store["user6"]["data"] == {"new": True}
    assert "old" not in _session_store["user6"]["data"]


# Test Case ID: TC_FITNESS_SessionMemory_update_session_memory_003
# Test Objective: Kiểm tra updated_at được set đúng
# Input: Cập nhật session
# Expected Output: updated_at gần với thời gian hiện tại
# Notes: Kiểm tra timestamp
@patch("app.memory.session_memory._now", return_value=5000.0)
def test_update_session_memory_timestamp(mock_now):
    update_session_memory("user7", {"test": True})
    assert _session_store["user7"]["updated_at"] == 5000.0


# Test Case ID: TC_FITNESS_SessionMemory_update_session_memory_004
# Test Objective: Kiểm tra update với data rỗng
# Input: data={} (rỗng)
# Expected Output: Session được lưu với data rỗng
# Notes: data rỗng vẫn hợp lệ
def test_update_session_memory_empty_data():
    update_session_memory("user8", {})
    result = get_session_memory("user8")
    assert result == {}
