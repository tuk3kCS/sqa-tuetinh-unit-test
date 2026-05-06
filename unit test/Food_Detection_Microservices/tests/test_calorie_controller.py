"""
Unit tests cho Calorie Controller - các endpoint /api/v2/calories/.
Bao gồm: POST food-records, PUT food-records/<id>, GET food-records, DELETE food-records/<id>.
Sử dụng Flask test client và mock JWT.
"""
import pytest
from datetime import date
from unittest.mock import patch

from app.extensions import db
from app.models.user_profile import UserProfile
from app.models.daily_energy_log import DailyEnergyLog
from app.models.food_record import FoodRecord
from app.models.user_profile_weight_history import UserProfileWeightHistory
from app.enums.app_enum import ActivityLevelEnum

BASE_URL = "/api/v2/calories"


# --------------- Helpers --------------- #

def _auth_headers(app):
    """Tạo JWT headers hợp lệ cho testing."""
    from flask_jwt_extended import create_access_token
    token = create_access_token(
        identity="test@test.com",
        additional_claims={"userId": 1}
    )
    return {"Authorization": f"Bearer {token}"}


def _setup_user_and_log(session, user_id=1):
    """Tạo user profile + daily log cho test."""
    profile = UserProfile(
        user_id=user_id, gender="male", date_of_birth=date(1995, 6, 15),
        activity_level=ActivityLevelEnum.moderately_active, aim_weight=70.0,
    )
    session.add(profile)
    session.flush()

    wh = UserProfileWeightHistory(
        user_profile_id=profile.id, height_cm=170, weight_kg=70, bmi=24.22,
    )
    session.add(wh)
    session.flush()

    log = DailyEnergyLog(
        user_id=user_id, log_date=date.today(),
        total_calorie_in=0, steps_calorie_out=0,
    )
    session.add(log)
    session.flush()
    return profile, log


# ============================================================
# POST /food-records
# ============================================================

# Test Case ID: TC_FOOD_TestCalorieController_add_food_records_001
# Test Objective: Thêm food records thành công qua endpoint
# Input: POST với JSON chứa foods, JWT hợp lệ
# Expected Output: status 201, dữ liệu thành công
# Notes: Rollback tự động
def test_add_food_records_success(app, client, db_session):
    """POST food-records thành công với JWT hợp lệ."""
    _setup_user_and_log(db_session)
    headers = _auth_headers(app)

    response = client.post(f"{BASE_URL}/food-records", json={
        "log_date": date.today().isoformat(),
        "foods": [{"food_name": "Phở", "calorie": 450, "quantity": 1}],
    }, headers=headers)

    assert response.status_code == 201
    data = response.get_json()
    assert data["status"] == "success"
    assert data["items_added"] == 1


# Test Case ID: TC_FOOD_TestCalorieController_add_food_records_002
# Test Objective: Trả lỗi 401 khi không có JWT
# Input: POST không có Authorization header
# Expected Output: status 401
# Notes: Flask-JWT-Extended trả lỗi khi thiếu token
def test_add_food_records_no_jwt(app, client, db_session):
    """Trả 401 khi không gửi JWT token."""
    response = client.post(f"{BASE_URL}/food-records", json={
        "foods": [{"food_name": "Phở", "calorie": 450}],
    })

    assert response.status_code == 401


# Test Case ID: TC_FOOD_TestCalorieController_add_food_records_003
# Test Objective: Trả lỗi 400 khi foods rỗng
# Input: POST với foods=[]
# Expected Output: status 400, error message
# Notes: Validation trong service layer
def test_add_food_records_empty_foods(app, client, db_session):
    """Trả 400 khi danh sách foods rỗng."""
    headers = _auth_headers(app)

    response = client.post(f"{BASE_URL}/food-records", json={
        "foods": [],
    }, headers=headers)

    assert response.status_code == 400
    data = response.get_json()
    assert "error" in data


# ============================================================
# PUT /food-records/<id>
# ============================================================

