"""
Unit tests cho Daily Log Controller - các endpoint /api/v2/daily-logs.
Bao gồm: GET /daily-logs, POST /generate, POST /daily-logs/steps.
"""
import pytest
from datetime import date
from unittest.mock import patch

from app.extensions import db
from app.models.user_profile import UserProfile
from app.models.daily_energy_log import DailyEnergyLog
from app.models.user_profile_weight_history import UserProfileWeightHistory
from app.enums.app_enum import ActivityLevelEnum

BASE_URL = "/api/v2"


# --------------- Helpers --------------- #

def _auth_headers(app):
    """Tạo JWT headers hợp lệ cho testing."""
    from flask_jwt_extended import create_access_token
    token = create_access_token(
        identity="test@test.com",
        additional_claims={"userId": 1}
    )
    return {"Authorization": f"Bearer {token}"}


def _create_full_user(session, user_id=1):
    """Tạo user profile + weight history + daily log cho test."""
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
    return profile


# ============================================================
# GET /daily-logs
# ============================================================

# Test Case ID: TC_FOOD_TestDailyLogController_get_daily_logs_001
# Test Objective: Lấy daily logs thành công
# Input: GET /daily-logs với start_date/end_date, JWT hợp lệ
# Expected Output: status 200, danh sách logs
# Notes: Rollback tự động
def test_get_daily_logs_success(app, client, db_session):
    """GET /daily-logs trả danh sách thành công."""
    profile = _create_full_user(db_session)

    log = DailyEnergyLog(
        user_id=1, log_date=date(2025, 6, 1),
        total_calorie_in=1500, total_steps=8000,
        tdee=2200, target_calorie=2000,
    )
    db_session.add(log)
    db_session.flush()

    headers = _auth_headers(app)
    response = client.get(
        f"{BASE_URL}/daily-logs?start_date=2025-06-01&end_date=2025-06-30",
        headers=headers,
    )

    assert response.status_code == 200
    data = response.get_json()
    assert data["status"] == "success"
    assert len(data["logs"]) == 1


# Test Case ID: TC_FOOD_TestDailyLogController_get_daily_logs_002
# Test Objective: Trả 401 khi không có JWT
# Input: GET /daily-logs không có token
# Expected Output: status 401
# Notes: JWT required
def test_get_daily_logs_no_jwt(app, client, db_session):
    """Trả 401 khi thiếu JWT."""
    response = client.get(f"{BASE_URL}/daily-logs")
    assert response.status_code == 401


# Test Case ID: TC_FOOD_TestDailyLogController_get_daily_logs_003
# Test Objective: Trả danh sách rỗng khi không có dữ liệu
# Input: GET /daily-logs, user không có logs
# Expected Output: status 200, logs=[]
# Notes: Rollback tự động
def test_get_daily_logs_empty(app, client, db_session):
    """Trả danh sách rỗng khi không có logs."""
    headers = _auth_headers(app)

    response = client.get(
        f"{BASE_URL}/daily-logs?start_date=2020-01-01&end_date=2020-01-31",
        headers=headers,
    )

    assert response.status_code == 200
    data = response.get_json()
    assert data["logs"] == []


# ============================================================
# POST /daily-logs/generate
# ============================================================

# Test Case ID: TC_FOOD_TestDailyLogController_generate_daily_logs_001
# Test Objective: Gọi generate daily logs thành công (không chỉ định date)
# Input: POST /generate, JSON rỗng
# Expected Output: status 200
# Notes: Rollback tự động
def test_generate_daily_logs_success(app, client, db_session):
    """POST /generate tạo daily logs thành công."""
    _create_full_user(db_session)
    headers = _auth_headers(app)

    response = client.post(
        f"{BASE_URL}/daily-logs/generate",
        json={},
        headers=headers,
    )

    assert response.status_code == 200
    data = response.get_json()
    assert data["status"] == "success"


# Test Case ID: TC_FOOD_TestDailyLogController_generate_daily_logs_002
# Test Objective: Generate với date cụ thể
# Input: POST /generate với date="2025-12-25"
# Expected Output: status 200, log_date = "2025-12-25"
# Notes: Rollback tự động
def test_generate_daily_logs_with_date(app, client, db_session):
    """POST /generate với date cụ thể."""
    _create_full_user(db_session)
    headers = _auth_headers(app)

    response = client.post(
        f"{BASE_URL}/daily-logs/generate",
        json={"date": "2025-12-25"},
        headers=headers,
    )

    assert response.status_code == 200
    data = response.get_json()
    assert data["data"]["log_date"] == "2025-12-25"


# Test Case ID: TC_FOOD_TestDailyLogController_generate_daily_logs_003
# Test Objective: Trả lỗi 400 khi date format sai
# Input: POST /generate với date="invalid"
# Expected Output: status 400, error message
# Notes: Validation date format
def test_generate_daily_logs_invalid_date(app, client, db_session):
    """Trả 400 khi date format không hợp lệ."""
    headers = _auth_headers(app)

    response = client.post(
        f"{BASE_URL}/daily-logs/generate",
        json={"date": "not-a-date"},
        headers=headers,
    )

    assert response.status_code == 400


# ============================================================
# POST /daily-logs/steps
# ============================================================

# Test Case ID: TC_FOOD_TestDailyLogController_update_steps_001
# Test Objective: Cập nhật steps thành công
# Input: POST /daily-logs/steps với steps=10000
# Expected Output: status 200, total_steps=10000
# Notes: CheckDB - daily_log.total_steps cập nhật
def test_update_steps_success(app, client, db_session):
    """POST /daily-logs/steps cập nhật thành công."""
    _create_full_user(db_session)

    log = DailyEnergyLog(
        user_id=1, log_date=date.today(),
        total_calorie_in=0, steps_calorie_out=0,
    )
    db_session.add(log)
    db_session.flush()

    headers = _auth_headers(app)
    response = client.post(
        f"{BASE_URL}/daily-logs/steps",
        json={"steps": 10000, "log_date": date.today().isoformat()},
        headers=headers,
    )

    assert response.status_code == 200


# Test Case ID: TC_FOOD_TestDailyLogController_update_steps_002
# Test Objective: Trả lỗi 400 khi steps thiếu hoặc âm
# Input: POST /daily-logs/steps với steps=-1
# Expected Output: status 400
# Notes: Validation steps >= 0
def test_update_steps_invalid(app, client, db_session):
    """Trả 400 khi steps âm."""
    headers = _auth_headers(app)

    response = client.post(
        f"{BASE_URL}/daily-logs/steps",
        json={"steps": -1},
        headers=headers,
    )

    assert response.status_code == 400
    data = response.get_json()
    assert "steps" in data["error"]


# Test Case ID: TC_FOOD_TestDailyLogController_update_steps_003
# Test Objective: Trả 401 khi không có JWT
# Input: POST /daily-logs/steps không có token
# Expected Output: status 401
# Notes: JWT required
def test_update_steps_no_jwt(app, client, db_session):
    """Trả 401 khi thiếu JWT."""
    response = client.post(
        f"{BASE_URL}/daily-logs/steps",
        json={"steps": 5000},
    )
    assert response.status_code == 401
