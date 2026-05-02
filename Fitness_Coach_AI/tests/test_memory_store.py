"""
Unit tests cho module app.memory.store
Kiểm tra get_user_state, save_plan, is_plan_active.
"""
import pytest
from datetime import date, timedelta
from unittest.mock import patch, MagicMock

from app.memory.store import get_user_state, save_plan, is_plan_active


# ============================================================
# get_user_state
# ============================================================

# Test Case ID: TC_AI_TestMemoryStore_get_user_state_001
# Test Objective: Kiểm tra lấy state khi user chưa có data
# Input: user_id="new_user"
# Expected Output: Dict rỗng {}
# Notes: _load_user_state cache trả {} khi repo trả None/{}
@patch("app.memory.store._repo")
@patch("app.memory.store._load_user_state")
def test_get_user_state_new_user(mock_load, mock_repo):
    mock_load.return_value = {}
    result = get_user_state("new_user")
    assert result == {}


# Test Case ID: TC_AI_TestMemoryStore_get_user_state_002
# Test Objective: Kiểm tra lấy state khi user đã có plan
# Input: user_id="existing_user" với state chứa meal_plan
# Expected Output: Dict chứa meal_plan
# Notes: _load_user_state trả state đã cache
@patch("app.memory.store._load_user_state")
def test_get_user_state_existing_user(mock_load):
    mock_load.return_value = {"meal_plan": {"plan": {"day1": {}}}}
    result = get_user_state("existing_user")
    assert "meal_plan" in result


# Test Case ID: TC_AI_TestMemoryStore_get_user_state_003
# Test Objective: Kiểm tra trả bản copy (không trả reference gốc)
# Input: user_id="user1"
# Expected Output: Thay đổi trên result không ảnh hưởng state gốc
# Notes: Hàm gọi .copy() → đảm bảo immutability
@patch("app.memory.store._load_user_state")
def test_get_user_state_returns_copy(mock_load):
    original = {"meal_plan": {"plan": {}}}
    mock_load.return_value = original
    result = get_user_state("user1")
    result["new_key"] = "should_not_affect_original"
    assert "new_key" not in original


# ============================================================
# save_plan
# ============================================================

# Test Case ID: TC_AI_TestMemoryStore_save_plan_001
# Test Objective: Kiểm tra lưu meal plan mới
# Input: user_id="1", plan_type="meal_plan", plan data, start/end dates
# Expected Output: repo.save_state được gọi với state chứa meal_plan
# Notes: Kiểm tra state được cập nhật đúng và repo được gọi
@patch("app.memory.store._load_user_state")
@patch("app.memory.store._repo")
def test_save_plan_meal(mock_repo, mock_load):
    mock_load.return_value = {}
    mock_load.cache_clear = MagicMock()

    start = date(2026, 4, 13)
    end = date(2026, 4, 19)
    plan = {"day1": {"breakfast": {}}}

    save_plan("1", "meal_plan", plan, start, end)

    call_args = mock_repo.save_state.call_args[0]
    assert call_args[0] == "1"
    state = call_args[1]
    assert state["meal_plan"]["plan"] == plan
    assert state["meal_plan"]["start_date"] == "2026-04-13"
    assert state["meal_plan"]["end_date"] == "2026-04-19"


# Test Case ID: TC_AI_TestMemoryStore_save_plan_002
# Test Objective: Kiểm tra lưu workout plan khi đã có meal plan
# Input: State đã có meal_plan, lưu thêm workout_plan
# Expected Output: State chứa cả meal_plan lẫn workout_plan
# Notes: Kiểm tra merge state
@patch("app.memory.store._load_user_state")
@patch("app.memory.store._repo")
def test_save_plan_workout_with_existing_meal(mock_repo, mock_load):
    mock_load.return_value = {"meal_plan": {"plan": {"day1": {}}, "start_date": "2026-04-13", "end_date": "2026-04-19"}}
    mock_load.cache_clear = MagicMock()

    start = date(2026, 4, 13)
    end = date(2026, 4, 19)
    workout = {"Monday": {"exercises": []}}

    save_plan("1", "workout_plan", workout, start, end)

    state = mock_repo.save_state.call_args[0][1]
    assert "meal_plan" in state
    assert "workout_plan" in state
    assert state["workout_plan"]["plan"] == workout


# Test Case ID: TC_AI_TestMemoryStore_save_plan_003
# Test Objective: Kiểm tra _to_iso với date object
# Input: date(2026, 4, 13)
# Expected Output: "2026-04-13"
# Notes: _to_iso chuyển date → isoformat string
@patch("app.memory.store._load_user_state")
@patch("app.memory.store._repo")
def test_save_plan_date_serialization(mock_repo, mock_load):
    mock_load.return_value = {}
    mock_load.cache_clear = MagicMock()

    save_plan("1", "meal_plan", {}, date(2026, 1, 1), date(2026, 1, 7))

    state = mock_repo.save_state.call_args[0][1]
    assert state["meal_plan"]["start_date"] == "2026-01-01"


# ============================================================
# is_plan_active
# ============================================================

# Test Case ID: TC_AI_TestMemoryStore_is_plan_active_001
# Test Objective: Kiểm tra plan còn hiệu lực (end_date >= today)
# Input: State với end_date trong tương lai
# Expected Output: True
# Notes: today <= end_date → active
def test_is_plan_active_valid():
    future = (date.today() + timedelta(days=3)).isoformat()
    state = {"meal_plan": {"end_date": future}}
    assert is_plan_active(state, "meal_plan") is True


# Test Case ID: TC_AI_TestMemoryStore_is_plan_active_002
# Test Objective: Kiểm tra plan đã hết hạn (end_date < today)
# Input: State với end_date trong quá khứ
# Expected Output: False
# Notes: today > end_date → không active
def test_is_plan_active_expired():
    past = (date.today() - timedelta(days=1)).isoformat()
    state = {"meal_plan": {"end_date": past}}
    assert is_plan_active(state, "meal_plan") is False


# Test Case ID: TC_AI_TestMemoryStore_is_plan_active_003
# Test Objective: Kiểm tra khi state rỗng (không có plan)
# Input: State rỗng {}
# Expected Output: False
# Notes: Nhánh not state or plan_type not in state
def test_is_plan_active_no_plan():
    assert is_plan_active({}, "meal_plan") is False


# Test Case ID: TC_AI_TestMemoryStore_is_plan_active_004
# Test Objective: Kiểm tra khi state là None
# Input: state=None
# Expected Output: False
# Notes: Nhánh not state
def test_is_plan_active_none_state():
    assert is_plan_active(None, "meal_plan") is False


# Test Case ID: TC_AI_TestMemoryStore_is_plan_active_005
# Test Objective: Kiểm tra khi end_date không hợp lệ (gây exception)
# Input: end_date là số thay vì chuỗi ngày
# Expected Output: False
# Notes: Nhánh except Exception → return False
def test_is_plan_active_invalid_end_date():
    state = {"meal_plan": {"end_date": 12345}}
    assert is_plan_active(state, "meal_plan") is False


# Test Case ID: TC_AI_TestMemoryStore_is_plan_active_006
# Test Objective: Kiểm tra plan active đúng ngày hết hạn (edge case)
# Input: end_date = today
# Expected Output: True (today <= end_date)
# Notes: Biên: ngày cuối cùng vẫn active
def test_is_plan_active_today_is_end_date():
    today = date.today().isoformat()
    state = {"meal_plan": {"end_date": today}}
    assert is_plan_active(state, "meal_plan") is True
