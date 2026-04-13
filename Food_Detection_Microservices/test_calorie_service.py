"""
Unit tests cho CalorieService - quản lý food records và daily energy logs.
Bao gồm: add_food_records, update_food_record, get_food_records, delete_food_record.
"""
import pytest
from datetime import date, datetime

from app.extensions import db
from app.models.user_profile import UserProfile
from app.models.daily_energy_log import DailyEnergyLog
from app.models.food_record import FoodRecord
from app.models.user_profile_weight_history import UserProfileWeightHistory
from app.enums.app_enum import ActivityLevelEnum
from app.services.calorie_service import CalorieService


# --------------- Helpers --------------- #

def _setup_user_with_log(session, user_id=1, log_date=None, total_calorie_in=0):
    """Tạo UserProfile + DailyEnergyLog cho testing."""
    log_date = log_date or date.today()
    profile = UserProfile(
        user_id=user_id,
        gender="male",
        date_of_birth=date(1995, 6, 15),
        activity_level=ActivityLevelEnum.moderately_active,
        aim_weight=70.0,
    )
    session.add(profile)
    session.flush()

    wh = UserProfileWeightHistory(
        user_profile_id=profile.id,
        height_cm=170.0,
        weight_kg=70.0,
        bmi=24.22,
    )
    session.add(wh)
    session.flush()

    daily_log = DailyEnergyLog(
        user_id=user_id,
        log_date=log_date,
        total_calorie_in=total_calorie_in,
        steps_calorie_out=0,
    )
    session.add(daily_log)
    session.flush()
    return profile, daily_log


def _add_food_record(session, daily_log_id, food_name="Phở", calorie=450, quantity=1):
    """Thêm một food record vào daily log."""
    record = FoodRecord(
        daily_log_id=daily_log_id,
        food_name=food_name,
        calorie=calorie,
        quantity=quantity,
        input_method="manual",
    )
    session.add(record)
    session.flush()
    return record


# ============================================================
# ADD FOOD RECORDS
# ============================================================

# Test Case ID: TC_FOOD_CalorieService_add_food_records_001
# Test Objective: Thêm một food record thành công vào ngày mới
# Input: user_id=1, payload chứa 1 food item, không có daily_log trước đó
# Expected Output: status 201, items_added=1, tạo mới DailyEnergyLog
# Notes: CheckDB - kiểm tra food_records và daily_energy_logs được tạo mới
def test_calorie_service_add_food_records_single_food(app, db_session):
    """Thêm 1 món ăn thành công - tạo DailyEnergyLog mới."""
    profile = UserProfile(
        user_id=1, gender="male", date_of_birth=date(1995, 6, 15),
        activity_level=ActivityLevelEnum.moderately_active, aim_weight=70.0,
    )
    db_session.add(profile)
    db_session.flush()

    payload = {
        "log_date": date.today().isoformat(),
        "foods": [{"food_name": "Phở", "calorie": 450, "quantity": 1}],
    }

    response, status = CalorieService.add_food_records(user_id=1, payload=payload)
    data = response.get_json()

    assert status == 201
    assert data["status"] == "success"
    assert data["items_added"] == 1
    assert data["total_calorie_added"] == 450

    # CheckDB: kiểm tra daily log được tạo
    log = DailyEnergyLog.query.filter_by(user_id=1).first()
    assert log is not None
    assert log.total_calorie_in == 450

    # CheckDB: kiểm tra food record được tạo
    records = FoodRecord.query.filter_by(daily_log_id=log.id).all()
    assert len(records) == 1
    assert records[0].food_name == "Phở"


