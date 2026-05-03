"""
Unit tests cho auth_service.py - gọi Auth Service qua HTTP.
Sử dụng thư viện 'responses' để mock HTTP requests.
"""
import pytest
import responses
import requests

from app.external.auth_service import fetch_user_profile, AUTH_SERVICE_URL


# ============================================================
# FETCH USER PROFILE
# ============================================================

# Test Case ID: TC_FOOD_TestAuthServiceClient_fetch_user_profile_001
# Test Objective: Fetch profile thành công từ Auth Service
# Input: JWT token hợp lệ, Auth Service trả 200 + JSON
# Expected Output: dict chứa gender, dateOfBirth, etc.
# Notes: Mock HTTP GET thành công
@responses.activate
def test_fetch_user_profile_success():
    """Fetch user profile thành công."""
    responses.add(
        responses.GET,
        AUTH_SERVICE_URL,
        json={
            "gender": "male",
            "dateOfBirth": "1995-06-15",
            "email": "test@test.com",
        },
        status=200,
    )

    result = fetch_user_profile("Bearer test-token")

    assert result["gender"] == "male"
    assert result["dateOfBirth"] == "1995-06-15"

    # Kiểm tra Authorization header được gửi
    assert responses.calls[0].request.headers["Authorization"] == "Bearer test-token"


# Test Case ID: TC_FOOD_TestAuthServiceClient_fetch_user_profile_002
# Test Objective: Raise Exception khi Auth Service trả non-200
# Input: Auth Service trả 401
# Expected Output: Exception chứa status code
# Notes: Kiểm tra xử lý HTTP error
@responses.activate
def test_fetch_user_profile_http_error():
    """Raise Exception khi Auth Service trả non-200."""
    responses.add(
        responses.GET,
        AUTH_SERVICE_URL,
        json={"error": "Unauthorized"},
        status=401,
    )

    with pytest.raises(Exception, match="401"):
        fetch_user_profile("Bearer invalid-token")


# Test Case ID: TC_FOOD_TestAuthServiceClient_fetch_user_profile_003
# Test Objective: Raise Exception khi Auth Service timeout
# Input: Auth Service không phản hồi (connection error)
# Expected Output: ConnectionError
# Notes: Mock connection error
@responses.activate
def test_fetch_user_profile_timeout():
    """Raise ConnectionError khi Auth Service timeout."""
    responses.add(
        responses.GET,
        AUTH_SERVICE_URL,
        body=requests.exceptions.ConnectionError("Connection refused"),
    )

    with pytest.raises(requests.exceptions.ConnectionError):
        fetch_user_profile("Bearer test-token")


# Test Case ID: TC_FOOD_TestAuthServiceClient_fetch_user_profile_004
# Test Objective: Raise Exception khi server trả 500
# Input: Auth Service trả 500 Internal Server Error
# Expected Output: Exception chứa "500"
# Notes: Server-side error
@responses.activate
def test_fetch_user_profile_server_error():
    """Raise Exception khi Auth Service trả 500."""
    responses.add(
        responses.GET,
        AUTH_SERVICE_URL,
        json={"error": "Internal Server Error"},
        status=500,
    )

    with pytest.raises(Exception, match="500"):
        fetch_user_profile("Bearer test-token")


# Test Case ID: TC_FOOD_TestAuthServiceClient_fetch_user_profile_005
# Test Objective: Trả JSON response đúng format
# Input: Auth Service trả 200 với đầy đủ fields
# Expected Output: dict chứa tất cả fields
# Notes: Kiểm tra parsing JSON
@responses.activate
def test_fetch_user_profile_returns_json():
    """Trả JSON response đầy đủ."""
    expected_data = {
        "id": 1,
        "email": "test@test.com",
        "gender": "female",
        "dateOfBirth": "1998-03-20",
        "name": "Test User",
    }

    responses.add(
        responses.GET,
        AUTH_SERVICE_URL,
        json=expected_data,
        status=200,
    )

    result = fetch_user_profile("Bearer valid-token")

    assert result == expected_data
