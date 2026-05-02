"""
Unit tests cho module app.agent.safety
Kiểm tra run_safety_check và _map_loose_safety.
"""
import json
import pytest
from unittest.mock import MagicMock, patch

from app.agent.safety import run_safety_check, _map_loose_safety


# ============================================================
# run_safety_check – Tin nhắn an toàn (qua LLM classifier)
# ============================================================

# Test Case ID: TC_AI_TestAgentSafety_run_safety_check_001
# Test Objective: Kiểm tra tin nhắn an toàn khi LLM trả JSON hợp lệ
# Input: Message bình thường, LLM trả {"safe": true, "category": "general", "confidence": 0.95, "reason": "general nutrition"}
# Expected Output: Dict safe=True, category="general"
# Notes: moderate() trả None → dùng LLM classifier, confidence >= 0.8
def test_run_safety_check_safe_message():
    llm = MagicMock()
    llm.moderate.return_value = None
    llm.chat.return_value = json.dumps({
        "safe": True, "category": "general",
        "confidence": 0.95, "reason": "general nutrition"
    })
    result = run_safety_check(llm, "Tôi nên ăn bao nhiêu protein?")
    assert result["safe"] is True
    assert result["category"] == "general"


# Test Case ID: TC_AI_TestAgentSafety_run_safety_check_002
# Test Objective: Kiểm tra tin nhắn không an toàn (medical)
# Input: Message y tế, LLM trả {"safe": false, "category": "medical", "confidence": 0.9}
# Expected Output: Dict safe=False, category="medical"
# Notes: moderate() trả None → dùng LLM classifier
def test_run_safety_check_unsafe_medical_message():
    llm = MagicMock()
    llm.moderate.return_value = None
    llm.chat.return_value = json.dumps({
        "safe": False, "category": "medical",
        "confidence": 0.9, "reason": "medical condition"
    })
    result = run_safety_check(llm, "Tôi bị tiểu đường nên ăn gì?")
    assert result["safe"] is False
    assert result["category"] == "medical"


# Test Case ID: TC_AI_TestAgentSafety_run_safety_check_003
# Test Objective: Kiểm tra fallback khi LLM trả JSON không hợp lệ
# Input: LLM trả chuỗi văn bản không phải JSON
# Expected Output: Dict safe=False, reason="invalid_safety_response"
# Notes: Cả validate_with_schema lẫn regex đều thất bại → trả fallback
def test_run_safety_check_invalid_llm_response_fallback():
    llm = MagicMock()
    llm.moderate.return_value = None
    llm.chat.return_value = "I cannot process this request"
    result = run_safety_check(llm, "test message")
    assert result["safe"] is False
    assert result["reason"] == "invalid_safety_response"


# Test Case ID: TC_AI_TestAgentSafety_run_safety_check_004
# Test Objective: Kiểm tra moderation API trả kết quả flagged (self-harm)
# Input: moderate() trả {"flagged": True, "categories": {"self-harm": True}}
# Expected Output: Dict safe=False, category="emergency"
# Notes: Nhánh moderation available + flagged + self-harm category
def test_run_safety_check_moderation_flagged_emergency():
    llm = MagicMock()
    llm.moderate.return_value = {
        "flagged": True,
        "categories": {"self-harm": True, "violence": False}
    }
    result = run_safety_check(llm, "Tôi muốn tự làm hại bản thân")
    assert result["safe"] is False
    assert result["category"] == "emergency"
    assert result["confidence"] == 0.99


# Test Case ID: TC_AI_TestAgentSafety_run_safety_check_005
# Test Objective: Kiểm tra moderation API trả kết quả flagged (medical)
# Input: moderate() trả {"flagged": True, "categories": {"medical": True}}
# Expected Output: Dict safe=False, category="medical"
# Notes: Nhánh moderation flagged + medical category
def test_run_safety_check_moderation_flagged_medical():
    llm = MagicMock()
    llm.moderate.return_value = {
        "flagged": True,
        "categories": {"medical": True}
    }
    result = run_safety_check(llm, "Tôi bị bệnh tim")
    assert result["safe"] is False
    assert result["category"] == "medical"
    assert result["confidence"] == 0.9


