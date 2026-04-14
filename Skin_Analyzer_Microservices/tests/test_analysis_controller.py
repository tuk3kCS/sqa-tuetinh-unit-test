"""
Unit tests cho analysis_controller – các endpoint REST API quản lý kết quả phân tích.

Sử dụng mock_jwt_identity fixture để giả lập JWT authentication.
"""

import pytest
import json
from unittest.mock import patch, MagicMock
from datetime import datetime

from app.models.analysis_entity import HealthAnalysis
from app import db


# ============================================================
# Helpers
# ============================================================

def _seed_entity(session, user_id=1, diagnosis="acne", confidence=0.9):
    """Tạo bản ghi HealthAnalysis trực tiếp trong DB."""
    entity = HealthAnalysis(
        user_id=user_id,
        analysis_image_url="http://img.test/ann.jpg",
        ai_diagnosis=diagnosis,
        ai_confidence=confidence,
        suggestions={"lifestyle": ["Rửa mặt 2 lần/ngày"]},
        created_at=datetime(2025, 3, 15, 10, 0, 0),
    )
    session.add(entity)
    session.flush()
    return entity


# ============================================================
# POST /api/v1/analysis/predict
# ============================================================

# Test Case ID: TC-FR-07-001nalysisController_predict_001
# Test Objective: Trả 401 khi không có JWT token
# Input: POST /predict không header Authorization
# Expected Output: HTTP 401 hoặc 422
# Notes: Kiểm tra middleware JWT chặn request
def test_predict_missing_jwt(client):
    """Không có JWT → bị chặn bởi jwt_required."""
    resp = client.post(
        "/api/v1/analysis/predict",
        json={"annotatedImageUrl": "url", "aiDiagnosis": "acne",
              "aiConfidence": 0.9, "suggestions": {}},
    )
    assert resp.status_code in (401, 422)


# Test Case ID: TC-FR-07-001nalysisController_predict_002
# Test Objective: Trả 400 khi thiếu trường bắt buộc
# Input: POST /predict với JWT hợp lệ nhưng thiếu aiDiagnosis
# Expected Output: HTTP 400, body chứa "Missing field"
# Notes: Kiểm tra validate required_fields
def test_predict_missing_field(client, mock_jwt_identity):
    """Thiếu trường bắt buộc → 400."""
    resp = client.post(
        "/api/v1/analysis/predict",
        json={"annotatedImageUrl": "url", "aiConfidence": 0.9, "suggestions": {}},
        content_type="application/json",
    )
    assert resp.status_code == 400
    data = resp.get_json()
    assert "Missing field" in data.get("error", "")


# Test Case ID: TC-FR-07-001nalysisController_predict_003
# Test Objective: Trả 201 khi request hợp lệ và lưu DB thành công
# Input: POST /predict với đầy đủ 4 trường bắt buộc + JWT
# Expected Output: HTTP 201, body chứa aiDiagnosis
# Notes: Mock AnalysisService.save_analysis để isolate controller logic
@patch("app.controllers.analysis_controller.AnalysisService")
def test_predict_success(mock_svc, client, mock_jwt_identity):
    """Request hợp lệ → 201 + JSON response."""
    mock_result = MagicMock()
    mock_result.to_json.return_value = {
        "id": 1, "aiDiagnosis": "acne", "aiConfidence": 0.9,
        "analysisImageUrl": "url", "suggestions": {},
        "doctorNote": None, "createdAt": "2025-01-01T00:00:00",
    }
    mock_svc.save_analysis.return_value = mock_result

    resp = client.post(
        "/api/v1/analysis/predict",
        json={
            "annotatedImageUrl": "url",
            "aiDiagnosis": "acne",
            "aiConfidence": 0.9,
            "suggestions": {"lifestyle": []},
        },
        content_type="application/json",
    )
    assert resp.status_code == 201
    data = resp.get_json()
    assert data["aiDiagnosis"] == "acne"


# ============================================================
# PATCH /api/v1/analysis/<id>/doctor-note
# ============================================================

