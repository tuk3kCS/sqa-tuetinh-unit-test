"""
Unit tests cho FoodDetectionService - xử lý phát hiện thực phẩm bằng ONNX model.
Bao gồm: preprocess, decode, detect_foods, post_process, upload_*.
Tất cả ONNX InferenceSession và Cloudinary đều được mock.
"""
import pytest
import numpy as np
from unittest.mock import patch, MagicMock
from PIL import Image

from app.config import Config


# --------------- Helpers --------------- #

def _make_mock_ort():
    """Tạo mock cho onnxruntime InferenceSession."""
    mock_session = MagicMock()
    input_mock = MagicMock()
    input_mock.name = "images"
    output_mock = MagicMock()
    output_mock.name = "output0"
    mock_session.get_inputs.return_value = [input_mock]
    mock_session.get_outputs.return_value = [output_mock]
    return mock_session


def _create_service():
    """Tạo FoodDetectionService với ONNX session đã mock."""
    with patch("app.services_AI.food_detection_services.ort.InferenceSession") as mock_cls:
        mock_cls.return_value = _make_mock_ort()
        from app.services_AI.food_detection_services import FoodDetectionService
        service = FoodDetectionService()
    return service


def _create_test_image(width=640, height=480):
    """Tạo ảnh PIL test đơn giản."""
    return Image.new("RGB", (width, height), color=(255, 0, 0))


# ============================================================
# PREPROCESS
# ============================================================

# Test Case ID: TC_FOOD_TestFoodDetectionService_preprocess_001
# Test Objective: Tiền xử lý ảnh PIL thành công
# Input: PIL Image 640x480
# Expected Output: numpy array shape (1, 3, 640, 640), original_size = (640, 480)
# Notes: Kiểm tra shape và dtype của output
def test_food_detection_preprocess_valid_image(app):
    """Tiền xử lý ảnh PIL đúng kích thước và format."""
    service = _create_service()
    image = _create_test_image(640, 480)

    result, original_size = service.preprocess(image)

    assert result.shape == (1, 3, 640, 640)
    assert result.dtype == np.float32
    assert original_size == (640, 480)
    # Giá trị pixel nằm trong [0, 1]
    assert result.max() <= 1.0
    assert result.min() >= 0.0


# Test Case ID: TC_FOOD_TestFoodDetectionService_preprocess_002
# Test Objective: Raise TypeError khi input không phải PIL Image
# Input: numpy array thay vì PIL Image
# Expected Output: TypeError
# Notes: Kiểm tra type checking
def test_food_detection_preprocess_invalid_input(app):
    """Raise TypeError cho input không phải PIL Image."""
    service = _create_service()

    with pytest.raises(TypeError, match="PIL.Image.Image"):
        service.preprocess(np.zeros((100, 100, 3)))


# Test Case ID: TC_FOOD_TestFoodDetectionService_preprocess_003
# Test Objective: Tiền xử lý ảnh kích thước khác nhau
# Input: PIL Image 1920x1080 (lớn hơn target)
# Expected Output: numpy array vẫn resize thành (1, 3, 640, 640)
# Notes: Ảnh lớn vẫn được resize đúng
def test_food_detection_preprocess_large_image(app):
    """Xử lý đúng ảnh kích thước lớn."""
    service = _create_service()
    image = _create_test_image(1920, 1080)

    result, original_size = service.preprocess(image)

    assert result.shape == (1, 3, 640, 640)
    assert original_size == (1920, 1080)


# ============================================================
# DECODE
# ============================================================

# Test Case ID: TC_FOOD_TestFoodDetectionService_decode_001
# Test Objective: Decode detections trên confidence threshold
# Input: ONNX output với 1 detection confidence=0.8 (> 0.25)
# Expected Output: 1 detection với class name đúng
# Notes: conf_threshold = Config.CONFIDENCE = 0.25
def test_food_detection_decode_above_threshold(app):
    """Decode detection trên ngưỡng confidence."""
    service = _create_service()

    # Giả lập output ONNX: [x1, y1, x2, y2, conf, cls_id]
    preds = np.array([[[100, 50, 300, 250, 0.8, 0]]])  # cls_id=0 → "Bánh canh"
    original_size = (640, 480)

    detections = service.decode(preds, original_size)

    assert len(detections) == 1
    assert detections[0]["class"] == "Bánh canh"  # CLASS_NAMES[0]
    assert detections[0]["confidence"] == 0.8
    assert len(detections[0]["bbox"]) == 4


# Test Case ID: TC_FOOD_TestFoodDetectionService_decode_002
# Test Objective: Bỏ qua detections dưới confidence threshold
# Input: ONNX output với confidence=0.1 (< 0.25)
# Expected Output: danh sách rỗng
# Notes: Detection dưới ngưỡng bị loại bỏ
def test_food_detection_decode_below_threshold(app):
    """Bỏ qua detection dưới ngưỡng confidence."""
    service = _create_service()

    preds = np.array([[[100, 50, 300, 250, 0.1, 0]]])
    original_size = (640, 480)

    detections = service.decode(preds, original_size)
    assert len(detections) == 0


# Test Case ID: TC_FOOD_TestFoodDetectionService_decode_003
# Test Objective: Lọc bỏ class "Mì" trong decode
# Input: ONNX output với cls_id=41 (Mì)
# Expected Output: danh sách rỗng
# Notes: "Mì" luôn bị skip trong decode
def test_food_detection_decode_filter_mi(app):
    """Lọc bỏ class 'Mì' trong kết quả decode."""
    service = _create_service()

    mi_idx = Config.CLASS_NAMES.index("Mì")
    preds = np.array([[[100, 50, 300, 250, 0.9, mi_idx]]])
    original_size = (640, 480)

    detections = service.decode(preds, original_size)
    assert len(detections) == 0


