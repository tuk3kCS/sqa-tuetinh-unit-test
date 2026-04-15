"""
Unit tests cho DailyLogService và các hàm hỗ trợ:
calculate_bmr_from_metrics, calculate_bmr, calculate_tdee,
create_daily_logs_for_all_users, get_daily_logs, update_daily_steps.
"""
import pytest
from datetime import date, datetime, timedelta
from unittest.mock import patch

from app.extensions import db
from app.models.user_profile import UserProfile
from app.models.daily_energy_log import DailyEnergyLog
from app.models.user_profile_weight_history import UserProfileWeightHistory
from app.enums.app_enum import ActivityLevelEnum
from app.services.daily_log_service import (
    calculate_bmr_from_metrics,
    calculate_bmr,
    calculate_tdee,
    create_daily_logs_for_all_users,
    get_latest_user_metrics,
    DailyLogService,
)


# --------------- Helpers --------------- #

def _create_full_profile(session, user_id=1, gender="male",
                         activity_level=ActivityLevelEnum.moderately_active,
                         aim_weight=70.0, height_cm=170.0, weight_kg=70.0,
                         dob=None, aim_day=None, aim_day_end=None,
                         day_of_activities=5):
    """Tạo UserProfile + WeightHistory đầy đủ cho testing."""
    dob = dob or date(1995, 6, 15)
    profile = UserProfile(
        user_id=user_id,
        gender=gender,
        date_of_birth=dob,
        activity_level=activity_level,
        aim_weight=aim_weight,
        day_of_activities=day_of_activities,
        aim_day=aim_day,
        aim_day_end=aim_day_end,
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
# CALCULATE BMR FROM METRICS
# ============================================================

# Test Case ID: TC_FOOD_DailyLogService_calculate_bmr_from_metrics_001
# Test Objective: Tính BMR cho nam giới theo Mifflin-St Jeor
# Input: height=170, weight=70, gender="male", dob=1995-06-15
# Expected Output: BMR > 0, công thức đúng cho nam
# Notes: Công thức: 10*w + 6.25*h - 5*age + 5
def test_calculate_bmr_from_metrics_male(app):
    """Tính BMR cho nam giới - công thức Mifflin-St Jeor."""
    today = date.today()
    dob = date(1995, 6, 15)
    age = today.year - dob.year - ((today.month, today.day) < (dob.month, dob.day))
    expected = int(10 * 70 + 6.25 * 170 - 5 * age + 5)

    result = calculate_bmr_from_metrics(170, 70, "male", dob)
    assert result == expected
    assert result > 0


# Test Case ID: TC_FOOD_DailyLogService_calculate_bmr_from_metrics_002
# Test Objective: Tính BMR cho nữ giới
# Input: height=160, weight=55, gender="female", dob=1998-03-20
# Expected Output: BMR > 0, công thức đúng cho nữ
# Notes: Công thức: 10*w + 6.25*h - 5*age - 161
def test_calculate_bmr_from_metrics_female(app):
    """Tính BMR cho nữ giới - công thức Mifflin-St Jeor."""
    today = date.today()
    dob = date(1998, 3, 20)
    age = today.year - dob.year - ((today.month, today.day) < (dob.month, dob.day))
    expected = int(10 * 55 + 6.25 * 160 - 5 * age - 161)

    result = calculate_bmr_from_metrics(160, 55, "female", dob)
    assert result == expected
    assert result > 0


# Test Case ID: TC_FOOD_DailyLogService_calculate_bmr_from_metrics_003
# Test Objective: Trả 0 khi thiếu thông số
# Input: height=None, weight=None, gender=None, dob=None
# Expected Output: BMR = 0
# Notes: Tất cả tham số đều thiếu
def test_calculate_bmr_from_metrics_missing_params(app):
    """Trả 0 khi thiếu bất kỳ thông số nào."""
    assert calculate_bmr_from_metrics(None, 70, "male", date(1995, 6, 15)) == 0
    assert calculate_bmr_from_metrics(170, None, "male", date(1995, 6, 15)) == 0
    assert calculate_bmr_from_metrics(170, 70, None, date(1995, 6, 15)) == 0
    assert calculate_bmr_from_metrics(170, 70, "male", None) == 0


# Test Case ID: TC_FOOD_DailyLogService_calculate_bmr_from_metrics_004
# Test Objective: Tính BMR cho người trẻ tuổi (18 tuổi)
# Input: height=175, weight=65, gender="male", dob gần 18 năm trước
# Expected Output: BMR > BMR của người lớn tuổi hơn
# Notes: Người trẻ có BMR cao hơn do hệ số -5*age nhỏ hơn
def test_calculate_bmr_from_metrics_young_person(app):
    """BMR cho người trẻ cao hơn người lớn tuổi cùng thông số."""
    today = date.today()
    young_dob = date(today.year - 18, 1, 1)
    old_dob = date(today.year - 50, 1, 1)

    bmr_young = calculate_bmr_from_metrics(175, 65, "male", young_dob)
    bmr_old = calculate_bmr_from_metrics(175, 65, "male", old_dob)

    assert bmr_young > bmr_old


# ============================================================
# CALCULATE BMR (wrapper)
# ============================================================

# Test Case ID: TC_FOOD_DailyLogService_calculate_bmr_001
# Test Objective: Tính BMR cho user có profile và weight history
# Input: user_id=1 có đầy đủ profile và weight history
# Expected Output: BMR > 0
# Notes: CheckDB - đọc từ UserProfile và WeightHistory
def test_calculate_bmr_user_exists(app, db_session):
    """Tính BMR cho user có đầy đủ thông tin."""
    _create_full_profile(db_session, user_id=1, height_cm=170, weight_kg=70)
    bmr = calculate_bmr(1)
    assert bmr > 0


# Test Case ID: TC_FOOD_DailyLogService_calculate_bmr_002
# Test Objective: Trả 0 khi user không tồn tại
# Input: user_id=9999 không có profile
# Expected Output: BMR = 0
# Notes: Rollback tự động
def test_calculate_bmr_user_not_exists(app, db_session):
    """Trả 0 khi user không có profile."""
    bmr = calculate_bmr(9999)
    assert bmr == 0


# ============================================================
# CALCULATE TDEE
# ============================================================

# Test Case ID: TC_FOOD_DailyLogService_calculate_tdee_001
# Test Objective: Tính TDEE cho user moderately_active không có mục tiêu cân nặng
# Input: user_id=1, activity_level=moderately_active
# Expected Output: tuple (bmr, tdee, target_calorie), tdee = bmr * 1.55
# Notes: Không có aim_day/aim_day_end nên target_calorie = tdee
def test_calculate_tdee_moderate_activity(app, db_session):
    """Tính TDEE cho mức hoạt động moderately_active."""
    _create_full_profile(
        db_session, user_id=1,
        activity_level=ActivityLevelEnum.moderately_active,
        height_cm=170, weight_kg=70,
    )
    result = calculate_tdee(1)
    assert result is not None

    bmr, tdee, target = result
    assert bmr > 0
    assert tdee == int(bmr * 1.55)
    assert target == tdee  # không có goal weight


# Test Case ID: TC_FOOD_DailyLogService_calculate_tdee_002
# Test Objective: Tính TDEE cho user sedentary
# Input: user_id=1, activity_level=sedentary
# Expected Output: tdee = bmr * 1.2
# Notes: Sedentary có hệ số thấp nhất
def test_calculate_tdee_sedentary(app, db_session):
    """Tính TDEE cho mức hoạt động sedentary - hệ số 1.2."""
    _create_full_profile(
        db_session, user_id=1,
        activity_level=ActivityLevelEnum.sedentary,
        height_cm=170, weight_kg=70,
    )
    result = calculate_tdee(1)
    bmr, tdee, target = result
    assert tdee == int(bmr * 1.2)


# Test Case ID: TC_FOOD_DailyLogService_calculate_tdee_003
# Test Objective: Trả None khi user không có profile
# Input: user_id=9999
# Expected Output: None
# Notes: Rollback tự động
def test_calculate_tdee_no_profile(app, db_session):
    """Trả None khi không có profile."""
    result = calculate_tdee(9999)
    assert result is None


# Test Case ID: TC_FOOD_DailyLogService_calculate_tdee_004
# Test Objective: Trả None khi thiếu chiều cao/cân nặng
# Input: user_id=1 có profile nhưng không có weight history
# Expected Output: None
# Notes: Profile tồn tại nhưng thiếu metrics
def test_calculate_tdee_no_metrics(app, db_session):
    """Trả None khi thiếu height/weight metrics."""
    profile = UserProfile(
        user_id=1, gender="male", date_of_birth=date(1995, 6, 15),
        activity_level=ActivityLevelEnum.moderately_active, aim_weight=70.0,
    )
    db_session.add(profile)
    db_session.flush()

    result = calculate_tdee(1)
    assert result is None


# Test Case ID: TC_FOOD_DailyLogService_calculate_tdee_005
# Test Objective: Tính target_calorie khi có mục tiêu giảm cân
# Input: aim_weight < weight_kg, aim_day/aim_day_end hợp lệ
# Expected Output: target_calorie < tdee (do cần thâm hụt calo)
# Notes: Kiểm tra giới hạn MAX_DEFICIT = -1000
def test_calculate_tdee_with_goal_lose_weight(app, db_session):
    """Target calorie giảm khi mục tiêu giảm cân."""
    today = date.today()
    _create_full_profile(
        db_session, user_id=1,
        activity_level=ActivityLevelEnum.moderately_active,
        height_cm=170, weight_kg=80, aim_weight=70,
        aim_day=datetime(today.year, today.month, today.day),
        aim_day_end=datetime(today.year, today.month, today.day) + timedelta(days=90),
        day_of_activities=5,
    )
    result = calculate_tdee(1)
    bmr, tdee, target = result
    assert target < tdee  # giảm cân → target thấp hơn tdee


# Test Case ID: TC_FOOD_DailyLogService_calculate_tdee_006
# Test Objective: Target calorie không thấp hơn giới hạn tối thiểu (nữ: 1200)
# Input: nữ, aim_weight thấp hơn nhiều → MAX_DEFICIT
# Expected Output: target_calorie >= 1200
# Notes: Đảm bảo an toàn sức khỏe - không giảm quá thấp
def test_calculate_tdee_female_min_calorie(app, db_session):
    """Nữ giới không được dưới 1200 calo mục tiêu."""
    today = date.today()
    _create_full_profile(
        db_session, user_id=1, gender="female",
        activity_level=ActivityLevelEnum.sedentary,
        height_cm=155, weight_kg=80, aim_weight=50,
        dob=date(1995, 1, 1),
        aim_day=datetime(today.year, today.month, today.day),
        aim_day_end=datetime(today.year, today.month, today.day) + timedelta(days=30),
        day_of_activities=3,
    )
    result = calculate_tdee(1)
    _, _, target = result
    assert target >= 1200


# ============================================================
# CREATE DAILY LOGS FOR ALL USERS
# ============================================================

# Test Case ID: TC_FOOD_DailyLogService_create_daily_logs_for_all_users_001
# Test Objective: Tạo daily log cho tất cả users có profile hợp lệ
# Input: 1 user có đầy đủ profile + weight
# Expected Output: created_logs=1, skipped_users=0
# Notes: CheckDB - DailyEnergyLog mới được tạo
def test_create_daily_logs_success(app, db_session):
    """Tạo daily log cho user có đầy đủ thông tin."""
    _create_full_profile(db_session, user_id=1, height_cm=170, weight_kg=70)

    target_date = date.today() + timedelta(days=1)
    result = create_daily_logs_for_all_users(target_date)

    assert result["created_logs"] == 1
    assert result["skipped_users"] == 0
    assert result["total_users"] == 1

    # CheckDB: log đã được tạo
    log = DailyEnergyLog.query.filter_by(user_id=1, log_date=target_date).first()
    assert log is not None
    assert log.tdee > 0


# Test Case ID: TC_FOOD_DailyLogService_create_daily_logs_for_all_users_002
# Test Objective: Không tạo log khi không có users
# Input: không có UserProfile nào
# Expected Output: created_logs=0, total_users=0
# Notes: Rollback tự động
def test_create_daily_logs_no_users(app, db_session):
    """Không tạo log khi không có user nào."""
    target_date = date.today() + timedelta(days=2)
    result = create_daily_logs_for_all_users(target_date)

    assert result["created_logs"] == 0
    assert result["total_users"] == 0


# Test Case ID: TC_FOOD_DailyLogService_create_daily_logs_for_all_users_003
# Test Objective: Bỏ qua user đã có log cho ngày đó
# Input: user_id=1 đã có DailyEnergyLog cho target_date
# Expected Output: created_logs=0 (bỏ qua vì đã tồn tại)
# Notes: CheckDB - không tạo log trùng lặp
def test_create_daily_logs_existing_log(app, db_session):
    """Bỏ qua nếu user đã có log cho ngày đó."""
    _create_full_profile(db_session, user_id=1, height_cm=170, weight_kg=70)

    target_date = date.today() + timedelta(days=3)
    existing = DailyEnergyLog(
        user_id=1, log_date=target_date, total_calorie_in=0, steps_calorie_out=0,
    )
    db_session.add(existing)
    db_session.flush()

    result = create_daily_logs_for_all_users(target_date)
    assert result["created_logs"] == 0


# Test Case ID: TC_FOOD_DailyLogService_create_daily_logs_for_all_users_004
# Test Objective: Skip user không có weight history (không tính được TDEE)
# Input: user có profile nhưng không có weight history
# Expected Output: skipped_users=1
# Notes: Rollback tự động
def test_create_daily_logs_skip_user_no_weight(app, db_session):
    """Skip user không có weight history."""
    profile = UserProfile(
        user_id=1, gender="male", date_of_birth=date(1995, 6, 15),
        activity_level=ActivityLevelEnum.moderately_active, aim_weight=70.0,
    )
    db_session.add(profile)
    db_session.flush()

    target_date = date.today() + timedelta(days=4)
    result = create_daily_logs_for_all_users(target_date)

    assert result["skipped_users"] == 1
    assert result["created_logs"] == 0


# ============================================================
# GET DAILY LOGS
# ============================================================

# Test Case ID: TC_FOOD_DailyLogService_get_daily_logs_001
# Test Objective: Lấy daily logs trong khoảng ngày có dữ liệu
# Input: user_id=1, start_date và end_date bao quanh log_date
# Expected Output: danh sách 1 log, không có error
# Notes: CheckDB - dữ liệu trả về khớp DB
def test_daily_log_service_get_daily_logs_with_data(app, db_session):
    """Lấy daily logs trong khoảng ngày có dữ liệu."""
    profile = UserProfile(
        user_id=1, gender="male", date_of_birth=date(1995, 6, 15),
        activity_level=ActivityLevelEnum.moderately_active, aim_weight=70.0,
    )
    db_session.add(profile)
    db_session.flush()

    log_date = date(2025, 6, 1)
    log = DailyEnergyLog(
        user_id=1, log_date=log_date,
        total_calorie_in=1500, total_steps=8000,
        steps_calorie_out=200, tdee=2200, target_calorie=2000,
    )
    db_session.add(log)
    db_session.flush()

    logs, error = DailyLogService.get_daily_logs(
        user_id=1, start_date="2025-06-01", end_date="2025-06-30"
    )

    assert error is None
    assert len(logs) == 1
    assert logs[0]["total_calorie_in"] == 1500
    assert logs[0]["total_steps"] == 8000


# Test Case ID: TC_FOOD_DailyLogService_get_daily_logs_002
# Test Objective: Trả danh sách rỗng khi không có logs
# Input: user_id=1, khoảng ngày không có dữ liệu
# Expected Output: danh sách rỗng, không có error
# Notes: Rollback tự động
def test_daily_log_service_get_daily_logs_empty(app, db_session):
    """Trả danh sách rỗng khi không có logs trong khoảng ngày."""
    logs, error = DailyLogService.get_daily_logs(
        user_id=1, start_date="2020-01-01", end_date="2020-01-31"
    )

    assert error is None
    assert logs == []


# Test Case ID: TC_FOOD_DailyLogService_get_daily_logs_003
# Test Objective: Trả lỗi khi định dạng ngày không hợp lệ
# Input: start_date="invalid-date"
# Expected Output: None, error chứa thông báo lỗi
# Notes: Kiểm tra xử lý ValueError
def test_daily_log_service_get_daily_logs_invalid_date(app, db_session):
    """Trả lỗi khi định dạng ngày không hợp lệ."""
    logs, error = DailyLogService.get_daily_logs(
        user_id=1, start_date="invalid-date"
    )

    assert error is not None
    assert "Invalid date format" in error


# Test Case ID: TC_FOOD_DailyLogService_get_daily_logs_004
# Test Objective: Lấy logs khi không chỉ định start_date/end_date
# Input: user_id=1, start_date=None, end_date=None
# Expected Output: Tất cả logs của user
# Notes: Không filter theo ngày
def test_daily_log_service_get_daily_logs_no_date_filter(app, db_session):
    """Lấy tất cả logs khi không filter ngày."""
    profile = UserProfile(
        user_id=1, gender="male", date_of_birth=date(1995, 6, 15),
        activity_level=ActivityLevelEnum.moderately_active, aim_weight=70.0,
    )
    db_session.add(profile)
    db_session.flush()

    for i in range(3):
        log = DailyEnergyLog(
            user_id=1, log_date=date(2025, 6, i + 1),
            total_calorie_in=0, steps_calorie_out=0,
        )
        db_session.add(log)
    db_session.flush()

    logs, error = DailyLogService.get_daily_logs(user_id=1)
    assert error is None
    assert len(logs) == 3


# ============================================================
# UPDATE DAILY STEPS
# ============================================================

# Test Case ID: TC_FOOD_DailyLogService_update_daily_steps_001
# Test Objective: Cập nhật số bước chân thành công
# Input: user_id=1, steps=10000, có profile + weight + daily log
# Expected Output: result chứa total_steps=10000, step_calorie > 0
# Notes: CheckDB - log.total_steps và log.steps_calorie_out cập nhật
def test_daily_log_service_update_daily_steps_success(app, db_session):
    """Cập nhật số bước chân thành công."""
    _create_full_profile(db_session, user_id=1, weight_kg=70)

    log = DailyEnergyLog(
        user_id=1, log_date=date.today(),
        total_calorie_in=0, steps_calorie_out=0,
    )
    db_session.add(log)
    db_session.flush()

    result, error = DailyLogService.update_daily_steps(
        user_id=1, steps=10000, log_date=date.today().strftime("%Y-%m-%d")
    )

    assert error is None
    assert result["total_steps"] == 10000
    # step_calorie = 10000 * 0.0005 * 70 = 350
    assert result["step_calorie"] == 350


# Test Case ID: TC_FOOD_DailyLogService_update_daily_steps_002
# Test Objective: Trả lỗi khi daily log không tồn tại
# Input: user_id=1 có profile nhưng không có daily log
# Expected Output: error "Daily log not found"
# Notes: Rollback tự động
def test_daily_log_service_update_daily_steps_log_not_found(app, db_session):
    """Trả lỗi khi không có daily log cho ngày đó."""
    _create_full_profile(db_session, user_id=1, weight_kg=70)

    result, error = DailyLogService.update_daily_steps(
        user_id=1, steps=5000, log_date="2020-01-01"
    )

    assert result is None
    assert error == "Daily log not found"


# Test Case ID: TC_FOOD_DailyLogService_update_daily_steps_003
# Test Objective: Trả lỗi khi user profile không tồn tại
# Input: user_id=9999 không có profile
# Expected Output: error "User profile not found"
# Notes: Rollback tự động
def test_daily_log_service_update_daily_steps_no_profile(app, db_session):
    """Trả lỗi khi không có user profile."""
    result, error = DailyLogService.update_daily_steps(user_id=9999, steps=5000)
    assert result is None
    assert error == "User profile not found"


# Test Case ID: TC_FOOD_DailyLogService_update_daily_steps_004
# Test Objective: Trả lỗi khi user không có weight history
# Input: user_id=1 có profile nhưng không có weight history
# Expected Output: error "User weight not found"
# Notes: Rollback tự động
def test_daily_log_service_update_daily_steps_no_weight(app, db_session):
    """Trả lỗi khi không có weight history."""
    profile = UserProfile(
        user_id=1, gender="male", date_of_birth=date(1995, 6, 15),
        activity_level=ActivityLevelEnum.moderately_active, aim_weight=70.0,
    )
    db_session.add(profile)
    db_session.flush()

    result, error = DailyLogService.update_daily_steps(user_id=1, steps=5000)
    assert result is None
    assert error == "User weight not found"
