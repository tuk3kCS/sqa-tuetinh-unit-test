"""
Unit tests cho module app.llm.factory
Kiểm tra hàm get_llm: tạo đúng instance LLM dựa trên config.
"""
import pytest
from unittest.mock import patch, MagicMock

import app.llm.factory as factory_module


# ============================================================
# get_llm
# ============================================================

@pytest.fixture(autouse=True)
def reset_singleton():
    """Reset singleton trước mỗi test để đảm bảo độc lập."""
    factory_module._LLM_INSTANCE = None
    yield
    factory_module._LLM_INSTANCE = None


# Test Case ID: TC-FR-11-001actory_get_llm_001
# Test Objective: Kiểm tra tạo OpenAIClient khi provider là "openai"
# Input: Config.LLM_PROVIDER = "openai"
# Expected Output: Instance của OpenAIClient
# Notes: Nhánh provider == "openai"
@patch("app.llm.factory.OpenAIClient")
@patch("app.llm.factory.Config")
def test_get_llm_openai_provider(mock_config, mock_openai_cls):
    mock_config.LLM_PROVIDER = "openai"
    mock_instance = MagicMock()
    mock_openai_cls.return_value = mock_instance

    result = factory_module.get_llm()
    assert result is mock_instance
    mock_openai_cls.assert_called_once()


# Test Case ID: TC-FR-11-001actory_get_llm_002
# Test Objective: Kiểm tra tạo OllamaClient khi provider là "ollama"
# Input: Config.LLM_PROVIDER = "ollama"
# Expected Output: Instance của OllamaClient
# Notes: Nhánh provider == "ollama"
@patch("app.llm.factory.OllamaClient")
@patch("app.llm.factory.Config")
def test_get_llm_ollama_provider(mock_config, mock_ollama_cls):
    mock_config.LLM_PROVIDER = "ollama"
    mock_instance = MagicMock()
    mock_ollama_cls.return_value = mock_instance

    result = factory_module.get_llm()
    assert result is mock_instance
    mock_ollama_cls.assert_called_once()


# Test Case ID: TC-FR-11-001actory_get_llm_003
# Test Objective: Kiểm tra raise ValueError khi provider không hợp lệ
# Input: Config.LLM_PROVIDER = "invalid_provider"
# Expected Output: Raise ValueError với message chứa "Unsupported"
# Notes: Nhánh else → raise ValueError
@patch("app.llm.factory.Config")
def test_get_llm_invalid_provider(mock_config):
    mock_config.LLM_PROVIDER = "invalid_provider"

    with pytest.raises(ValueError, match="Unsupported"):
        factory_module.get_llm()


# Test Case ID: TC-FR-11-001actory_get_llm_004
# Test Objective: Kiểm tra singleton – gọi get_llm lần 2 trả cùng instance
# Input: Gọi get_llm() 2 lần liên tiếp
# Expected Output: Cùng một instance, constructor chỉ gọi 1 lần
# Notes: Nhánh _LLM_INSTANCE is not None → return ngay
@patch("app.llm.factory.OpenAIClient")
@patch("app.llm.factory.Config")
def test_get_llm_singleton_returns_same_instance(mock_config, mock_openai_cls):
    mock_config.LLM_PROVIDER = "openai"
    mock_instance = MagicMock()
    mock_openai_cls.return_value = mock_instance

    result1 = factory_module.get_llm()
    result2 = factory_module.get_llm()
    assert result1 is result2
    mock_openai_cls.assert_called_once()


# Test Case ID: TC-FR-11-001actory_get_llm_005
# Test Objective: Kiểm tra provider viết hoa (case insensitive)
# Input: Config.LLM_PROVIDER = "OpenAI"
# Expected Output: Tạo OpenAIClient (lower() trước khi so sánh)
# Notes: provider.lower() xử lý case
@patch("app.llm.factory.OpenAIClient")
@patch("app.llm.factory.Config")
def test_get_llm_case_insensitive_provider(mock_config, mock_openai_cls):
    mock_config.LLM_PROVIDER = "OpenAI"
    mock_openai_cls.return_value = MagicMock()

    result = factory_module.get_llm()
    assert result is not None
    mock_openai_cls.assert_called_once()
