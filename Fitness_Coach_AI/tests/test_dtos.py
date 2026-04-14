"""
Unit tests cho module app.dto.dtos và app.dto.ai_profile_input_dto
Kiểm tra DTOs: MealPlanProfileDTO, WorkoutPlanProfileDTO, AIProfileInputDTO,
và các helper _pick, _to_int, _to_float, _unwrap_payload.
"""
import pytest

from app.dto.dtos import (
    MealPlanProfileDTO,
    WorkoutPlanProfileDTO,
    DTOValidationError,
    _pick,
    _to_int,
    _to_float,
    _unwrap_payload,
)
from app.dto.ai_profile_input_dto import AIProfileInputDTO


# ============================================================
# _pick helper
# ============================================================

# Test Case ID: TC-FR-00-001s_pick_001
# Test Objective: Kiểm tra _pick trả giá trị đúng khi key đầu tiên tồn tại
# Input: dict {"calorie_target": 2000}, keys=("calorie_target", "calorieTarget")
# Expected Output: 2000
# Notes: Key đầu tiên match → trả ngay
def test_pick_first_key_found():
    result = _pick({"calorie_target": 2000}, "calorie_target", "calorieTarget")
    assert result == 2000


# Test Case ID: TC-FR-00-001s_pick_002
# Test Objective: Kiểm tra _pick dùng key thay thế (camelCase)
# Input: dict {"calorieTarget": 1800}, keys=("calorie_target", "calorieTarget")
# Expected Output: 1800
# Notes: Key đầu tiên không tồn tại, key thứ hai match
def test_pick_fallback_key():
    result = _pick({"calorieTarget": 1800}, "calorie_target", "calorieTarget")
    assert result == 1800


# Test Case ID: TC-FR-00-001s_pick_003
# Test Objective: Kiểm tra _pick trả default khi không tìm thấy key
# Input: dict rỗng, default="fallback"
# Expected Output: "fallback"
# Notes: Không có key nào match → trả default
def test_pick_default_value():
    result = _pick({}, "missing_key", default="fallback")
    assert result == "fallback"


# Test Case ID: TC-FR-00-001s_pick_004
# Test Objective: Kiểm tra _pick raise DTOValidationError khi required=True và không tìm thấy
# Input: dict rỗng, required=True
# Expected Output: Raise DTOValidationError
# Notes: Nhánh required check
def test_pick_required_missing():
    with pytest.raises(DTOValidationError, match="Missing required field"):
        _pick({}, "field_a", "field_b", required=True)


# Test Case ID: TC-FR-00-001s_pick_005
# Test Objective: Kiểm tra _pick bỏ qua key có giá trị None
# Input: dict {"key": None}, required=True
# Expected Output: Raise DTOValidationError (None bị bỏ qua)
# Notes: d[k] is not None check → None không tính
def test_pick_skips_none_value():
    with pytest.raises(DTOValidationError):
        _pick({"key": None}, "key", required=True)


# ============================================================
# _to_int helper
# ============================================================

# Test Case ID: TC-FR-00-001s_to_int_001
# Test Objective: Kiểm tra chuyển đổi string sang int
# Input: "42"
# Expected Output: 42
# Notes: int("42") thành công
def test_to_int_from_string():
    assert _to_int("42", "field") == 42


# Test Case ID: TC-FR-00-001s_to_int_002
# Test Objective: Kiểm tra chuyển đổi float sang int (truncate)
# Input: 3.7
# Expected Output: 3
# Notes: int(3.7) = 3 (truncate)
def test_to_int_from_float():
    assert _to_int(3.7, "field") == 3


# Test Case ID: TC-FR-00-001s_to_int_003
# Test Objective: Kiểm tra raise DTOValidationError khi giá trị không convert được
# Input: "not_a_number"
# Expected Output: Raise DTOValidationError
# Notes: int("not_a_number") raise ValueError → bắt và raise DTO error
def test_to_int_invalid():
    with pytest.raises(DTOValidationError, match="must be int"):
        _to_int("not_a_number", "age")


# ============================================================
# _to_float helper
# ============================================================

# Test Case ID: TC-FR-00-001s_to_float_001
# Test Objective: Kiểm tra chuyển đổi string sang float
# Input: "70.5"
# Expected Output: 70.5
# Notes: float("70.5") thành công
def test_to_float_from_string():
    assert _to_float("70.5", "weight") == 70.5


