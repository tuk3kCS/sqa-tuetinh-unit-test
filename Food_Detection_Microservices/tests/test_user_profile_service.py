"""
Unit tests cho UserProfileService và các hàm hỗ trợ:
get_user_profile, create_user_profile, update_user_profile,
get_weight_history, build_ai_profile_input, build_ai_goal_input,
upsert_today_daily_log, build_user_profile_response.
"""
import pytest
from datetime import date, datetime
from unittest.mock import patch, MagicMock

from app.extensions import db
from app.models.user_profile import UserProfile
from app.models.daily_energy_log import DailyEnergyLog
from app.models.user_profile_weight_history import UserProfileWeightHistory
from app.enums.app_enum import ActivityLevelEnum
from app.services.user_profile_service import (
    UserProfileService,
    upsert_today_daily_log,
    build_user_profile_response,
    _get_profile_and_latest_weight,
)


# --------------- Helpers --------------- #

def _create_profile_with_weight(session, user_id=1, gender="male",
                                 activity_level=ActivityLevelEnum.moderately_active,
                                 aim_weight=70.0, height_cm=170.0, weight_kg=70.0,
                                 dob=None, day_of_activities=5):
    """Tạo UserProfile + WeightHistory cho testing."""
    dob = dob or date(1995, 6, 15)
    profile = UserProfile(
        user_id=user_id,
        gender=gender,
        date_of_birth=dob,
        activity_level=activity_level,
        aim_weight=aim_weight,
        day_of_activities=day_of_activities,
    )
    session.add(profile)
    session.flush()

    bmi = round(weight_kg / ((height_cm / 100) ** 2), 2) if height_cm and weight_kg else None
    wh = UserProfileWeightHistory(
        user_profile_id=profile.id,
        height_cm=height_cm,
        weight_kg=weight_kg,
        bmi=bmi,
    )
    session.add(wh)
    session.flush()
    return profile, wh


# ============================================================
# GET USER PROFILE
# ============================================================

# Test Case ID: TC-FR-00-001serProfileService_get_user_profile_001
# Test Objective: Lấy profile thành công khi user tồn tại
# Input: user_id=1 có profile trong DB
# Expected Output: status 200, dữ liệu profile đúng
# Notes: CheckDB - dữ liệu trả về khớp DB
def test_user_profile_service_get_user_profile_exists(app, db_session):
    """Lấy profile thành công khi user có profile."""
    _create_profile_with_weight(db_session, user_id=1, gender="male")

    response, status = UserProfileService.get_user_profile(user_id=1)
    data = response.get_json()

    assert status == 200
    assert data["user_id"] == 1
    assert data["gender"] == "male"
    assert data["height_cm"] == 170.0
    assert data["weight_kg"] == 70.0


# Test Case ID: TC-FR-00-001serProfileService_get_user_profile_002
# Test Objective: Trả lỗi khi user không có profile
# Input: user_id=9999 không tồn tại
# Expected Output: status 404, error "Profile not found"
# Notes: Rollback tự động
def test_user_profile_service_get_user_profile_not_exists(app, db_session):
    """Trả lỗi 404 khi không tìm thấy profile."""
    response, status = UserProfileService.get_user_profile(user_id=9999)
    data = response.get_json()

    assert status == 404
    assert data["error"] == "Profile not found"


# ============================================================
# CREATE USER PROFILE
# ============================================================

# Test Case ID: TC-FR-00-001serProfileService_create_user_profile_001
# Test Objective: Tạo profile mới thành công
# Input: user_id=1 chưa có profile, payload hợp lệ, auth service trả dữ liệu
# Expected Output: status 201, profile mới được tạo
# Notes: CheckDB - UserProfile và WeightHistory được tạo
@patch("app.services.user_profile_service.fetch_user_profile")
@patch("app.services.user_profile_service.upsert_today_daily_log", return_value=True)
def test_user_profile_service_create_success(mock_upsert, mock_fetch, app, db_session):
    """Tạo profile mới thành công với dữ liệu từ Auth Service."""
    mock_fetch.return_value = {
        "gender": "male",
        "dateOfBirth": "1995-06-15",
    }

    payload = {
        "activity_level": "moderately_active",
        "aim_weight": 70.0,
        "height_cm": 170.0,
        "weight_kg": 70.0,
    }

    response, status = UserProfileService.create_user_profile(
        user_id=1, payload=payload, jwt_token="fake-token"
    )
    data = response.get_json()

    assert status == 201
    assert data["user_id"] == 1
    assert data["gender"] == "male"

    # CheckDB: profile đã được tạo
    profile = UserProfile.query.filter_by(user_id=1).first()
    assert profile is not None


