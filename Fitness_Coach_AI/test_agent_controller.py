"""
Unit tests cho module app.controllers.agent_controller
Kiểm tra tất cả endpoint qua Flask test client.
"""
import json
import pytest
from unittest.mock import patch, MagicMock


# ============================================================
# CHAT endpoint – POST /api/v3/agent/chat
# ============================================================

# Test Case ID: TC_FITNESS_AgentController_chat_001
# Test Objective: Kiểm tra chat thành công với message hợp lệ
# Input: POST {"message": "Xin chào"} với JWT hợp lệ
# Expected Output: 200, response chứa kết quả từ AgentService.chat
# Notes: mock_jwt_identity cung cấp userId=1
@patch("app.controllers.agent_controller.AgentService")
def test_chat_success(mock_service, client, mock_jwt_identity):
    mock_service.chat.return_value = {
        "type": "message", "message": "Chào bạn!", "intent": "chat_qa"
    }
    resp = client.post(
        "/api/v3/agent/chat",
        json={"message": "Xin chào"},
        headers={"Authorization": "Bearer fake-token"}
    )
    assert resp.status_code == 200
    data = resp.get_json()
    assert data["type"] == "message"


# Test Case ID: TC_FITNESS_AgentController_chat_002
# Test Objective: Kiểm tra chat thiếu message → 400
# Input: POST {} (không có key "message")
# Expected Output: 400, {"error": "Message is required"}
# Notes: Nhánh validation – thiếu trường bắt buộc
@patch("app.controllers.agent_controller.AgentService")
def test_chat_missing_message(mock_service, client, mock_jwt_identity):
    resp = client.post(
        "/api/v3/agent/chat",
        json={},
        headers={"Authorization": "Bearer fake-token"}
    )
    assert resp.status_code == 400
    assert "Message is required" in resp.get_json()["error"]


# Test Case ID: TC_FITNESS_AgentController_chat_003
# Test Objective: Kiểm tra chat với body None (no JSON) → 400
# Input: POST với content-type nhưng body rỗng
# Expected Output: 400
# Notes: request.get_json() trả None khi body rỗng
@patch("app.controllers.agent_controller.AgentService")
def test_chat_no_body(mock_service, client, mock_jwt_identity):
    resp = client.post(
        "/api/v3/agent/chat",
        headers={"Authorization": "Bearer fake-token"},
        content_type="application/json"
    )
    assert resp.status_code == 400


# Test Case ID: TC_FITNESS_AgentController_chat_004
# Test Objective: Kiểm tra chat khi user_id trong body khác JWT → 403
# Input: POST {"message": "hi", "user_id": 999} nhưng JWT userId=1
# Expected Output: 403, {"error": "Unauthorized"}
# Notes: Nhánh kiểm tra user_id mismatch
@patch("app.controllers.agent_controller.AgentService")
def test_chat_user_id_mismatch(mock_service, client, mock_jwt_identity):
    resp = client.post(
        "/api/v3/agent/chat",
        json={"message": "hi", "user_id": 999},
        headers={"Authorization": "Bearer fake-token"}
    )
    assert resp.status_code == 403


# ============================================================
# GET MEAL PLAN – GET /api/v3/agent/meal-plan
# ============================================================

# Test Case ID: TC_FITNESS_AgentController_get_meal_plan_001
# Test Objective: Kiểm tra lấy meal plan thành công
# Input: GET request với JWT hợp lệ
# Expected Output: 200, response từ AgentService.get_meal_plan
# Notes: Happy path
@patch("app.controllers.agent_controller.AgentService")
def test_get_meal_plan_success(mock_service, client, mock_jwt_identity):
    mock_service.get_meal_plan.return_value = {
        "type": "message", "plan": {"day1": {}}
    }
    resp = client.get(
        "/api/v3/agent/meal-plan",
        headers={"Authorization": "Bearer fake-token"}
    )
    assert resp.status_code == 200
    assert resp.get_json()["type"] == "message"


# Test Case ID: TC_FITNESS_AgentController_get_meal_plan_002
# Test Objective: Kiểm tra khi không có meal plan
# Input: GET request, AgentService trả no_plan
# Expected Output: 200, {"type": "no_plan"}
# Notes: Không có plan trong DB → trả no_plan
@patch("app.controllers.agent_controller.AgentService")
def test_get_meal_plan_no_plan(mock_service, client, mock_jwt_identity):
    mock_service.get_meal_plan.return_value = {
        "type": "no_plan", "message": "No meal plan found"
    }
    resp = client.get(
        "/api/v3/agent/meal-plan",
        headers={"Authorization": "Bearer fake-token"}
    )
    assert resp.status_code == 200
    assert resp.get_json()["type"] == "no_plan"


