"""
Unit tests cho User Profile Controller - các endpoint /api/v2/user-profile/.
Bao gồm: GET/POST/PUT profile, GET weight-history, GET ai/profile-input, GET ai/goal-input.
"""
import pytest
from datetime import date
from unittest.mock import patch

from app.extensions import db
from app.models.user_profile import UserProfile
from app.models.daily_energy_log import DailyEnergyLog
from app.models.user_profile_weight_history import UserProfileWeightHistory
from app.enums.app_enum import ActivityLevelEnum

BASE_URL = "/api/v2/user-profile"


# --------------- Helpers --------------- #

def _auth_headers(app):
    """Tạo JWT headers hợp lệ cho testing."""
    from flask_jwt_extended import create_access_token
    token = create_access_token(
        identity="test@test.com",
        additional_claims={"userId": 1}
    )
    return {"Authorization": f"Bearer {token}"}


def _create_profile(session, user_id=1):
    """Tạo user profile + weight history cho test."""
    profile = UserProfile(
        user_id=user_id, gender="male", date_of_birth=date(1995, 6, 15),
        activity_level=ActivityLevelEnum.moderately_active,
        aim_weight=70.0, day_of_activities=5,
    )
    session.add(profile)
    session.flush()

    wh = UserProfileWeightHistory(
        user_profile_id=profile.id, height_cm=170, weight_kg=70, bmi=24.22,
    )
    session.add(wh)
    session.flush()
    return profile, wh


# ============================================================
# GET /user-profile
# ============================================================

# Test Case ID: TC_FOOD_TestUserProfileController_get_user_profile_001
# Test Objective: Lấy profile thành công
# Input: GET /user-profile với JWT hợp lệ, user có profile
# Expected Output: status 200, dữ liệu profile
# Notes: Rollback tự động
def test_get_user_profile_success(app, client, db_session):
    """GET user-profile trả profile thành công."""
    _create_profile(db_session, user_id=1)
    headers = _auth_headers(app)

    response = client.get(BASE_URL, headers=headers)

    assert response.status_code == 200
    data = response.get_json()
    assert data["user_id"] == 1
    assert data["gender"] == "male"


# Test Case ID: TC_FOOD_TestUserProfileController_get_user_profile_002
# Test Objective: Trả 404 khi không có profile
# Input: GET /user-profile, user chưa tạo profile
# Expected Output: status 404, error "Profile not found"
# Notes: Rollback tự động
def test_get_user_profile_not_found(app, client, db_session):
    """Trả 404 khi user chưa có profile."""
    headers = _auth_headers(app)

    response = client.get(BASE_URL, headers=headers)

    assert response.status_code == 404


# Test Case ID: TC_FOOD_TestUserProfileController_get_user_profile_003
# Test Objective: Trả 401 khi không có JWT
# Input: GET /user-profile không có Authorization header
# Expected Output: status 401
# Notes: JWT required
def test_get_user_profile_no_jwt(app, client, db_session):
    """Trả 401 khi thiếu JWT."""
    response = client.get(BASE_URL)
    assert response.status_code == 401


# ============================================================
# POST /user-profile
# ============================================================

# Test Case ID: TC_FOOD_TestUserProfileController_create_user_profile_001
# Test Objective: Tạo profile mới thành công
# Input: POST với payload hợp lệ, auth service trả dữ liệu
# Expected Output: status 201
# Notes: Mock fetch_user_profile và upsert_today_daily_log
@patch("app.services.user_profile_service.upsert_today_daily_log", return_value=True)
@patch("app.services.user_profile_service.fetch_user_profile")
def test_create_user_profile_success(mock_fetch, mock_upsert, app, client, db_session):
    """POST user-profile tạo profile thành công."""
    mock_fetch.return_value = {"gender": "male", "dateOfBirth": "1995-06-15"}

    headers = _auth_headers(app)
    response = client.post(BASE_URL, json={
        "activity_level": "moderately_active",
        "aim_weight": 70.0,
        "height_cm": 170.0,
        "weight_kg": 70.0,
    }, headers=headers)

    assert response.status_code == 201


# Test Case ID: TC_FOOD_TestUserProfileController_create_user_profile_002
# Test Objective: Trả 400 khi profile đã tồn tại
# Input: POST khi user đã có profile
# Expected Output: status 400
# Notes: Rollback tự động
def test_create_user_profile_already_exists(app, client, db_session):
    """Trả 400 khi profile đã tồn tại."""
    _create_profile(db_session, user_id=1)
    headers = _auth_headers(app)

    response = client.post(BASE_URL, json={
        "activity_level": "moderately_active",
        "aim_weight": 70.0,
    }, headers=headers)

    assert response.status_code == 400


