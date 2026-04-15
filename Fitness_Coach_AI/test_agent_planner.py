"""
Unit tests cho module app.agent.planner
Kiểm tra run_planner và _map_loose_planner.
"""
import json
import pytest
from unittest.mock import MagicMock

from app.agent.planner import run_planner, _map_loose_planner


# ============================================================
# run_planner – Các intent khác nhau
# ============================================================

# Test Case ID: TC_FITNESS_Planner_run_planner_001
# Test Objective: Kiểm tra intent "general" khi LLM trả JSON hợp lệ
# Input: Message chat bình thường, LLM trả {"intent": "general", "decision": "answer", "reason": "chat", "confidence": 0.9}
# Expected Output: Dict intent="general", decision="answer"
# Notes: Happy path – LLM trả đúng schema
def test_run_planner_general_intent():
    llm = MagicMock()
    llm.chat.return_value = json.dumps({
        "intent": "general", "decision": "answer",
        "reason": "chat", "confidence": 0.9
    })
    result = run_planner(llm, "Xin chào!", state={})
    assert result["intent"] == "general"
    assert result["decision"] == "answer"


# Test Case ID: TC_FITNESS_Planner_run_planner_002
# Test Objective: Kiểm tra intent "meal" với decision "create_new"
# Input: Message yêu cầu tạo meal plan
# Expected Output: Dict intent="meal", decision="create_new"
# Notes: Nhánh meal plan intent
def test_run_planner_meal_plan_intent():
    llm = MagicMock()
    llm.chat.return_value = json.dumps({
        "intent": "meal", "decision": "create_new",
        "reason": "user wants meal plan", "confidence": 0.88
    })
    result = run_planner(llm, "Tạo cho tôi kế hoạch ăn uống", state={})
    assert result["intent"] == "meal"
    assert result["decision"] == "create_new"


# Test Case ID: TC_FITNESS_Planner_run_planner_003
# Test Objective: Kiểm tra intent "workout" với decision "use_existing"
# Input: Message hỏi về workout hiện tại
# Expected Output: Dict intent="workout", decision="use_existing"
# Notes: Nhánh workout intent
def test_run_planner_workout_intent():
    llm = MagicMock()
    llm.chat.return_value = json.dumps({
        "intent": "workout", "decision": "use_existing",
        "reason": "plan exists", "confidence": 0.95
    })
    result = run_planner(llm, "Hôm nay tập gì?", state={"workout_plan": {}})
    assert result["intent"] == "workout"
    assert result["decision"] == "use_existing"


# Test Case ID: TC_FITNESS_Planner_run_planner_004
# Test Objective: Kiểm tra fallback khi LLM trả response không hợp lệ
# Input: LLM trả chuỗi văn bản không phải JSON
# Expected Output: Fallback dict {"intent": "general", "decision": "answer", "confidence": 0.0}
# Notes: Tất cả parsing thất bại → trả fallback mặc định
def test_run_planner_invalid_response_fallback():
    llm = MagicMock()
    llm.chat.return_value = "I don't understand the format"
    result = run_planner(llm, "test", state={})
    assert result["intent"] == "general"
    assert result["decision"] == "answer"
    assert result["confidence"] == 0.0
    assert result["reason"] == "fallback_parse"


# Test Case ID: TC_FITNESS_Planner_run_planner_005
# Test Objective: Kiểm tra LLM trả JSON trong markdown → regex extract thành công
# Input: LLM trả ```json\n{...}\n```
# Expected Output: Dict parse thành công
# Notes: Nhánh regex extract + validate_with_schema
def test_run_planner_json_in_markdown():
    llm = MagicMock()
    llm.chat.return_value = '```json\n{"intent": "meal", "decision": "ask_create", "reason": "no plan yet"}\n```'
    result = run_planner(llm, "Tôi muốn ăn gì?", state={})
    assert result["intent"] == "meal"
    assert result["decision"] == "ask_create"