# ============================================================
# CREATE MEAL PLAN – POST /api/v3/agent/meal-plan
# ============================================================

# Test Case ID: TC_FITNESS_AgentController_create_meal_plan_001
# Test Objective: Kiểm tra tạo meal plan thành công
# Input: POST với JWT, UserProfileClient trả profile hợp lệ
# Expected Output: 200, plan_created response
# Notes: Happy path – mock UserProfileClient + AgentService
@patch("app.controllers.agent_controller.AgentService")
@patch("app.controllers.agent_controller.UserProfileClient")
def test_create_meal_plan_success(mock_profile_client, mock_service, client, mock_jwt_identity):
    mock_profile_client.get_ai_goal_input.return_value = {
        "data": {
            "calorie_target": 2000, "gender": "male",
            "weight_kg": 70, "goal": "lose_weight"
        }
    }
    mock_service.create_meal_plan.return_value = {
        "type": "plan_created", "message": "Created"
    }
    resp = client.post(
        "/api/v3/agent/meal-plan",
        headers={"Authorization": "Bearer fake-token"}
    )
    assert resp.status_code == 200


# Test Case ID: TC_FITNESS_AgentController_create_meal_plan_002
# Test Objective: Kiểm tra khi UserProfileClient raise Exception → 503
# Input: UserProfileClient.get_ai_goal_input raise Exception
# Expected Output: 503, {"error": "User profile service unavailable"}
# Notes: Nhánh except Exception → 503
@patch("app.controllers.agent_controller.UserProfileClient")
def test_create_meal_plan_profile_service_unavailable(mock_profile_client, client, mock_jwt_identity):
    mock_profile_client.get_ai_goal_input.side_effect = Exception("Service down")
    resp = client.post(
        "/api/v3/agent/meal-plan",
        headers={"Authorization": "Bearer fake-token"}
    )
    assert resp.status_code == 503


# Test Case ID: TC_FITNESS_AgentController_create_meal_plan_003
# Test Objective: Kiểm tra khi DTO validation thất bại → 400
# Input: UserProfileClient trả data thiếu required field
# Expected Output: 400
# Notes: Nhánh DTOValidationError
@patch("app.controllers.agent_controller.UserProfileClient")
def test_create_meal_plan_dto_validation_error(mock_profile_client, client, mock_jwt_identity):
    mock_profile_client.get_ai_goal_input.return_value = {"data": {"gender": "male"}}
    resp = client.post(
        "/api/v3/agent/meal-plan",
        headers={"Authorization": "Bearer fake-token"}
    )
    assert resp.status_code == 400


# ============================================================
# GET WORKOUT PLAN – GET /api/v3/agent/workout-plan
# ============================================================

# Test Case ID: TC_FITNESS_AgentController_get_workout_plan_001
# Test Objective: Kiểm tra lấy workout plan thành công
# Input: GET request với JWT hợp lệ
# Expected Output: 200, response từ AgentService
# Notes: Happy path
@patch("app.controllers.agent_controller.AgentService")
def test_get_workout_plan_success(mock_service, client, mock_jwt_identity):
    mock_service.get_workout_plan.return_value = {
        "type": "message", "plan": {"Monday": {}}
    }
    resp = client.get(
        "/api/v3/agent/workout-plan",
        headers={"Authorization": "Bearer fake-token"}
    )
    assert resp.status_code == 200


# ============================================================
# CREATE WORKOUT PLAN – POST /api/v3/agent/workout-plan
# ============================================================

# Test Case ID: TC_FITNESS_AgentController_create_workout_plan_001
# Test Objective: Kiểm tra tạo workout plan thành công
# Input: POST với JWT, UserProfileClient trả profile hợp lệ
# Expected Output: 200
# Notes: Happy path
@patch("app.controllers.agent_controller.AgentService")
@patch("app.controllers.agent_controller.UserProfileClient")
def test_create_workout_plan_success(mock_profile_client, mock_service, client, mock_jwt_identity):
    mock_profile_client.get_ai_profile_input.return_value = {
        "data": {
            "age": 25, "gender": "male", "height_cm": 175, "weight_kg": 70,
            "experience_level": "intermediate", "available_days_per_week": 5
        }
    }
    mock_service.create_workout_plan.return_value = {
        "type": "plan_created", "message": "Created"
    }
    resp = client.post(
        "/api/v3/agent/workout-plan",
        headers={"Authorization": "Bearer fake-token"}
    )
    assert resp.status_code == 200


