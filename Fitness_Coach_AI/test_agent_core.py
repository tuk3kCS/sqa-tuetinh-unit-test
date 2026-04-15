"""
Unit tests cho module app.agent.core
Kiểm tra handle_chat, create_meal_plan, create_workout_plan, _safe_parse_json.
"""
import json
import pytest
from unittest.mock import MagicMock, patch, PropertyMock
from types import SimpleNamespace

from app.agent.core import handle_chat, create_meal_plan, create_workout_plan, _safe_parse_json


# ============================================================
# _safe_parse_json
# ============================================================

# Test Case ID: TC_FITNESS_Core_safe_parse_json_001
# Test Objective: Kiểm tra parse JSON hợp lệ trực tiếp
# Input: Chuỗi JSON hợp lệ với đầy đủ required_keys
# Expected Output: Dict chứa đúng giá trị
# Notes: Nhánh validate_json thành công ngay lần đầu
def test_safe_parse_json_valid_json():
    result = _safe_parse_json(
        '{"daily_meals": {}, "explanation": "ok", "disclaimer": "note"}',
        ["daily_meals", "explanation", "disclaimer"]
    )
    assert result is not None
    assert result["explanation"] == "ok"


# Test Case ID: TC_FITNESS_Core_safe_parse_json_002
# Test Objective: Kiểm tra parse JSON bọc trong markdown fences
# Input: Chuỗi ```json\n{...}\n```
# Expected Output: Dict parse thành công qua regex extract
# Notes: Nhánh regex fallback khi validate_json đầu tiên thất bại
def test_safe_parse_json_json_with_markdown_fences():
    text = '```json\n{"daily_meals": {}, "explanation": "ok", "disclaimer": "d"}\n```'
    result = _safe_parse_json(text, ["daily_meals", "explanation", "disclaimer"])
    assert result is not None
    assert result["daily_meals"] == {}


# Test Case ID: TC_FITNESS_Core_safe_parse_json_003
# Test Objective: Kiểm tra trả None khi text hoàn toàn không chứa JSON
# Input: Chuỗi văn bản thuần "no json here"
# Expected Output: None
# Notes: Cả validate_json lẫn regex đều thất bại
def test_safe_parse_json_completely_invalid():
    result = _safe_parse_json("no json here at all", ["key"])
    assert result is None


# Test Case ID: TC_FITNESS_Core_safe_parse_json_004
# Test Objective: Kiểm tra trả None khi JSON hợp lệ nhưng thiếu required keys
# Input: JSON có key "a" nhưng yêu cầu key "missing_key"
# Expected Output: None
# Notes: validate_json raise ValueError cho cả 2 lần thử
def test_safe_parse_json_valid_json_missing_keys():
    result = _safe_parse_json('{"a": 1}', ["missing_key"])
    assert result is None


# Test Case ID: TC_FITNESS_Core_safe_parse_json_005
# Test Objective: Kiểm tra parse JSON khi có text trước JSON object
# Input: "Here is the plan: {\"daily_meals\": ...}"
# Expected Output: Dict parse thành công qua regex extract
# Notes: Regex tìm thấy JSON substring sau text mô tả
def test_safe_parse_json_json_embedded_in_text():
    text = 'Here is the plan: {"daily_meals": {}, "explanation": "e", "disclaimer": "d"}'
    result = _safe_parse_json(text, ["daily_meals", "explanation", "disclaimer"])
    assert result is not None


# ============================================================
# handle_chat
# ============================================================

# Test Case ID: TC_FITNESS_Core_handle_chat_001
# Test Objective: Kiểm tra chat bình thường – tin nhắn an toàn
# Input: user_id="1", message="Xin chào"
# Expected Output: Dict type="message", intent="chat_qa"
# Notes: safety check safe → LLM trả câu trả lời
@patch("app.agent.core.update_session_memory")
@patch("app.agent.core.get_session_memory", return_value={"chat_history": []})
@patch("app.agent.core.get_user_state", return_value={})
@patch("app.agent.core.run_safety_check", return_value={"safe": True, "category": "general"})
def test_handle_chat_normal_message(mock_safety, mock_state, mock_session, mock_update):
    llm = MagicMock()
    llm.chat.return_value = "Xin chào! Tôi có thể giúp gì?"
    result = handle_chat(llm, "1", "Xin chào")
    assert result["type"] == "message"
    assert result["intent"] == "chat_qa"
    assert "Xin chào" in result["message"]
    mock_update.assert_called_once()


