"""
Unit tests cho module app.services.workout_plan_service
Kiểm tra WorkoutPlanService CRUD operations với DB thực (SQLite in-memory).
"""
import pytest

from app.services.workout_plan_service import WorkoutPlanService
from app.models.user_plan import UserPlan
from app import db


# ============================================================
# get_by_user_id
# ============================================================

# Test Case ID: TC_AI_TestWorkoutPlanService_get_by_user_id_001
# Test Objective: Kiểm tra lấy workout plan khi user chưa có plan
# Input: user_id=9999 (không tồn tại trong DB)
# Expected Output: None
# Notes: CheckDB: user_plans không có record cho user_id=9999. Rollback tự động.
def test_get_by_user_id_not_found(db_session):
    result = WorkoutPlanService.get_by_user_id(9999)
    assert result is None


# Test Case ID: TC_AI_TestWorkoutPlanService_get_by_user_id_002
# Test Objective: Kiểm tra lấy workout plan khi tồn tại
# Input: Tạo UserPlan với workout_plan data, sau đó gọi get_by_user_id
# Expected Output: Dict workout_plan đã lưu
# Notes: CheckDB: record tồn tại với workout_plan không None. Rollback tự động.
def test_get_by_user_id_found(db_session):
    plan_data = {"Monday": {"workout_type": "Strength"}}
    user_plan = UserPlan(user_id=100, workout_plan=plan_data)
    db_session.add(user_plan)
    db_session.flush()

    result = WorkoutPlanService.get_by_user_id(100)
    assert result == plan_data


# Test Case ID: TC_AI_TestWorkoutPlanService_get_by_user_id_003
# Test Objective: Kiểm tra khi user có record nhưng workout_plan là None
# Input: UserPlan tồn tại với workout_plan=None
# Expected Output: None
# Notes: CheckDB: record tồn tại nhưng workout_plan=None. Rollback tự động.
def test_get_by_user_id_plan_is_none(db_session):
    user_plan = UserPlan(user_id=101, workout_plan=None, meal_plan={"day1": {}})
    db_session.add(user_plan)
    db_session.flush()

    result = WorkoutPlanService.get_by_user_id(101)
    assert result is None


# ============================================================
# create
# ============================================================

# Test Case ID: TC_AI_TestWorkoutPlanService_create_001
# Test Objective: Kiểm tra tạo workout plan mới cho user chưa có record
# Input: user_id=200, workout_plan data
# Expected Output: workout_plan data được trả về, DB có record mới
# Notes: CheckDB: INSERT mới vào user_plans. Rollback tự động.
def test_create_new_user(db_session):
    plan_data = {"Monday": {"workout_type": "Cardio"}}
    result = WorkoutPlanService.create(200, plan_data)
    assert result == plan_data

    saved = UserPlan.query.filter_by(user_id=200).first()
    assert saved is not None
    assert saved.workout_plan == plan_data


# Test Case ID: TC_AI_TestWorkoutPlanService_create_002
# Test Objective: Kiểm tra tạo workout plan khi user đã có record nhưng chưa có workout_plan
# Input: UserPlan tồn tại với workout_plan=None → tạo mới
# Expected Output: workout_plan được cập nhật
# Notes: CheckDB: UPDATE existing record. Rollback tự động.
def test_create_existing_user_no_workout(db_session):
    user_plan = UserPlan(user_id=201, meal_plan={"day1": {}}, workout_plan=None)
    db_session.add(user_plan)
    db_session.flush()

    plan_data = {"Tuesday": {"workout_type": "Yoga"}}
    result = WorkoutPlanService.create(201, plan_data)
    assert result == plan_data


# Test Case ID: TC_AI_TestWorkoutPlanService_create_003
# Test Objective: Kiểm tra tạo workout plan khi đã tồn tại → raise ValueError
# Input: UserPlan đã có workout_plan → cố tạo lại
# Expected Output: Raise ValueError "Workout plan already exists"
# Notes: CheckDB: DB không thay đổi. Rollback tự động.
def test_create_already_exists(db_session):
    user_plan = UserPlan(user_id=202, workout_plan={"Monday": {}})
    db_session.add(user_plan)
    db_session.flush()

    with pytest.raises(ValueError, match="already exists"):
        WorkoutPlanService.create(202, {"Tuesday": {}})


