"""
Unit tests cho utils.py – các hàm tiện ích: IoU, NMS, dedup, crop, draw, base64, Cloudinary.
"""

import pytest
import base64
from unittest.mock import patch, MagicMock
from PIL import Image

from app.utils.utils import (
    calculate_iou,
    apply_nms,
    deduplicate_by_label,
    crop_regions,
    draw_boxes,
    image_to_base64,
    upload_base64_to_cloudinary,
)


# ============================================================
# Helpers
# ============================================================

def _create_test_image(width=200, height=200):
    return Image.new("RGB", (width, height), color=(100, 150, 200))


# ============================================================
# calculate_iou
# ============================================================

# Test Case ID: TC-FR-07-001tils_calculate_iou_001
# Test Objective: IoU = 1.0 khi hai box trùng hoàn toàn
# Input: box1 = box2 = [0, 0, 100, 100]
# Expected Output: 1.0
# Notes: Trường hợp trùng hoàn toàn
def test_calculate_iou_identical_boxes():
    """Hai box giống hệt → IoU = 1.0."""
    assert calculate_iou([0, 0, 100, 100], [0, 0, 100, 100]) == 1.0


# Test Case ID: TC-FR-07-001tils_calculate_iou_002
# Test Objective: IoU = 0.0 khi hai box không giao nhau
# Input: box1 = [0, 0, 50, 50], box2 = [100, 100, 200, 200]
# Expected Output: 0.0
# Notes: Không có diện tích giao
def test_calculate_iou_no_overlap():
    """Hai box không giao nhau → IoU = 0.0."""
    assert calculate_iou([0, 0, 50, 50], [100, 100, 200, 200]) == 0.0


# Test Case ID: TC-FR-07-001tils_calculate_iou_003
# Test Objective: IoU chính xác khi hai box giao nhau một phần
# Input: box1 = [0, 0, 100, 100], box2 = [50, 50, 150, 150]
# Expected Output: ~0.142857 (2500 / 17500)
# Notes: inter = 50×50 = 2500, union = 10000+10000-2500 = 17500
def test_calculate_iou_partial_overlap():
    """Hai box giao 1 phần → IoU = 2500/17500 ≈ 0.1429."""
    iou = calculate_iou([0, 0, 100, 100], [50, 50, 150, 150])
    assert iou == pytest.approx(2500 / 17500, rel=1e-3)


# Test Case ID: TC-FR-07-001tils_calculate_iou_004
# Test Objective: IoU = 0 khi union area = 0 (box thoái hóa)
# Input: box1 = [0, 0, 0, 0], box2 = [0, 0, 0, 0]
# Expected Output: 0.0
# Notes: Phòng chia cho 0
def test_calculate_iou_zero_area():
    """Box thoái hóa (area=0) → IoU = 0.0."""
    assert calculate_iou([0, 0, 0, 0], [0, 0, 0, 0]) == 0.0


# Test Case ID: TC-FR-07-001tils_calculate_iou_005
# Test Objective: IoU khi box1 nằm hoàn toàn trong box2
# Input: box1 = [25, 25, 75, 75], box2 = [0, 0, 100, 100]
# Expected Output: 2500/10000 = 0.25
# Notes: inner box area = 2500, union = 2500+10000-2500 = 10000
def test_calculate_iou_contained_box():
    """Box nhỏ nằm trong box lớn → IoU = area_nhỏ / area_lớn."""
    iou = calculate_iou([25, 25, 75, 75], [0, 0, 100, 100])
    assert iou == pytest.approx(0.25, rel=1e-3)


# ============================================================
# apply_nms
# ============================================================

# Test Case ID: TC-FR-07-001tils_apply_nms_001
# Test Objective: NMS gộp 2 bbox gần nhau cùng class
# Input: 2 detections cùng class, IoU > 0.5
# Expected Output: 1 detection (merged)
# Notes: Kiểm tra NMS cluster + merge
def test_apply_nms_merge_overlapping():
    """2 detections cùng class + IoU cao → gộp thành 1."""
    dets = [
        {"class": "acne", "confidence": 0.9, "bbox": [0, 0, 100, 100]},
        {"class": "acne", "confidence": 0.8, "bbox": [10, 10, 110, 110]},
    ]
    result = apply_nms(dets, iou_threshold=0.5)
    assert len(result) == 1
    assert result[0]["confidence"] == 0.9  # Giữ confidence cao nhất


# Test Case ID: TC-FR-07-001tils_apply_nms_002
# Test Objective: NMS giữ nguyên detections không giao nhau
# Input: 2 detections cùng class, bbox xa nhau
# Expected Output: 2 detections
# Notes: IoU ≈ 0 → không gộp
def test_apply_nms_keep_separate():
    """2 detections cùng class nhưng xa nhau → giữ cả 2."""
    dets = [
        {"class": "acne", "confidence": 0.9, "bbox": [0, 0, 50, 50]},
        {"class": "acne", "confidence": 0.8, "bbox": [200, 200, 300, 300]},
    ]
    result = apply_nms(dets, iou_threshold=0.5)
    assert len(result) == 2