# ============================================================
# PUT /user-profile
# ============================================================

# Test Case ID: TC_FOOD_TestUserProfileController_update_user_profile_001
# Test Objective: Cập nhật profile thành công
# Input: PUT với activity_level mới
# Expected Output: status 200
# Notes: Mock upsert_today_daily_log
@patch("app.services.user_profile_service.upsert_today_daily_log", return_value=True)
def test_update_user_profile_success(mock_upsert, app, client, db_session):
    """PUT user-profile cập nhật thành công."""
    _create_profile(db_session, user_id=1)
    headers = _auth_headers(app)

    response = client.put(BASE_URL, json={
        "activity_level": "very_active",
    }, headers=headers)

    assert response.status_code == 200


# ============================================================
# GET /user-profile/weight-history
# ============================================================

# Test Case ID: TC_FOOD_TestUserProfileController_weight_history_001
# Test Objective: Lấy lịch sử cân nặng thành công
# Input: GET /weight-history, user có weight history
# Expected Output: status 200, weight_history list
# Notes: Rollback tự động
def test_get_weight_history_success(app, client, db_session):
    """GET weight-history trả lịch sử thành công."""
    _create_profile(db_session, user_id=1)
    headers = _auth_headers(app)

    response = client.get(f"{BASE_URL}/weight-history", headers=headers)

    assert response.status_code == 200
    data = response.get_json()
    assert len(data["weight_history"]) >= 1


# Test Case ID: TC_FOOD_TestUserProfileController_weight_history_002
# Test Objective: Trả 401 khi không có JWT
# Input: GET /weight-history không có token
# Expected Output: status 401
# Notes: JWT required
def test_get_weight_history_no_jwt(app, client, db_session):
    """Trả 401 khi thiếu JWT."""
    response = client.get(f"{BASE_URL}/weight-history")
    assert response.status_code == 401


# ============================================================
# GET /user-profile/ai/profile-input
# ============================================================

# Test Case ID: TC_FOOD_TestUserProfileController_ai_profile_input_001
# Test Objective: Lấy AI profile input thành công
# Input: GET /ai/profile-input, user có đầy đủ dữ liệu
# Expected Output: status 200, dict chứa age, gender, etc.
# Notes: Rollback tự động
def test_get_ai_profile_input_success(app, client, db_session):
    """GET ai/profile-input trả dữ liệu thành công."""
    _create_profile(db_session, user_id=1)
    headers = _auth_headers(app)

    response = client.get(f"{BASE_URL}/ai/profile-input", headers=headers)

    assert response.status_code == 200
    data = response.get_json()
    assert "age" in data
    assert "gender" in data


# Test Case ID: TC_FOOD_TestUserProfileController_ai_profile_input_002
# Test Objective: Trả 400 khi thiếu dữ liệu
# Input: GET /ai/profile-input, user không có profile
# Expected Output: status 400, error message
# Notes: Rollback tự động
def test_get_ai_profile_input_error(app, client, db_session):
    """Trả 400 khi thiếu dữ liệu profile."""
    headers = _auth_headers(app)

    response = client.get(f"{BASE_URL}/ai/profile-input", headers=headers)

    assert response.status_code == 400
    data = response.get_json()
    assert "error" in data


# ============================================================
# GET /user-profile/ai/goal-input
# ============================================================

# Test Case ID: TC_FOOD_TestUserProfileController_ai_goal_input_001
# Test Objective: Lấy AI goal input thành công
# Input: GET /ai/goal-input, user có đầy đủ dữ liệu + daily log
# Expected Output: status 200, dict chứa goal, calorie_target
# Notes: Rollback tự động
def test_get_ai_goal_input_success(app, client, db_session):
    """GET ai/goal-input trả dữ liệu thành công."""
    _create_profile(db_session, user_id=1)

    log = DailyEnergyLog(
        user_id=1, log_date=date.today(),
        total_calorie_in=0, target_calorie=2200,
    )
    db_session.add(log)
    db_session.flush()

    headers = _auth_headers(app)
    response = client.get(f"{BASE_URL}/ai/goal-input", headers=headers)

    assert response.status_code == 200
    data = response.get_json()
    assert "goal" in data
    assert "calorie_target" in data


# Test Case ID: TC_FOOD_TestUserProfileController_ai_goal_input_002
# Test Objective: Trả 400 khi thiếu profile
# Input: GET /ai/goal-input, user không có profile
# Expected Output: status 400
# Notes: Rollback tự động
def test_get_ai_goal_input_error(app, client, db_session):
    """Trả 400 khi thiếu dữ liệu profile."""
    headers = _auth_headers(app)

    response = client.get(f"{BASE_URL}/ai/goal-input", headers=headers)

    assert response.status_code == 400