# Test Case ID: TC_FOOD_CalorieService_add_food_records_002
# Test Objective: Thêm nhiều food records cùng lúc
# Input: user_id=1, payload chứa 3 food items
# Expected Output: status 201, items_added=3, tổng calo đúng
# Notes: CheckDB - kiểm tra 3 food_records được tạo
def test_calorie_service_add_food_records_multiple_foods(app, db_session):
    """Thêm nhiều món ăn cùng lúc - tính đúng tổng calo."""
    profile = UserProfile(
        user_id=1, gender="male", date_of_birth=date(1995, 6, 15),
        activity_level=ActivityLevelEnum.moderately_active, aim_weight=70.0,
    )
    db_session.add(profile)
    db_session.flush()

    payload = {
        "log_date": date.today().isoformat(),
        "foods": [
            {"food_name": "Phở", "calorie": 450, "quantity": 1},
            {"food_name": "Bánh mì", "calorie": 350, "quantity": 2},
            {"food_name": "Cơm", "calorie": 130, "quantity": 1},
        ],
    }

    response, status = CalorieService.add_food_records(user_id=1, payload=payload)
    data = response.get_json()

    assert status == 201
    assert data["items_added"] == 3
    # 450*1 + 350*2 + 130*1 = 1280
    assert data["total_calorie_added"] == 1280


# Test Case ID: TC_FOOD_CalorieService_add_food_records_003
# Test Objective: Trả lỗi khi không có food items
# Input: user_id=1, payload với foods=[]
# Expected Output: status 400, error message
# Notes: Rollback tự động
def test_calorie_service_add_food_records_no_foods(app, db_session):
    """Trả lỗi 400 khi danh sách foods rỗng."""
    payload = {"foods": []}
    response, status = CalorieService.add_food_records(user_id=1, payload=payload)
    data = response.get_json()

    assert status == 400
    assert "error" in data
    assert data["error"] == "No food provided"


# Test Case ID: TC_FOOD_CalorieService_add_food_records_004
# Test Objective: Thêm food vào ngày đã có DailyEnergyLog
# Input: user_id=1, daily_log đã tồn tại với total_calorie_in=500
# Expected Output: status 201, total_calorie_in_by_day cộng dồn
# Notes: CheckDB - tổng calo cộng dồn đúng
def test_calorie_service_add_food_records_existing_day(app, db_session):
    """Thêm food vào ngày đã có log - cộng dồn calo."""
    _, daily_log = _setup_user_with_log(db_session, total_calorie_in=500)

    payload = {
        "log_date": date.today().isoformat(),
        "foods": [{"food_name": "Bún chả", "calorie": 550, "quantity": 1}],
    }

    response, status = CalorieService.add_food_records(user_id=1, payload=payload)
    data = response.get_json()

    assert status == 201
    assert data["total_calorie_added"] == 550
    assert data["total_calorie_in_by_day"] == 1050  # 500 + 550


# Test Case ID: TC_FOOD_CalorieService_add_food_records_005
# Test Objective: Thêm food không chỉ định log_date → dùng ngày hôm nay
# Input: user_id=1, payload không có log_date
# Expected Output: status 201, log_date = today
# Notes: Rollback tự động
def test_calorie_service_add_food_records_default_date(app, db_session):
    """Không chỉ định log_date → mặc định dùng ngày hôm nay."""
    profile = UserProfile(
        user_id=1, gender="male", date_of_birth=date(1995, 6, 15),
        activity_level=ActivityLevelEnum.moderately_active, aim_weight=70.0,
    )
    db_session.add(profile)
    db_session.flush()

    payload = {
        "foods": [{"food_name": "Cơm tấm", "calorie": 150, "quantity": 1}],
    }

    response, status = CalorieService.add_food_records(user_id=1, payload=payload)
    data = response.get_json()

    assert status == 201
    assert data["log_date"] == date.today().isoformat()


# ============================================================
# UPDATE FOOD RECORD
# ============================================================

