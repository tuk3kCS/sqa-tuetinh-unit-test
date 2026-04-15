"""
Unit tests cho module app.utils.schema_validator
Kiểm tra validate_with_schema: parse JSON và validate theo JSON Schema.
"""
import json
import io
import pytest

from app.utils.schema_validator import validate_with_schema


SAMPLE_SCHEMA = {
    "type": "object",
    "properties": {
        "safe": {"type": "boolean"},
        "category": {"type": "string", "enum": ["general", "medical", "emergency"]},
    },
    "required": ["safe", "category"],
}


# ============================================================
# validate_with_schema – JSON string input
# ============================================================

# Test Case ID: TC_FITNESS_SchemaValidator_validate_with_schema_001
# Test Objective: Kiểm tra JSON string hợp lệ khớp schema
# Input: JSON string khớp hoàn toàn với schema
# Expected Output: Dict chứa đúng giá trị
# Notes: Happy path – parse + validate thành công
def test_validate_with_schema_valid_json_string():
    raw = json.dumps({"safe": True, "category": "general"})
    result = validate_with_schema(raw, SAMPLE_SCHEMA)
    assert result["safe"] is True
    assert result["category"] == "general"


# Test Case ID: TC_FITNESS_SchemaValidator_validate_with_schema_002
# Test Objective: Kiểm tra JSON string hợp lệ nhưng vi phạm schema
# Input: category="invalid_value" (không nằm trong enum)
# Expected Output: Raise ValueError "Schema validation failed"
# Notes: Nhánh ValidationError
def test_validate_with_schema_schema_violation():
    raw = json.dumps({"safe": True, "category": "invalid_value"})
    with pytest.raises(ValueError, match="Schema validation failed"):
        validate_with_schema(raw, SAMPLE_SCHEMA)


# Test Case ID: TC_FITNESS_SchemaValidator_validate_with_schema_003
# Test Objective: Kiểm tra raise khi input không phải JSON
# Input: Chuỗi văn bản "not json"
# Expected Output: Raise ValueError "not valid JSON"
# Notes: Nhánh JSONDecodeError
def test_validate_with_schema_invalid_json():
    with pytest.raises(ValueError, match="not valid JSON"):
        validate_with_schema("not json at all", SAMPLE_SCHEMA)


# Test Case ID: TC_FITNESS_SchemaValidator_validate_with_schema_004
# Test Objective: Kiểm tra JSON thiếu required field "category"
# Input: JSON chỉ có "safe" nhưng schema yêu cầu cả "category"
# Expected Output: Raise ValueError "Schema validation failed"
# Notes: Required field missing → ValidationError
def test_validate_with_schema_missing_required():
    raw = json.dumps({"safe": True})
    with pytest.raises(ValueError, match="Schema validation failed"):
        validate_with_schema(raw, SAMPLE_SCHEMA)


# Test Case ID: TC_FITNESS_SchemaValidator_validate_with_schema_005
# Test Objective: Kiểm tra JSON với wrong type (safe phải là boolean)
# Input: safe="yes" (string thay vì boolean)
# Expected Output: Raise ValueError "Schema validation failed"
# Notes: Type mismatch → ValidationError
def test_validate_with_schema_wrong_type():
    raw = json.dumps({"safe": "yes", "category": "general"})
    with pytest.raises(ValueError, match="Schema validation failed"):
        validate_with_schema(raw, SAMPLE_SCHEMA)


# ============================================================
# validate_with_schema – File-like object input
# ============================================================

# Test Case ID: TC_FITNESS_SchemaValidator_validate_with_schema_006
# Test Objective: Kiểm tra input là file-like object (StringIO)
# Input: io.StringIO chứa JSON hợp lệ
# Expected Output: Dict chứa đúng giá trị
# Notes: Nhánh hasattr(text, "read")
def test_validate_with_schema_file_like_object():
    content = json.dumps({"safe": False, "category": "medical"})
    file_obj = io.StringIO(content)
    result = validate_with_schema(file_obj, SAMPLE_SCHEMA)
    assert result["safe"] is False
    assert result["category"] == "medical"