# Test Case ID: TC-FR-07-001tils_apply_nms_003
# Test Objective: NMS trả list rỗng khi input rỗng
# Input: []
# Expected Output: []
# Notes: Edge case
def test_apply_nms_empty_input():
    """Input rỗng → output rỗng."""
    assert apply_nms([]) == []


# Test Case ID: TC-FR-07-001tils_apply_nms_004
# Test Objective: NMS không gộp cross-class detections
# Input: 2 detections khác class, bbox trùng
# Expected Output: 2 detections
# Notes: NMS xử lý theo từng class riêng
def test_apply_nms_different_classes():
    """Khác class → không gộp dù bbox trùng."""
    dets = [
        {"class": "acne", "confidence": 0.9, "bbox": [0, 0, 100, 100]},
        {"class": "eczema", "confidence": 0.8, "bbox": [0, 0, 100, 100]},
    ]
    result = apply_nms(dets, iou_threshold=0.5)
    assert len(result) == 2


# ============================================================
# deduplicate_by_label
# ============================================================

# Test Case ID: TC-FR-07-001tils_deduplicate_by_label_001
# Test Objective: Giữ 1 detection duy nhất per label (confidence cao nhất)
# Input: 3 detections cùng class "acne" với confidence khác nhau
# Expected Output: 1 detection có confidence cao nhất
# Notes: Kiểm tra dedup logic
def test_deduplicate_by_label_same_class():
    """3 detections cùng class → giữ 1 confidence cao nhất."""
    dets = [
        {"class": "acne", "confidence": 0.7, "bbox": [0, 0, 50, 50]},
        {"class": "acne", "confidence": 0.9, "bbox": [10, 10, 60, 60]},
        {"class": "acne", "confidence": 0.8, "bbox": [20, 20, 70, 70]},
    ]
    result = deduplicate_by_label(dets)
    assert len(result) == 1
    assert result[0]["confidence"] == 0.9


# Test Case ID: TC-FR-07-001tils_deduplicate_by_label_002
# Test Objective: Giữ nguyên khi mỗi detection là class khác nhau
# Input: 2 detections khác class
# Expected Output: 2 detections, sắp xếp confidence giảm dần
# Notes: Kiểm tra sort
def test_deduplicate_by_label_different_classes():
    """Khác class → giữ tất cả, sắp xếp confidence giảm dần."""
    dets = [
        {"class": "eczema", "confidence": 0.7, "bbox": [0, 0, 50, 50]},
        {"class": "acne", "confidence": 0.9, "bbox": [10, 10, 60, 60]},
    ]
    result = deduplicate_by_label(dets)
    assert len(result) == 2
    assert result[0]["confidence"] >= result[1]["confidence"]


# Test Case ID: TC-FR-07-001tils_deduplicate_by_label_003
# Test Objective: Trả list rỗng khi input rỗng
# Input: []
# Expected Output: []
# Notes: Edge case
def test_deduplicate_by_label_empty():
    """Input rỗng → output rỗng."""
    assert deduplicate_by_label([]) == []


# ============================================================
# crop_regions
# ============================================================

# Test Case ID: TC-FR-07-001tils_crop_regions_001
# Test Objective: Crop đúng vùng từ ảnh theo bbox
# Input: Ảnh 200×200, 1 detection bbox [10, 10, 50, 50]
# Expected Output: List 1 ảnh crop kích thước 40×40
# Notes: Kiểm tra PIL crop hoạt động đúng
def test_crop_regions_single():
    """Crop 1 vùng → list 1 ảnh đúng kích thước."""
    img = _create_test_image()
    dets = [{"bbox": [10, 10, 50, 50]}]
    crops = crop_regions(img, dets)
    assert len(crops) == 1
    assert crops[0].size == (40, 40)


# Test Case ID: TC-FR-07-001tils_crop_regions_002
# Test Objective: Crop nhiều vùng cùng lúc
# Input: 2 detections với bbox khác nhau
# Expected Output: List 2 ảnh crop
# Notes: Kiểm tra xử lý multi-detection
def test_crop_regions_multiple():
    """Crop 2 vùng → list 2 ảnh."""
    img = _create_test_image()
    dets = [
        {"bbox": [0, 0, 50, 50]},
        {"bbox": [60, 60, 120, 120]},
    ]
    crops = crop_regions(img, dets)
    assert len(crops) == 2


# Test Case ID: TC-FR-07-001tils_crop_regions_003
# Test Objective: Crop list rỗng khi không có detection
# Input: Ảnh hợp lệ, detections=[]
# Expected Output: []
# Notes: Edge case
def test_crop_regions_empty():
    """Không detection → list rỗng."""
    assert crop_regions(_create_test_image(), []) == []


# ============================================================
# draw_boxes
# ============================================================

