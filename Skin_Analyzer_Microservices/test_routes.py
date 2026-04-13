"""
Unit tests cho app-level routes (routes.py) – POST /api/v1/analyze và GET /api/v1/health.

Mock các service AI (detection + classification) và Cloudinary upload.
"""

import pytest
import io
from unittest.mock import patch, MagicMock
from PIL import Image


# ============================================================
# Helpers
# ============================================================

def _create_test_image_bytes():
    """Tạo JPEG bytes để gửi qua multipart form."""
    img = Image.new("RGB", (100, 100), color=(200, 150, 100))
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    buf.seek(0)
    return buf


# ============================================================
# GET /api/v1/health
# ============================================================

# Test Case ID: TC_SKIN_Routes_health_001
# Test Objective: Health endpoint trả 200 + {"status": "ok"}
# Input: GET /api/v1/health
# Expected Output: HTTP 200, JSON {"status": "ok"}
# Notes: Không cần auth
def test_health_endpoint(client):
    """Health check trả 200."""
    resp = client.get("/api/v1/health")
    assert resp.status_code == 200
    assert resp.get_json() == {"status": "ok"}


# Test Case ID: TC_SKIN_Routes_health_002
# Test Objective: Root endpoint trả 200 "OK"
# Input: GET /
# Expected Output: HTTP 200, body "OK"
# Notes: Không cần auth
def test_root_endpoint(client):
    """Root '/' trả 200 OK."""
    resp = client.get("/")
    assert resp.status_code == 200
    assert b"OK" in resp.data


# ============================================================
# POST /api/v1/analyze – auth checks
# ============================================================

# Test Case ID: TC_SKIN_Routes_analyze_001
# Test Objective: Trả 401 khi không có JWT
# Input: POST /api/v1/analyze không header Authorization
# Expected Output: HTTP 401 hoặc 422
# Notes: JWT middleware
def test_analyze_missing_jwt(client):
    """Không JWT → 401."""
    resp = client.post("/api/v1/analyze")
    assert resp.status_code in (401, 422)


# Test Case ID: TC_SKIN_Routes_analyze_002
# Test Objective: Trả 400 khi không upload ảnh
# Input: POST /api/v1/analyze với JWT nhưng không có file
# Expected Output: HTTP 400, "No image uploaded"
# Notes: Kiểm tra validate file input
def test_analyze_no_image(client, mock_jwt_identity):
    """Không upload ảnh → 400."""
    resp = client.post("/api/v1/analyze")
    assert resp.status_code == 400
    assert "No image uploaded" in resp.get_json().get("error", "")


# ============================================================
# POST /api/v1/analyze – no detections (da bình thường)
# ============================================================

# Test Case ID: TC_SKIN_Routes_analyze_003
# Test Objective: Trả kết quả trống khi detection không phát hiện gì
# Input: Ảnh hợp lệ, detection model trả []
# Expected Output: HTTP 200, detection=[], total_detections=0
# Notes: Mock SkinDetectionService.detect trả [], mock upload Cloudinary
@patch("app.routes.routes.upload_base64_to_cloudinary", return_value="http://cloud.test/img.jpg")
@patch("app.routes.routes.image_to_base64", return_value="base64data")
@patch("app.routes.routes.SkinDetectionService")
def test_analyze_no_detections(mock_det_cls, mock_b64, mock_upload, client, mock_jwt_identity):
    """Không phát hiện → trả detection=[], suggestions mặc định."""
    mock_det_instance = MagicMock()
    mock_det_instance.detect.return_value = []
    mock_det_cls.return_value = mock_det_instance

    img_bytes = _create_test_image_bytes()
    resp = client.post(
        "/api/v1/analyze",
        data={"image": (img_bytes, "test.jpg")},
        content_type="multipart/form-data",
    )

    assert resp.status_code == 200
    data = resp.get_json()
    assert data["status"] == "success"
    assert data["detection"] == []
    assert data["metadata"]["total_detections"] == 0


# ============================================================
# POST /api/v1/analyze – có detections (phát hiện vấn đề)
# ============================================================

