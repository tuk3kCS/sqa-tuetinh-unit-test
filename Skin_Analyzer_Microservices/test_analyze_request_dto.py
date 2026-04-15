"""
Unit tests cho AnalyzeRequestDTO – validation và parsing request data.
"""

import pytest
from app.models.analyze_request_dto import AnalyzeRequestDTO


# ============================================================
# Helpers
# ============================================================

def _valid_data():
    """Dữ liệu hợp lệ đầy đủ cho AnalyzeRequestDTO."""
    return {
        "annotated_image_url": "http://img.test/annotated.jpg",
        "detection": [{"detected_class": "acne", "confidence": 0.9}],
        "health_issue_info": "Phát hiện mụn trứng cá",
        "lifestyle_suggestions": {"lifestyle": ["Rửa mặt"], "diet": ["Uống nước"]},
        "metadata": {"timestamp": "2025-01-01T00:00:00", "total_detections": 1},
        "status": "success",
    }


# ============================================================
# __init__ – validation
# ============================================================

# Test Case ID: TC_SKIN_AnalyzeRequestDTO___init___001
# Test Objective: Tạo DTO thành công với dữ liệu hợp lệ đầy đủ
# Input: Dict có tất cả 6 required fields đúng type
# Expected Output: DTO object với các attribute khớp input
# Notes: Kiểm tra happy path
def test_dto_init_valid_data():
    """Dữ liệu hợp lệ → DTO tạo thành công, attribute đúng."""
    data = _valid_data()
    dto = AnalyzeRequestDTO(data)

    assert dto.annotated_image_url == data["annotated_image_url"]
    assert dto.detection == data["detection"]
    assert dto.health_issue_info == data["health_issue_info"]
    assert dto.lifestyle_suggestions == data["lifestyle_suggestions"]
    assert dto.metadata == data["metadata"]
    assert dto.status == data["status"]


# Test Case ID: TC_SKIN_AnalyzeRequestDTO___init___002
# Test Objective: Raise ValueError khi thiếu 1 trường bắt buộc (annotated_image_url)
# Input: Dict thiếu "annotated_image_url"
# Expected Output: ValueError chứa "Missing fields"
# Notes: Kiểm tra required field validation
def test_dto_init_missing_annotated_image_url():
    """Thiếu annotated_image_url → ValueError."""
    data = _valid_data()
    del data["annotated_image_url"]

    with pytest.raises(ValueError, match="Missing fields"):
        AnalyzeRequestDTO(data)


# Test Case ID: TC_SKIN_AnalyzeRequestDTO___init___003
# Test Objective: Raise ValueError khi thiếu nhiều trường bắt buộc
# Input: Dict chỉ có status
# Expected Output: ValueError liệt kê các trường thiếu
# Notes: Kiểm tra validate nhiều trường cùng lúc
def test_dto_init_missing_multiple_fields():
    """Thiếu nhiều trường → ValueError liệt kê tất cả."""
    data = {"status": "success"}

    with pytest.raises(ValueError, match="Missing fields"):
        AnalyzeRequestDTO(data)


# Test Case ID: TC_SKIN_AnalyzeRequestDTO___init___004
# Test Objective: Raise ValueError khi detection không phải list
# Input: detection = "not_a_list"
# Expected Output: ValueError "must be a list"
# Notes: Kiểm tra type validation
def test_dto_init_detection_wrong_type():
    """detection không phải list → ValueError."""
    data = _valid_data()
    data["detection"] = "not_a_list"

    with pytest.raises(ValueError, match="must be a list"):
        AnalyzeRequestDTO(data)


# Test Case ID: TC_SKIN_AnalyzeRequestDTO___init___005
# Test Objective: Raise ValueError khi lifestyle_suggestions không phải dict
# Input: lifestyle_suggestions = ["wrong_type"]
# Expected Output: ValueError "must be a dict"
# Notes: Kiểm tra type validation
def test_dto_init_lifestyle_suggestions_wrong_type():
    """lifestyle_suggestions không phải dict → ValueError."""
    data = _valid_data()
    data["lifestyle_suggestions"] = ["wrong_type"]

    with pytest.raises(ValueError, match="must be a dict"):
        AnalyzeRequestDTO(data)


# Test Case ID: TC_SKIN_AnalyzeRequestDTO___init___006
# Test Objective: Raise ValueError khi metadata không phải dict
# Input: metadata = 12345
# Expected Output: ValueError "must be a dict"
# Notes: Kiểm tra type validation
def test_dto_init_metadata_wrong_type():
    """metadata không phải dict → ValueError."""
    data = _valid_data()
    data["metadata"] = 12345

    with pytest.raises(ValueError, match="must be a dict"):
        AnalyzeRequestDTO(data)


# Test Case ID: TC_SKIN_AnalyzeRequestDTO___init___007
# Test Objective: health_issue_info là optional – None vẫn hợp lệ
# Input: health_issue_info = None (có key nhưng value None)
# Expected Output: DTO tạo thành công, health_issue_info = None
# Notes: Dùng data.get() nên None hợp lệ
def test_dto_init_health_issue_info_none():
    """health_issue_info = None vẫn hợp lệ (optional)."""
    data = _valid_data()
    data["health_issue_info"] = None

    dto = AnalyzeRequestDTO(data)
    assert dto.health_issue_info is None


# ============================================================
# to_json
# ============================================================

# Test Case ID: TC_SKIN_AnalyzeRequestDTO_to_json_001
# Test Objective: to_json trả dict khớp dữ liệu gốc
# Input: DTO đã tạo từ valid data
# Expected Output: Dict giống input data
# Notes: Kiểm tra serialization roundtrip
def test_dto_to_json_correct():
    """to_json trả dict khớp với data gốc."""
    data = _valid_data()
    dto = AnalyzeRequestDTO(data)
    result = dto.to_json()

    assert result["annotated_image_url"] == data["annotated_image_url"]
    assert result["detection"] == data["detection"]
    assert result["health_issue_info"] == data["health_issue_info"]
    assert result["lifestyle_suggestions"] == data["lifestyle_suggestions"]
    assert result["metadata"] == data["metadata"]
    assert result["status"] == data["status"]


# Test Case ID: TC_SKIN_AnalyzeRequestDTO_to_json_002
# Test Objective: to_json chứa đúng 6 key
# Input: DTO hợp lệ
# Expected Output: Dict có đúng 6 key
# Notes: Không thêm key thừa
def test_dto_to_json_key_count():
    """to_json trả dict đúng 6 key."""
    dto = AnalyzeRequestDTO(_valid_data())
    result = dto.to_json()

    expected_keys = {
        "annotated_image_url", "detection", "health_issue_info",
        "lifestyle_suggestions", "metadata", "status",
    }
    assert set(result.keys()) == expected_keys


# Test Case ID: TC_SKIN_AnalyzeRequestDTO_to_json_003
# Test Objective: to_json xử lý detection rỗng
# Input: detection = []
# Expected Output: JSON có detection = []
# Notes: Edge case
def test_dto_to_json_empty_detection():
    """detection rỗng → JSON detection = []."""
    data = _valid_data()
    data["detection"] = []

    dto = AnalyzeRequestDTO(data)
    assert dto.to_json()["detection"] == []
