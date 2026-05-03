"""
Unit tests cho cloudinary_helper - upload ảnh lên Cloudinary.
Mock cloudinary.uploader.upload.
"""
import pytest
from unittest.mock import patch, MagicMock
from PIL import Image

from app.utils.cloudinary_helper import upload_image_to_cloudinary


# Test Case ID: TC_FOOD_TestCloudinaryHelper_upload_image_to_cloudinary_001
# Test Objective: Upload ảnh thành công, trả về secure URL
# Input: PIL Image hợp lệ
# Expected Output: URL dạng "https://..."
# Notes: Mock cloudinary.uploader.upload
@patch("app.utils.cloudinary_helper.cloudinary.uploader.upload")
def test_upload_image_success(mock_upload):
    """Upload ảnh thành công trả URL."""
    mock_upload.return_value = {
        "secure_url": "https://res.cloudinary.com/test/image.jpg",
        "public_id": "food-detection/test123",
    }

    image = Image.new("RGB", (100, 100), color=(255, 0, 0))
    url = upload_image_to_cloudinary(image, folder="food-detection")

    assert url == "https://res.cloudinary.com/test/image.jpg"
    mock_upload.assert_called_once()

    # Kiểm tra args: folder và resource_type
    call_kwargs = mock_upload.call_args[1]
    assert call_kwargs["folder"] == "food-detection"
    assert call_kwargs["resource_type"] == "image"


# Test Case ID: TC_FOOD_TestCloudinaryHelper_upload_image_to_cloudinary_002
# Test Objective: Xử lý lỗi khi Cloudinary API gặp sự cố
# Input: PIL Image, cloudinary raise Exception
# Expected Output: Exception được propagate
# Notes: Không catch exception trong helper, propagate lên caller
@patch("app.utils.cloudinary_helper.cloudinary.uploader.upload")
def test_upload_image_api_error(mock_upload):
    """Propagate exception khi Cloudinary API lỗi."""
    mock_upload.side_effect = Exception("Cloudinary API error: invalid credentials")

    image = Image.new("RGB", (100, 100), color=(0, 255, 0))

    with pytest.raises(Exception, match="Cloudinary API error"):
        upload_image_to_cloudinary(image)


# Test Case ID: TC_FOOD_TestCloudinaryHelper_upload_image_to_cloudinary_003
# Test Objective: Trả None khi Cloudinary response không chứa secure_url
# Input: PIL Image, cloudinary trả response không có secure_url
# Expected Output: None
# Notes: result.get("secure_url") trả None nếu key không tồn tại
@patch("app.utils.cloudinary_helper.cloudinary.uploader.upload")
def test_upload_image_no_url_in_response(mock_upload):
    """Trả None khi response không chứa secure_url."""
    mock_upload.return_value = {"public_id": "test123"}

    image = Image.new("RGB", (100, 100), color=(0, 0, 255))
    url = upload_image_to_cloudinary(image)

    assert url is None
