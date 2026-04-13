"""
Unit tests cho SkinDetectionService – phát hiện vùng da bất thường bằng YOLO ONNX.

Mock onnxruntime.InferenceSession để không cần model thật.
"""

import pytest
import numpy as np
from unittest.mock import MagicMock, patch
from PIL import Image

from app.config import Config


# ============================================================
# Helpers
# ============================================================

def _make_mock_session(outputs=None):
    """Tạo mock ONNX InferenceSession cho detection."""
    session = MagicMock()
    mock_input = MagicMock()
    mock_input.name = "images"
    mock_output = MagicMock()
    mock_output.name = "output0"
    session.get_inputs.return_value = [mock_input]
    session.get_outputs.return_value = [mock_output]
    if outputs is not None:
        session.run.return_value = outputs
    return session


def _create_test_image(width=640, height=640):
    """Tạo ảnh PIL RGB đơn giản."""
    return Image.new("RGB", (width, height), color=(128, 128, 128))


# ============================================================
# preprocess
# ============================================================

# Test Case ID: TC_SKIN_SkinDetectionService_preprocess_001
# Test Objective: Preprocess ảnh PIL trả về tensor đúng shape và original_size
# Input: PIL Image 640×640 RGB
# Expected Output: numpy (1, 3, 640, 640) float32, original_size=(640, 640)
# Notes: Kiểm tra shape, dtype, original_size
@patch("app.services_AI.objectdetection.objectdetection_service.ort.InferenceSession")
def test_detection_preprocess_valid_image(mock_ort):
    """Ảnh PIL hợp lệ → tensor (1,3,H,W) + original_size tuple."""
    mock_ort.return_value = _make_mock_session()

    from app.services_AI.objectdetection.objectdetection_service import SkinDetectionService
    service = SkinDetectionService()

    image = _create_test_image(800, 600)
    tensor, original_size = service.preprocess(image)

    assert tensor.shape == (1, 3, 640, 640)
    assert tensor.dtype == np.float32
    assert original_size == (800, 600)


# Test Case ID: TC_SKIN_SkinDetectionService_preprocess_002
# Test Objective: Preprocess raise TypeError khi input không phải PIL Image
# Input: string "not_an_image"
# Expected Output: TypeError
# Notes: Kiểm tra nhánh validate đầu vào
@patch("app.services_AI.objectdetection.objectdetection_service.ort.InferenceSession")
def test_detection_preprocess_invalid_input(mock_ort):
    """Input không phải PIL.Image → raise TypeError."""
    mock_ort.return_value = _make_mock_session()

    from app.services_AI.objectdetection.objectdetection_service import SkinDetectionService
    service = SkinDetectionService()

    with pytest.raises(TypeError, match="expects PIL.Image.Image"):
        service.preprocess("not_an_image")


# Test Case ID: TC_SKIN_SkinDetectionService_preprocess_003
# Test Objective: Giá trị pixel nằm trong khoảng [0, 1] sau chuẩn hóa
# Input: PIL Image
# Expected Output: tensor.min() >= 0 và tensor.max() <= 1
# Notes: Kiểm tra normalize /255.0
@patch("app.services_AI.objectdetection.objectdetection_service.ort.InferenceSession")
def test_detection_preprocess_normalized_values(mock_ort):
    """Pixel values phải nằm trong [0, 1] sau khi /255.0."""
    mock_ort.return_value = _make_mock_session()

    from app.services_AI.objectdetection.objectdetection_service import SkinDetectionService
    service = SkinDetectionService()

    tensor, _ = service.preprocess(_create_test_image())

    assert tensor.min() >= 0.0
    assert tensor.max() <= 1.0


# ============================================================
# decode
# ============================================================