# Test Case ID: TC-FR-00-001s_to_float_002
# Test Objective: Kiểm tra chuyển đổi int sang float
# Input: 70
# Expected Output: 70.0
# Notes: float(70) = 70.0
def test_to_float_from_int():
    assert _to_float(70, "weight") == 70.0


# Test Case ID: TC-FR-00-001s_to_float_003
# Test Objective: Kiểm tra raise DTOValidationError khi giá trị không convert được
# Input: "abc"
# Expected Output: Raise DTOValidationError
# Notes: float("abc") raise ValueError
def test_to_float_invalid():
    with pytest.raises(DTOValidationError, match="must be number"):
        _to_float("abc", "weight_kg")


# ============================================================
# _unwrap_payload helper
# ============================================================

# Test Case ID: TC-FR-00-001s_unwrap_payload_001
# Test Objective: Kiểm tra unwrap khi payload có key "data"
# Input: {"data": {"age": 25}}
# Expected Output: {"age": 25}
# Notes: Nhánh "data" key detected → bóc lớp ngoài
def test_unwrap_payload_with_data_key():
    result = _unwrap_payload({"data": {"age": 25}})
    assert result == {"age": 25}


# Test Case ID: TC-FR-00-001s_unwrap_payload_002
# Test Objective: Kiểm tra unwrap khi payload có key "result"
# Input: {"result": {"name": "test"}}
# Expected Output: {"name": "test"}
# Notes: Nhánh "result" key detected
def test_unwrap_payload_with_result_key():
    result = _unwrap_payload({"result": {"name": "test"}})
    assert result == {"name": "test"}


# Test Case ID: TC-FR-00-001s_unwrap_payload_003
# Test Objective: Kiểm tra unwrap khi payload không có key đặc biệt
# Input: {"age": 25, "gender": "male"}
# Expected Output: Trả nguyên dict (không unwrap)
# Notes: Không có "data", "result", "payload" key → trả nguyên
def test_unwrap_payload_no_special_key():
    data = {"age": 25, "gender": "male"}
    result = _unwrap_payload(data)
    assert result == data


# Test Case ID: TC-FR-00-001s_unwrap_payload_004
# Test Objective: Kiểm tra raise DTOValidationError khi payload không phải dict
# Input: "not a dict"
# Expected Output: Raise DTOValidationError
# Notes: isinstance check thất bại
def test_unwrap_payload_not_dict():
    with pytest.raises(DTOValidationError, match="must be object"):
        _unwrap_payload("not a dict")


# Test Case ID: TC-FR-00-001s_unwrap_payload_005
# Test Objective: Kiểm tra khi "data" key tồn tại nhưng value không phải dict
# Input: {"data": "string_value", "age": 25}
# Expected Output: Trả nguyên dict gốc (data value không phải dict → bỏ qua)
# Notes: Nhánh isinstance(data[k], dict) thất bại cho "data" key
def test_unwrap_payload_data_not_dict():
    data = {"data": "string_value", "age": 25}
    result = _unwrap_payload(data)
    assert result == data


# ============================================================
# MealPlanProfileDTO.from_dict
# ============================================================

# Test Case ID: TC-FR-00-001s_MealPlanProfileDTO_from_dict_001
# Test Objective: Kiểm tra tạo DTO thành công với data hợp lệ
# Input: Dict đầy đủ required fields
# Expected Output: DTO instance với đúng giá trị
# Notes: Happy path
def test_meal_plan_dto_valid():
    data = {"calorie_target": 2000, "gender": "male", "weight_kg": 70.5, "goal": "lose_weight"}
    dto = MealPlanProfileDTO.from_dict(data)
    assert dto.calorie_target == 2000
    assert dto.gender == "male"
    assert dto.weight_kg == 70.5
    assert dto.goal == "lose_weight"


# Test Case ID: TC-FR-00-001s_MealPlanProfileDTO_from_dict_002
# Test Objective: Kiểm tra tạo DTO với data bọc trong "data" key
# Input: {"data": {"calorie_target": 1800, ...}}
# Expected Output: DTO tạo thành công (unwrap trước)
# Notes: _unwrap_payload bóc key "data"
def test_meal_plan_dto_wrapped_in_data():
    raw = {"data": {"calorie_target": 1800, "gender": "female", "weight_kg": 55, "goal": "health"}}
    dto = MealPlanProfileDTO.from_dict(raw)
    assert dto.calorie_target == 1800