# Test Case ID: TC-FR-00-001serProfileService_create_user_profile_002
# Test Objective: Trả lỗi khi profile đã tồn tại
# Input: user_id=1 đã có profile
# Expected Output: status 400, error "Profile already exists"
# Notes: Không tạo trùng profile
def test_user_profile_service_create_already_exists(app, db_session):
    """Trả lỗi 400 khi profile đã tồn tại."""
    _create_profile_with_weight(db_session, user_id=1)

    response, status = UserProfileService.create_user_profile(
        user_id=1, payload={}, jwt_token="fake"
    )
    data = response.get_json()

    assert status == 400
    assert data["error"] == "Profile already exists"


# Test Case ID: TC-FR-00-001serProfileService_create_user_profile_003
# Test Objective: Trả lỗi 502 khi Auth Service lỗi
# Input: fetch_user_profile raise Exception
# Expected Output: status 502
# Notes: Xử lý lỗi từ external service
@patch("app.services.user_profile_service.fetch_user_profile", side_effect=Exception("Connection refused"))
def test_user_profile_service_create_auth_error(mock_fetch, app, db_session):
    """Trả lỗi 502 khi Auth Service không phản hồi."""
    payload = {"activity_level": "moderately_active", "aim_weight": 70.0}

    response, status = UserProfileService.create_user_profile(
        user_id=1, payload=payload, jwt_token="fake"
    )
    data = response.get_json()

    assert status == 502
    assert "Connection refused" in data["error"]


# ============================================================
# UPDATE USER PROFILE
# ============================================================

# Test Case ID: TC-FR-00-001serProfileService_update_user_profile_001
# Test Objective: Cập nhật profile thành công khi đã tồn tại
# Input: user_id=1 có profile, payload chứa activity_level mới
# Expected Output: status 200, profile đã cập nhật
# Notes: CheckDB - activity_level cập nhật đúng
@patch("app.services.user_profile_service.upsert_today_daily_log", return_value=True)
def test_user_profile_service_update_exists(mock_upsert, app, db_session):
    """Cập nhật profile thành công."""
    _create_profile_with_weight(db_session, user_id=1)

    payload = {"activity_level": "very_active"}

    response, status = UserProfileService.update_user_profile(
        user_id=1, payload=payload, jwt_token="fake"
    )
    data = response.get_json()

    assert status == 200
    assert data["user_id"] == 1


# Test Case ID: TC-FR-00-001serProfileService_update_user_profile_002
# Test Objective: Tự tạo profile mới khi chưa có (fallback to create)
# Input: user_id=2 chưa có profile
# Expected Output: status 201 (gọi create_user_profile)
# Notes: update fallback to create khi profile không tồn tại
@patch("app.services.user_profile_service.fetch_user_profile")
@patch("app.services.user_profile_service.upsert_today_daily_log", return_value=True)
def test_user_profile_service_update_creates_if_not_exists(mock_upsert, mock_fetch, app, db_session):
    """Tạo profile mới khi chưa tồn tại."""
    mock_fetch.return_value = {"gender": "female", "dateOfBirth": "1998-03-20"}

    payload = {
        "activity_level": "lightly_active",
        "aim_weight": 55.0,
        "height_cm": 160.0,
        "weight_kg": 55.0,
    }

    response, status = UserProfileService.update_user_profile(
        user_id=2, payload=payload, jwt_token="fake"
    )

    assert status == 201


# Test Case ID: TC-FR-00-001serProfileService_update_user_profile_003
# Test Objective: Thêm weight history mới khi weight thay đổi
# Input: user_id=1, weight_kg mới khác weight cũ
# Expected Output: WeightHistory mới được tạo
# Notes: CheckDB - 2 weight history records tồn tại
@patch("app.services.user_profile_service.upsert_today_daily_log", return_value=True)
def test_user_profile_service_update_weight_changed(mock_upsert, app, db_session):
    """Thêm weight history khi weight thay đổi."""
    profile, _ = _create_profile_with_weight(db_session, user_id=1, weight_kg=70)

    payload = {"weight_kg": 68.5}

    response, status = UserProfileService.update_user_profile(
        user_id=1, payload=payload, jwt_token="fake"
    )

    assert status == 200

    # CheckDB: có 2 weight history records
    histories = UserProfileWeightHistory.query.filter_by(
        user_profile_id=profile.id
    ).all()
    assert len(histories) == 2


