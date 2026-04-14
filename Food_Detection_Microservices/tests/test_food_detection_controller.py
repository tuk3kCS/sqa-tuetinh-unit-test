"""
Unit tests cho Food Detection Controller - endpoint /api/v2/detect.
Bao gồm: GET health check, POST detect (valid image, no image, no detections, error).
Mock FoodDetectionService và NutritionService.
"""
import io
import pytest
from unittest.mock import patch, MagicMock
from PIL import Image

BASE_URL = "/api/v2/detect"


# --------------- Helpers --------------- #

def _auth_headers(app):
    """Tạo JWT headers hợp lệ cho testing."""
    from flask_jwt_extended import create_access_token
    token = create_access_token(
        identity="test@test.com",
        additional_claims={"userId": 1}
    )
    return {"Authorization": f"Bearer {token}"}


def _create_image_file():
    """Tạo file ảnh giả cho upload."""
    img = Image.new("RGB", (100, 100), color=(255, 0, 0))
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    buf.seek(0)
    return buf


# ============================================================
# GET /detect - Health Check
# ============================================================

# Test Case ID: TC-FR-00-001oodDetectionController_detect_001
# Test Objective: Health check endpoint trả "ready"
# Input: GET /api/v2/detect với JWT
# Expected Output: status 200, {"status": "ready"}
# Notes: Không cần image, chỉ kiểm tra service sẵn sàng
def test_detect_health_check(app, client, db_session):
    """GET /detect trả health check thành công."""
    headers = _auth_headers(app)
    response = client.get(BASE_URL, headers=headers)

    assert response.status_code == 200
    data = response.get_json()
    assert data["status"] == "ready"


# ============================================================
# POST /detect - Valid Image
# ============================================================

# Test Case ID: TC-FR-00-001oodDetectionController_detect_002
# Test Objective: Phát hiện thực phẩm thành công với ảnh hợp lệ
# Input: POST với file ảnh JPEG, FoodDetectionService trả detections
# Expected Output: status 200, detection list, nutrition_analysis
# Notes: Mock FoodDetectionService, NutritionService, upload
@patch("app.controllers.food_detection_controller.NutritionService")
@patch("app.controllers.food_detection_controller.FoodDetectionService")
def test_detect_valid_image_with_detections(mock_fds_cls, mock_ns_cls, app, client, db_session):
    """POST /detect phát hiện thành công."""
    mock_service = MagicMock()
    mock_fds_cls.return_value = mock_service

    # detect_foods trả về 1 detection
    mock_service.detect_foods.return_value = [
        {"class": "Phở", "confidence": 0.9, "bbox": [10, 10, 200, 200]}
    ]
    # post_process trả về detections đã lọc
    mock_service.post_process.return_value = [
        {"class": "Phở", "confidence": 0.9, "bbox": [10, 10, 200, 200]}
    ]
    mock_service.upload_annotated_image.return_value = "https://cloudinary.com/annotated.jpg"

    mock_ns_cls.analyze.return_value = {
        "individual_items": [{"name": "Phở", "nutrition": {"Calories": 450}}],
        "total_nutrition": {"Calories": 450, "Fat": 15, "Carbs": 55, "Protein": 20},
        "items_count": 1,
    }

    headers = _auth_headers(app)
    image_file = _create_image_file()

    response = client.post(
        BASE_URL,
        data={"image": (image_file, "test.jpg")},
        content_type="multipart/form-data",
        headers=headers,
    )

    assert response.status_code == 200
    data = response.get_json()
    assert data["status"] == "success"
    assert len(data["detection"]) == 1
    assert data["detection"][0]["detected_class"] == "Phở"
    assert data["nutrition_analysis"]["items_count"] == 1


# Test Case ID: TC-FR-00-001oodDetectionController_detect_003
# Test Objective: Trả lỗi 400 khi không upload ảnh
# Input: POST không có file image
# Expected Output: status 400, error "No image uploaded"
# Notes: Validation ở controller layer
def test_detect_no_image(app, client, db_session):
    """Trả 400 khi không gửi file ảnh."""
    headers = _auth_headers(app)

    response = client.post(
        BASE_URL,
        data={},
        content_type="multipart/form-data",
        headers=headers,
    )

    assert response.status_code == 400
    data = response.get_json()
    assert data["error"] == "No image uploaded"


# Test Case ID: TC-FR-00-001oodDetectionController_detect_004
# Test Objective: Trả kết quả rỗng khi không phát hiện thực phẩm
# Input: POST ảnh hợp lệ, FoodDetectionService trả rỗng
# Expected Output: status 200, detection=[], total_nutrition Calories=0
# Notes: Mock service trả danh sách rỗng
@patch("app.controllers.food_detection_controller.FoodDetectionService")
def test_detect_no_detections(mock_fds_cls, app, client, db_session):
    """Trả kết quả rỗng khi không phát hiện thực phẩm."""
    mock_service = MagicMock()
    mock_fds_cls.return_value = mock_service
    mock_service.detect_foods.return_value = []
    mock_service.post_process.return_value = []
    mock_service.upload_original_image.return_value = "https://cloudinary.com/original.jpg"

    headers = _auth_headers(app)
    image_file = _create_image_file()

    response = client.post(
        BASE_URL,
        data={"image": (image_file, "test.jpg")},
        content_type="multipart/form-data",
        headers=headers,
    )

    assert response.status_code == 200
    data = response.get_json()
    assert data["detection"] == []
    assert data["nutrition_analysis"]["items_count"] == 0


# Test Case ID: TC-FR-00-001oodDetectionController_detect_005
# Test Objective: Trả 401 khi không có JWT
# Input: POST không có Authorization header
# Expected Output: status 401
# Notes: JWT required
def test_detect_no_jwt(app, client, db_session):
    """Trả 401 khi thiếu JWT token."""
    image_file = _create_image_file()

    response = client.post(
        BASE_URL,
        data={"image": (image_file, "test.jpg")},
        content_type="multipart/form-data",
    )

    assert response.status_code == 401


# Test Case ID: TC-FR-00-001oodDetectionController_detect_006
# Test Objective: Trả 502 khi detection service bị lỗi
# Input: POST ảnh hợp lệ, detect_foods raise Exception
# Expected Output: status 502, error message
# Notes: Xử lý lỗi từ AI service
@patch("app.controllers.food_detection_controller.FoodDetectionService")
def test_detect_service_error(mock_fds_cls, app, client, db_session):
    """Trả 502 khi detection service gặp lỗi."""
    mock_service = MagicMock()
    mock_fds_cls.return_value = mock_service
    mock_service.detect_foods.side_effect = Exception("ONNX runtime error")

    headers = _auth_headers(app)
    image_file = _create_image_file()

    response = client.post(
        BASE_URL,
        data={"image": (image_file, "test.jpg")},
        content_type="multipart/form-data",
        headers=headers,
    )

    assert response.status_code == 502
    data = response.get_json()
    assert "ONNX runtime error" in data["error"]