# Test Case ID: TC-FR-00-001s_MealPlanProfileDTO_from_dict_003
# Test Objective: Kiểm tra raise khi thiếu required field
# Input: Dict thiếu "calorie_target"
# Expected Output: Raise DTOValidationError
# Notes: _pick raise khi required=True và key không tìm thấy
def test_meal_plan_dto_missing_required_field():
    with pytest.raises(DTOValidationError):
        MealPlanProfileDTO.from_dict({"gender": "male", "weight_kg": 70, "goal": "fit"})


# Test Case ID: TC-FR-00-001s_MealPlanProfileDTO_from_dict_004
# Test Objective: Kiểm tra raise khi calorie_target không convert được sang int
# Input: calorie_target="abc"
# Expected Output: Raise DTOValidationError "must be int"
# Notes: _to_int thất bại
def test_meal_plan_dto_wrong_type():
    with pytest.raises(DTOValidationError, match="must be int"):
        MealPlanProfileDTO.from_dict({
            "calorie_target": "abc", "gender": "male",
            "weight_kg": 70, "goal": "fit"
        })


# Test Case ID: TC-FR-00-001s_MealPlanProfileDTO_from_dict_005
# Test Objective: Kiểm tra camelCase key support
# Input: {"calorieTarget": 2200, "gender": "male", "weightKg": 80, "goal": "gain"}
# Expected Output: DTO tạo thành công
# Notes: _pick hỗ trợ cả snake_case và camelCase
def test_meal_plan_dto_camel_case_keys():
    data = {"calorieTarget": 2200, "gender": "male", "weightKg": 80, "goal": "gain"}
    dto = MealPlanProfileDTO.from_dict(data)
    assert dto.calorie_target == 2200
    assert dto.weight_kg == 80.0


# ============================================================
# WorkoutPlanProfileDTO.from_dict
# ============================================================

# Test Case ID: TC-FR-00-001s_WorkoutPlanProfileDTO_from_dict_001
# Test Objective: Kiểm tra tạo DTO thành công với tất cả fields
# Input: Dict đầy đủ required + optional fields
# Expected Output: DTO instance với đúng giá trị
# Notes: Happy path – tất cả fields
def test_workout_plan_dto_valid_all_fields():
    data = {
        "age": 25, "gender": "male", "height_cm": 175, "weight_kg": 70,
        "experience_level": "intermediate", "available_days_per_week": 5,
        "goal": "build_muscle", "session_duration_minutes": 60,
        "injuries": ["knee pain"], "calorie_target": 2500
    }
    dto = WorkoutPlanProfileDTO.from_dict(data)
    assert dto.age == 25
    assert dto.goal == "build_muscle"
    assert dto.injuries == ["knee pain"]
    assert dto.calorie_target == 2500


# Test Case ID: TC-FR-00-001s_WorkoutPlanProfileDTO_from_dict_002
# Test Objective: Kiểm tra tạo DTO chỉ với required fields (optional = None)
# Input: Dict chỉ có required fields
# Expected Output: DTO với optional fields = None
# Notes: Optional fields có default None
def test_workout_plan_dto_only_required():
    data = {
        "age": 30, "gender": "female", "height_cm": 160, "weight_kg": 55,
        "experience_level": "beginner", "available_days_per_week": 3
    }
    dto = WorkoutPlanProfileDTO.from_dict(data)
    assert dto.goal is None
    assert dto.session_duration_minutes is None
    assert dto.injuries is None
    assert dto.calorie_target is None


# Test Case ID: TC-FR-00-001s_WorkoutPlanProfileDTO_from_dict_003
# Test Objective: Kiểm tra raise khi thiếu required field "age"
# Input: Dict thiếu "age"
# Expected Output: Raise DTOValidationError
# Notes: age là required field
def test_workout_plan_dto_missing_required():
    with pytest.raises(DTOValidationError):
        WorkoutPlanProfileDTO.from_dict({
            "gender": "male", "height_cm": 175, "weight_kg": 70,
            "experience_level": "intermediate", "available_days_per_week": 5
        })


# Test Case ID: TC-FR-00-001s_WorkoutPlanProfileDTO_from_dict_004
# Test Objective: Kiểm tra injuries là string đơn lẻ → chuyển thành list
# Input: injuries="back pain" (string, không phải list)
# Expected Output: injuries=["back pain"]
# Notes: Nhánh else trong injuries processing
def test_workout_plan_dto_injuries_single_string():
    data = {
        "age": 25, "gender": "male", "height_cm": 175, "weight_kg": 70,
        "experience_level": "beginner", "available_days_per_week": 3,
        "injuries": "back pain"
    }
    dto = WorkoutPlanProfileDTO.from_dict(data)
    assert dto.injuries == ["back pain"]