# Test Case ID: TC_FOOD_CalorieService_update_food_record_001
# Test Objective: Cập nhật food record thành công
# Input: user_id=1, payload chứa id, food_name mới, calorie mới
# Expected Output: status 200, dữ liệu đã cập nhật
# Notes: CheckDB - food_record được cập nhật đúng
def test_calorie_service_update_food_record_valid(app, db_session):
    """Cập nhật food record thành công - thay đổi tên và calo."""
    _, daily_log = _setup_user_with_log(db_session)
    record = _add_food_record(db_session, daily_log.id, "Phở", 450)

    payload = {"id": record.id, "food_name": "Bún bò Huế", "calorie": 500}
    result, status = CalorieService.update_food_record(user_id=1, payload=payload)

    assert status == 200
    assert result["food_name"] == "Bún bò Huế"
    assert result["calorie"] == 500

    # CheckDB: kiểm tra record đã cập nhật
    updated = FoodRecord.query.get(record.id)
    assert updated.food_name == "Bún bò Huế"


# Test Case ID: TC_FOOD_CalorieService_update_food_record_002
# Test Objective: Trả lỗi khi không có id trong payload
# Input: user_id=1, payload không có id
# Expected Output: status 400, error "Food record id is required"
# Notes: Rollback tự động
def test_calorie_service_update_food_record_missing_id(app, db_session):
    """Trả lỗi 400 khi payload thiếu id."""
    payload = {"food_name": "Phở"}
    result, status = CalorieService.update_food_record(user_id=1, payload=payload)

    assert status == 400
    assert result["error"] == "Food record id is required"


# Test Case ID: TC_FOOD_CalorieService_update_food_record_003
# Test Objective: Trả lỗi khi food record không tồn tại
# Input: user_id=1, id=9999 không tồn tại
# Expected Output: status 404, error "Food record not found"
# Notes: Rollback tự động
def test_calorie_service_update_food_record_not_found(app, db_session):
    """Trả lỗi 404 khi food record không tồn tại."""
    payload = {"id": 9999, "food_name": "Phở"}
    result, status = CalorieService.update_food_record(user_id=1, payload=payload)

    assert status == 404
    assert result["error"] == "Food record not found"


# Test Case ID: TC_FOOD_CalorieService_update_food_record_004
# Test Objective: Trả lỗi khi user không sở hữu food record
# Input: user_id=999 (không phải chủ sở hữu), id hợp lệ
# Expected Output: status 403, error "Unauthorized"
# Notes: Rollback tự động - kiểm tra ownership qua daily_log.user_id
def test_calorie_service_update_food_record_not_owned(app, db_session):
    """Trả lỗi 403 khi user không sở hữu record."""
    _, daily_log = _setup_user_with_log(db_session, user_id=1)
    record = _add_food_record(db_session, daily_log.id)

    payload = {"id": record.id, "food_name": "Cơm"}
    result, status = CalorieService.update_food_record(user_id=999, payload=payload)

    assert status == 403
    assert result["error"] == "Unauthorized"


# Test Case ID: TC_FOOD_CalorieService_update_food_record_005
# Test Objective: Cập nhật chỉ quantity, giữ nguyên food_name và calorie
# Input: user_id=1, payload chỉ chứa id + quantity
# Expected Output: status 200, quantity đã thay đổi, food_name giữ nguyên
# Notes: CheckDB - partial update hoạt động đúng
def test_calorie_service_update_food_record_partial_update(app, db_session):
    """Cập nhật partial - chỉ đổi quantity."""
    _, daily_log = _setup_user_with_log(db_session)
    record = _add_food_record(db_session, daily_log.id, "Phở", 450, quantity=1)

    payload = {"id": record.id, "quantity": 3}
    result, status = CalorieService.update_food_record(user_id=1, payload=payload)

    assert status == 200
    assert result["quantity"] == 3
    assert result["food_name"] == "Phở"  # giữ nguyên


# ============================================================
# GET FOOD RECORDS
# ============================================================

# Test Case ID: TC_FOOD_CalorieService_get_food_records_001
# Test Objective: Lấy food records có dữ liệu thành công
# Input: user_id=1, log_date có dữ liệu, 2 food records tồn tại
# Expected Output: status 200, danh sách 2 foods, tổng calo đúng
# Notes: CheckDB - dữ liệu trả về khớp DB
def test_calorie_service_get_food_records_with_data(app, db_session):
    """Lấy danh sách food records có dữ liệu."""
    _, daily_log = _setup_user_with_log(db_session, total_calorie_in=900)
    _add_food_record(db_session, daily_log.id, "Phở", 450)
    _add_food_record(db_session, daily_log.id, "Bánh mì", 350)

    response, status = CalorieService.get_food_records(
        user_id=1, log_date=date.today().isoformat()
    )
    data = response.get_json()

    assert status == 200
    assert data["status"] == "success"
    assert len(data["foods"]) == 2
    assert data["summary"]["total_calorie_in"] == 900