# Test Case ID: TC_SKIN_Routes_analyze_004
# Test Objective: Trả kết quả phân tích đầy đủ khi phát hiện vấn đề da
# Input: Ảnh hợp lệ, detection trả 1 kết quả không cần classification
# Expected Output: HTTP 200, detection có 1 phần tử, annotated_image_url có giá trị
# Notes: Mock detection + crop + upload; class không cần classification
@patch("app.routes.routes.generate_lifestyle_suggestions", return_value={"lifestyle": [], "diet": []})
@patch("app.routes.routes.generate_health_issue_info", return_value="Phát hiện mụn đầu đen")
@patch("app.routes.routes.upload_base64_to_cloudinary", return_value="http://cloud.test/ann.jpg")
@patch("app.routes.routes.image_to_base64", return_value="base64data")
@patch("app.routes.routes.draw_boxes", return_value=Image.new("RGB", (100, 100)))
@patch("app.routes.routes.crop_regions")
@patch("app.routes.routes.SkinDetectionService")
def test_analyze_with_detections_no_classification(
    mock_det_cls, mock_crop, mock_draw, mock_b64,
    mock_upload, mock_health, mock_lifestyle,
    client, mock_jwt_identity,
):
    """Detection trả kết quả, class không cần classification → response đầy đủ."""
    mock_det_instance = MagicMock()
    mock_det_instance.detect.return_value = [
        {"class": "Blackhead", "confidence": 0.85, "bbox": [10, 10, 50, 50]},
    ]
    mock_det_cls.return_value = mock_det_instance
    mock_crop.return_value = [Image.new("RGB", (40, 40))]

    img_bytes = _create_test_image_bytes()
    resp = client.post(
        "/api/v1/analyze",
        data={"image": (img_bytes, "test.jpg")},
        content_type="multipart/form-data",
    )

    assert resp.status_code == 200
    data = resp.get_json()
    assert data["status"] == "success"
    assert len(data["detection"]) == 1
    assert data["detection"][0]["detected_class"] == "Blackhead"
    assert data["detection"][0]["requires_classification"] is False


# Test Case ID: TC_SKIN_Routes_analyze_005
# Test Objective: Trả kết quả với classification khi class cần phân loại thêm
# Input: Detection trả class "acne scar" (trong CLASSES_REQUIRING_CLASSIFICATION)
# Expected Output: detection[0].requires_classification=True, disease_prediction có giá trị
# Notes: Mock cả SkinClassificationService
@patch("app.routes.routes.generate_lifestyle_suggestions", return_value={"lifestyle": [], "diet": []})
@patch("app.routes.routes.generate_health_issue_info", return_value="info")
@patch("app.routes.routes.upload_base64_to_cloudinary", return_value="http://cloud.test/ann.jpg")
@patch("app.routes.routes.image_to_base64", return_value="base64data")
@patch("app.routes.routes.draw_boxes", return_value=Image.new("RGB", (100, 100)))
@patch("app.routes.routes.crop_regions")
@patch("app.routes.routes.SkinClassificationService")
@patch("app.routes.routes.SkinDetectionService")
def test_analyze_with_classification(
    mock_det_cls, mock_clf_cls, mock_crop, mock_draw,
    mock_b64, mock_upload, mock_health, mock_lifestyle,
    client, mock_jwt_identity,
):
    """Class cần classification → gọi classify, response có disease_prediction."""
    # Detection
    mock_det_instance = MagicMock()
    mock_det_instance.detect.return_value = [
        {"class": "acne scar", "confidence": 0.88, "bbox": [20, 20, 80, 80]},
    ]
    mock_det_cls.return_value = mock_det_instance

    # Classification
    mock_clf_instance = MagicMock()
    mock_clf_instance.classify.return_value = {
        "class_index": 1, "class_name": "acne", "confidence": 0.92,
    }
    mock_clf_cls.return_value = mock_clf_instance

    mock_crop.return_value = [Image.new("RGB", (60, 60))]

    img_bytes = _create_test_image_bytes()
    resp = client.post(
        "/api/v1/analyze",
        data={"image": (img_bytes, "test.jpg")},
        content_type="multipart/form-data",
    )

    assert resp.status_code == 200
    data = resp.get_json()
    det = data["detection"][0]
    assert det["requires_classification"] is True
    assert det["disease_prediction"]["class_name"] == "acne"