# Test Case ID: TC_FITNESS_Core_handle_chat_002
# Test Objective: Kiểm tra chat bị từ chối khi tin nhắn không an toàn
# Input: Tin nhắn bị safety check đánh dấu unsafe
# Expected Output: Dict type="message", intent="safety", decision="refuse"
# Notes: Nhánh safety["safe"] == False → trả message từ chối
@patch("app.agent.core.run_safety_check", return_value={"safe": False, "category": "medical"})
def test_handle_chat_unsafe_message_blocked(mock_safety):
    llm = MagicMock()
    result = handle_chat(llm, "1", "Tôi bị bệnh nên uống thuốc gì?")
    assert result["type"] == "message"
    assert result["intent"] == "safety"
    assert result["decision"] == "refuse"
    assert "y tế" in result["message"]
    llm.chat.assert_not_called()


# Test Case ID: TC_FITNESS_Core_handle_chat_003
# Test Objective: Kiểm tra xử lý khi LLM gặp lỗi trong quá trình chat
# Input: LLM.chat raise Exception
# Expected Output: Exception được propagate lên caller
# Notes: Hàm không bắt exception từ llm.chat → raise lên
@patch("app.agent.core.update_session_memory")
@patch("app.agent.core.get_session_memory", return_value={"chat_history": []})
@patch("app.agent.core.get_user_state", return_value={})
@patch("app.agent.core.run_safety_check", return_value={"safe": True})
def test_handle_chat_llm_error(mock_safety, mock_state, mock_session, mock_update):
    llm = MagicMock()
    llm.chat.side_effect = Exception("LLM unavailable")
    with pytest.raises(Exception, match="LLM unavailable"):
        handle_chat(llm, "1", "Test")


# Test Case ID: TC_FITNESS_Core_handle_chat_004
# Test Objective: Kiểm tra chat history được cập nhật đúng cách
# Input: Session đã có chat_history trước đó
# Expected Output: update_session_memory được gọi với history mới (bao gồm user + assistant)
# Notes: Kiểm tra session memory được cập nhật
@patch("app.agent.core.update_session_memory")
@patch("app.agent.core.get_session_memory", return_value={
    "chat_history": [{"role": "user", "content": "old msg"}]
})
@patch("app.agent.core.get_user_state", return_value={})
@patch("app.agent.core.run_safety_check", return_value={"safe": True})
def test_handle_chat_updates_session_history(mock_safety, mock_state, mock_session, mock_update):
    llm = MagicMock()
    llm.chat.return_value = "Đây là câu trả lời"
    handle_chat(llm, "1", "Câu hỏi mới")
    call_args = mock_update.call_args[0]
    assert call_args[0] == "1"
    session_data = call_args[1]
    assert len(session_data["chat_history"]) >= 3


# ============================================================
# create_meal_plan
# ============================================================

# Test Case ID: TC_FITNESS_Core_create_meal_plan_001
# Test Objective: Kiểm tra tạo meal plan thành công
# Input: Profile hợp lệ, LLM trả JSON meal plan đúng schema
# Expected Output: Dict type="plan_created" với plan data
# Notes: Happy path – mọi thứ hoạt động bình thường
@patch("app.agent.core.save_plan")
@patch("app.agent.core.get_retriever")
@patch("app.agent.core.get_user_state", return_value={"goals": "lose_weight"})
def test_create_meal_plan_valid_profile(mock_state, mock_retriever, mock_save):
    mock_retriever.return_value.retrieve.return_value = []
    llm = MagicMock()
    plan_data = {
        "daily_meals": {"day1": {}},
        "explanation": "Kế hoạch ăn uống",
        "disclaimer": "Đây chỉ là gợi ý"
    }
    llm.chat.return_value = json.dumps(plan_data)

    profile = SimpleNamespace(
        calorie_target=2000, gender="male",
        weight_kg=70, goal="lose_weight"
    )
    result = create_meal_plan(llm, "1", profile)
    assert result["type"] == "plan_created"
    assert result["plan"]["daily_meals"] == {"day1": {}}
    mock_save.assert_called_once()