# ============================================================
# WEIGHT HISTORY
# ============================================================

# Test Case ID: TC-FR-00-001serProfileService_get_weight_history_001
# Test Objective: Lấy lịch sử cân nặng thành công
# Input: user_id=1 có profile + weight history
# Expected Output: status 200, weight_history có dữ liệu
# Notes: CheckDB - dữ liệu trả về khớp DB
def test_user_profile_service_get_weight_history_exists(app, db_session):
    """Lấy weight history thành công."""
    _create_profile_with_weight(db_session, user_id=1, weight_kg=70, height_cm=170)

    response, status = UserProfileService.get_weight_history(user_id=1)
    data = response.get_json()

    assert status == 200
    assert len(data["weight_history"]) == 1
    assert data["weight_history"][0]["weight_kg"] == 70.0
    assert data["weight_history"][0]["comment"] == "Normal"


# Test Case ID: TC-FR-00-001serProfileService_get_weight_history_002
# Test Objective: Trả lỗi khi user không có profile
# Input: user_id=9999
# Expected Output: status 404
# Notes: Rollback tự động
def test_user_profile_service_get_weight_history_not_exists(app, db_session):
    """Trả lỗi 404 khi không có profile."""
    response, status = UserProfileService.get_weight_history(user_id=9999)
    data = response.get_json()

    assert status == 404
    assert data["error"] == "Profile not found"


# Test Case ID: TC-FR-00-001serProfileService_get_weight_history_003
# Test Objective: Kiểm tra BMI comment cho các mức BMI khác nhau
# Input: Nhiều weight history với BMI khác nhau
# Expected Output: comment phù hợp (Underweight, Normal, Overweight, Obese)
# Notes: CheckDB - kiểm tra logic bmi_comment
def test_user_profile_service_weight_history_bmi_comments(app, db_session):
    """Kiểm tra BMI comment cho các mức BMI."""
    profile = UserProfile(
        user_id=1, gender="male", date_of_birth=date(1995, 6, 15),
        activity_level=ActivityLevelEnum.moderately_active, aim_weight=70.0,
    )
    db_session.add(profile)
    db_session.flush()

    bmi_cases = [
        (170, 45, 15.57),   # Underweight < 18.5
        (170, 70, 24.22),   # Normal 18.5-25
        (170, 85, 29.41),   # Overweight 25-30
        (170, 100, 34.60),  # Obese >= 30
    ]

    for h, w, bmi_val in bmi_cases:
        wh = UserProfileWeightHistory(
            user_profile_id=profile.id, height_cm=h, weight_kg=w,
            bmi=bmi_val,
        )
        db_session.add(wh)
    db_session.flush()

    response, status = UserProfileService.get_weight_history(user_id=1)
    data = response.get_json()

    comments = [h["comment"] for h in data["weight_history"]]
    assert "Underweight" in comments
    assert "Normal" in comments
    assert "Overweight" in comments
    assert "Obese" in comments


# ============================================================
# BUILD AI PROFILE INPUT
# ============================================================

# Test Case ID: TC-FR-00-001serProfileService_build_ai_profile_input_001
# Test Objective: Tạo AI profile input thành công
# Input: user_id=1 có đầy đủ profile + weight
# Expected Output: dict chứa age, gender, height_cm, weight_kg, experience_level
# Notes: Kiểm tra mapping activity_level → experience_level
def test_user_profile_service_build_ai_profile_input_success(app, db_session):
    """Tạo AI profile input thành công."""
    _create_profile_with_weight(
        db_session, user_id=1,
        activity_level=ActivityLevelEnum.moderately_active,
        day_of_activities=5,
    )

    data, error = UserProfileService.build_ai_profile_input(user_id=1)

    assert error is None
    assert data["gender"] == "male"
    assert data["height_cm"] == 170
    assert data["weight_kg"] == 70.0
    assert data["experience_level"] == "intermediate"
    assert data["available_days_per_week"] == 5
    assert data["age"] > 0