# Test Case ID: TC_FITNESS_AgentController_create_workout_plan_002
# Test Objective: Kiểm tra khi profile service không khả dụng → 503
# Input: UserProfileClient raise Exception
# Expected Output: 503
# Notes: Nhánh except Exception
@patch("app.controllers.agent_controller.UserProfileClient")
def test_create_workout_plan_service_unavailable(mock_profile_client, client, mock_jwt_identity):
    mock_profile_client.get_ai_profile_input.side_effect = Exception("timeout")
    resp = client.post(
        "/api/v3/agent/workout-plan",
        headers={"Authorization": "Bearer fake-token"}
    )
    assert resp.status_code == 503


# ============================================================
# DB CRUD – workout-plan/db
# ============================================================

# Test Case ID: TC_FITNESS_AgentController_post_workout_plan_001
# Test Objective: Kiểm tra POST workout plan DB thành công
# Input: POST {"plan": {...}} qua /workout-plan/db
# Expected Output: 201, plan_created
# Notes: Happy path – WorkoutPlanService.create thành công
@patch("app.services.workout_plan_service.WorkoutPlanService.create")
def test_post_workout_plan_db_success(mock_create, client, mock_jwt_identity):
    mock_create.return_value = {"Monday": {"workout_type": "Strength"}}
    resp = client.post(
        "/api/v3/agent/workout-plan/db",
        json={"plan": {"Monday": {"workout_type": "Strength"}}},
        headers={"Authorization": "Bearer fake-token"}
    )
    assert resp.status_code == 201
    assert resp.get_json()["type"] == "plan_created"


# Test Case ID: TC_FITNESS_AgentController_post_workout_plan_002
# Test Objective: Kiểm tra POST workout plan DB thiếu plan → 400
# Input: POST {} (thiếu key "plan")
# Expected Output: 400, error message
# Notes: Nhánh validation body
@patch("app.services.workout_plan_service.WorkoutPlanService.create")
def test_post_workout_plan_db_missing_plan(mock_create, client, mock_jwt_identity):
    resp = client.post(
        "/api/v3/agent/workout-plan/db",
        json={},
        headers={"Authorization": "Bearer fake-token"}
    )
    assert resp.status_code == 400


# Test Case ID: TC_FITNESS_AgentController_post_workout_plan_003
# Test Objective: Kiểm tra POST workout plan DB khi đã tồn tại → 400
# Input: WorkoutPlanService.create raise ValueError
# Expected Output: 400
# Notes: Nhánh ValueError
@patch("app.services.workout_plan_service.WorkoutPlanService.create")
def test_post_workout_plan_db_already_exists(mock_create, client, mock_jwt_identity):
    mock_create.side_effect = ValueError("Workout plan already exists")
    resp = client.post(
        "/api/v3/agent/workout-plan/db",
        json={"plan": {"Monday": {}}},
        headers={"Authorization": "Bearer fake-token"}
    )
    assert resp.status_code == 400


# Test Case ID: TC_FITNESS_AgentController_put_workout_plan_001
# Test Objective: Kiểm tra PUT workout plan DB thành công
# Input: PUT {"plan": {...}}
# Expected Output: 200, plan_updated
# Notes: Happy path
@patch("app.services.workout_plan_service.WorkoutPlanService.update")
def test_put_workout_plan_db_success(mock_update, client, mock_jwt_identity):
    mock_update.return_value = {"Monday": {"workout_type": "Cardio"}}
    resp = client.put(
        "/api/v3/agent/workout-plan/db",
        json={"plan": {"Monday": {"workout_type": "Cardio"}}},
        headers={"Authorization": "Bearer fake-token"}
    )
    assert resp.status_code == 200
    assert resp.get_json()["type"] == "plan_updated"


# Test Case ID: TC_FITNESS_AgentController_put_workout_plan_002
# Test Objective: Kiểm tra PUT workout plan DB khi không tìm thấy → 404
# Input: WorkoutPlanService.update raise ValueError
# Expected Output: 404
# Notes: Nhánh ValueError
@patch("app.services.workout_plan_service.WorkoutPlanService.update")
def test_put_workout_plan_db_not_found(mock_update, client, mock_jwt_identity):
    mock_update.side_effect = ValueError("Workout plan not found")
    resp = client.put(
        "/api/v3/agent/workout-plan/db",
        json={"plan": {}},
        headers={"Authorization": "Bearer fake-token"}
    )
    assert resp.status_code == 404


