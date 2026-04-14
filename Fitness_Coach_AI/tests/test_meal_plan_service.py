"""
Unit tests cho module app.services.meal_plan_service
Kiểm tra MealPlanService CRUD operations với DB thực (SQLite in-memory).
"""
import pytest

from app.services.meal_plan_service import MealPlanService
from app.models.user_plan import UserPlan
from app import db


# ============================================================
# get_by_user_id
# ============================================================

# Test Case ID: TC-FR-00-001ealPlanService_get_by_user_id_001
# Test Objective: Kiểm tra lấy meal plan khi user chưa có record
# Input: user_id=9999
# Expected Output: None
# Notes: CheckDB: không có record cho user_id. Rollback tự động.
def test_get_by_user_id_not_found(db_session):
    result = MealPlanService.get_by_user_id(9999)
    assert result is None


# Test Case ID: TC-FR-00-001ealPlanService_get_by_user_id_002
# Test Objective: Kiểm tra lấy meal plan khi tồn tại
# Input: UserPlan với meal_plan data
# Expected Output: Dict meal_plan
# Notes: CheckDB: record tồn tại với meal_plan data. Rollback tự động.
def test_get_by_user_id_found(db_session):
    plan_data = {"day1": {"breakfast": {"description": "Phở"}}}
    user_plan = UserPlan(user_id=100, meal_plan=plan_data)
    db_session.add(user_plan)
    db_session.flush()

    result = MealPlanService.get_by_user_id(100)
    assert result == plan_data


# Test Case ID: TC-FR-00-001ealPlanService_get_by_user_id_003
# Test Objective: Kiểm tra khi record tồn tại nhưng meal_plan là None
# Input: UserPlan với meal_plan=None
# Expected Output: None
# Notes: CheckDB: record tồn tại nhưng meal_plan=None. Rollback tự động.
def test_get_by_user_id_plan_is_none(db_session):
    user_plan = UserPlan(user_id=101, meal_plan=None, workout_plan={"Mon": {}})
    db_session.add(user_plan)
    db_session.flush()

    result = MealPlanService.get_by_user_id(101)
    assert result is None


# ============================================================
# create
# ============================================================

# Test Case ID: TC-FR-00-001ealPlanService_create_001
# Test Objective: Kiểm tra tạo meal plan cho user mới (chưa có record)
# Input: user_id=200, plan data
# Expected Output: Plan data, DB có record mới
# Notes: CheckDB: INSERT mới vào user_plans. Rollback tự động.
def test_create_new_user(db_session):
    plan_data = {"day1": {"breakfast": {"description": "Cơm tấm"}}}
    result = MealPlanService.create(200, plan_data)
    assert result == plan_data

    saved = UserPlan.query.filter_by(user_id=200).first()
    assert saved is not None
    assert saved.meal_plan == plan_data


# Test Case ID: TC-FR-00-001ealPlanService_create_002
# Test Objective: Kiểm tra tạo meal plan khi user đã có record (update meal_plan)
# Input: UserPlan tồn tại, gọi create với plan mới
# Expected Output: Plan mới được cập nhật (MealPlanService.create là upsert)
# Notes: CheckDB: UPDATE existing record. Rollback tự động.
def test_create_existing_user_updates(db_session):
    user_plan = UserPlan(user_id=201, meal_plan={"old": True})
    db_session.add(user_plan)
    db_session.flush()

    new_plan = {"day1": {"breakfast": {"description": "Bún bò"}}}
    result = MealPlanService.create(201, new_plan)
    assert result == new_plan

    saved = UserPlan.query.filter_by(user_id=201).first()
    assert saved.meal_plan == new_plan


