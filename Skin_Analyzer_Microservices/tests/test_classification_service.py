"""
Unit tests cho SkinClassificationService – phân loại bệnh da từ ảnh crop.

Mock onnxruntime.InferenceSession để không cần model thật.
"""

import pytest
import numpy as np
from unittest.mock import MagicMock, patch
from PIL import Image
from io import BytesIO

from app.config import Config


@pytest.fixture(autouse=True)
def _stub_torchvision_and_torch_ops_for_classification(monkeypatch):
    """
    conftest gán torch/torchvision = MagicMock và có thể không có torchvision thật.
    Stub Compose (preprocess) + softmax/from_numpy/max bằng numpy để test không cần PyTorch runtime.
    """
    _h, _w = Config.CLASSIFICATION_IMG_SIZE

    def _fake_compose(_transform_list):
        class _Compose:
            def __call__(self, _pil_img):
                mid = MagicMock()
                leaf = MagicMock()
                leaf.numpy.return_value = np.zeros((1, 3, _h, _w), dtype=np.float32)
                mid.unsqueeze.return_value = leaf
                return mid

        return _Compose()

    def _softmax_np(x, dim=1):
        x = np.asarray(x, dtype=np.float64)
        ex = np.exp(x - np.max(x, axis=dim, keepdims=True))
        return ex / np.sum(ex, axis=dim, keepdims=True)

    def _from_numpy(a):
        return np.asarray(a, dtype=np.float32)

    def _torch_max_np(probs, dim):
        arr = np.asarray(probs)
        pred_i = int(np.argmax(arr, axis=dim).reshape(-1)[0])
        conf_v = float(np.max(arr, axis=dim).reshape(-1)[0])

        class _Conf:
            def item(self):
                return conf_v

        class _Pred:
            def item(self):
                return pred_i

        return _Conf(), _Pred()

    monkeypatch.setattr(
        "app.services_AI.classification.classification_service.transforms.Compose",
        _fake_compose,
    )
    monkeypatch.setattr(
        "app.services_AI.classification.classification_service.torch.softmax",
        _softmax_np,
    )
    monkeypatch.setattr(
        "app.services_AI.classification.classification_service.torch.from_numpy",
        _from_numpy,
    )
    monkeypatch.setattr(
        "app.services_AI.classification.classification_service.torch.max",
        _torch_max_np,
    )


# ============================================================
# Helpers
# ============================================================

def _make_mock_session(logits=None):
    """Tạo mock ONNX InferenceSession trả về logits cho softmax."""
    session = MagicMock()
    mock_input = MagicMock()
    mock_input.name = "input"
    session.get_inputs.return_value = [mock_input]

    # Mặc định logits: class 0 có xác suất cao nhất
    if logits is None:
        logits = np.array([[10.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                            0.0, 0.0, 0.0]], dtype=np.float32)
    session.run.return_value = [logits]
    return session


def _create_test_image(width=224, height=224):
    """Tạo ảnh PIL RGB đơn giản để test."""
    return Image.new("RGB", (width, height), color=(128, 128, 128))


def _create_file_like_image():
    """Tạo file-like object chứa ảnh JPEG."""
    img = _create_test_image()
    buf = BytesIO()
    img.save(buf, format="JPEG")
    buf.seek(0)
    return buf


# ============================================================
# _preprocess
# ============================================================

# Test Case ID: TC_SKIN_TestSkinClassificationService__preprocess_001
# Test Objective: Preprocess ảnh PIL hợp lệ trả về numpy array đúng shape
# Input: PIL Image 224×224 RGB
# Expected Output: numpy array shape (1, 3, 224, 224), dtype float32
# Notes: Kiểm tra output shape và dtype
@patch("app.services_AI.classification.classification_service.ort.InferenceSession")
def test_classification_preprocess_valid_pil_image(mock_ort):
    """Ảnh PIL hợp lệ → numpy (1, 3, 224, 224) float32."""
    mock_ort.return_value = _make_mock_session()

    from app.services_AI.classification.classification_service import SkinClassificationService
    service = SkinClassificationService()

    image = _create_test_image()
    result = service._preprocess(image)

    assert isinstance(result, np.ndarray)
    assert result.shape == (1, 3, 224, 224)
    assert result.dtype == np.float32


# Test Case ID: TC_SKIN_TestSkinClassificationService__preprocess_002
# Test Objective: Preprocess file-like object (FileStorage) trả về đúng shape
# Input: BytesIO chứa JPEG
# Expected Output: numpy array shape (1, 3, 224, 224)
# Notes: Nhánh code xử lý khi input không phải PIL.Image
@patch("app.services_AI.classification.classification_service.ort.InferenceSession")
def test_classification_preprocess_file_like_object(mock_ort):
    """File-like object → tự mở và chuyển thành tensor đúng shape."""
    mock_ort.return_value = _make_mock_session()

    from app.services_AI.classification.classification_service import SkinClassificationService
    service = SkinClassificationService()

    file_like = _create_file_like_image()
    result = service._preprocess(file_like)

    assert result.shape == (1, 3, 224, 224)
    assert result.dtype == np.float32