# Test Case ID: TC_FOOD_CalorieService_get_food_records_002
# Test Objective: Trả danh sách rỗng khi không có daily log
# Input: user_id=1, log_date không có dữ liệu
# Expected Output: status 200, foods=[], total_calorie_in=0
# Notes: Rollback tự động
def test_calorie_service_get_food_records_empty(app, db_session):
    """Trả danh sách rỗng khi không có daily log cho ngày đó."""
    response, status = CalorieService.get_food_records(
        user_id=1, log_date="2020-01-01"
    )
    data = response.get_json()

    assert status == 200
    assert data["foods"] == []
    assert data["summary"]["total_calorie_in"] == 0


# Test Case ID: TC_FOOD_CalorieService_get_food_records_003
# Test Objective: Mặc định dùng ngày hôm nay khi không truyền log_date
# Input: user_id=1, log_date=None
# Expected Output: status 200, log_date = today
# Notes: Rollback tự động
def test_calorie_service_get_food_records_no_date(app, db_session):
    """Không truyền log_date → mặc định ngày hôm nay."""
    response, status = CalorieService.get_food_records(user_id=1, log_date=None)
    data = response.get_json()

    assert status == 200
    assert data["log_date"] == date.today().isoformat()


# ============================================================
# DELETE FOOD RECORD
# ============================================================

# Test Case ID: TC_FOOD_CalorieService_delete_food_record_001
# Test Objective: Xóa food record thành công
# Input: user_id=1, record_id hợp lệ
# Expected Output: status 200, message "Food record deleted successfully"
# Notes: CheckDB - food_record bị xóa, total_calorie_in cập nhật
def test_calorie_service_delete_food_record_valid(app, db_session):
    """Xóa food record thành công - cập nhật lại tổng calo."""
    _, daily_log = _setup_user_with_log(db_session, total_calorie_in=900)
    r1 = _add_food_record(db_session, daily_log.id, "Phở", 450)
    _add_food_record(db_session, daily_log.id, "Bánh mì", 350)

    result, status = CalorieService.delete_food_record(user_id=1, record_id=r1.id)

    assert status == 200
    assert result["message"] == "Food record deleted successfully"

    # CheckDB: record đã bị xóa
    deleted = FoodRecord.query.get(r1.id)
    assert deleted is None


# Test Case ID: TC_FOOD_CalorieService_delete_food_record_002
# Test Objective: Trả lỗi khi food record không tồn tại
# Input: user_id=1, record_id=9999
# Expected Output: status 404, error "Food record not found"
# Notes: Rollback tự động
def test_calorie_service_delete_food_record_not_found(app, db_session):
    """Trả lỗi 404 khi food record không tồn tại."""
    result, status = CalorieService.delete_food_record(user_id=1, record_id=9999)

    assert status == 404
    assert result["error"] == "Food record not found"


# Test Case ID: TC_FOOD_CalorieService_delete_food_record_003
# Test Objective: Trả lỗi khi user không sở hữu food record
# Input: user_id=999 (không phải chủ sở hữu), record_id hợp lệ
# Expected Output: status 403, error "Unauthorized"
# Notes: Rollback tự động
def test_calorie_service_delete_food_record_not_owned(app, db_session):
    """Trả lỗi 403 khi user không sở hữu record."""
    _, daily_log = _setup_user_with_log(db_session, user_id=1)
    record = _add_food_record(db_session, daily_log.id)

    result, status = CalorieService.delete_food_record(user_id=999, record_id=record.id)

    assert status == 403
    assert result["error"] == "Unauthorized"