# Test Case ID: TC_FITNESS_Core_create_meal_plan_002
# Test Objective: Kiểm tra khi LLM trả JSON không hợp lệ
# Input: LLM trả chuỗi văn bản không phải JSON
# Expected Output: Dict type="error"
# Notes: _safe_parse_json trả None → trả error response
@patch("app.agent.core.get_retriever")
@patch("app.agent.core.get_user_state", return_value={})
def test_create_meal_plan_invalid_llm_response(mock_state, mock_retriever):
    mock_retriever.return_value.retrieve.return_value = []
    llm = MagicMock()
    llm.chat.return_value = "I cannot create a plan right now"

    profile = SimpleNamespace(
        calorie_target=2000, gender="female",
        weight_kg=55, goal="maintain"
    )
    result = create_meal_plan(llm, "1", profile)
    assert result["type"] == "error"


# Test Case ID: TC_FITNESS_Core_create_meal_plan_003
# Test Objective: Kiểm tra RAG context được sử dụng khi retriever trả kết quả
# Input: Retriever trả documents, LLM trả plan hợp lệ
# Expected Output: LLM.chat được gọi với prompt chứa context từ RAG
# Notes: Kiểm tra tích hợp RAG
@patch("app.agent.core.save_plan")
@patch("app.agent.core.get_retriever")
@patch("app.agent.core.get_user_state", return_value={})
def test_create_meal_plan_rag_context_used(mock_state, mock_retriever, mock_save):
    mock_retriever.return_value.retrieve.return_value = [
        {"page_content": "Ăn nhiều rau xanh", "metadata": {"source": "nutrition_guide"}}
    ]
    llm = MagicMock()
    plan_data = {
        "daily_meals": {}, "explanation": "ok", "disclaimer": "d"
    }
    llm.chat.return_value = json.dumps(plan_data)

    profile = SimpleNamespace(
        calorie_target=1800, gender="female",
        weight_kg=50, goal="health"
    )
    result = create_meal_plan(llm, "1", profile)
    assert result["type"] == "plan_created"
    prompt_arg = llm.chat.call_args[0][1]
    assert "Ăn nhiều rau xanh" in prompt_arg


# Test Case ID: TC_FITNESS_Core_create_meal_plan_004
# Test Objective: Kiểm tra khi retriever raise exception → context rỗng, vẫn tiếp tục
# Input: Retriever raise Exception
# Expected Output: Vẫn tạo plan bình thường (context="")
# Notes: Nhánh except Exception trong RAG block
@patch("app.agent.core.save_plan")
@patch("app.agent.core.get_retriever")
@patch("app.agent.core.get_user_state", return_value={})
def test_create_meal_plan_retriever_error(mock_state, mock_retriever, mock_save):
    mock_retriever.return_value.retrieve.side_effect = Exception("Vector store error")
    llm = MagicMock()
    plan_data = {
        "daily_meals": {}, "explanation": "ok", "disclaimer": "d"
    }
    llm.chat.return_value = json.dumps(plan_data)

    profile = SimpleNamespace(
        calorie_target=2000, gender="male",
        weight_kg=70, goal="gain"
    )
    result = create_meal_plan(llm, "1", profile)
    assert result["type"] == "plan_created"


# ============================================================
# create_workout_plan
# ============================================================