# Test Case ID: TC_AI_TestAgentSafety_run_safety_check_006
# Test Objective: Kiểm tra moderation API trả kết quả flagged (generic - không rơi vào emergency/medical)
# Input: moderate() trả {"flagged": True, "categories": {"hate": True}}
# Expected Output: Dict safe=False, category="general"
# Notes: Nhánh generic flagged khi không match emergency/medical categories
def test_run_safety_check_moderation_flagged_generic():
    llm = MagicMock()
    llm.moderate.return_value = {
        "flagged": True,
        "categories": {"hate": True}
    }
    result = run_safety_check(llm, "bad content")
    assert result["safe"] is False
    assert result["category"] == "general"
    assert result["confidence"] == 0.8


# Test Case ID: TC_AI_TestAgentSafety_run_safety_check_007
# Test Objective: Kiểm tra moderation API trả kết quả không flagged
# Input: moderate() trả {"flagged": False}
# Expected Output: Dict safe=True, reason="moderation_allow"
# Notes: Nhánh not flagged → safe
def test_run_safety_check_moderation_not_flagged():
    llm = MagicMock()
    llm.moderate.return_value = {"flagged": False}
    result = run_safety_check(llm, "Hôm nay ăn gì?")
    assert result["safe"] is True
    assert result["reason"] == "moderation_allow"


# Test Case ID: TC_AI_TestAgentSafety_run_safety_check_008
# Test Objective: Kiểm tra khi moderate() raise exception → fallback sang LLM classifier
# Input: moderate() raise Exception, LLM trả JSON hợp lệ safe=True
# Expected Output: Dict safe=True (từ LLM classifier)
# Notes: Exception trong moderate() bị bắt → mod=None → dùng LLM
def test_run_safety_check_moderation_raises_exception():
    llm = MagicMock()
    llm.moderate.side_effect = Exception("API error")
    llm.chat.return_value = json.dumps({
        "safe": True, "category": "general",
        "confidence": 0.92, "reason": "general"
    })
    result = run_safety_check(llm, "Tôi nên tập gì?")
    assert result["safe"] is True


# Test Case ID: TC_AI_TestAgentSafety_run_safety_check_009
# Test Objective: Kiểm tra low confidence → đánh dấu unsafe
# Input: LLM trả confidence=0.3 (< 0.8 threshold)
# Expected Output: Dict safe=False, reason chứa "low_confidence"
# Notes: Nhánh confidence < CONFIDENCE_THRESHOLD
def test_run_safety_check_low_confidence_marks_unsafe():
    llm = MagicMock()
    llm.moderate.return_value = None
    llm.chat.return_value = json.dumps({
        "safe": True, "category": "general",
        "confidence": 0.3, "reason": ""
    })
    result = run_safety_check(llm, "Câu hỏi mơ hồ")
    assert result["safe"] is False
    assert result["reason"] == "low_confidence"


# Test Case ID: TC_AI_TestAgentSafety_run_safety_check_010
# Test Objective: Kiểm tra LLM trả JSON trong markdown fences → regex extract thành công
# Input: LLM trả ```json\n{...}\n``` wrapping
# Expected Output: Dict parse thành công từ JSON bên trong
# Notes: Nhánh regex extract JSON substring
def test_run_safety_check_json_in_markdown_fences():
    llm = MagicMock()
    llm.moderate.return_value = None
    llm.chat.return_value = '```json\n{"safe": true, "category": "general", "confidence": 0.95, "reason": "ok"}\n```'
    result = run_safety_check(llm, "Ăn gì hôm nay?")
    assert result["safe"] is True
    assert result["category"] == "general"


# Test Case ID: TC_AI_TestAgentSafety_run_safety_check_011
# Test Objective: Kiểm tra moderation trả categories=None (edge case)
# Input: moderate() trả {"flagged": True, "categories": None}
# Expected Output: Dict safe=False, category="general" (generic flagged branch)
# Notes: categories=None → cats = {} → không match bất kỳ specific category nào
def test_run_safety_check_moderation_flagged_categories_none():
    llm = MagicMock()
    llm.moderate.return_value = {"flagged": True, "categories": None}
    result = run_safety_check(llm, "test")
    assert result["safe"] is False
    assert result["category"] == "general"