# ============================================================
# DETECT FOODS (end-to-end)
# ============================================================

# Test Case ID: TC_FOOD_TestFoodDetectionService_detect_foods_001
# Test Objective: End-to-end detection với ONNX mocked
# Input: PIL Image, ONNX session trả về 1 detection
# Expected Output: danh sách detections
# Notes: Mock ONNX session.run()
def test_food_detection_detect_foods_integration(app):
    """End-to-end detect_foods với ONNX mock."""
    with patch("app.services_AI.food_detection_services.ort.InferenceSession") as mock_cls:
        mock_session = _make_mock_ort()
        # Output shape: (1, N, 6) - 1 detection
        mock_session.run.return_value = [
            np.array([[[100, 50, 300, 250, 0.85, 46]]])  # cls_id=46 → "Phở"
        ]
        mock_cls.return_value = mock_session

        from app.services_AI.food_detection_services import FoodDetectionService
        service = FoodDetectionService()

        image = _create_test_image()
        detections = service.detect_foods(image)

    assert len(detections) == 1
    assert detections[0]["class"] == "Phở"
    assert detections[0]["confidence"] == 0.85


# ============================================================
# POST PROCESS
# ============================================================

# Test Case ID: TC_FOOD_TestFoodDetectionService_post_process_001
# Test Objective: Post-process áp dụng NMS và dedup
# Input: danh sách foods với 2 detections cùng class (overlapping)
# Expected Output: 1 detection sau dedup
# Notes: apply_nms + deduplicate_by_label
def test_food_detection_post_process_nms_dedup(app):
    """Post-process giảm detections bằng NMS và dedup."""
    service = _create_service()

    foods = [
        {"class": "Phở", "confidence": 0.9, "bbox": [10, 10, 200, 200]},
        {"class": "Phở", "confidence": 0.7, "bbox": [15, 15, 205, 205]},
    ]

    result = service.post_process(foods)

    # Sau dedup, chỉ còn 1 Phở
    assert len(result) == 1


# Test Case ID: TC_FOOD_TestFoodDetectionService_post_process_002
# Test Objective: Post-process trả rỗng khi input rỗng
# Input: foods = []
# Expected Output: danh sách rỗng
# Notes: Edge case
def test_food_detection_post_process_empty(app):
    """Trả rỗng khi không có detections."""
    service = _create_service()
    result = service.post_process([])
    assert result == []


# Test Case ID: TC_FOOD_TestFoodDetectionService_post_process_003
# Test Objective: Post-process giữ lại các detections khác class
# Input: foods với 2 class khác nhau
# Expected Output: 2 detections (mỗi class giữ 1)
# Notes: NMS áp dụng theo class, dedup giữ best per class
def test_food_detection_post_process_different_classes(app):
    """Giữ 1 detection cho mỗi class khác nhau."""
    service = _create_service()

    foods = [
        {"class": "Phở", "confidence": 0.9, "bbox": [10, 10, 200, 200]},
        {"class": "Bánh mì", "confidence": 0.8, "bbox": [300, 10, 500, 200]},
    ]

    result = service.post_process(foods)
    assert len(result) == 2


# ============================================================
# UPLOAD IMAGES
# ============================================================

# Test Case ID: TC_FOOD_TestFoodDetectionService_upload_annotated_image_001
# Test Objective: Upload ảnh annotated lên Cloudinary
# Input: PIL Image + detections
# Expected Output: URL từ Cloudinary
# Notes: Mock upload_base64_to_cloudinary
@patch("app.services_AI.food_detection_services.upload_base64_to_cloudinary", return_value="https://cloudinary.com/annotated.jpg")
@patch("app.services_AI.food_detection_services.draw_boxes")
@patch("app.services_AI.food_detection_services.image_to_base64", return_value="base64data")
def test_food_detection_upload_annotated_image(mock_b64, mock_draw, mock_upload, app):
    """Upload ảnh annotated thành công."""
    service = _create_service()
    mock_draw.return_value = _create_test_image()

    foods = [{"class": "Phở", "confidence": 0.9, "bbox": [10, 10, 200, 200]}]
    url = service.upload_annotated_image(_create_test_image(), foods)

    assert url == "https://cloudinary.com/annotated.jpg"
    mock_upload.assert_called_once_with("base64data", folder="food-detection/annotated")


# Test Case ID: TC_FOOD_TestFoodDetectionService_upload_original_image_001
# Test Objective: Upload ảnh gốc lên Cloudinary
# Input: PIL Image
# Expected Output: URL từ Cloudinary
# Notes: Mock upload_base64_to_cloudinary
@patch("app.services_AI.food_detection_services.upload_base64_to_cloudinary", return_value="https://cloudinary.com/original.jpg")
@patch("app.services_AI.food_detection_services.image_to_base64", return_value="base64data")
def test_food_detection_upload_original_image(mock_b64, mock_upload, app):
    """Upload ảnh gốc thành công."""
    service = _create_service()

    url = service.upload_original_image(_create_test_image())

    assert url == "https://cloudinary.com/original.jpg"
    mock_upload.assert_called_once_with("base64data", folder="food-detection/original")
