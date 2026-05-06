"""
Unit tests cho NutritionService - phân tích dinh dưỡng từ kết quả phát hiện thực phẩm.
"""
import pytest
from app.services.nutrition_service import NutritionService


# Test Case ID: TC_FOOD_TestNutritionService_analyze_001
# Test Objective: Phân tích dinh dưỡng cho 1 món ăn đã biết
# Input: detections chứa 1 món "Phở"
# Expected Output: items_count=1, total_nutrition có Calories=450
# Notes: Dữ liệu dinh dưỡng lấy từ FOOD_NUTRITION_DB
def test_nutrition_service_analyze_single_known_food(app):
    """Phân tích 1 món ăn đã biết trong cơ sở dữ liệu."""
    detections = [{"detected_class": "Phở", "confidence": 0.9, "bbox": [0, 0, 100, 100]}]
    result = NutritionService.analyze(detections)

    assert result["items_count"] == 1
    assert result["total_nutrition"]["Calories"] == 450
    assert result["total_nutrition"]["Protein"] == 20
    assert len(result["individual_items"]) == 1
    assert result["individual_items"][0]["name"] == "Phở"


# Test Case ID: TC_FOOD_TestNutritionService_analyze_002
# Test Objective: Phân tích dinh dưỡng cho nhiều món ăn
# Input: detections chứa "Phở" và "Bánh mì"
# Expected Output: items_count=2, tổng calo = 450 + 350 = 800
# Notes: Kiểm tra cộng dồn dinh dưỡng đúng
def test_nutrition_service_analyze_multiple_foods(app):
    """Phân tích nhiều món ăn - tổng dinh dưỡng cộng dồn."""
    detections = [
        {"detected_class": "Phở", "confidence": 0.9, "bbox": [0, 0, 100, 100]},
        {"detected_class": "Bánh mì", "confidence": 0.8, "bbox": [100, 0, 200, 100]},
    ]
    result = NutritionService.analyze(detections)

    assert result["items_count"] == 2
    assert result["total_nutrition"]["Calories"] == 450 + 350  # Phở + Bánh mì
    assert result["total_nutrition"]["Fat"] == 15 + 12
    assert result["total_nutrition"]["Protein"] == 20 + 12


# Test Case ID: TC_FOOD_TestNutritionService_analyze_003
# Test Objective: Phân tích món ăn không có trong cơ sở dữ liệu
# Input: detections chứa "UnknownFood" không có trong DB
# Expected Output: items_count=0, tổng dinh dưỡng = 0
# Notes: Món ăn không nhận diện được bị bỏ qua
def test_nutrition_service_analyze_unknown_food(app):
    """Món ăn không nhận diện được - bỏ qua, trả 0."""
    detections = [
        {"detected_class": "UnknownFood", "confidence": 0.5, "bbox": [0, 0, 50, 50]}
    ]
    result = NutritionService.analyze(detections)

    assert result["items_count"] == 0
    assert result["total_nutrition"]["Calories"] == 0
    assert result["individual_items"] == []


# Test Case ID: TC_FOOD_TestNutritionService_analyze_004
# Test Objective: Phân tích danh sách detections rỗng
# Input: detections = []
# Expected Output: items_count=0, tổng dinh dưỡng = 0
# Notes: Edge case - không có detections
def test_nutrition_service_analyze_empty_list(app):
    """Danh sách detections rỗng - trả kết quả zero."""
    result = NutritionService.analyze([])

    assert result["items_count"] == 0
    assert result["total_nutrition"]["Calories"] == 0
    assert result["total_nutrition"]["Fat"] == 0
    assert result["individual_items"] == []


# Test Case ID: TC_FOOD_TestNutritionService_analyze_005
# Test Objective: Phân tích hỗn hợp món biết và không biết
# Input: detections chứa "Phở" (biết) và "Alien" (không biết)
# Expected Output: items_count=1, chỉ tính dinh dưỡng của Phở
# Notes: Món không biết bị bỏ qua, món biết vẫn tính đúng
def test_nutrition_service_analyze_mixed_known_unknown(app):
    """Hỗn hợp món biết và không biết - chỉ tính món biết."""
    detections = [
        {"detected_class": "Phở", "confidence": 0.9, "bbox": [0, 0, 100, 100]},
        {"detected_class": "AlienFood", "confidence": 0.7, "bbox": [200, 0, 300, 100]},
    ]
    result = NutritionService.analyze(detections)

    assert result["items_count"] == 1
    assert result["total_nutrition"]["Calories"] == 450