# Test Case ID: TC_FITNESS_Core_create_workout_plan_001
# Test Objective: Kiểm tra tạo workout plan thành công
# Input: Profile hợp lệ, LLM trả JSON workout plan đúng schema
# Expected Output: Dict type="plan_created" với plan data
# Notes: Happy path
@patch("app.agent.core.save_plan")
@patch("app.agent.core.get_retriever")
@patch("app.agent.core.get_user_state", return_value={"goals": "build_muscle"})
def test_create_workout_plan_valid_profile(mock_state, mock_retriever, mock_save):
    mock_retriever.return_value.retrieve.return_value = []
    llm = MagicMock()
    plan_data = {
        "weekly_schedule": {"Monday": {}},
        "explanation": "Lịch tập",
        "disclaimer": "Tham khảo HLV"
    }
    llm.chat.return_value = json.dumps(plan_data)

    profile = SimpleNamespace(
        age=25, gender="male", height_cm=175, weight_kg=70,
        experience_level="intermediate", goal="build_muscle",
        available_days_per_week=5, session_duration_minutes=60,
        injuries=None, calorie_target=2500
    )
    result = create_workout_plan(llm, "1", profile)
    assert result["type"] == "plan_created"
    assert "weekly_schedule" in result["plan"]
    mock_save.assert_called_once()


# Test Case ID: TC_FITNESS_Core_create_workout_plan_002
# Test Objective: Kiểm tra khi LLM gặp lỗi
# Input: LLM trả response không parse được
# Expected Output: Dict type="error"
# Notes: _safe_parse_json trả None
@patch("app.agent.core.get_retriever")
@patch("app.agent.core.get_user_state", return_value={})
def test_create_workout_plan_llm_error(mock_state, mock_retriever):
    mock_retriever.return_value.retrieve.return_value = []
    llm = MagicMock()
    llm.chat.return_value = "Error generating plan"

    profile = SimpleNamespace(
        age=30, gender="female", height_cm=160, weight_kg=55,
        experience_level="beginner", goal=None,
        available_days_per_week=3, session_duration_minutes=45,
        injuries=[], calorie_target=None
    )
    result = create_workout_plan(llm, "1", profile)
    assert result["type"] == "error"


# Test Case ID: TC_FITNESS_Core_create_workout_plan_003
# Test Objective: Kiểm tra khi profile.goal là None → default "general_fitness"
# Input: Profile với goal=None
# Expected Output: Prompt gửi tới LLM chứa "general_fitness"
# Notes: Nhánh goal = profile.goal or "general_fitness"
@patch("app.agent.core.save_plan")
@patch("app.agent.core.get_retriever")
@patch("app.agent.core.get_user_state", return_value={})
def test_create_workout_plan_default_goal(mock_state, mock_retriever, mock_save):
    mock_retriever.return_value.retrieve.return_value = []
    llm = MagicMock()
    plan_data = {
        "weekly_schedule": {}, "explanation": "e", "disclaimer": "d"
    }
    llm.chat.return_value = json.dumps(plan_data)

    profile = SimpleNamespace(
        age=25, gender="male", height_cm=175, weight_kg=70,
        experience_level="beginner", goal=None,
        available_days_per_week=3, session_duration_minutes=30,
        injuries=None, calorie_target=None
    )
    result = create_workout_plan(llm, "1", profile)
    prompt_arg = llm.chat.call_args[0][1]
    assert "general_fitness" in prompt_arg


# Test Case ID: TC_FITNESS_Core_create_workout_plan_004
# Test Objective: Kiểm tra retriever exception → context rỗng
# Input: Retriever raise Exception
# Expected Output: Vẫn tiếp tục tạo plan
# Notes: Nhánh except Exception trong RAG block
@patch("app.agent.core.save_plan")
@patch("app.agent.core.get_retriever")
@patch("app.agent.core.get_user_state", return_value={})
def test_create_workout_plan_retriever_error(mock_state, mock_retriever, mock_save):
    mock_retriever.return_value.retrieve.side_effect = Exception("DB down")
    llm = MagicMock()
    plan_data = {
        "weekly_schedule": {}, "explanation": "e", "disclaimer": "d"
    }
    llm.chat.return_value = json.dumps(plan_data)

    profile = SimpleNamespace(
        age=25, gender="male", height_cm=175, weight_kg=70,
        experience_level="beginner", goal="fitness",
        available_days_per_week=3, session_duration_minutes=30,
        injuries=None, calorie_target=None
    )
    result = create_workout_plan(llm, "1", profile)
    assert result["type"] == "plan_created"