# Test Case ID: TC-FR-00-001serProfileService_build_ai_profile_input_002
# Test Objective: Trả lỗi khi user không có profile
# Input: user_id=9999
# Expected Output: None, error message
# Notes: Rollback tự động
def test_user_profile_service_build_ai_profile_input_no_profile(app, db_session):
    """Trả lỗi khi không có profile."""
    data, error = UserProfileService.build_ai_profile_input(user_id=9999)

    assert data is None
    assert error == "User profile not found"


# Test Case ID: TC-FR-00-001serProfileService_build_ai_profile_input_003
# Test Objective: Trả lỗi khi profile không có date_of_birth
# Input: user_id=1 có profile nhưng không có date_of_birth
# Expected Output: None, error "Date of birth not set"
# Notes: Rollback tự động
def test_user_profile_service_build_ai_profile_input_no_dob(app, db_session):
    """Trả lỗi khi profile thiếu date_of_birth."""
    profile = UserProfile(
        user_id=1, gender="male", date_of_birth=None,
        activity_level=ActivityLevelEnum.moderately_active, aim_weight=70.0,
    )
    db_session.add(profile)
    db_session.flush()

    data, error = UserProfileService.build_ai_profile_input(user_id=1)
    assert data is None
    assert error == "Date of birth not set"


# ============================================================
# BUILD AI GOAL INPUT
# ============================================================

# Test Case ID: TC-FR-00-001serProfileService_build_ai_goal_input_001
# Test Objective: Tạo AI goal input - mục tiêu giảm cân
# Input: aim_weight < weight_kg
# Expected Output: goal = "lose weight"
# Notes: weight_diff < 0 → goal = lose weight
def test_user_profile_service_build_ai_goal_input_lose_weight(app, db_session):
    """AI goal input cho mục tiêu giảm cân."""
    _create_profile_with_weight(
        db_session, user_id=1, aim_weight=60, weight_kg=70,
    )

    log = DailyEnergyLog(
        user_id=1, log_date=date.today(),
        total_calorie_in=0, target_calorie=2000,
    )
    db_session.add(log)
    db_session.flush()

    data, error = UserProfileService.build_ai_goal_input(user_id=1)

    assert error is None
    assert data["goal"] == "lose weight"
    assert data["calorie_target"] == 2000


# Test Case ID: TC-FR-00-001serProfileService_build_ai_goal_input_002
# Test Objective: Tạo AI goal input - mục tiêu tăng cân
# Input: aim_weight > weight_kg
# Expected Output: goal = "gain weight"
# Notes: weight_diff > 0 → goal = gain weight
def test_user_profile_service_build_ai_goal_input_gain_weight(app, db_session):
    """AI goal input cho mục tiêu tăng cân."""
    _create_profile_with_weight(
        db_session, user_id=1, aim_weight=80, weight_kg=70,
    )

    log = DailyEnergyLog(
        user_id=1, log_date=date.today(),
        total_calorie_in=0, target_calorie=2500,
    )
    db_session.add(log)
    db_session.flush()

    data, error = UserProfileService.build_ai_goal_input(user_id=1)

    assert error is None
    assert data["goal"] == "gain weight"


# Test Case ID: TC-FR-00-001serProfileService_build_ai_goal_input_003
# Test Objective: Tạo AI goal input - duy trì cân nặng
# Input: aim_weight == weight_kg
# Expected Output: goal = "maintenance"
# Notes: weight_diff == 0 → maintenance
def test_user_profile_service_build_ai_goal_input_maintenance(app, db_session):
    """AI goal input cho mục tiêu duy trì cân nặng."""
    _create_profile_with_weight(
        db_session, user_id=1, aim_weight=70, weight_kg=70,
    )

    log = DailyEnergyLog(
        user_id=1, log_date=date.today(),
        total_calorie_in=0, target_calorie=2200,
    )
    db_session.add(log)
    db_session.flush()

    data, error = UserProfileService.build_ai_goal_input(user_id=1)

    assert error is None
    assert data["goal"] == "maintenance"