# Test Case ID: TC_SKIN_SkinDetectionService_decode_001
# Test Objective: Decode output có detection trên ngưỡng confidence
# Input: ONNX output với 1 detection conf=0.9 > threshold
# Expected Output: List 1 detection dict có class, confidence, bbox
# Notes: Kiểm tra scale_x, scale_y được áp dụng đúng
@patch("app.services_AI.objectdetection.objectdetection_service.ort.InferenceSession")
def test_detection_decode_above_threshold(mock_ort):
    """Detection có confidence > threshold → nằm trong kết quả."""
    mock_ort.return_value = _make_mock_session()

    from app.services_AI.objectdetection.objectdetection_service import SkinDetectionService
    service = SkinDetectionService()

    # Giả lập output: 1 detection [x1, y1, x2, y2, conf, cls_id]
    preds = np.array([[[100, 100, 200, 200, 0.9, 0]]], dtype=np.float32)
    outputs = [preds]
    original_size = (640, 640)

    detections = service.decode(outputs, original_size)

    assert len(detections) == 1
    assert detections[0]["class"] == Config.CLASS_NAMES[0]
    assert detections[0]["confidence"] == 0.9
    assert len(detections[0]["bbox"]) == 4


# Test Case ID: TC_SKIN_SkinDetectionService_decode_002
# Test Objective: Lọc detection dưới ngưỡng confidence
# Input: ONNX output với conf=0.1 < threshold (0.25)
# Expected Output: List rỗng
# Notes: Kiểm tra nhánh conf < self.conf_threshold
@patch("app.services_AI.objectdetection.objectdetection_service.ort.InferenceSession")
def test_detection_decode_below_threshold(mock_ort):
    """Detection có confidence < threshold → bị loại bỏ."""
    mock_ort.return_value = _make_mock_session()

    from app.services_AI.objectdetection.objectdetection_service import SkinDetectionService
    service = SkinDetectionService()

    preds = np.array([[[100, 100, 200, 200, 0.1, 0]]], dtype=np.float32)
    outputs = [preds]

    detections = service.decode(outputs, (640, 640))

    assert len(detections) == 0


# Test Case ID: TC_SKIN_SkinDetectionService_decode_003
# Test Objective: Bbox được scale đúng khi ảnh gốc khác kích thước model
# Input: original_size=(1280, 960), model input (640, 640)
# Expected Output: bbox tọa độ đã scale ×2 theo chiều rộng, ×1.5 theo chiều cao
# Notes: scale_x = 1280/640 = 2, scale_y = 960/640 = 1.5
@patch("app.services_AI.objectdetection.objectdetection_service.ort.InferenceSession")
def test_detection_decode_bbox_scaling(mock_ort):
    """Bbox phải được scale theo tỉ lệ ảnh gốc / model input."""
    mock_ort.return_value = _make_mock_session()

    from app.services_AI.objectdetection.objectdetection_service import SkinDetectionService
    service = SkinDetectionService()

    preds = np.array([[[100, 100, 200, 200, 0.9, 0]]], dtype=np.float32)
    outputs = [preds]
    # scale_x = 1280/640 = 2, scale_y = 960/640 = 1.5
    detections = service.decode(outputs, (1280, 960))

    bbox = detections[0]["bbox"]
    assert bbox[0] == pytest.approx(200.0)  # 100 * 2
    assert bbox[1] == pytest.approx(150.0)  # 100 * 1.5
    assert bbox[2] == pytest.approx(400.0)  # 200 * 2
    assert bbox[3] == pytest.approx(300.0)  # 200 * 1.5


# Test Case ID: TC_SKIN_SkinDetectionService_decode_004
# Test Objective: Decode nhiều detections, chỉ giữ các detection trên ngưỡng
# Input: 3 detections – 2 trên ngưỡng, 1 dưới ngưỡng
# Expected Output: List 2 detections
# Notes: Kiểm tra lọc hỗn hợp
@patch("app.services_AI.objectdetection.objectdetection_service.ort.InferenceSession")
def test_detection_decode_mixed_confidences(mock_ort):
    """Chỉ giữ detections có confidence >= threshold."""
    mock_ort.return_value = _make_mock_session()

    from app.services_AI.objectdetection.objectdetection_service import SkinDetectionService
    service = SkinDetectionService()

    preds = np.array([
        [
            [10, 10, 50, 50, 0.8, 1],   # Trên ngưỡng
            [60, 60, 100, 100, 0.1, 2],  # Dưới ngưỡng
            [120, 120, 200, 200, 0.5, 3] # Trên ngưỡng
        ]
    ], dtype=np.float32)

    detections = service.decode([preds], (640, 640))
    assert len(detections) == 2