# Test Case ID: TC_SKIN_TestSkinClassificationService__preprocess_003
# Test Objective: Preprocess ảnh RGBA (4 kênh) vẫn convert đúng sang RGB
# Input: PIL Image mode RGBA
# Expected Output: numpy array shape (1, 3, H, W) – chỉ 3 kênh
# Notes: Đảm bảo convert("RGB") hoạt động với ảnh có alpha channel
@patch("app.services_AI.classification.classification_service.ort.InferenceSession")
def test_classification_preprocess_rgba_image(mock_ort):
    """Ảnh RGBA → convert RGB thành công, shape vẫn (1, 3, H, W)."""
    mock_ort.return_value = _make_mock_session()

    from app.services_AI.classification.classification_service import SkinClassificationService
    service = SkinClassificationService()

    image = Image.new("RGBA", (224, 224), color=(128, 128, 128, 255))
    result = service._preprocess(image)

    assert result.shape[1] == 3  # Đúng 3 kênh RGB


# ============================================================
# classify
# ============================================================

# Test Case ID: TC_SKIN_TestSkinClassificationService_classify_001
# Test Objective: Classify trả về class_index, class_name, confidence đúng
# Input: Ảnh PIL hợp lệ, ONNX session trả logits class 0 cao nhất
# Expected Output: dict có class_index=0, class_name=CLASS_NAMES[0], confidence gần 1.0
# Notes: Mock ONNX session trả logits cố định
@patch("app.services_AI.classification.classification_service.ort.InferenceSession")
def test_classification_classify_returns_correct_class(mock_ort):
    """Logits cao nhất ở index 0 → trả class_index=0 với confidence cao."""
    logits = np.zeros((1, 19), dtype=np.float32)
    logits[0, 0] = 20.0  # Class 0 vượt trội
    mock_ort.return_value = _make_mock_session(logits)

    from app.services_AI.classification.classification_service import SkinClassificationService
    service = SkinClassificationService()

    image = _create_test_image()
    result = service.classify(image)

    assert result["class_index"] == 0
    assert result["class_name"] == service.class_names[0]
    assert result["confidence"] > 0.99


# Test Case ID: TC_SKIN_TestSkinClassificationService_classify_002
# Test Objective: Classify trả đúng class khi logits cao nhất ở index khác 0
# Input: ONNX logits cao nhất ở index 3 (Eyebag)
# Expected Output: class_index=3, class_name=CLASS_NAMES[3]
# Notes: Kiểm tra argmax hoạt động đúng
@patch("app.services_AI.classification.classification_service.ort.InferenceSession")
def test_classification_classify_different_class_output(mock_ort):
    """Logits cao nhất ở index 3 → trả class_index=3."""
    logits = np.zeros((1, 19), dtype=np.float32)
    logits[0, 3] = 15.0  # Class 3 vượt trội
    mock_ort.return_value = _make_mock_session(logits)

    from app.services_AI.classification.classification_service import SkinClassificationService
    service = SkinClassificationService()

    result = service.classify(_create_test_image())

    assert result["class_index"] == 3
    assert result["class_name"] == service.class_names[3]


# Test Case ID: TC_SKIN_TestSkinClassificationService_classify_003
# Test Objective: Confidence thấp khi logits phân bố đều
# Input: ONNX logits đều nhau cho tất cả class
# Expected Output: confidence xấp xỉ 1/NUM_CLASSES
# Notes: Kiểm tra softmax phân bố đều
@patch("app.services_AI.classification.classification_service.ort.InferenceSession")
def test_classification_classify_low_confidence(mock_ort):
    """Logits đều nhau → confidence thấp (xấp xỉ 1/19)."""
    logits = np.ones((1, 19), dtype=np.float32)
    mock_ort.return_value = _make_mock_session(logits)

    from app.services_AI.classification.classification_service import SkinClassificationService
    service = SkinClassificationService()

    result = service.classify(_create_test_image())

    assert result["confidence"] < 0.2  # ≈ 1/19 ≈ 0.0526


# Test Case ID: TC_SKIN_TestSkinClassificationService_classify_004
# Test Objective: Kết quả classify có đủ 3 key bắt buộc
# Input: Ảnh PIL hợp lệ bất kỳ
# Expected Output: dict chứa "class_index", "class_name", "confidence"
# Notes: Kiểm tra cấu trúc output
@patch("app.services_AI.classification.classification_service.ort.InferenceSession")
def test_classification_classify_output_structure(mock_ort):
    """Output dict phải chứa đủ 3 key: class_index, class_name, confidence."""
    mock_ort.return_value = _make_mock_session()

    from app.services_AI.classification.classification_service import SkinClassificationService
    service = SkinClassificationService()

    result = service.classify(_create_test_image())

    assert "class_index" in result
    assert "class_name" in result
    assert "confidence" in result
    assert isinstance(result["class_index"], int)
    assert isinstance(result["class_name"], str)
    assert isinstance(result["confidence"], float)
