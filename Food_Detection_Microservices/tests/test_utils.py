"""
Unit tests cho các hàm tiện ích trong app/utils/utils.py.
Bao gồm: calculate_iou, apply_nms, deduplicate_by_label,
get_nutrition_by_name, calculate_total_nutrition,
crop_regions, draw_boxes, image_to_base64.
"""
import base64
import pytest
from PIL import Image

from app.utils.utils import (
    calculate_iou,
    apply_nms,
    deduplicate_by_label,
    get_nutrition_by_name,
    calculate_total_nutrition,
    crop_regions,
    draw_boxes,
    image_to_base64,
)


# ============================================================
# CALCULATE IOU
# ============================================================

# Test Case ID: TC_FOOD_TestUtils_calculate_iou_001
# Test Objective: Tính IoU cho 2 bounding boxes chồng lấp một phần
# Input: box1=[0,0,100,100], box2=[50,50,150,150]
# Expected Output: IoU > 0 và < 1
# Notes: Diện tích giao = 50*50 = 2500, diện tích hợp = 17500
def test_calculate_iou_overlapping():
    """IoU > 0 cho 2 boxes chồng lấp một phần."""
    box1 = [0, 0, 100, 100]
    box2 = [50, 50, 150, 150]
    iou = calculate_iou(box1, box2)

    # intersection = 50*50 = 2500
    # union = 10000 + 10000 - 2500 = 17500
    expected = 2500 / 17500
    assert abs(iou - expected) < 1e-6
    assert 0 < iou < 1


# Test Case ID: TC_FOOD_TestUtils_calculate_iou_002
# Test Objective: IoU = 0 cho 2 boxes không chồng lấp
# Input: box1=[0,0,50,50], box2=[100,100,200,200]
# Expected Output: IoU = 0.0
# Notes: Không có diện tích giao
def test_calculate_iou_non_overlapping():
    """IoU = 0 cho 2 boxes hoàn toàn tách biệt."""
    box1 = [0, 0, 50, 50]
    box2 = [100, 100, 200, 200]
    iou = calculate_iou(box1, box2)
    assert iou == 0.0


# Test Case ID: TC_FOOD_TestUtils_calculate_iou_003
# Test Objective: IoU = 1 cho 2 boxes giống hệt nhau
# Input: box1 = box2 = [10, 10, 100, 100]
# Expected Output: IoU = 1.0
# Notes: Giao = Hợp = diện tích 1 box
def test_calculate_iou_identical():
    """IoU = 1 cho 2 boxes giống hệt."""
    box = [10, 10, 100, 100]
    iou = calculate_iou(box, box)
    assert iou == 1.0


# Test Case ID: TC_FOOD_TestUtils_calculate_iou_004
# Test Objective: IoU = 0 khi diện tích = 0 (box suy biến)
# Input: box1 = box2 = [0, 0, 0, 0] (điểm)
# Expected Output: IoU = 0.0
# Notes: union_area = 0, tránh chia cho 0
def test_calculate_iou_zero_area():
    """IoU = 0 cho boxes diện tích bằng 0."""
    box = [0, 0, 0, 0]
    iou = calculate_iou(box, box)
    assert iou == 0.0


# ============================================================
# APPLY NMS
# ============================================================

# Test Case ID: TC_FOOD_TestUtils_apply_nms_001
# Test Objective: NMS gộp detections cùng class chồng lấp cao
# Input: 2 detections cùng class "Phở" chồng lấp > 0.5
# Expected Output: 1 detection sau NMS
# Notes: IoU > threshold → gộp thành 1 cluster
def test_apply_nms_overlapping():
    """NMS gộp detections chồng lấp cao cùng class."""
    detections = [
        {"class": "Phở", "confidence": 0.9, "bbox": [10, 10, 200, 200]},
        {"class": "Phở", "confidence": 0.7, "bbox": [15, 15, 205, 205]},
    ]
    result = apply_nms(detections, iou_threshold=0.5)
    assert len(result) == 1
    assert result[0]["confidence"] == 0.9  # giữ detection có confidence cao nhất


# Test Case ID: TC_FOOD_TestUtils_apply_nms_002
# Test Objective: NMS giữ nguyên detections không chồng lấp
# Input: 2 detections cùng class nhưng ở vị trí khác xa
# Expected Output: 2 detections (không gộp)
# Notes: IoU < threshold → giữ nguyên
def test_apply_nms_no_overlap():
    """NMS giữ nguyên detections không chồng lấp."""
    detections = [
        {"class": "Phở", "confidence": 0.9, "bbox": [0, 0, 100, 100]},
        {"class": "Phở", "confidence": 0.8, "bbox": [500, 500, 600, 600]},
    ]
    result = apply_nms(detections, iou_threshold=0.5)
    assert len(result) == 2


# Test Case ID: TC_FOOD_TestUtils_apply_nms_003
# Test Objective: NMS trả rỗng cho input rỗng
# Input: detections = []
# Expected Output: []
# Notes: Edge case
def test_apply_nms_empty():
    """NMS trả rỗng cho danh sách rỗng."""
    result = apply_nms([], iou_threshold=0.5)
    assert result == []