# Test Case ID: TC-FR-07-001tils_draw_boxes_001
# Test Objective: draw_boxes trả về Image sau khi vẽ bbox
# Input: Ảnh 200×200, 1 detection
# Expected Output: PIL Image cùng kích thước, không lỗi
# Notes: Chỉ kiểm tra không exception, vì khó so pixel
def test_draw_boxes_returns_image():
    """Vẽ box trả về ảnh PIL cùng size."""
    img = _create_test_image()
    dets = [{"class": "acne", "confidence": 0.9, "bbox": [10, 10, 80, 80]}]
    result = draw_boxes(img, dets)
    assert isinstance(result, Image.Image)
    assert result.size == (200, 200)


# Test Case ID: TC-FR-07-001tils_draw_boxes_002
# Test Objective: draw_boxes hoạt động với list detections rỗng
# Input: Ảnh hợp lệ, detections=[]
# Expected Output: PIL Image không thay đổi
# Notes: Edge case – không vẽ gì
def test_draw_boxes_empty_detections():
    """Detections rỗng → trả ảnh gốc không lỗi."""
    img = _create_test_image()
    result = draw_boxes(img, [])
    assert isinstance(result, Image.Image)


# ============================================================
# image_to_base64
# ============================================================

# Test Case ID: TC-FR-07-001tils_image_to_base64_001
# Test Objective: Chuyển PIL Image sang base64 string hợp lệ
# Input: Ảnh PIL RGB 100×100
# Expected Output: String base64 decode được thành bytes
# Notes: Kiểm tra output là base64 hợp lệ
def test_image_to_base64_valid():
    """PIL Image → base64 string decode được."""
    img = _create_test_image(100, 100)
    b64 = image_to_base64(img)
    assert isinstance(b64, str)
    decoded = base64.b64decode(b64)
    assert len(decoded) > 0


# Test Case ID: TC-FR-07-001tils_image_to_base64_002
# Test Objective: Kiểm tra format PNG
# Input: Ảnh PIL, format="PNG"
# Expected Output: Base64 string, decode bắt đầu bằng PNG signature
# Notes: Kiểm tra tham số format
def test_image_to_base64_png_format():
    """Format PNG → base64 chứa PNG bytes."""
    img = _create_test_image(50, 50)
    b64 = image_to_base64(img, format="PNG")
    decoded = base64.b64decode(b64)
    assert decoded[:4] == b'\x89PNG'


# ============================================================
# upload_base64_to_cloudinary
# ============================================================

# Test Case ID: TC-FR-07-001tils_upload_base64_to_cloudinary_001
# Test Objective: Fallback data URI khi không có Cloudinary credentials
# Input: base64 string, không set CLOUDINARY env vars
# Expected Output: String bắt đầu bằng "data:image/jpeg;base64,"
# Notes: Kiểm tra nhánh fallback
@patch.dict("os.environ", {}, clear=True)
@patch("app.utils.utils.cloudinary.config")
def test_upload_cloudinary_fallback_no_credentials(mock_config):
    """Không có credentials → trả data URI fallback."""
    mock_cfg = MagicMock()
    mock_cfg.api_key = None
    mock_config.return_value = mock_cfg

    img = _create_test_image(50, 50)
    b64_str = image_to_base64(img)
    result = upload_base64_to_cloudinary(b64_str)

    assert result.startswith("data:image/jpeg;base64,")


# Test Case ID: TC-FR-07-001tils_upload_base64_to_cloudinary_002
# Test Objective: Upload thành công khi có Cloudinary credentials
# Input: base64 string, CLOUDINARY_URL set
# Expected Output: URL từ Cloudinary response
# Notes: Mock cloudinary.uploader.upload
@patch("app.utils.utils.cloudinary.uploader.upload")
@patch.dict("os.environ", {"CLOUDINARY_URL": "cloudinary://key:secret@cloud"})
def test_upload_cloudinary_success(mock_upload):
    """Có credentials → gọi Cloudinary upload, trả secure_url."""
    mock_upload.return_value = {"secure_url": "https://res.cloudinary.com/test/img.jpg"}

    img = _create_test_image(50, 50)
    b64_str = image_to_base64(img)
    result = upload_base64_to_cloudinary(b64_str)

    assert result == "https://res.cloudinary.com/test/img.jpg"
    mock_upload.assert_called_once()


# Test Case ID: TC-FR-07-001tils_upload_base64_to_cloudinary_003
# Test Objective: Xử lý base64 có prefix "data:image..." (strip prefix)
# Input: "data:image/jpeg;base64,{actual_base64}"
# Expected Output: Upload thành công (prefix bị loại bỏ)
# Notes: Kiểm tra nhánh startswith("data:image")
@patch("app.utils.utils.cloudinary.uploader.upload")
@patch.dict("os.environ", {"CLOUDINARY_URL": "cloudinary://key:secret@cloud"})
def test_upload_cloudinary_strips_data_uri_prefix(mock_upload):
    """Base64 có prefix data:image → strip prefix trước khi upload."""
    mock_upload.return_value = {"secure_url": "https://res.cloudinary.com/test/img.jpg"}

    img = _create_test_image(50, 50)
    raw_b64 = image_to_base64(img)
    prefixed = f"data:image/jpeg;base64,{raw_b64}"

    result = upload_base64_to_cloudinary(prefixed)
    assert result == "https://res.cloudinary.com/test/img.jpg"