# ============================================================
# validate_with_schema – Dict input (fallback)
# ============================================================

# Test Case ID: TC_FITNESS_SchemaValidator_validate_with_schema_007
# Test Objective: Kiểm tra input là dict (đã parse sẵn – coerce to str)
# Input: Dict object → str(dict) → json.loads
# Expected Output: Raise ValueError (str(dict) tạo ra Python repr, không phải JSON)
# Notes: Nhánh fallback json.loads(str(text)) – Python dict repr dùng single quotes → lỗi parse
def test_validate_with_schema_dict_input_fails():
    with pytest.raises(ValueError, match="not valid JSON"):
        validate_with_schema({"safe": True, "category": "general"}, SAMPLE_SCHEMA)


# ============================================================
# validate_with_schema – Extra fields (schema không cấm)
# ============================================================

# Test Case ID: TC_FITNESS_SchemaValidator_validate_with_schema_008
# Test Objective: Kiểm tra JSON có extra fields (schema không có additionalProperties: false)
# Input: JSON với key phụ "extra_field"
# Expected Output: Dict chứa cả extra fields – validate vẫn pass
# Notes: Schema mặc định cho phép extra fields
def test_validate_with_schema_extra_fields_allowed():
    raw = json.dumps({"safe": True, "category": "general", "extra": "ok"})
    result = validate_with_schema(raw, SAMPLE_SCHEMA)
    assert result["extra"] == "ok"


# ============================================================
# validate_with_schema – Empty string
# ============================================================

# Test Case ID: TC_FITNESS_SchemaValidator_validate_with_schema_009
# Test Objective: Kiểm tra chuỗi rỗng → raise
# Input: ""
# Expected Output: Raise ValueError
# Notes: json.loads("") raise JSONDecodeError
def test_validate_with_schema_empty_string():
    with pytest.raises(ValueError, match="not valid JSON"):
        validate_with_schema("", SAMPLE_SCHEMA)


# ============================================================
# validate_with_schema – Schema phức tạp hơn
# ============================================================

PLANNER_SCHEMA = {
    "type": "object",
    "properties": {
        "intent": {"type": "string", "enum": ["meal", "workout", "general"]},
        "decision": {"type": "string", "enum": ["use_existing", "ask_create", "create_new", "answer"]},
        "reason": {"type": "string"},
        "confidence": {"type": "number", "minimum": 0.0, "maximum": 1.0},
    },
    "required": ["intent", "decision"],
}


# Test Case ID: TC_FITNESS_SchemaValidator_validate_with_schema_010
# Test Objective: Kiểm tra planner schema hợp lệ
# Input: JSON đúng planner schema
# Expected Output: Dict đầy đủ
# Notes: Schema phức tạp hơn với enum và number constraints
def test_validate_with_schema_planner_valid():
    raw = json.dumps({
        "intent": "meal", "decision": "create_new",
        "reason": "user request", "confidence": 0.85
    })
    result = validate_with_schema(raw, PLANNER_SCHEMA)
    assert result["intent"] == "meal"
    assert result["confidence"] == 0.85


# Test Case ID: TC_FITNESS_SchemaValidator_validate_with_schema_011
# Test Objective: Kiểm tra planner schema với confidence ngoài range
# Input: confidence=1.5 (> maximum 1.0)
# Expected Output: Raise ValueError "Schema validation failed"
# Notes: Nhánh number maximum constraint
def test_validate_with_schema_planner_confidence_out_of_range():
    raw = json.dumps({
        "intent": "meal", "decision": "answer", "confidence": 1.5
    })
    with pytest.raises(ValueError, match="Schema validation failed"):
        validate_with_schema(raw, PLANNER_SCHEMA)