# Test Case ID: TC_FOOD_TestUtils_apply_nms_004
# Test Objective: NMS xử lý nhiều class khác nhau
# Input: detections có 2 class khác nhau, mỗi class 2 detections chồng lấp
# Expected Output: 2 detections (1 per class)
# Notes: NMS áp dụng riêng cho mỗi class
def test_apply_nms_multiple_classes():
    """NMS áp dụng riêng cho mỗi class."""
    detections = [
        {"class": "Phở", "confidence": 0.9, "bbox": [10, 10, 200, 200]},
        {"class": "Phở", "confidence": 0.7, "bbox": [15, 15, 205, 205]},
        {"class": "Cơm", "confidence": 0.8, "bbox": [300, 10, 500, 200]},
        {"class": "Cơm", "confidence": 0.6, "bbox": [305, 15, 505, 205]},
    ]
    result = apply_nms(detections, iou_threshold=0.5)
    assert len(result) == 2
    classes = {d["class"] for d in result}
    assert classes == {"Phở", "Cơm"}


# ============================================================
# DEDUPLICATE BY LABEL
# ============================================================

# Test Case ID: TC_FOOD_TestUtils_deduplicate_by_label_001
# Test Objective: Dedup giữ detection confidence cao nhất mỗi label
# Input: 3 detections cùng "Phở" với confidence khác nhau
# Expected Output: 1 detection, confidence cao nhất
# Notes: Chỉ giữ best per label
def test_deduplicate_by_label_duplicates():
    """Dedup giữ detection tốt nhất cho mỗi label."""
    detections = [
        {"class": "Phở", "confidence": 0.7, "bbox": [10, 10, 200, 200]},
        {"class": "Phở", "confidence": 0.9, "bbox": [50, 50, 250, 250]},
        {"class": "Phở", "confidence": 0.5, "bbox": [100, 100, 300, 300]},
    ]
    result = deduplicate_by_label(detections)
    assert len(result) == 1
    assert result[0]["confidence"] == 0.9


# Test Case ID: TC_FOOD_TestUtils_deduplicate_by_label_002
# Test Objective: Dedup giữ nguyên khi mỗi detection có label khác nhau
# Input: 3 detections với 3 labels khác nhau
# Expected Output: 3 detections
# Notes: Không trùng label → không loại bỏ
def test_deduplicate_by_label_unique():
    """Giữ nguyên khi tất cả labels khác nhau."""
    detections = [
        {"class": "Phở", "confidence": 0.9, "bbox": [10, 10, 200, 200]},
        {"class": "Cơm", "confidence": 0.8, "bbox": [300, 10, 500, 200]},
        {"class": "Bánh mì", "confidence": 0.7, "bbox": [600, 10, 800, 200]},
    ]
    result = deduplicate_by_label(detections)
    assert len(result) == 3


# Test Case ID: TC_FOOD_TestUtils_deduplicate_by_label_003
# Test Objective: Dedup trả rỗng cho input rỗng
# Input: detections = []
# Expected Output: []
# Notes: Edge case
def test_deduplicate_by_label_empty():
    """Trả rỗng cho danh sách rỗng."""
    result = deduplicate_by_label([])
    assert result == []


# ============================================================
# GET NUTRITION BY NAME
# ============================================================

# Test Case ID: TC_FOOD_TestUtils_get_nutrition_by_name_001
# Test Objective: Tìm thông tin dinh dưỡng cho món ăn đã biết
# Input: food_name="Phở"
# Expected Output: dict chứa name, serving_type, nutrition
# Notes: Dữ liệu từ FOOD_NUTRITION_DB
def test_get_nutrition_by_name_known(app):
    """Tìm thông tin dinh dưỡng cho Phở."""
    result = get_nutrition_by_name("Phở")
    assert result is not None
    assert result["name"] == "Phở"
    assert result["nutrition"]["Calories"] == 450


# Test Case ID: TC_FOOD_TestUtils_get_nutrition_by_name_002
# Test Objective: Trả None cho món ăn không có trong DB
# Input: food_name="AlienFood"
# Expected Output: None
# Notes: Không tìm thấy trong database
def test_get_nutrition_by_name_unknown(app):
    """Trả None cho món ăn không biết."""
    result = get_nutrition_by_name("AlienFood")
    assert result is None


# Test Case ID: TC_FOOD_TestUtils_get_nutrition_by_name_003
# Test Objective: Tìm kiếm không phân biệt hoa thường
# Input: food_name="phở" (lowercase)
# Expected Output: dict chứa name="Phở"
# Notes: Normalized comparison
def test_get_nutrition_by_name_case_insensitive(app):
    """Tìm kiếm case-insensitive."""
    result = get_nutrition_by_name("phở")
    assert result is not None
    assert result["name"] == "Phở"