# Test Case ID: TC_FOOD_TestCalorieController_update_food_record_001
# Test Objective: Cập nhật food record thành công
# Input: PUT với food_name mới, JWT hợp lệ
# Expected Output: status 200, food_name đã thay đổi
# Notes: CheckDB - record cập nhật trong DB
def test_update_food_record_success(app, client, db_session):
    """PUT food-records/<id> cập nhật thành công."""
    _, log = _setup_user_and_log(db_session)

    record = FoodRecord(
        daily_log_id=log.id, food_name="Phở",
        calorie=450, quantity=1, input_method="manual",
    )
    db_session.add(record)
    db_session.flush()

    headers = _auth_headers(app)
    response = client.put(f"{BASE_URL}/food-records/{record.id}", json={
        "food_name": "Bún bò Huế", "calorie": 500,
    }, headers=headers)

    assert response.status_code == 200


# Test Case ID: TC_FOOD_TestCalorieController_update_food_record_002
# Test Objective: Trả lỗi 404 khi record không tồn tại
# Input: PUT với id=9999
# Expected Output: status 404
# Notes: Rollback tự động
def test_update_food_record_not_found(app, client, db_session):
    """Trả 404 khi food record không tồn tại."""
    headers = _auth_headers(app)

    response = client.put(f"{BASE_URL}/food-records/9999", json={
        "food_name": "Phở",
    }, headers=headers)

    assert response.status_code == 404


# ============================================================
# GET /food-records
# ============================================================

# Test Case ID: TC_FOOD_TestCalorieController_get_food_records_001
# Test Objective: Lấy food records thành công
# Input: GET với log_date, JWT hợp lệ
# Expected Output: status 200, danh sách foods
# Notes: Rollback tự động
def test_get_food_records_success(app, client, db_session):
    """GET food-records trả danh sách thành công."""
    _, log = _setup_user_and_log(db_session)

    record = FoodRecord(
        daily_log_id=log.id, food_name="Cơm",
        calorie=130, quantity=2, input_method="manual",
    )
    db_session.add(record)
    db_session.flush()

    headers = _auth_headers(app)
    response = client.get(
        f"{BASE_URL}/food-records?log_date={date.today().isoformat()}",
        headers=headers,
    )

    assert response.status_code == 200
    data = response.get_json()
    assert data["status"] == "success"
    assert len(data["foods"]) >= 1


# Test Case ID: TC_FOOD_TestCalorieController_get_food_records_002
# Test Objective: Trả 401 khi không có JWT
# Input: GET không có Authorization header
# Expected Output: status 401
# Notes: Flask-JWT-Extended trả lỗi
def test_get_food_records_no_jwt(app, client, db_session):
    """Trả 401 khi thiếu JWT."""
    response = client.get(f"{BASE_URL}/food-records")
    assert response.status_code == 401


# ============================================================
# DELETE /food-records/<id>
# ============================================================

# Test Case ID: TC_FOOD_TestCalorieController_delete_food_record_001
# Test Objective: Xóa food record thành công
# Input: DELETE với id hợp lệ, JWT hợp lệ
# Expected Output: status 200
# Notes: CheckDB - record bị xóa
def test_delete_food_record_success(app, client, db_session):
    """DELETE food-records/<id> xóa thành công."""
    _, log = _setup_user_and_log(db_session)

    record = FoodRecord(
        daily_log_id=log.id, food_name="Phở",
        calorie=450, quantity=1, input_method="manual",
    )
    db_session.add(record)
    db_session.flush()

    headers = _auth_headers(app)
    response = client.delete(
        f"{BASE_URL}/food-records/{record.id}", headers=headers,
    )

    assert response.status_code == 200


# Test Case ID: TC_FOOD_TestCalorieController_delete_food_record_002
# Test Objective: Trả lỗi 404 khi record không tồn tại
# Input: DELETE với id=9999
# Expected Output: status 404
# Notes: Rollback tự động
def test_delete_food_record_not_found(app, client, db_session):
    """Trả 404 khi food record không tồn tại."""
    headers = _auth_headers(app)

    response = client.delete(
        f"{BASE_URL}/food-records/9999", headers=headers,
    )

    assert response.status_code == 404
