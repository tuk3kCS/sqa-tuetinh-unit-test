"""
Unit tests cho AnalysisResult – DTO chuyển đổi entity DB sang JSON response.
"""

import pytest
from unittest.mock import MagicMock
from datetime import datetime

from app.models.analysis_model import AnalysisResult


# ============================================================
# Helpers
# ============================================================

def _make_entity(
    id=1,
    analysis_image_url="http://img.test/ann.jpg",
    ai_diagnosis="acne",
    ai_confidence=0.92,
    suggestions=None,
    doctor_note=None,
    created_at=None,
):
    """Tạo mock entity giả lập HealthAnalysis."""
    entity = MagicMock()
    entity.id = id
    entity.analysis_image_url = analysis_image_url
    entity.ai_diagnosis = ai_diagnosis
    entity.ai_confidence = ai_confidence
    entity.suggestions = suggestions or {"lifestyle": ["Rửa mặt 2 lần/ngày"]}
    entity.doctor_note = doctor_note
    entity.created_at = created_at or datetime(2025, 6, 15, 10, 30, 0)
    return entity


# ============================================================
# __init__
# ============================================================

# Test Case ID: TC-FR-07-001nalysisResult___init___001
# Test Objective: Tạo AnalysisResult từ entity với đầy đủ attributes
# Input: Mock entity với tất cả fields
# Expected Output: AnalysisResult có các attribute map đúng (camelCase)
# Notes: Kiểm tra mapping entity → DTO
def test_analysis_result_init_from_entity():
    """Entity đầy đủ → AnalysisResult map đúng tất cả attribute."""
    entity = _make_entity(
        id=42,
        ai_diagnosis="eczema",
        ai_confidence=0.88,
        doctor_note="Cần theo dõi",
    )
    result = AnalysisResult(entity)

    assert result.id == 42
    assert result.analysisImageUrl == "http://img.test/ann.jpg"
    assert result.aiDiagnosis == "eczema"
    assert result.aiConfidence == 0.88
    assert result.doctorNote == "Cần theo dõi"
    assert result.createdAt == "2025-06-15T10:30:00"


# Test Case ID: TC-FR-07-001nalysisResult___init___002
# Test Objective: doctor_note = None khi entity chưa có ghi chú
# Input: entity.doctor_note = None
# Expected Output: result.doctorNote = None
# Notes: Kiểm tra nullable field
def test_analysis_result_init_null_doctor_note():
    """doctor_note None → doctorNote None."""
    entity = _make_entity(doctor_note=None)
    result = AnalysisResult(entity)
    assert result.doctorNote is None


# Test Case ID: TC-FR-07-001nalysisResult___init___003
# Test Objective: createdAt được chuyển sang ISO format string
# Input: entity.created_at = datetime(2025, 1, 1, 0, 0, 0)
# Expected Output: createdAt = "2025-01-01T00:00:00"
# Notes: Kiểm tra isoformat() conversion
def test_analysis_result_init_created_at_iso():
    """created_at datetime → createdAt ISO string."""
    entity = _make_entity(created_at=datetime(2025, 12, 31, 23, 59, 59))
    result = AnalysisResult(entity)
    assert result.createdAt == "2025-12-31T23:59:59"


# Test Case ID: TC-FR-07-001nalysisResult___init___004
# Test Objective: suggestions là dict JSON được giữ nguyên
# Input: entity.suggestions = {"lifestyle": [...], "diet": [...]}
# Expected Output: result.suggestions giữ nguyên dict
# Notes: Kiểm tra JSON field không bị biến đổi
def test_analysis_result_init_suggestions_preserved():
    """suggestions dict giữ nguyên không biến đổi."""
    suggestions = {"lifestyle": ["Ngủ đủ giấc"], "diet": ["Ăn rau xanh"]}
    entity = _make_entity(suggestions=suggestions)
    result = AnalysisResult(entity)
    assert result.suggestions == suggestions


# ============================================================
# to_json
# ============================================================

# Test Case ID: TC-FR-07-001nalysisResult_to_json_001
# Test Objective: to_json trả dict với camelCase keys đúng chuẩn
# Input: AnalysisResult từ entity hợp lệ
# Expected Output: Dict có 7 keys: id, analysisImageUrl, aiDiagnosis, aiConfidence, suggestions, doctorNote, createdAt
# Notes: Kiểm tra đúng format camelCase cho FE
def test_analysis_result_to_json_keys():
    """to_json trả dict đúng 7 camelCase keys."""
    entity = _make_entity()
    result = AnalysisResult(entity)
    json_data = result.to_json()

    expected_keys = {
        "id", "analysisImageUrl", "aiDiagnosis",
        "aiConfidence", "suggestions", "doctorNote", "createdAt",
    }
    assert set(json_data.keys()) == expected_keys


# Test Case ID: TC-FR-07-001nalysisResult_to_json_002
# Test Objective: to_json values khớp với attributes
# Input: AnalysisResult từ entity cụ thể
# Expected Output: Mỗi value trong dict khớp attribute tương ứng
# Notes: Kiểm tra mapping chính xác
def test_analysis_result_to_json_values():
    """to_json values khớp chính xác với attributes."""
    entity = _make_entity(
        id=7,
        ai_diagnosis="rosacea",
        ai_confidence=0.75,
        doctor_note="Tái khám",
    )
    result = AnalysisResult(entity)
    json_data = result.to_json()

    assert json_data["id"] == 7
    assert json_data["aiDiagnosis"] == "rosacea"
    assert json_data["aiConfidence"] == 0.75
    assert json_data["doctorNote"] == "Tái khám"
    assert json_data["analysisImageUrl"] == "http://img.test/ann.jpg"


# Test Case ID: TC-FR-07-001nalysisResult_to_json_003
# Test Objective: to_json xử lý doctorNote = None đúng
# Input: entity chưa có doctor_note
# Expected Output: json["doctorNote"] = None
# Notes: FE cần nhận None thay vì key bị thiếu
def test_analysis_result_to_json_null_doctor_note():
    """doctorNote None → JSON chứa key với value None."""
    entity = _make_entity(doctor_note=None)
    json_data = AnalysisResult(entity).to_json()
    assert "doctorNote" in json_data
    assert json_data["doctorNote"] is None


# Test Case ID: TC-FR-07-001nalysisResult_to_json_004
# Test Objective: to_json trả suggestions đúng format nested dict
# Input: suggestions chứa lifestyle và diet arrays
# Expected Output: JSON suggestions giữ nguyên nested structure
# Notes: Kiểm tra JSON serialization cho nested dict
def test_analysis_result_to_json_suggestions_structure():
    """suggestions nested dict → JSON giữ nguyên cấu trúc."""
    suggestions = {
        "lifestyle": ["Tip 1", "Tip 2"],
        "diet": ["Diet 1", "Diet 2", "Diet 3"],
    }
    entity = _make_entity(suggestions=suggestions)
    json_data = AnalysisResult(entity).to_json()

    assert json_data["suggestions"]["lifestyle"] == ["Tip 1", "Tip 2"]
    assert len(json_data["suggestions"]["diet"]) == 3