# ============================================================
# detect (integration với preprocess + decode)
# ============================================================

# Test Case ID: TC_SKIN_SkinDetectionService_detect_001
# Test Objective: Detect gọi preprocess rồi session.run rồi decode đúng flow
# Input: PIL Image, mock session trả output hợp lệ
# Expected Output: List detections từ decode
# Notes: Kiểm tra end-to-end flow qua detect()
@patch("app.services_AI.objectdetection.objectdetection_service.ort.InferenceSession")
def test_detection_detect_end_to_end(mock_ort):
    """detect() pipeline: preprocess → session.run → decode."""
    preds = np.array([[[100, 100, 200, 200, 0.9, 0]]], dtype=np.float32)
    session = _make_mock_session()
    session.run.return_value = [preds]
    mock_ort.return_value = session

    from app.services_AI.objectdetection.objectdetection_service import SkinDetectionService
    service = SkinDetectionService()

    detections = service.detect(_create_test_image())

    assert len(detections) == 1
    session.run.assert_called_once()


# ============================================================
# post_process (NMS + dedup)
# ============================================================

# Test Case ID: TC_SKIN_SkinDetectionService_post_process_001
# Test Objective: post_process áp dụng NMS và dedup đúng
# Input: 2 detections cùng class, bbox gần nhau (IoU > 0.5)
# Expected Output: Chỉ còn 1 detection (merged)
# Notes: Kiểm tra NMS loại bỏ trùng lặp
@patch("app.services_AI.objectdetection.objectdetection_service.ort.InferenceSession")
def test_detection_post_process_nms_dedup(mock_ort):
    """NMS gộp 2 bbox gần nhau cùng class thành 1."""
    mock_ort.return_value = _make_mock_session()

    from app.services_AI.objectdetection.objectdetection_service import SkinDetectionService
    service = SkinDetectionService()

    skins = [
        {"class": "acne", "confidence": 0.9, "bbox": [100, 100, 200, 200]},
        {"class": "acne", "confidence": 0.85, "bbox": [110, 110, 210, 210]},
    ]

    result = service.post_process(skins)
    assert len(result) == 1
    assert result[0]["confidence"] == 0.9


# Test Case ID: TC_SKIN_SkinDetectionService_post_process_002
# Test Objective: post_process giữ nguyên detections khác class
# Input: 2 detections khác class, bbox trùng
# Expected Output: Cả 2 đều còn (NMS chỉ gộp cùng class)
# Notes: Kiểm tra NMS không gộp cross-class, nhưng dedup chỉ giữ 1 per label
@patch("app.services_AI.objectdetection.objectdetection_service.ort.InferenceSession")
def test_detection_post_process_different_classes(mock_ort):
    """Detections khác class → NMS không gộp, dedup giữ best per label."""
    mock_ort.return_value = _make_mock_session()

    from app.services_AI.objectdetection.objectdetection_service import SkinDetectionService
    service = SkinDetectionService()

    skins = [
        {"class": "acne", "confidence": 0.9, "bbox": [100, 100, 200, 200]},
        {"class": "eczema", "confidence": 0.85, "bbox": [100, 100, 200, 200]},
    ]

    result = service.post_process(skins)
    assert len(result) == 2


# Test Case ID: TC_SKIN_SkinDetectionService_post_process_003
# Test Objective: post_process trả list rỗng khi input rỗng
# Input: []
# Expected Output: []
# Notes: Kiểm tra edge case danh sách rỗng
@patch("app.services_AI.objectdetection.objectdetection_service.ort.InferenceSession")
def test_detection_post_process_empty_input(mock_ort):
    """Input rỗng → output rỗng."""
    mock_ort.return_value = _make_mock_session()

    from app.services_AI.objectdetection.objectdetection_service import SkinDetectionService
    service = SkinDetectionService()

    result = service.post_process([])
    assert result == []
