"""
Unit tests cho module app.clients.user_profile_client
Kiểm tra UserProfileClient.get_ai_profile_input và get_ai_goal_input.
Sử dụng thư viện 'responses' để mock HTTP calls.
"""
import pytest
import responses
import requests

from app.clients.user_profile_client import UserProfileClient


BASE_URL = UserProfileClient.BASE_URL


# ============================================================
# get_ai_profile_input
# ============================================================

# Test Case ID: TC-FR-00-001serProfileClient_get_ai_profile_input_001
# Test Objective: Kiểm tra lấy profile thành công
# Input: API trả 200 với JSON profile data
# Expected Output: Dict profile data
# Notes: Happy path – HTTP 200
@responses.activate
def test_get_ai_profile_input_success():
    profile_data = {
        "data": {
            "age": 25, "gender": "male", "height_cm": 175,
            "weight_kg": 70, "experience_level": "intermediate"
        }
    }
    responses.add(
        responses.GET,
        f"{BASE_URL}/ai/profile-input",
        json=profile_data,
        status=200,
    )
    result = UserProfileClient.get_ai_profile_input("valid-token")
    assert result == profile_data
    assert responses.calls[0].request.headers["Authorization"] == "Bearer valid-token"


# Test Case ID: TC-FR-00-001serProfileClient_get_ai_profile_input_002
# Test Objective: Kiểm tra khi API trả HTTP 401
# Input: API trả 401 Unauthorized
# Expected Output: Raise HTTPError
# Notes: raise_for_status() raise cho 4xx status
@responses.activate
def test_get_ai_profile_input_http_401():
    responses.add(
        responses.GET,
        f"{BASE_URL}/ai/profile-input",
        json={"error": "Unauthorized"},
        status=401,
    )
    with pytest.raises(requests.HTTPError):
        UserProfileClient.get_ai_profile_input("bad-token")


# Test Case ID: TC-FR-00-001serProfileClient_get_ai_profile_input_003
# Test Objective: Kiểm tra khi API trả HTTP 500
# Input: API trả 500 Internal Server Error
# Expected Output: Raise HTTPError
# Notes: raise_for_status() raise cho 5xx status
@responses.activate
def test_get_ai_profile_input_http_500():
    responses.add(
        responses.GET,
        f"{BASE_URL}/ai/profile-input",
        json={"error": "Internal Server Error"},
        status=500,
    )
    with pytest.raises(requests.HTTPError):
        UserProfileClient.get_ai_profile_input("token")


# Test Case ID: TC-FR-00-001serProfileClient_get_ai_profile_input_004
# Test Objective: Kiểm tra khi timeout
# Input: API không phản hồi trong thời gian timeout
# Expected Output: Raise ConnectionError
# Notes: responses library raise ConnectionError cho timeout simulation
@responses.activate
def test_get_ai_profile_input_timeout():
    responses.add(
        responses.GET,
        f"{BASE_URL}/ai/profile-input",
        body=requests.ConnectionError("Connection timeout"),
    )
    with pytest.raises(requests.ConnectionError):
        UserProfileClient.get_ai_profile_input("token")


# Test Case ID: TC-FR-00-001serProfileClient_get_ai_profile_input_005
# Test Objective: Kiểm tra Authorization header được gửi đúng format
# Input: access_token="my-jwt-token"
# Expected Output: Header Authorization: "Bearer my-jwt-token"
# Notes: Kiểm tra header format
@responses.activate
def test_get_ai_profile_input_auth_header():
    responses.add(
        responses.GET,
        f"{BASE_URL}/ai/profile-input",
        json={},
        status=200,
    )
    UserProfileClient.get_ai_profile_input("my-jwt-token")
    assert responses.calls[0].request.headers["Authorization"] == "Bearer my-jwt-token"


# ============================================================
# get_ai_goal_input
# ============================================================

# Test Case ID: TC-FR-00-001serProfileClient_get_ai_goal_input_001
# Test Objective: Kiểm tra lấy goal input thành công
# Input: API trả 200 với goal data
# Expected Output: Dict goal data
# Notes: Happy path
@responses.activate
def test_get_ai_goal_input_success():
    goal_data = {
        "data": {
            "calorie_target": 2000, "gender": "male",
            "weight_kg": 70, "goal": "lose_weight"
        }
    }
    responses.add(
        responses.GET,
        f"{BASE_URL}/ai/goal-input",
        json=goal_data,
        status=200,
    )
    result = UserProfileClient.get_ai_goal_input("valid-token")
    assert result == goal_data


# Test Case ID: TC-FR-00-001serProfileClient_get_ai_goal_input_002
# Test Objective: Kiểm tra khi API trả lỗi 404
# Input: API trả 404
# Expected Output: Raise HTTPError
# Notes: Endpoint không tồn tại hoặc user không có goal
@responses.activate
def test_get_ai_goal_input_http_404():
    responses.add(
        responses.GET,
        f"{BASE_URL}/ai/goal-input",
        json={"error": "Not Found"},
        status=404,
    )
    with pytest.raises(requests.HTTPError):
        UserProfileClient.get_ai_goal_input("token")


# Test Case ID: TC-FR-00-001serProfileClient_get_ai_goal_input_003
# Test Objective: Kiểm tra khi connection error
# Input: API không thể kết nối
# Expected Output: Raise ConnectionError
# Notes: Mạng không khả dụng
@responses.activate
def test_get_ai_goal_input_connection_error():
    responses.add(
        responses.GET,
        f"{BASE_URL}/ai/goal-input",
        body=requests.ConnectionError("Service unavailable"),
    )
    with pytest.raises(requests.ConnectionError):
        UserProfileClient.get_ai_goal_input("token")