# Test Case ID: TC_FITNESS_AgentController_delete_workout_plan_001
# Test Objective: Kiểm tra DELETE workout plan DB thành công
# Input: DELETE request
# Expected Output: 200, plan_deleted
# Notes: Happy path
@patch("app.services.workout_plan_service.WorkoutPlanService.delete")
def test_delete_workout_plan_db_success(mock_delete, client, mock_jwt_identity):
    mock_delete.return_value = None
    resp = client.delete(
        "/api/v3/agent/workout-plan/db",
        headers={"Authorization": "Bearer fake-token"}
    )
    assert resp.status_code == 200
    assert resp.get_json()["type"] == "plan_deleted"


# Test Case ID: TC_FITNESS_AgentController_delete_workout_plan_002
# Test Objective: Kiểm tra DELETE workout plan DB không tìm thấy → 404
# Input: WorkoutPlanService.delete raise ValueError
# Expected Output: 404
# Notes: Nhánh ValueError
@patch("app.services.workout_plan_service.WorkoutPlanService.delete")
def test_delete_workout_plan_db_not_found(mock_delete, client, mock_jwt_identity):
    mock_delete.side_effect = ValueError("Workout plan not found")
    resp = client.delete(
        "/api/v3/agent/workout-plan/db",
        headers={"Authorization": "Bearer fake-token"}
    )
    assert resp.status_code == 404


# ============================================================
# DB CRUD – meal-plan/db
# ============================================================

# Test Case ID: TC_FITNESS_AgentController_post_meal_plan_001
# Test Objective: Kiểm tra POST meal plan DB thành công
# Input: POST {"plan": {...}}
# Expected Output: 201, plan_created
# Notes: Happy path
@patch("app.services.meal_plan_service.MealPlanService.create")
def test_post_meal_plan_db_success(mock_create, client, mock_jwt_identity):
    mock_create.return_value = {"day1": {"breakfast": {}}}
    resp = client.post(
        "/api/v3/agent/meal-plan/db",
        json={"plan": {"day1": {"breakfast": {}}}},
        headers={"Authorization": "Bearer fake-token"}
    )
    assert resp.status_code == 201
    assert resp.get_json()["type"] == "plan_created"


# Test Case ID: TC_FITNESS_AgentController_put_meal_plan_001
# Test Objective: Kiểm tra PUT meal plan DB thành công
# Input: PUT {"plan": {...}}
# Expected Output: 200, plan_updated
# Notes: Happy path
@patch("app.services.meal_plan_service.MealPlanService.update")
def test_put_meal_plan_db_success(mock_update, client, mock_jwt_identity):
    mock_update.return_value = {"day1": {}}
    resp = client.put(
        "/api/v3/agent/meal-plan/db",
        json={"plan": {"day1": {}}},
        headers={"Authorization": "Bearer fake-token"}
    )
    assert resp.status_code == 200
    assert resp.get_json()["type"] == "plan_updated"


# Test Case ID: TC_FITNESS_AgentController_put_meal_plan_002
# Test Objective: Kiểm tra PUT meal plan DB không tìm thấy → 404
# Input: MealPlanService.update raise ValueError
# Expected Output: 404
# Notes: Nhánh ValueError
@patch("app.services.meal_plan_service.MealPlanService.update")
def test_put_meal_plan_db_not_found(mock_update, client, mock_jwt_identity):
    mock_update.side_effect = ValueError("Meal plan not found")
    resp = client.put(
        "/api/v3/agent/meal-plan/db",
        json={"plan": {}},
        headers={"Authorization": "Bearer fake-token"}
    )
    assert resp.status_code == 404


# Test Case ID: TC_FITNESS_AgentController_delete_meal_plan_001
# Test Objective: Kiểm tra DELETE meal plan DB thành công
# Input: DELETE request
# Expected Output: 200, plan_deleted
# Notes: Happy path
@patch("app.services.meal_plan_service.MealPlanService.delete")
def test_delete_meal_plan_db_success(mock_delete, client, mock_jwt_identity):
    resp = client.delete(
        "/api/v3/agent/meal-plan/db",
        headers={"Authorization": "Bearer fake-token"}
    )
    assert resp.status_code == 200
    assert resp.get_json()["type"] == "plan_deleted"


# Test Case ID: TC_FITNESS_AgentController_delete_meal_plan_002
# Test Objective: Kiểm tra DELETE meal plan DB không tìm thấy → 404
# Input: MealPlanService.delete raise ValueError
# Expected Output: 404
# Notes: Nhánh ValueError
@patch("app.services.meal_plan_service.MealPlanService.delete")
def test_delete_meal_plan_db_not_found(mock_delete, client, mock_jwt_identity):
    mock_delete.side_effect = ValueError("Meal plan not found")
    resp = client.delete(
        "/api/v3/agent/meal-plan/db",
        headers={"Authorization": "Bearer fake-token"}
    )
    assert resp.status_code == 404