# ============================================================
# update
# ============================================================

# Test Case ID: TC_AI_TestWorkoutPlanService_update_001
# Test Objective: Kiểm tra cập nhật workout plan thành công
# Input: UserPlan tồn tại với workout_plan → cập nhật plan mới
# Expected Output: Plan mới được trả về, DB được cập nhật
# Notes: CheckDB: workout_plan trong DB phải thay đổi. Rollback tự động.
def test_update_success(db_session):
    user_plan = UserPlan(user_id=300, workout_plan={"Monday": {"old": True}})
    db_session.add(user_plan)
    db_session.flush()

    new_plan = {"Monday": {"new": True}, "Tuesday": {"workout_type": "HIIT"}}
    result = WorkoutPlanService.update(300, new_plan)
    assert result == new_plan


# Test Case ID: TC_AI_TestWorkoutPlanService_update_002
# Test Objective: Kiểm tra cập nhật khi không tìm thấy user → raise ValueError
# Input: user_id=999 không tồn tại
# Expected Output: Raise ValueError "Workout plan not found"
# Notes: CheckDB: không có record nào bị ảnh hưởng. Rollback tự động.
def test_update_not_found(db_session):
    with pytest.raises(ValueError, match="not found"):
        WorkoutPlanService.update(999, {"Monday": {}})


# Test Case ID: TC_AI_TestWorkoutPlanService_update_003
# Test Objective: Kiểm tra cập nhật khi record tồn tại nhưng workout_plan=None
# Input: UserPlan tồn tại nhưng workout_plan=None
# Expected Output: Raise ValueError
# Notes: Nhánh not plan.workout_plan
def test_update_plan_is_none(db_session):
    user_plan = UserPlan(user_id=301, workout_plan=None)
    db_session.add(user_plan)
    db_session.flush()

    with pytest.raises(ValueError, match="not found"):
        WorkoutPlanService.update(301, {"Monday": {}})


# ============================================================
# delete
# ============================================================

# Test Case ID: TC_AI_TestWorkoutPlanService_delete_001
# Test Objective: Kiểm tra xóa workout plan thành công
# Input: UserPlan tồn tại với workout_plan
# Expected Output: workout_plan = None trong DB
# Notes: CheckDB: workout_plan trở thành None sau delete. Rollback tự động.
def test_delete_success(db_session):
    user_plan = UserPlan(user_id=400, workout_plan={"Monday": {}})
    db_session.add(user_plan)
    db_session.flush()

    WorkoutPlanService.delete(400)
    saved = UserPlan.query.filter_by(user_id=400).first()
    assert saved.workout_plan is None


# Test Case ID: TC_AI_TestWorkoutPlanService_delete_002
# Test Objective: Kiểm tra xóa khi không tìm thấy → raise ValueError
# Input: user_id=999 không tồn tại
# Expected Output: Raise ValueError "Workout plan not found"
# Notes: CheckDB: không có thay đổi. Rollback tự động.
def test_delete_not_found(db_session):
    with pytest.raises(ValueError, match="not found"):
        WorkoutPlanService.delete(999)


# Test Case ID: TC_AI_TestWorkoutPlanService_delete_003
# Test Objective: Kiểm tra xóa khi record tồn tại nhưng workout_plan=None
# Input: UserPlan tồn tại nhưng workout_plan=None
# Expected Output: Raise ValueError
# Notes: Nhánh not plan.workout_plan
def test_delete_plan_already_none(db_session):
    user_plan = UserPlan(user_id=401, workout_plan=None)
    db_session.add(user_plan)
    db_session.flush()

    with pytest.raises(ValueError, match="not found"):
        WorkoutPlanService.delete(401)