# Test Case ID: TC-FR-00-001ealPlanService_create_003
# Test Objective: Kiểm tra tạo meal plan khi user có record nhưng meal_plan=None
# Input: UserPlan tồn tại với meal_plan=None
# Expected Output: meal_plan được set thành plan mới
# Notes: CheckDB: meal_plan cập nhật từ None → plan data. Rollback tự động.
def test_create_existing_user_meal_is_none(db_session):
    user_plan = UserPlan(user_id=202, meal_plan=None)
    db_session.add(user_plan)
    db_session.flush()

    plan_data = {"day1": {}}
    result = MealPlanService.create(202, plan_data)
    assert result == plan_data


# ============================================================
# update
# ============================================================

# Test Case ID: TC-FR-00-001ealPlanService_update_001
# Test Objective: Kiểm tra cập nhật meal plan thành công
# Input: UserPlan tồn tại với meal_plan → cập nhật mới
# Expected Output: Plan mới được trả về và lưu vào DB
# Notes: CheckDB: meal_plan thay đổi. Rollback tự động.
def test_update_success(db_session):
    user_plan = UserPlan(user_id=300, meal_plan={"day1": {"old": True}})
    db_session.add(user_plan)
    db_session.flush()

    new_plan = {"day1": {"breakfast": {"description": "Mì Quảng"}}}
    result = MealPlanService.update(300, new_plan)
    assert result == new_plan


# Test Case ID: TC-FR-00-001ealPlanService_update_002
# Test Objective: Kiểm tra update khi không tìm thấy user → raise ValueError
# Input: user_id=999 không tồn tại
# Expected Output: Raise ValueError "Meal plan not found"
# Notes: CheckDB: không có record nào bị thay đổi. Rollback tự động.
def test_update_not_found(db_session):
    with pytest.raises(ValueError, match="not found"):
        MealPlanService.update(999, {"day1": {}})


# Test Case ID: TC-FR-00-001ealPlanService_update_003
# Test Objective: Kiểm tra update khi record tồn tại nhưng meal_plan=None
# Input: UserPlan tồn tại nhưng meal_plan=None
# Expected Output: Raise ValueError
# Notes: Nhánh not user_plan.meal_plan
def test_update_plan_is_none(db_session):
    user_plan = UserPlan(user_id=301, meal_plan=None)
    db_session.add(user_plan)
    db_session.flush()

    with pytest.raises(ValueError, match="not found"):
        MealPlanService.update(301, {"day1": {}})


# ============================================================
# delete
# ============================================================

# Test Case ID: TC-FR-00-001ealPlanService_delete_001
# Test Objective: Kiểm tra xóa meal plan thành công
# Input: UserPlan tồn tại với meal_plan
# Expected Output: meal_plan = None trong DB
# Notes: CheckDB: meal_plan trở thành None. Rollback tự động.
def test_delete_success(db_session):
    user_plan = UserPlan(user_id=400, meal_plan={"day1": {}})
    db_session.add(user_plan)
    db_session.flush()

    MealPlanService.delete(400)
    saved = UserPlan.query.filter_by(user_id=400).first()
    assert saved.meal_plan is None


# Test Case ID: TC-FR-00-001ealPlanService_delete_002
# Test Objective: Kiểm tra xóa khi không tìm thấy → raise ValueError
# Input: user_id=999
# Expected Output: Raise ValueError "Meal plan not found"
# Notes: CheckDB: không có thay đổi. Rollback tự động.
def test_delete_not_found(db_session):
    with pytest.raises(ValueError, match="not found"):
        MealPlanService.delete(999)


# Test Case ID: TC-FR-00-001ealPlanService_delete_003
# Test Objective: Kiểm tra xóa khi record tồn tại nhưng meal_plan=None
# Input: UserPlan tồn tại nhưng meal_plan=None
# Expected Output: Raise ValueError
# Notes: Nhánh not user_plan.meal_plan
def test_delete_plan_already_none(db_session):
    user_plan = UserPlan(user_id=401, meal_plan=None)
    db_session.add(user_plan)
    db_session.flush()

    with pytest.raises(ValueError, match="not found"):
        MealPlanService.delete(401)
