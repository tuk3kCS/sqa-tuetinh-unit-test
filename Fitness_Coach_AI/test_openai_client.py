"""
Unit tests cho module app.llm.openai_client
Kiểm tra OpenAIClient.chat và OpenAIClient.moderate.
"""
import pytest
from unittest.mock import patch, MagicMock, PropertyMock

from app.llm.openai_client import OpenAIClient


@pytest.fixture
def openai_client():
    """Tạo OpenAIClient với mock OpenAI SDK."""
    with patch("app.llm.openai_client.OpenAI") as mock_openai:
        mock_sdk = MagicMock()
        mock_openai.return_value = mock_sdk
        client = OpenAIClient()
        client._mock_sdk = mock_sdk
        yield client


# ============================================================
# chat
# ============================================================

# Test Case ID: TC_FITNESS_OpenAIClient_chat_001
# Test Objective: Kiểm tra chat thành công – trả về output_text
# Input: system_prompt và user_prompt hợp lệ
# Expected Output: Chuỗi response từ API
# Notes: Happy path – responses.create trả kết quả bình thường
def test_openai_chat_successful(openai_client):
    mock_response = MagicMock()
    mock_response.output_text = "Đây là câu trả lời từ GPT"
    openai_client._mock_sdk.responses.create.return_value = mock_response

    result = openai_client.chat("System prompt", "User prompt")
    assert result == "Đây là câu trả lời từ GPT"
    openai_client._mock_sdk.responses.create.assert_called_once()


# Test Case ID: TC_FITNESS_OpenAIClient_chat_002
# Test Objective: Kiểm tra chat khi API raise exception
# Input: API raise Exception
# Expected Output: Exception được propagate lên caller
# Notes: Hàm không bắt exception → raise thẳng
def test_openai_chat_api_error(openai_client):
    openai_client._mock_sdk.responses.create.side_effect = Exception("API rate limit")
    with pytest.raises(Exception, match="API rate limit"):
        openai_client.chat("System", "User")


# Test Case ID: TC_FITNESS_OpenAIClient_chat_003
# Test Objective: Kiểm tra chat gửi đúng format messages
# Input: system_prompt="SP", user_prompt="UP"
# Expected Output: API nhận đúng model và input messages
# Notes: Kiểm tra đầu vào gửi tới API
def test_openai_chat_correct_payload(openai_client):
    mock_response = MagicMock()
    mock_response.output_text = "ok"
    openai_client._mock_sdk.responses.create.return_value = mock_response

    openai_client.chat("SP", "UP")
    call_kwargs = openai_client._mock_sdk.responses.create.call_args
    input_msgs = call_kwargs.kwargs.get("input") or call_kwargs[1].get("input")
    assert len(input_msgs) == 2
    assert input_msgs[0]["role"] == "system"
    assert input_msgs[1]["role"] == "user"


# ============================================================
# moderate
# ============================================================

# Test Case ID: TC_FITNESS_OpenAIClient_moderate_001
# Test Objective: Kiểm tra moderate trả kết quả flagged
# Input: Nội dung bị flagged bởi moderation API
# Expected Output: Dict chứa thông tin moderation (flagged result)
# Notes: Nhánh res.results[0] qua attribute access
def test_openai_moderate_flagged_content(openai_client):
    mock_result = MagicMock()
    mock_result.flagged = True
    mock_result.categories = {"self-harm": True}

    mock_response = MagicMock()
    mock_response.__getitem__ = MagicMock(side_effect=Exception("not subscriptable"))
    mock_response.results = [mock_result]

    openai_client._mock_sdk.moderations.create.return_value = mock_response

    result = openai_client.moderate("harmful content")
    assert result is mock_result


# Test Case ID: TC_FITNESS_OpenAIClient_moderate_002
# Test Objective: Kiểm tra moderate trả kết quả clean (không flagged)
# Input: Nội dung bình thường
# Expected Output: Dict kết quả moderation (không flagged)
# Notes: API trả kết quả bình thường qua dict-like access
def test_openai_moderate_clean_content(openai_client):
    mock_result = {"flagged": False, "categories": {}}
    mock_response = {"results": [mock_result]}

    openai_client._mock_sdk.moderations.create.return_value = mock_response

    result = openai_client.moderate("Tôi muốn ăn gì hôm nay?")
    assert result == mock_result


# Test Case ID: TC_FITNESS_OpenAIClient_moderate_003
# Test Objective: Kiểm tra moderate trả None khi API lỗi
# Input: API raise Exception
# Expected Output: None (exception bị bắt bên trong)
# Notes: Nhánh except Exception → return None
def test_openai_moderate_api_error(openai_client):
    openai_client._mock_sdk.moderations.create.side_effect = Exception("API error")
    result = openai_client.moderate("test")
    assert result is None


# Test Case ID: TC_FITNESS_OpenAIClient_moderate_004
# Test Objective: Kiểm tra moderate khi response format không mong đợi
# Input: API trả object không có "results" key hay .results attribute phù hợp
# Expected Output: None (exception bị bắt)
# Notes: Cả dict access lẫn attribute access đều thất bại → None
def test_openai_moderate_unexpected_response_format(openai_client):
    openai_client._mock_sdk.moderations.create.return_value = "unexpected"
    result = openai_client.moderate("test")
    assert result is None