# Test Case ID: TC_FITNESS_Planner_run_planner_006
# Test Objective: Kiểm tra LLM trả JSON loose format → _map_loose_planner mapping
# Input: LLM trả JSON với key "label" thay vì "intent", "action" thay vì "decision"
# Expected Output: Dict được map thành công
# Notes: Nhánh _map_loose_planner
def test_run_planner_loose_format_mapped():
    llm = MagicMock()
    llm.chat.return_value = json.dumps({
        "label": "meal planning", "action": "create new plan"
    })
    result = run_planner(llm, "Tạo thực đơn", state={})
    assert result["intent"] == "meal"
    assert result["decision"] == "create_new"


# ============================================================
# _map_loose_planner
# ============================================================

# Test Case ID: TC_FITNESS_Planner_map_loose_planner_001
# Test Objective: Kiểm tra khi input đã có "intent" và "decision" → normalize trực tiếp
# Input: {"intent": "general", "decision": "answer", "confidence": 0.7}
# Expected Output: Dict chuẩn hóa giữ nguyên giá trị
# Notes: Nhánh "intent" in p and "decision" in p
def test_map_loose_planner_already_has_required_keys():
    result = _map_loose_planner({"intent": "general", "decision": "answer", "confidence": 0.7})
    assert result["intent"] == "general"
    assert result["decision"] == "answer"
    assert result["confidence"] == 0.7


# Test Case ID: TC_FITNESS_Planner_map_loose_planner_002
# Test Objective: Kiểm tra mapping key "label" chứa "workout" → intent="workout"
# Input: {"label": "workout exercise", "next_action": "use_existing"}
# Expected Output: intent="workout", decision="use_existing"
# Notes: Nhánh label → intent mapping + next_action → decision mapping
def test_map_loose_planner_label_workout():
    result = _map_loose_planner({"label": "workout exercise", "next_action": "use_existing"})
    assert result["intent"] == "workout"
    assert result["decision"] == "use_existing"


# Test Case ID: TC_FITNESS_Planner_map_loose_planner_003
# Test Objective: Kiểm tra mapping "classification" chứa "food" → intent="meal"
# Input: {"classification": "food related", "action": "ask create plan"}
# Expected Output: intent="meal", decision="ask_create"
# Notes: Nhánh food → meal mapping + ask → ask_create
def test_map_loose_planner_classification_food():
    result = _map_loose_planner({"classification": "food related", "action": "ask create plan"})
    assert result["intent"] == "meal"
    assert result["decision"] == "ask_create"


# Test Case ID: TC_FITNESS_Planner_map_loose_planner_004
# Test Objective: Kiểm tra mapping label không thuộc meal/workout → intent="general"
# Input: {"label": "greeting", "action": "respond to user"}
# Expected Output: intent="general", decision="answer"
# Notes: "greeting" không chứa meal/workout keywords → general; "respond" → answer
def test_map_loose_planner_general_fallback():
    result = _map_loose_planner({"label": "greeting", "action": "respond to user"})
    assert result["intent"] == "general"
    assert result["decision"] == "answer"


# Test Case ID: TC_FITNESS_Planner_map_loose_planner_005
# Test Objective: Kiểm tra trả None khi không có key nào phù hợp
# Input: {"unknown": "value"}
# Expected Output: None
# Notes: Không match bất kỳ nhánh nào
def test_map_loose_planner_no_matching_keys():
    result = _map_loose_planner({"unknown": "value"})
    assert result is None


# Test Case ID: TC_FITNESS_Planner_map_loose_planner_006
# Test Objective: Kiểm tra trả None khi input không phải dict
# Input: "not a dict"
# Expected Output: None
# Notes: isinstance check thất bại
def test_map_loose_planner_non_dict():
    result = _map_loose_planner("not a dict")
    assert result is None


# Test Case ID: TC_FITNESS_Planner_map_loose_planner_007
# Test Objective: Kiểm tra decision "create_new" mapping
# Input: {"label": "meal", "action": "create new plan for user"}
# Expected Output: decision="create_new"
# Notes: "create" keyword → create_new
def test_map_loose_planner_decision_create_new():
    result = _map_loose_planner({"label": "meal prep", "action": "create"})
    assert result["decision"] == "create_new"
