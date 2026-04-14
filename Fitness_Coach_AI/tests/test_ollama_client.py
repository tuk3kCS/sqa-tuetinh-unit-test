"""
Unit tests cho module app.llm.ollama_client
Kiểm tra OllamaClient.chat và OllamaClient.moderate.
"""
import pytest
from unittest.mock import patch, MagicMock

from app.llm.ollama_client import OllamaClient


@pytest.fixture
def ollama_client():
    """Tạo OllamaClient với config mặc định."""
    with patch("app.llm.ollama_client.Config") as mock_config:
        mock_config.OLLAMA_MODEL = "llama3.1"
        mock_config.OLLAMA_BASE_URL = "http://localhost:11434"
        mock_config.DEFAULT_TEMPERATURE = 0.3
        client = OllamaClient()
        yield client


# ============================================================
# chat
# ============================================================

# Test Case ID: TC-FR-00-001llamaClient_chat_001
# Test Objective: Kiểm tra chat thành công
# Input: system_prompt và user_prompt hợp lệ
# Expected Output: Chuỗi response từ Ollama API
# Notes: Happy path – requests.post trả kết quả bình thường
@patch("app.llm.ollama_client.requests.post")
def test_ollama_chat_successful(mock_post, ollama_client):
    mock_response = MagicMock()
    mock_response.json.return_value = {
        "message": {"content": "Câu trả lời từ Llama"}
    }
    mock_response.raise_for_status.return_value = None
    mock_post.return_value = mock_response

    result = ollama_client.chat("System prompt", "User prompt")
    assert result == "Câu trả lời từ Llama"
    mock_post.assert_called_once()


# Test Case ID: TC-FR-00-001llamaClient_chat_002
# Test Objective: Kiểm tra chat khi kết nối thất bại
# Input: requests.post raise ConnectionError
# Expected Output: Exception được propagate lên
# Notes: Hàm gọi raise_for_status() → lỗi mạng sẽ raise
@patch("app.llm.ollama_client.requests.post")
def test_ollama_chat_connection_error(mock_post, ollama_client):
    mock_post.side_effect = ConnectionError("Cannot connect to Ollama")
    with pytest.raises(ConnectionError, match="Cannot connect"):
        ollama_client.chat("System", "User")


# Test Case ID: TC-FR-00-001llamaClient_chat_003
# Test Objective: Kiểm tra chat gửi đúng payload format
# Input: system_prompt="SP", user_prompt="UP", temperature=0.5
# Expected Output: POST request chứa đúng model, messages, stream=False
# Notes: Kiểm tra cấu trúc payload
@patch("app.llm.ollama_client.requests.post")
def test_ollama_chat_correct_payload(mock_post, ollama_client):
    mock_response = MagicMock()
    mock_response.json.return_value = {"message": {"content": "ok"}}
    mock_response.raise_for_status.return_value = None
    mock_post.return_value = mock_response

    ollama_client.chat("SP", "UP", temperature=0.5)
    call_kwargs = mock_post.call_args
    payload = call_kwargs.kwargs.get("json") or call_kwargs[1].get("json")
    assert payload["model"] == "llama3.1"
    assert payload["stream"] is False
    assert len(payload["messages"]) == 2
    assert payload["options"]["temperature"] == 0.5


# Test Case ID: TC-FR-00-001llamaClient_chat_004
# Test Objective: Kiểm tra timeout được truyền đúng
# Input: Bất kỳ prompt nào
# Expected Output: requests.post được gọi với timeout=120
# Notes: Kiểm tra tham số timeout
@patch("app.llm.ollama_client.requests.post")
def test_ollama_chat_timeout_set(mock_post, ollama_client):
    mock_response = MagicMock()
    mock_response.json.return_value = {"message": {"content": "ok"}}
    mock_response.raise_for_status.return_value = None
    mock_post.return_value = mock_response

    ollama_client.chat("S", "U")
    call_kwargs = mock_post.call_args
    timeout = call_kwargs.kwargs.get("timeout") or call_kwargs[1].get("timeout")
    assert timeout == 120


# Test Case ID: TC-FR-00-001llamaClient_chat_005
# Test Objective: Kiểm tra khi HTTP status lỗi (500)
# Input: Server trả 500
# Expected Output: Raise HTTPError qua raise_for_status()
# Notes: response.raise_for_status() raise HTTPError
@patch("app.llm.ollama_client.requests.post")
def test_ollama_chat_http_error(mock_post, ollama_client):
    import requests
    mock_response = MagicMock()
    mock_response.raise_for_status.side_effect = requests.HTTPError("500 Server Error")
    mock_post.return_value = mock_response

    with pytest.raises(requests.HTTPError):
        ollama_client.chat("S", "U")


# ============================================================
# moderate
# ============================================================

# Test Case ID: TC-FR-00-001llamaClient_moderate_001
# Test Objective: Kiểm tra moderate luôn trả None
# Input: Bất kỳ text nào
# Expected Output: None
# Notes: Ollama không hỗ trợ moderation → trả None
def test_ollama_moderate_always_returns_none(ollama_client):
    result = ollama_client.moderate("any text")
    assert result is None


# Test Case ID: TC-FR-00-001llamaClient_moderate_002
# Test Objective: Kiểm tra moderate với chuỗi rỗng vẫn trả None
# Input: Chuỗi rỗng
# Expected Output: None
# Notes: Hàm trả None bất kể input
def test_ollama_moderate_empty_string(ollama_client):
    result = ollama_client.moderate("")
    assert result is None


# Test Case ID: TC-FR-00-001llamaClient_moderate_003
# Test Objective: Kiểm tra moderate với nội dung có hại vẫn trả None
# Input: Nội dung nguy hiểm
# Expected Output: None (Ollama không có moderation)
# Notes: Khẳng định hành vi – không hỗ trợ moderation
def test_ollama_moderate_harmful_content_still_none(ollama_client):
    result = ollama_client.moderate("self harm content")
    assert result is None