# Test Case ID: TC-FR-00-001s_WorkoutPlanProfileDTO_from_dict_005
# Test Objective: Kiểm tra camelCase keys
# Input: {"heightCm": 170, "weightKg": 65, ...}
# Expected Output: DTO tạo thành công với đúng giá trị
# Notes: _pick hỗ trợ camelCase
def test_workout_plan_dto_camel_case():
    data = {
        "age": 28, "gender": "female", "heightCm": 170, "weightKg": 65,
        "experienceLevel": "intermediate", "availableDaysPerWeek": 4
    }
    dto = WorkoutPlanProfileDTO.from_dict(data)
    assert dto.height_cm == 170.0
    assert dto.weight_kg == 65.0


# ============================================================
# AIProfileInputDTO.from_request
# ============================================================

# Test Case ID: TC-FR-00-001s_AIProfileInputDTO_from_request_001
# Test Objective: Kiểm tra tạo DTO thành công với payload đầy đủ
# Input: Dict đầy đủ tất cả required fields
# Expected Output: DTO instance
# Notes: Happy path
def test_ai_profile_input_dto_valid():
    payload = {
        "age": 27, "gender": "male", "height_cm": 175, "weight_kg": 70.5,
        "experience_level": "intermediate", "goal": "fitness",
        "available_days_per_week": 5, "session_duration_minutes": 60,
        "injuries": ["knee"], "calorie_target": 2200
    }
    dto = AIProfileInputDTO.from_request(payload)
    assert dto.age == 27
    assert dto.weight_kg == 70.5
    assert dto.injuries == ["knee"]


# Test Case ID: TC-FR-00-001s_AIProfileInputDTO_from_request_002
# Test Objective: Kiểm tra raise khi payload rỗng (None)
# Input: None
# Expected Output: Raise ValueError "Empty payload"
# Notes: Nhánh not payload
def test_ai_profile_input_dto_empty_payload():
    with pytest.raises(ValueError, match="Empty payload"):
        AIProfileInputDTO.from_request(None)


# Test Case ID: TC-FR-00-001s_AIProfileInputDTO_from_request_003
# Test Objective: Kiểm tra raise khi thiếu required field
# Input: Dict thiếu "goal"
# Expected Output: Raise ValueError "Missing field: goal"
# Notes: Nhánh field not in payload
def test_ai_profile_input_dto_missing_field():
    payload = {
        "age": 27, "gender": "male", "height_cm": 175, "weight_kg": 70.5,
        "experience_level": "intermediate",
        "available_days_per_week": 5, "session_duration_minutes": 60,
        "injuries": [], "calorie_target": 2200
    }
    with pytest.raises(ValueError, match="Missing field: goal"):
        AIProfileInputDTO.from_request(payload)


# Test Case ID: TC-FR-00-001s_AIProfileInputDTO_from_request_004
# Test Objective: Kiểm tra default injuries khi không có key
# Input: payload đầy đủ nhưng không có "injuries" key
# Expected Output: Raise ValueError (injuries là required field)
# Notes: "injuries" nằm trong required_fields
def test_ai_profile_input_dto_missing_injuries():
    payload = {
        "age": 27, "gender": "male", "height_cm": 175, "weight_kg": 70,
        "experience_level": "beginner", "goal": "fit",
        "available_days_per_week": 3, "session_duration_minutes": 30,
        "calorie_target": 2000
    }
    with pytest.raises(ValueError, match="Missing field: injuries"):
        AIProfileInputDTO.from_request(payload)


# Test Case ID: TC-FR-00-001s_AIProfileInputDTO_from_request_005
# Test Objective: Kiểm tra type conversion (string numbers)
# Input: age="25" (string), weight_kg="70.5" (string)
# Expected Output: DTO với age=25 (int), weight_kg=70.5 (float)
# Notes: int() và float() chấp nhận string input
def test_ai_profile_input_dto_string_numbers():
    payload = {
        "age": "25", "gender": "male", "height_cm": "175", "weight_kg": "70.5",
        "experience_level": "intermediate", "goal": "fitness",
        "available_days_per_week": "5", "session_duration_minutes": "60",
        "injuries": [], "calorie_target": "2200"
    }
    dto = AIProfileInputDTO.from_request(payload)
    assert dto.age == 25
    assert dto.weight_kg == 70.5
