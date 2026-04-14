"""
Unit tests cho module app.services.agent_service
Kiểm tra AgentService: chat, get_meal_plan, create_meal_plan, get_workout_plan, create_workout_plan.
"""
import pytest
from unittest.mock import patch, MagicMock

from app.services.agent_service import AgentService


# ============================================================
# AgentService.chat
# ============================================================

# Test Case ID: TC-FR-00-001gentService_chat_001
# Test Objective: Kiểm tra chat delegate tới handle_chat đúng tham số
# Input: llm mock, user_id=1, message="Xin chào"
# Expected Output: Kết quả từ handle_chat
# Notes: AgentService.chat chỉ là wrapper → kiểm tra delegation
@patch("app.services.agent_service.handle_chat")
def test_agent_service_chat(mock_handle_chat):
    mock_handle_chat.return_value = {"type": "message", "message": "Hi"}
    llm = MagicMock()
    result = AgentService.chat(llm=llm, user_id=1, message="Xin chào")
    assert result["type"] == "message"
    mock_handle_chat.assert_called_once_with(llm=llm, user_id=1, message="Xin chào")


# Test Case ID: TC-FR-00-001gentService_chat_002
# Test Objective: Kiểm tra chat propagate exception từ handle_chat
# Input: handle_chat raise Exception
# Expected Output: Exception propagate lên
# Notes: AgentService không bắt exception
@patch("app.services.agent_service.handle_chat")
def test_agent_service_chat_error(mock_handle_chat):
    mock_handle_chat.side_effect = Exception("LLM down")
    with pytest.raises(Exception, match="LLM down"):
        AgentService.chat(llm=MagicMock(), user_id=1, message="test")


# ============================================================
# AgentService.get_meal_plan
# ============================================================

# Test Case ID: TC-FR-00-001gentService_get_meal_plan_001
# Test Objective: Kiểm tra lấy meal plan khi có plan trong state
# Input: user_id=1, state chứa meal_plan
# Expected Output: Dict type="message" với plan data
# Notes: Nhánh plan tồn tại
@patch("app.services.agent_service.get_user_state")
def test_agent_service_get_meal_plan_found(mock_state):
    mock_state.return_value = {
        "meal_plan": {
            "plan": {"day1": {}},
            "start_date": "2026-04-13",
            "end_date": "2026-04-19"
        }
    }
    result = AgentService.get_meal_plan(user_id=1)
    assert result["type"] == "message"
    assert result["plan"] == {"day1": {}}
    assert result["start_date"] == "2026-04-13"


# Test Case ID: TC-FR-00-001gentService_get_meal_plan_002
# Test Objective: Kiểm tra khi không có meal plan → no_plan
# Input: user_id=1, state rỗng
# Expected Output: Dict type="no_plan"
# Notes: Nhánh plan không tồn tại
@patch("app.services.agent_service.get_user_state")
def test_agent_service_get_meal_plan_not_found(mock_state):
    mock_state.return_value = {}
    result = AgentService.get_meal_plan(user_id=1)
    assert result["type"] == "no_plan"


# Test Case ID: TC-FR-00-001gentService_get_meal_plan_003
# Test Objective: Kiểm tra khi state có meal_plan nhưng giá trị None
# Input: state={"meal_plan": None}
# Expected Output: Dict type="no_plan"
# Notes: state.get("meal_plan") trả None → falsy
@patch("app.services.agent_service.get_user_state")
def test_agent_service_get_meal_plan_none_value(mock_state):
    mock_state.return_value = {"meal_plan": None}
    result = AgentService.get_meal_plan(user_id=1)
    assert result["type"] == "no_plan"


# ============================================================
# AgentService.create_meal_plan
# ============================================================

# Test Case ID: TC-FR-00-001gentService_create_meal_plan_001
# Test Objective: Kiểm tra tạo meal plan delegate tới core.create_meal_plan
# Input: llm, user_id, goal_input DTO
# Expected Output: Kết quả từ create_meal_plan
# Notes: Kiểm tra delegation
@patch("app.services.agent_service.create_meal_plan")
def test_agent_service_create_meal_plan(mock_create):
    mock_create.return_value = {"type": "plan_created"}
    llm = MagicMock()
    goal_input = MagicMock()
    result = AgentService.create_meal_plan(llm=llm, user_id=1, goal_input=goal_input)
    assert result["type"] == "plan_created"
    mock_create.assert_called_once_with(llm, 1, goal_input)


# ============================================================
# AgentService.get_workout_plan
# ============================================================

# Test Case ID: TC-FR-00-001gentService_get_workout_plan_001
# Test Objective: Kiểm tra lấy workout plan khi tồn tại
# Input: user_id=1, state chứa workout_plan
# Expected Output: Dict type="message" với plan data
# Notes: Happy path
@patch("app.services.agent_service.get_user_state")
def test_agent_service_get_workout_plan_found(mock_state):
    mock_state.return_value = {
        "workout_plan": {
            "plan": {"Monday": {}},
            "start_date": "2026-04-13",
            "end_date": "2026-04-19"
        }
    }
    result = AgentService.get_workout_plan(user_id=1)
    assert result["type"] == "message"
    assert result["plan"] == {"Monday": {}}


# Test Case ID: TC-FR-00-001gentService_get_workout_plan_002
# Test Objective: Kiểm tra khi không có workout plan → no_plan
# Input: user_id=1, state rỗng
# Expected Output: Dict type="no_plan"
# Notes: Nhánh plan không tồn tại
@patch("app.services.agent_service.get_user_state")
def test_agent_service_get_workout_plan_not_found(mock_state):
    mock_state.return_value = {}
    result = AgentService.get_workout_plan(user_id=1)
    assert result["type"] == "no_plan"


# ============================================================
# AgentService.create_workout_plan
# ============================================================

# Test Case ID: TC-FR-00-001gentService_create_workout_plan_001
# Test Objective: Kiểm tra tạo workout plan delegate tới core.create_workout_plan
# Input: llm, user_id, profile_input DTO
# Expected Output: Kết quả từ create_workout_plan
# Notes: Kiểm tra delegation
@patch("app.services.agent_service.create_workout_plan")
def test_agent_service_create_workout_plan(mock_create):
    mock_create.return_value = {"type": "plan_created"}
    llm = MagicMock()
    profile_input = MagicMock()
    result = AgentService.create_workout_plan(llm=llm, user_id=1, profile_input=profile_input)
    assert result["type"] == "plan_created"
    mock_create.assert_called_once_with(llm=llm, user_id=1, profile=profile_input)
