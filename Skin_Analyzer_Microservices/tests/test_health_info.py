"""
Unit tests cho health_info.py – generate_health_issue_info & generate_lifestyle_suggestions.

Kiểm tra logic tổng hợp thông tin sức khỏe và gợi ý lối sống từ kết quả AI.
"""

import pytest
from app.utils.health_info import (
    generate_health_issue_info,
    generate_lifestyle_suggestions,
    DISEASE_INFO,
    COSMETIC_ISSUES,
    HIGH_CONFIDENCE_THRESHOLD,
    MEDIUM_CONFIDENCE_THRESHOLD,
)


# ============================================================
# generate_health_issue_info
# ============================================================

# Test Case ID: TC-FR-07-001ealthInfo_generate_health_issue_info_001
# Test Objective: Trả thông tin bệnh khi có disease classification với confidence cao
# Input: results có requires_classification=True, disease_prediction "acne" conf=0.9
# Expected Output: String chứa "Mụn trứng cá" (tên tiếng Việt)
# Notes: combined_confidence = 0.9*0.6 + 0.85*0.4 = 0.88 > MEDIUM
def test_health_issue_info_known_disease():
    """Bệnh acne confidence cao → trả text chứa tên bệnh tiếng Việt."""
    results = [{
        "detected_class": "pustules",
        "requires_classification": True,
        "disease_prediction": {
            "class_name": "acne",
            "confidence": 0.9,
        },
        "confidence": 0.85,
    }]
    confidences = [0.85]

    info = generate_health_issue_info(results, confidences)

    assert info is not None
    assert "Mụn trứng cá" in info


# Test Case ID: TC-FR-07-001ealthInfo_generate_health_issue_info_002
# Test Objective: Trả thông tin vấn đề thẩm mỹ khi không cần classification
# Input: results có requires_classification=False, detected_class="Dark Circle"
# Expected Output: String chứa "Quầng thâm mắt"
# Notes: Cosmetic issue lookup
def test_health_issue_info_cosmetic_issue():
    """Vấn đề thẩm mỹ (không cần classification) → text chứa tên tiếng Việt."""
    results = [{
        "detected_class": "Dark Circle",
        "requires_classification": False,
        "disease_prediction": None,
        "confidence": 0.8,
    }]
    confidences = [0.8]

    info = generate_health_issue_info(results, confidences)

    assert info is not None
    assert "Quầng thâm mắt" in info


# Test Case ID: TC-FR-07-001ealthInfo_generate_health_issue_info_003
# Test Objective: Trả None khi confidence dưới ngưỡng MEDIUM
# Input: combined_confidence < 0.5
# Expected Output: None
# Notes: Kiểm tra nhánh conf < MEDIUM_CONFIDENCE_THRESHOLD
def test_health_issue_info_low_confidence():
    """Confidence thấp dưới ngưỡng → trả None."""
    results = [{
        "detected_class": "pustules",
        "requires_classification": True,
        "disease_prediction": {
            "class_name": "acne",
            "confidence": 0.2,  # combined = 0.2*0.6 + 0.1*0.4 = 0.16
        },
        "confidence": 0.1,
    }]
    confidences = [0.1]

    info = generate_health_issue_info(results, confidences)
    assert info is None


# Test Case ID: TC-FR-07-001ealthInfo_generate_health_issue_info_004
# Test Objective: Xử lý nhiều issues – cả bệnh và thẩm mỹ
# Input: 2 results – 1 bệnh da liễu + 1 vấn đề thẩm mỹ
# Expected Output: String chứa cả tên bệnh và tên vấn đề thẩm mỹ
# Notes: Kiểm tra tổng hợp nhiều vấn đề
def test_health_issue_info_multiple_issues():
    """Nhiều vấn đề → text chứa tất cả."""
    results = [
        {
            "detected_class": "pustules",
            "requires_classification": True,
            "disease_prediction": {"class_name": "eczema", "confidence": 0.85},
            "confidence": 0.9,
        },
        {
            "detected_class": "Eyebag",
            "requires_classification": False,
            "disease_prediction": None,
            "confidence": 0.8,
        },
    ]
    confidences = [0.9, 0.8]

    info = generate_health_issue_info(results, confidences)

    assert info is not None
    assert "Viêm da dị ứng" in info
    assert "Bọng mắt" in info


# Test Case ID: TC-FR-07-001ealthInfo_generate_health_issue_info_005
# Test Objective: Trả None khi results rỗng
# Input: results = [], confidences = []
# Expected Output: None
# Notes: Edge case – không có kết quả
def test_health_issue_info_empty_results():
    """results rỗng → None."""
    assert generate_health_issue_info([], []) is None


# Test Case ID: TC-FR-07-001ealthInfo_generate_health_issue_info_006
# Test Objective: Trả None khi disease class là "none" (da khỏe mạnh)
# Input: disease_prediction class_name="none" với confidence cao
# Expected Output: None (bỏ qua class "none")
# Notes: Kiểm tra filter class_name != "none"
def test_health_issue_info_none_disease():
    """Class 'none' → bỏ qua, trả None nếu chỉ có 'none'."""
    results = [{
        "detected_class": "pustules",
        "requires_classification": True,
        "disease_prediction": {"class_name": "none", "confidence": 0.95},
        "confidence": 0.9,
    }]
    confidences = [0.9]

    info = generate_health_issue_info(results, confidences)
    assert info is None