# Test Case ID: TC-FR-07-001nalysisController_update_doctor_note_001
# Test Objective: Trả 401 khi không có JWT
# Input: PATCH /<id>/doctor-note không header Authorization
# Expected Output: HTTP 401 hoặc 422
# Notes: JWT middleware
def test_update_doctor_note_missing_jwt(client):
    """Không JWT → 401."""
    resp = client.patch(
        "/api/v1/analysis/1/doctor-note",
        json={"doctorNote": "test"},
    )
    assert resp.status_code in (401, 422)


# Test Case ID: TC-FR-07-001nalysisController_update_doctor_note_002
# Test Objective: Trả 400 khi thiếu doctorNote trong body
# Input: PATCH với JWT hợp lệ nhưng body rỗng
# Expected Output: HTTP 400, "Missing doctorNote"
# Notes: Kiểm tra validate body
def test_update_doctor_note_missing_field(client, mock_jwt_identity):
    """Thiếu doctorNote → 400."""
    resp = client.patch(
        "/api/v1/analysis/1/doctor-note",
        json={},
        content_type="application/json",
    )
    assert resp.status_code == 400
    assert "Missing doctorNote" in resp.get_json().get("error", "")


# Test Case ID: TC-FR-07-001nalysisController_update_doctor_note_003
# Test Objective: Trả 404 khi record không tồn tại hoặc unauthorized
# Input: record_id không tồn tại, JWT hợp lệ
# Expected Output: HTTP 404
# Notes: AnalysisService.update_doctor_note trả None
@patch("app.controllers.analysis_controller.AnalysisService")
def test_update_doctor_note_not_found(mock_svc, client, mock_jwt_identity):
    """Record không tồn tại → 404."""
    mock_svc.update_doctor_note.return_value = None

    resp = client.patch(
        "/api/v1/analysis/9999/doctor-note",
        json={"doctorNote": "note"},
        content_type="application/json",
    )
    assert resp.status_code == 404


# Test Case ID: TC-FR-07-001nalysisController_update_doctor_note_004
# Test Objective: Trả 200 khi cập nhật thành công
# Input: record hợp lệ, doctorNote mới, JWT chính chủ
# Expected Output: HTTP 200, body có doctorNote đã cập nhật
# Notes: Mock AnalysisService trả kết quả thành công
@patch("app.controllers.analysis_controller.AnalysisService")
def test_update_doctor_note_success(mock_svc, client, mock_jwt_identity):
    """Cập nhật thành công → 200 + JSON."""
    mock_result = MagicMock()
    mock_result.to_json.return_value = {
        "id": 1, "doctorNote": "Tái khám 2 tuần",
        "aiDiagnosis": "acne", "aiConfidence": 0.9,
        "analysisImageUrl": "url", "suggestions": {},
        "createdAt": "2025-01-01T00:00:00",
    }
    mock_svc.update_doctor_note.return_value = mock_result

    resp = client.patch(
        "/api/v1/analysis/1/doctor-note",
        json={"doctorNote": "Tái khám 2 tuần"},
        content_type="application/json",
    )
    assert resp.status_code == 200
    assert resp.get_json()["doctorNote"] == "Tái khám 2 tuần"


# ============================================================
# GET /api/v1/analysis/history
# ============================================================

# Test Case ID: TC-FR-07-001nalysisController_history_001
# Test Objective: Trả 401 khi không có JWT
# Input: GET /history không header
# Expected Output: HTTP 401 hoặc 422
# Notes: JWT middleware
def test_history_missing_jwt(client):
    """Không JWT → 401."""
    resp = client.get("/api/v1/analysis/history")
    assert resp.status_code in (401, 422)