# ============================================================
# _map_loose_safety
# ============================================================

# Test Case ID: TC_AI_TestAgentSafety_map_loose_safety_001
# Test Objective: Kiểm tra mapping khi input có đủ "safe" và "category"
# Input: {"safe": True, "category": "general", "confidence": 0.8}
# Expected Output: Dict chuẩn hóa với safe=True, category="general"
# Notes: Nhánh "safe" in p and "category" in p
def test_map_loose_safety_has_safe_and_category():
    result = _map_loose_safety({"safe": True, "category": "general", "confidence": 0.8})
    assert result["safe"] is True
    assert result["category"] == "general"
    assert result["confidence"] == 0.8


# Test Case ID: TC_AI_TestAgentSafety_map_loose_safety_002
# Test Objective: Kiểm tra mapping classification "greeting" → safe
# Input: {"classification": "greeting"}
# Expected Output: Dict safe=True, category="general"
# Notes: Nhánh cls chứa "greet"
def test_map_loose_safety_classification_greeting():
    result = _map_loose_safety({"classification": "greeting"})
    assert result["safe"] is True
    assert result["category"] == "general"


# Test Case ID: TC_AI_TestAgentSafety_map_loose_safety_003
# Test Objective: Kiểm tra mapping classification "medical" → unsafe
# Input: {"label": "medical_advice"}
# Expected Output: Dict safe=False, category="medical"
# Notes: Nhánh cls chứa "medical"
def test_map_loose_safety_label_medical():
    result = _map_loose_safety({"label": "medical_advice"})
    assert result["safe"] is False
    assert result["category"] == "medical"


# Test Case ID: TC_AI_TestAgentSafety_map_loose_safety_004
# Test Objective: Kiểm tra mapping classification "suicide" → emergency
# Input: {"category": "suicide_risk"}
# Expected Output: Dict safe=False, category="emergency"
# Notes: Nhánh cls chứa "suicid"
def test_map_loose_safety_category_emergency():
    result = _map_loose_safety({"classification": "suicide_risk"})
    assert result["safe"] is False
    assert result["category"] == "emergency"
    assert result["confidence"] == 0.99


# Test Case ID: TC_AI_TestAgentSafety_map_loose_safety_005
# Test Objective: Kiểm tra mapping "is_safe" key
# Input: {"is_safe": True}
# Expected Output: Dict safe=True, category="general", confidence=0.6
# Notes: Nhánh "is_safe" in p
def test_map_loose_safety_is_safe_key():
    result = _map_loose_safety({"is_safe": True})
    assert result["safe"] is True
    assert result["confidence"] == 0.6


# Test Case ID: TC_AI_TestAgentSafety_map_loose_safety_006
# Test Objective: Kiểm tra trả None khi input không có key nào phù hợp
# Input: {"unknown_key": "value"}
# Expected Output: None
# Notes: Không match bất kỳ nhánh nào → trả None
def test_map_loose_safety_no_matching_keys():
    result = _map_loose_safety({"unknown_key": "value"})
    assert result is None


# Test Case ID: TC_AI_TestAgentSafety_map_loose_safety_007
# Test Objective: Kiểm tra trả None khi input không phải dict
# Input: "not a dict"
# Expected Output: None
# Notes: isinstance check thất bại → trả None
def test_map_loose_safety_non_dict_input():
    result = _map_loose_safety("not a dict")
    assert result is None


# Test Case ID: TC_AI_TestAgentSafety_map_loose_safety_008
# Test Objective: Kiểm tra mapping "is_safe" = False
# Input: {"is_safe": False}
# Expected Output: Dict safe=False
# Notes: bool(False) = False
def test_map_loose_safety_is_safe_false():
    result = _map_loose_safety({"is_safe": False})
    assert result["safe"] is False
    assert result["reason"] == "mapped_from:is_safe"
