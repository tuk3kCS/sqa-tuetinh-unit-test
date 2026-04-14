"""
Unit tests cho module app.memory.repository
Kiểm tra UserStateRepositoryImpl với DB thực (SQLite in-memory).
"""
import pytest

from app.memory.repository import UserStateRepositoryImpl
from app.models.user_plan import UserPlan
from app import db


@pytest.fixture
def repo():
    """Tạo instance repository cho mỗi test."""
    return UserStateRepositoryImpl()


# ============================================================
# get_state
# ============================================================

# Test Case ID: TC-FR-00-001serStateRepository_get_state_001
# Test Objective: Kiểm tra lấy state khi user không tồn tại
# Input: user_id="99999"
# Expected Output: Dict rỗng {}
# Notes: CheckDB: không có record cho user_id. Rollback tự động.
def test_get_state_not_exists(db_session, repo):
    result = repo.get_state("99999")
    assert result == {}


# Test Case ID: TC-FR-00-001serStateRepository_get_state_002
# Test Objective: Kiểm tra lấy state khi user tồn tại
# Input: UserPlan với meal_plan và workout_plan
# Expected Output: Dict chứa meal_plan và workout_plan
# Notes: CheckDB: record tồn tại với cả hai plan. Rollback tự động.
def test_get_state_exists(db_session, repo):
    meal = {"day1": {"breakfast": {}}}
    workout = {"Monday": {"exercises": []}}
    user_plan = UserPlan(user_id=500, meal_plan=meal, workout_plan=workout)
    db_session.add(user_plan)
    db_session.flush()

    result = repo.get_state("500")
    assert result["meal_plan"] == meal
    assert result["workout_plan"] == workout


# Test Case ID: TC-FR-00-001serStateRepository_get_state_003
# Test Objective: Kiểm tra lấy state khi user tồn tại nhưng plans là None
# Input: UserPlan tồn tại nhưng cả meal_plan và workout_plan đều None
# Expected Output: Dict {"meal_plan": None, "workout_plan": None}
# Notes: CheckDB: record tồn tại, plans đều None. Rollback tự động.
def test_get_state_exists_with_null_plans(db_session, repo):
    user_plan = UserPlan(user_id=501)
    db_session.add(user_plan)
    db_session.flush()

    result = repo.get_state("501")
    assert result["meal_plan"] is None
    assert result["workout_plan"] is None


# ============================================================
# save_state – INSERT
# ============================================================

# Test Case ID: TC-FR-00-001serStateRepository_save_state_001
# Test Objective: Kiểm tra INSERT mới khi user chưa có record
# Input: user_id="600", state với meal_plan
# Expected Output: DB có record mới với meal_plan
# Notes: CheckDB: INSERT mới vào user_plans. Rollback tự động.
def test_save_state_insert_new(db_session, repo):
    state = {
        "meal_plan": {"day1": {"breakfast": {}}},
        "workout_plan": None
    }
    repo.save_state("600", state)

    saved = UserPlan.query.filter_by(user_id=600).first()
    assert saved is not None
    assert saved.meal_plan == {"day1": {"breakfast": {}}}
    assert saved.workout_plan is None


# ============================================================
# save_state – UPDATE
# ============================================================

# Test Case ID: TC-FR-00-001serStateRepository_save_state_002
# Test Objective: Kiểm tra UPDATE khi user đã có record
# Input: UserPlan tồn tại → cập nhật workout_plan
# Expected Output: workout_plan được cập nhật
# Notes: CheckDB: UPDATE existing record. Rollback tự động.
def test_save_state_update_existing(db_session, repo):
    user_plan = UserPlan(user_id=601, meal_plan={"day1": {}})
    db_session.add(user_plan)
    db_session.flush()

    new_state = {
        "meal_plan": {"day1": {"updated": True}},
        "workout_plan": {"Monday": {"exercises": []}}
    }
    repo.save_state("601", new_state)

    saved = UserPlan.query.filter_by(user_id=601).first()
    assert saved.meal_plan == {"day1": {"updated": True}}
    assert saved.workout_plan == {"Monday": {"exercises": []}}


# Test Case ID: TC-FR-00-001serStateRepository_save_state_003
# Test Objective: Kiểm tra partial update (chỉ cập nhật meal_plan)
# Input: State chỉ chứa meal_plan (không có workout_plan key)
# Expected Output: meal_plan cập nhật, workout_plan giữ nguyên
# Notes: CheckDB: chỉ field có trong state mới được cập nhật. Rollback tự động.
def test_save_state_partial_update(db_session, repo):
    user_plan = UserPlan(user_id=602, meal_plan={"old": True}, workout_plan={"original": True})
    db_session.add(user_plan)
    db_session.flush()

    repo.save_state("602", {"meal_plan": {"new": True}})

    saved = UserPlan.query.filter_by(user_id=602).first()
    assert saved.meal_plan == {"new": True}
    assert saved.workout_plan == {"original": True}


# Test Case ID: TC-FR-00-001serStateRepository_save_state_004
# Test Objective: Kiểm tra INSERT cả meal_plan và workout_plan cùng lúc
# Input: user mới, state chứa cả hai plan
# Expected Output: DB có record mới với cả hai plan
# Notes: CheckDB: cả hai fields được set. Rollback tự động.
def test_save_state_insert_both_plans(db_session, repo):
    state = {
        "meal_plan": {"day1": {}},
        "workout_plan": {"Monday": {}}
    }
    repo.save_state("603", state)

    saved = UserPlan.query.filter_by(user_id=603).first()
    assert saved is not None
    assert saved.meal_plan == {"day1": {}}
    assert saved.workout_plan == {"Monday": {}}