# Test Case ID: TC-FR-07-001nalysisController_history_002
# Test Objective: Trả 200 + danh sách khi user có lịch sử
# Input: JWT hợp lệ, DB có bản ghi cho user
# Expected Output: HTTP 200, JSON array
# Notes: Mock AnalysisService.get_history_by_user
@patch("app.controllers.analysis_controller.AnalysisService")
def test_history_success(mock_svc, client, mock_jwt_identity):
    """Có lịch sử → 200 + JSON array."""
    mock_result = MagicMock()
    mock_result.to_json.return_value = {
        "id": 1, "aiDiagnosis": "acne", "aiConfidence": 0.9,
        "analysisImageUrl": "url", "suggestions": {},
        "doctorNote": None, "createdAt": "2025-01-01T00:00:00",
    }
    mock_svc.get_history_by_user.return_value = [mock_result]

    resp = client.get("/api/v1/analysis/history")
    assert resp.status_code == 200
    data = resp.get_json()
    assert isinstance(data, list)
    assert len(data) == 1


# Test Case ID: TC-FR-07-001nalysisController_history_003
# Test Objective: Trả 200 + list rỗng khi user chưa có lịch sử
# Input: JWT hợp lệ, DB không có bản ghi
# Expected Output: HTTP 200, []
# Notes: Kiểm tra edge case list rỗng
@patch("app.controllers.analysis_controller.AnalysisService")
def test_history_empty(mock_svc, client, mock_jwt_identity):
    """Không có lịch sử → 200 + []."""
    mock_svc.get_history_by_user.return_value = []

    resp = client.get("/api/v1/analysis/history")
    assert resp.status_code == 200
    assert resp.get_json() == []


# ============================================================
# POST /api/v1/analysis/save-ai-result
# ============================================================

# Test Case ID: TC-FR-07-001nalysisController_save_ai_result_001
# Test Objective: Trả 401 khi không có JWT
# Input: POST /save-ai-result không header
# Expected Output: HTTP 401 hoặc 422
# Notes: JWT middleware
def test_save_ai_result_missing_jwt(client):
    """Không JWT → 401."""
    resp = client.post(
        "/api/v1/analysis/save-ai-result",
        json={"annotated_image_url": "url"},
    )
    assert resp.status_code in (401, 422)


# Test Case ID: TC-FR-07-001nalysisController_save_ai_result_002
# Test Objective: Trả 400 khi DTO validation fail (thiếu trường)
# Input: JSON thiếu required fields
# Expected Output: HTTP 400
# Notes: AnalyzeRequestDTO raise ValueError
def test_save_ai_result_invalid_dto(client, mock_jwt_identity):
    """DTO thiếu trường bắt buộc → 400."""
    resp = client.post(
        "/api/v1/analysis/save-ai-result",
        json={"annotated_image_url": "url"},
        content_type="application/json",
    )
    assert resp.status_code == 400


# Test Case ID: TC-FR-07-001nalysisController_save_ai_result_003
# Test Objective: Trả 201 khi DTO hợp lệ và lưu thành công
# Input: JSON đầy đủ các trường bắt buộc
# Expected Output: HTTP 201, JSON response
# Notes: Mock AnalysisService.save_analysis_from_request
@patch("app.controllers.analysis_controller.AnalysisService")
def test_save_ai_result_success(mock_svc, client, mock_jwt_identity):
    """Request hợp lệ → 201 + JSON."""
    mock_result = MagicMock()
    mock_result.to_json.return_value = {
        "id": 1, "aiDiagnosis": "acne", "aiConfidence": 0.9,
        "analysisImageUrl": "url", "suggestions": {},
        "doctorNote": None, "createdAt": "2025-01-01T00:00:00",
    }
    mock_svc.save_analysis_from_request.return_value = mock_result

    resp = client.post(
        "/api/v1/analysis/save-ai-result",
        json={
            "annotated_image_url": "http://img.test/ann.jpg",
            "detection": [{"detected_class": "acne", "confidence": 0.9}],
            "health_issue_info": "info",
            "lifestyle_suggestions": {"lifestyle": [], "diet": []},
            "metadata": {"timestamp": "2025-01-01"},
            "status": "success",
        },
        content_type="application/json",
    )
    assert resp.status_code == 201
    data = resp.get_json()
    assert data["aiDiagnosis"] == "acne"