# Test Case ID: TC-FR-00-001serProfileService_build_ai_goal_input_004
# Test Objective: Trả lỗi khi không có profile
# Input: user_id=9999
# Expected Output: None, error message
# Notes: Rollback tự động
def test_user_profile_service_build_ai_goal_input_no_profile(app, db_session):
    """Trả lỗi khi không có profile."""
    data, error = UserProfileService.build_ai_goal_input(user_id=9999)

    assert data is None
    assert error == "User profile not found"


# ============================================================
# UPSERT TODAY DAILY LOG
# ============================================================

# Test Case ID: TC-FR-00-001serProfileService_upsert_today_daily_log_001
# Test Objective: Tạo daily log mới cho hôm nay
# Input: user_id=1, chưa có daily log hôm nay
# Expected Output: True, DailyEnergyLog mới được tạo
# Notes: CheckDB - daily log được tạo với tdee và target_calorie
def test_upsert_today_daily_log_create(app, db_session):
    """Tạo daily log mới khi chưa tồn tại."""
    _create_profile_with_weight(db_session, user_id=1)

    result = upsert_today_daily_log(user_id=1)
    assert result is True

    # CheckDB: log đã được tạo
    log = DailyEnergyLog.query.filter_by(
        user_id=1, log_date=date.today()
    ).first()
    assert log is not None
    assert log.tdee > 0


# Test Case ID: TC-FR-00-001serProfileService_upsert_today_daily_log_002
# Test Objective: Trả False khi không tính được TDEE
# Input: user_id=9999 không có profile
# Expected Output: False
# Notes: calculate_tdee trả None
def test_upsert_today_daily_log_no_tdee(app, db_session):
    """Trả False khi không tính được TDEE."""
    result = upsert_today_daily_log(user_id=9999)
    assert result is False


# Test Case ID: TC-FR-00-001serProfileService_upsert_today_daily_log_003
# Test Objective: Cập nhật daily log đã tồn tại
# Input: user_id=1, đã có daily log hôm nay
# Expected Output: True, tdee được cập nhật
# Notes: CheckDB - log cũ được update, không tạo mới
def test_upsert_today_daily_log_update(app, db_session):
    """Cập nhật daily log khi đã tồn tại."""
    _create_profile_with_weight(db_session, user_id=1)

    # Tạo log với giá trị cũ
    old_log = DailyEnergyLog(
        user_id=1, log_date=date.today(),
        tdee=1000, target_calorie=1000,
        total_calorie_in=0, steps_calorie_out=0,
    )
    db_session.add(old_log)
    db_session.flush()

    result = upsert_today_daily_log(user_id=1)
    assert result is True

    # CheckDB: chỉ có 1 log (update, không tạo mới)
    logs = DailyEnergyLog.query.filter_by(
        user_id=1, log_date=date.today()
    ).all()
    assert len(logs) == 1


# ============================================================
# BUILD USER PROFILE RESPONSE
# ============================================================

# Test Case ID: TC-FR-00-001serProfileService_build_user_profile_response_001
# Test Objective: Build response với weight history
# Input: profile có weight history
# Expected Output: dict chứa height_cm, weight_kg, bmi
# Notes: Rollback tự động
def test_build_user_profile_response_with_weight(app, db_session):
    """Build response chứa đầy đủ thông tin weight."""
    profile, wh = _create_profile_with_weight(
        db_session, user_id=1, height_cm=170, weight_kg=70,
    )

    result = build_user_profile_response(profile)

    assert result["user_id"] == 1
    assert result["gender"] == "male"
    assert result["height_cm"] == 170.0
    assert result["weight_kg"] == 70.0
    assert result["bmi"] is not None


# Test Case ID: TC-FR-00-001serProfileService_build_user_profile_response_002
# Test Objective: Build response khi không có weight history
# Input: profile không có weight history
# Expected Output: height_cm=None, weight_kg=None, bmi=None
# Notes: Rollback tự động
def test_build_user_profile_response_no_weight(app, db_session):
    """Build response khi thiếu weight history."""
    profile = UserProfile(
        user_id=1, gender="male", date_of_birth=date(1995, 6, 15),
        activity_level=ActivityLevelEnum.moderately_active, aim_weight=70.0,
    )
    db_session.add(profile)
    db_session.flush()

    result = build_user_profile_response(profile)

    assert result["height_cm"] is None
    assert result["weight_kg"] is None
    assert result["bmi"] is None