# Test Case ID: TC_FOOD_TestUtils_get_nutrition_by_name_004
# Test Objective: Tìm kiếm với khoảng trắng thừa
# Input: food_name="  Phở  " (có space)
# Expected Output: dict chứa name="Phở"
# Notes: strip() spaces
def test_get_nutrition_by_name_whitespace(app):
    """Xử lý khoảng trắng thừa."""
    result = get_nutrition_by_name("  Phở  ")
    assert result is not None
    assert result["name"] == "Phở"


# ============================================================
# CALCULATE TOTAL NUTRITION
# ============================================================

# Test Case ID: TC_FOOD_TestUtils_calculate_total_nutrition_001
# Test Objective: Tính tổng dinh dưỡng cho nhiều detections
# Input: 2 detections: "Phở" + "Bánh mì"
# Expected Output: tổng Calories = 450 + 350 = 800
# Notes: Kiểm tra cộng dồn đúng
def test_calculate_total_nutrition_multiple(app):
    """Tính tổng dinh dưỡng cho nhiều món."""
    detections = [
        {"detected_class": "Phở"},
        {"detected_class": "Bánh mì"},
    ]
    result = calculate_total_nutrition(detections)

    assert result["items_count"] == 2
    assert result["total_nutrition"]["Calories"] == 450 + 350


# Test Case ID: TC_FOOD_TestUtils_calculate_total_nutrition_002
# Test Objective: Tính tổng dinh dưỡng cho danh sách rỗng
# Input: detections = []
# Expected Output: items_count=0, Calories=0
# Notes: Edge case
def test_calculate_total_nutrition_empty(app):
    """Tổng dinh dưỡng = 0 cho danh sách rỗng."""
    result = calculate_total_nutrition([])

    assert result["items_count"] == 0
    assert result["total_nutrition"]["Calories"] == 0


# Test Case ID: TC_FOOD_TestUtils_calculate_total_nutrition_003
# Test Objective: Bỏ qua món không có trong DB
# Input: 1 detection cho "UnknownFood"
# Expected Output: items_count=0
# Notes: Món không nhận diện bị bỏ qua
def test_calculate_total_nutrition_unknown(app):
    """Bỏ qua món không có trong nutrition DB."""
    detections = [{"detected_class": "UnknownFood"}]
    result = calculate_total_nutrition(detections)

    assert result["items_count"] == 0
    assert result["total_nutrition"]["Calories"] == 0


# ============================================================
# CROP REGIONS
# ============================================================

# Test Case ID: TC_FOOD_TestUtils_crop_regions_001
# Test Objective: Cắt vùng phát hiện từ ảnh
# Input: PIL Image 500x500, 2 detections
# Expected Output: 2 cropped images
# Notes: Kiểm tra số crops đúng
def test_crop_regions():
    """Cắt vùng phát hiện đúng số lượng."""
    image = Image.new("RGB", (500, 500), color=(255, 0, 0))
    detections = [
        {"bbox": [10, 10, 200, 200]},
        {"bbox": [250, 250, 400, 400]},
    ]
    crops = crop_regions(image, detections)

    assert len(crops) == 2
    assert isinstance(crops[0], Image.Image)
    assert isinstance(crops[1], Image.Image)


# ============================================================
# DRAW BOXES
# ============================================================

# Test Case ID: TC_FOOD_TestUtils_draw_boxes_001
# Test Objective: Vẽ bounding boxes lên ảnh
# Input: PIL Image + detections với class và confidence
# Expected Output: PIL Image (cùng kích thước)
# Notes: Kiểm tra ảnh đầu ra có kích thước đúng
def test_draw_boxes():
    """Vẽ bounding boxes lên ảnh, trả PIL Image."""
    image = Image.new("RGB", (500, 500), color=(255, 255, 255))
    detections = [
        {"class": "Phở", "confidence": 0.9, "bbox": [10, 20, 200, 200]},
    ]
    result = draw_boxes(image, detections)

    assert isinstance(result, Image.Image)
    assert result.size == (500, 500)


# ============================================================
# IMAGE TO BASE64
# ============================================================

# Test Case ID: TC_FOOD_TestUtils_image_to_base64_001
# Test Objective: Convert PIL Image sang base64 string
# Input: PIL Image 100x100
# Expected Output: base64 string hợp lệ, decode được
# Notes: Kiểm tra format JPEG
def test_image_to_base64():
    """Convert PIL Image sang base64 hợp lệ."""
    image = Image.new("RGB", (100, 100), color=(0, 0, 255))
    b64 = image_to_base64(image)

    assert isinstance(b64, str)
    assert len(b64) > 0
    # Verify base64 có thể decode lại
    decoded = base64.b64decode(b64)
    assert len(decoded) > 0


# Test Case ID: TC_FOOD_TestUtils_image_to_base64_002
# Test Objective: Convert ảnh với format PNG
# Input: PIL Image, format="PNG"
# Expected Output: base64 string hợp lệ
# Notes: Hỗ trợ nhiều format
def test_image_to_base64_png():
    """Convert ảnh sang base64 với format PNG."""
    image = Image.new("RGB", (50, 50), color=(0, 255, 0))
    b64 = image_to_base64(image, format="PNG")

    assert isinstance(b64, str)
    decoded = base64.b64decode(b64)
    assert len(decoded) > 0