# ============================================================
# generate_lifestyle_suggestions
# ============================================================

# Test Case ID: TC-FR-07-001ealthInfo_generate_lifestyle_suggestions_001
# Test Objective: Trả gợi ý từ DISEASE_INFO khi có bệnh da liễu
# Input: results với disease "acne", confidence cao
# Expected Output: dict có "lifestyle" và "diet" lists chứa gợi ý acne
# Notes: Kiểm tra lookup DISEASE_INFO
def test_lifestyle_suggestions_disease():
    """Bệnh acne → gợi ý từ DISEASE_INFO['acne']."""
    results = [{
        "detected_class": "pustules",
        "requires_classification": True,
        "disease_prediction": {"class_name": "acne", "confidence": 0.85},
        "confidence": 0.9,
    }]
    confidences = [0.9]

    suggestions = generate_lifestyle_suggestions(results, confidences)

    assert "lifestyle" in suggestions
    assert "diet" in suggestions
    assert len(suggestions["lifestyle"]) > 0
    assert len(suggestions["diet"]) > 0
    # Gợi ý chung bác sĩ
    assert any("bác sĩ" in s for s in suggestions["lifestyle"])


# Test Case ID: TC-FR-07-001ealthInfo_generate_lifestyle_suggestions_002
# Test Objective: Trả gợi ý từ COSMETIC_ISSUES khi có vấn đề thẩm mỹ
# Input: results với detected_class="Eyebag"
# Expected Output: dict có lifestyle chứa gợi ý cho bọng mắt
# Notes: Kiểm tra lookup COSMETIC_ISSUES
def test_lifestyle_suggestions_cosmetic():
    """Vấn đề thẩm mỹ Eyebag → gợi ý từ COSMETIC_ISSUES."""
    results = [{
        "detected_class": "Eyebag",
        "requires_classification": False,
        "disease_prediction": None,
        "confidence": 0.8,
    }]
    confidences = [0.8]

    suggestions = generate_lifestyle_suggestions(results, confidences)

    assert len(suggestions["lifestyle"]) > 0
    assert len(suggestions["diet"]) > 0


# Test Case ID: TC-FR-07-001ealthInfo_generate_lifestyle_suggestions_003
# Test Objective: Trả gợi ý mặc định (none) khi results rỗng
# Input: results = []
# Expected Output: DISEASE_INFO["none"] suggestions
# Notes: Kiểm tra fallback
def test_lifestyle_suggestions_empty_results():
    """results rỗng → gợi ý mặc định từ DISEASE_INFO['none']."""
    suggestions = generate_lifestyle_suggestions([], [])

    assert suggestions["lifestyle"] == DISEASE_INFO["none"]["lifestyle"]
    assert suggestions["diet"] == DISEASE_INFO["none"]["diet"]


# Test Case ID: TC-FR-07-001ealthInfo_generate_lifestyle_suggestions_004
# Test Objective: Trả gợi ý mặc định khi confidence thấp (không vấn đề nào vượt ngưỡng)
# Input: classification conf rất thấp → combined < MEDIUM
# Expected Output: Gợi ý mặc định (none)
# Notes: Kiểm tra nhánh diseases=[] và cosmetics=[]
def test_lifestyle_suggestions_low_confidence():
    """Confidence thấp → không match, trả gợi ý mặc định."""
    results = [{
        "detected_class": "pustules",
        "requires_classification": True,
        "disease_prediction": {"class_name": "acne", "confidence": 0.1},
        "confidence": 0.1,
    }]
    confidences = [0.1]

    suggestions = generate_lifestyle_suggestions(results, confidences)

    assert suggestions["lifestyle"] == DISEASE_INFO["none"]["lifestyle"]


# Test Case ID: TC-FR-07-001ealthInfo_generate_lifestyle_suggestions_005
# Test Objective: Không có gợi ý trùng lặp khi nhiều vấn đề có chung gợi ý
# Input: 2 bệnh khác nhau
# Expected Output: Các suggestion không trùng nhau
# Notes: Kiểm tra deduplicate logic
def test_lifestyle_suggestions_no_duplicates():
    """Nhiều bệnh → gợi ý không trùng lặp."""
    results = [
        {
            "detected_class": "pustules",
            "requires_classification": True,
            "disease_prediction": {"class_name": "acne", "confidence": 0.85},
            "confidence": 0.9,
        },
        {
            "detected_class": "skinredness",
            "requires_classification": True,
            "disease_prediction": {"class_name": "rosacea", "confidence": 0.8},
            "confidence": 0.85,
        },
    ]
    confidences = [0.9, 0.85]

    suggestions = generate_lifestyle_suggestions(results, confidences)

    # Không có phần tử trùng
    assert len(suggestions["lifestyle"]) == len(set(suggestions["lifestyle"]))
    assert len(suggestions["diet"]) == len(set(suggestions["diet"]))
