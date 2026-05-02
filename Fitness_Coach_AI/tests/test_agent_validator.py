"""
Unit tests cho module app.agent.validator
Kiểm tra hàm validate_json: phân tích chuỗi JSON và kiểm tra các key bắt buộc.
"""
import json
import pytest

from app.agent.validator import validate_json


# ============================================================
# validate_json – Happy path
# ============================================================

# Test Case ID: TC_AI_TestAgentValidator_validate_json_001
# Test Objective: Kiểm tra parse JSON hợp lệ với đầy đủ required keys
# Input: Chuỗi JSON hợp lệ, required_keys=["safe", "category"]
# Expected Output: Dict chứa đúng giá trị đã truyền vào
# Notes: Trường hợp cơ bản nhất – happy path
def test_validate_json_valid_with_all_required_keys():
    raw = json.dumps({"safe": True, "category": "general", "confidence": 0.9})
    result = validate_json(raw, required_keys=["safe", "category"])
    assert result["safe"] is True
    assert result["category"] == "general"
    assert result["confidence"] == 0.9


# Test Case ID: TC_AI_TestAgentValidator_validate_json_002
# Test Objective: Kiểm tra parse JSON hợp lệ khi không truyền required_keys
# Input: Chuỗi JSON hợp lệ, required_keys mặc định (None)
# Expected Output: Dict chứa đúng giá trị, không raise exception
# Notes: required_keys=None → bỏ qua kiểm tra key
def test_validate_json_valid_no_required_keys():
    raw = json.dumps({"any_key": 42})
    result = validate_json(raw)
    assert result == {"any_key": 42}


# Test Case ID: TC_AI_TestAgentValidator_validate_json_003
# Test Objective: Kiểm tra parse JSON hợp lệ với required_keys rỗng
# Input: Chuỗi JSON hợp lệ, required_keys=[]
# Expected Output: Dict đầy đủ, không raise exception
# Notes: Danh sách rỗng tương đương không yêu cầu key nào
def test_validate_json_valid_empty_required_keys():
    raw = json.dumps({"a": 1, "b": 2})
    result = validate_json(raw, required_keys=[])
    assert result == {"a": 1, "b": 2}


# ============================================================
# validate_json – Thiếu key
# ============================================================

# Test Case ID: TC_AI_TestAgentValidator_validate_json_004
# Test Objective: Kiểm tra raise ValueError khi thiếu key bắt buộc
# Input: JSON hợp lệ nhưng thiếu key "category"
# Expected Output: Raise ValueError với message chứa "Missing key"
# Notes: Kiểm tra nhánh thiếu key
def test_validate_json_missing_required_key():
    raw = json.dumps({"safe": True})
    with pytest.raises(ValueError, match="Missing key"):
        validate_json(raw, required_keys=["safe", "category"])


# Test Case ID: TC_AI_TestAgentValidator_validate_json_005
# Test Objective: Kiểm tra raise ValueError khi thiếu nhiều key (lỗi ở key đầu tiên)
# Input: JSON rỗng {}, required_keys=["intent", "decision"]
# Expected Output: Raise ValueError cho key đầu tiên thiếu
# Notes: Hàm raise ngay khi gặp key đầu tiên không tồn tại
def test_validate_json_missing_multiple_required_keys():
    raw = json.dumps({})
    with pytest.raises(ValueError, match="Missing key"):
        validate_json(raw, required_keys=["intent", "decision"])


# ============================================================
# validate_json – JSON không hợp lệ
# ============================================================

# Test Case ID: TC_AI_TestAgentValidator_validate_json_006
# Test Objective: Kiểm tra raise ValueError khi input không phải JSON
# Input: Chuỗi văn bản bình thường "not json at all"
# Expected Output: Raise ValueError với message "not valid JSON"
# Notes: Nhánh json.JSONDecodeError
def test_validate_json_invalid_json_string():
    with pytest.raises(ValueError, match="not valid JSON"):
        validate_json("not json at all")


# Test Case ID: TC_AI_TestAgentValidator_validate_json_007
# Test Objective: Kiểm tra raise ValueError khi input là chuỗi rỗng
# Input: Chuỗi rỗng ""
# Expected Output: Raise ValueError
# Notes: Chuỗi rỗng không phải JSON hợp lệ
def test_validate_json_empty_string():
    with pytest.raises(ValueError, match="not valid JSON"):
        validate_json("")


# Test Case ID: TC_AI_TestAgentValidator_validate_json_008
# Test Objective: Kiểm tra JSON không hoàn chỉnh (bị cắt giữa chừng)
# Input: Chuỗi '{"safe": true, "cat'
# Expected Output: Raise ValueError
# Notes: JSON bị cắt ngang → JSONDecodeError
def test_validate_json_truncated_json():
    with pytest.raises(ValueError, match="not valid JSON"):
        validate_json('{"safe": true, "cat')


# ============================================================
# validate_json – Edge cases
# ============================================================

# Test Case ID: TC_AI_TestAgentValidator_validate_json_009
# Test Objective: Kiểm tra JSON lồng nhau (nested) với required_keys ở cấp đầu
# Input: JSON có object lồng nhau, required_keys=["plan"]
# Expected Output: Dict đầy đủ, không raise exception
# Notes: required_keys chỉ kiểm tra key ở cấp đầu tiên
def test_validate_json_nested_json_with_required_top_key():
    raw = json.dumps({"plan": {"day1": {"meals": []}}, "explanation": "ok"})
    result = validate_json(raw, required_keys=["plan"])
    assert "plan" in result
    assert "day1" in result["plan"]


# Test Case ID: TC_AI_TestAgentValidator_validate_json_010
# Test Objective: Kiểm tra JSON array (không phải object) – không raise nếu không yêu cầu key
# Input: JSON array '[1, 2, 3]' không có required_keys
# Expected Output: List [1, 2, 3] – hàm trả về kết quả json.loads bình thường
# Notes: json.loads("[1,2,3]") trả về list, không phải dict
def test_validate_json_array_no_required_keys():
    raw = json.dumps([1, 2, 3])
    result = validate_json(raw)
    assert result == [1, 2, 3]


# Test Case ID: TC_AI_TestAgentValidator_validate_json_011
# Test Objective: Kiểm tra key có giá trị None vẫn tồn tại (không bị coi là thiếu)
# Input: JSON {"safe": None}, required_keys=["safe"]
# Expected Output: Dict {"safe": None} – key tồn tại dù value là None
# Notes: Hàm chỉ kiểm tra "k not in data", None vẫn tính là có key
def test_validate_json_key_with_none_value_still_present():
    raw = json.dumps({"safe": None})
    result = validate_json(raw, required_keys=["safe"])
    assert result == {"safe": None}
