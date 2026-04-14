"""
Unit tests cho AnalysisService – module xử lý lưu / cập nhật / truy vấn kết quả phân tích da.

Sử dụng fixtures từ conftest: app, client, db_session, mock_jwt_identity.
Mỗi test được rollback tự động nhờ db_session fixture.
"""

import pytest
from unittest.mock import MagicMock, patch
from datetime import datetime

from app.services.analysis_service import AnalysisService
from app.models.analysis_entity import HealthAnalysis
from app.models.analysis_model import AnalysisResult
from app import db


# ============================================================
# Helpers – tạo dữ liệu mẫu dùng chung
# ============================================================

def _make_dto(detection=None, lifestyle_suggestions=None, annotated_url="http://img.test/ann.jpg"):
    """Tạo mock DTO giả lập AnalyzeRequestDTO."""
    dto = MagicMock()
    dto.annotated_image_url = annotated_url
    dto.detection = detection if detection is not None else [
        {"detected_class": "acne", "confidence": 0.92}
    ]
    dto.lifestyle_suggestions = lifestyle_suggestions or {"lifestyle": [], "diet": []}
    return dto


def _seed_entity(session, user_id=1, diagnosis="eczema", confidence=0.85):
    """Chèn trực tiếp một HealthAnalysis vào DB để dùng trong test."""
    entity = HealthAnalysis(
        user_id=user_id,
        analysis_image_url="http://img.test/seed.jpg",
        ai_diagnosis=diagnosis,
        ai_confidence=confidence,
        suggestions={"lifestyle": ["Ngủ đủ giấc"]},
        created_at=datetime(2025, 1, 1, 12, 0, 0),
    )
    session.add(entity)
    session.flush()
    return entity


# ============================================================
# save_analysis_from_request
# ============================================================

# Test Case ID: TC-FR-07-001nalysisService_save_analysis_from_request_001
# Test Objective: Lưu thành công khi DTO hợp lệ có detection
# Input: user_id=1, dto hợp lệ với detection=[{detected_class, confidence}]
# Expected Output: Trả về AnalysisResult, DB có bản ghi tương ứng
# Notes: CheckDB – kiểm tra bảng health_analysis có bản ghi mới
def test_analysis_service_save_analysis_from_request_valid_dto(app, db_session):
    """Lưu thành công khi DTO hợp lệ, kiểm tra entity được tạo đúng."""
    dto = _make_dto()

    with app.app_context():
        result = AnalysisService.save_analysis_from_request(user_id=1, dto=dto)

        assert isinstance(result, AnalysisResult)
        assert result.aiDiagnosis == "acne"
        assert result.aiConfidence == 0.92

        # CheckDB – bản ghi tồn tại
        entity = db_session.query(HealthAnalysis).filter_by(user_id=1).first()
        assert entity is not None
        assert entity.ai_diagnosis == "acne"


# Test Case ID: TC-FR-07-001nalysisService_save_analysis_from_request_002
# Test Objective: Lưu thành công khi detection rỗng (không phát hiện bệnh)
# Input: user_id=2, dto.detection = []
# Expected Output: AnalysisResult có aiDiagnosis = None, aiConfidence = None
# Notes: CheckDB – health_analysis có ai_diagnosis IS NULL
def test_analysis_service_save_analysis_from_request_empty_detection(app, db_session):
    """Detection rỗng → diagnosis và confidence đều None."""
    dto = _make_dto(detection=[])

    with app.app_context():
        result = AnalysisService.save_analysis_from_request(user_id=2, dto=dto)

        assert result.aiDiagnosis is None
        assert result.aiConfidence is None

        entity = db_session.query(HealthAnalysis).filter_by(user_id=2).first()
        assert entity is not None
        assert entity.ai_diagnosis is None


# Test Case ID: TC-FR-07-001nalysisService_save_analysis_from_request_003
# Test Objective: Lưu DTO có nhiều detections – chỉ lấy cái đầu tiên
# Input: dto.detection có 3 phần tử
# Expected Output: AnalysisResult chỉ chứa detection[0]
# Notes: CheckDB – health_analysis.ai_diagnosis == detection[0].detected_class
def test_analysis_service_save_analysis_from_request_multiple_detections(app, db_session):
    """Nhiều detection → service chỉ lấy phần tử đầu tiên."""
    detections = [
        {"detected_class": "rosacea", "confidence": 0.9},
        {"detected_class": "eczema", "confidence": 0.8},
        {"detected_class": "acne", "confidence": 0.7},
    ]
    dto = _make_dto(detection=detections)

    with app.app_context():
        result = AnalysisService.save_analysis_from_request(user_id=3, dto=dto)

        assert result.aiDiagnosis == "rosacea"
        assert result.aiConfidence == 0.9


# Test Case ID: TC-FR-07-001nalysisService_save_analysis_from_request_004
# Test Objective: Kiểm tra suggestions được lưu đúng format JSON
# Input: lifestyle_suggestions chứa lifestyle và diet arrays
# Expected Output: entity.suggestions trùng khớp dữ liệu đầu vào
# Notes: CheckDB – health_analysis.suggestions chứa đúng JSON
def test_analysis_service_save_analysis_from_request_suggestions_saved(app, db_session):
    """Suggestions được lưu nguyên vẹn vào DB dưới dạng JSON."""
    suggestions = {"lifestyle": ["Ngủ đủ giấc"], "diet": ["Ăn rau xanh"]}
    dto = _make_dto(lifestyle_suggestions=suggestions)

    with app.app_context():
        result = AnalysisService.save_analysis_from_request(user_id=4, dto=dto)

        entity = db_session.query(HealthAnalysis).filter_by(user_id=4).first()
        assert entity.suggestions == suggestions


# ============================================================
# update_doctor_note
# ============================================================

# Test Case ID: TC-FR-07-001nalysisService_update_doctor_note_001
# Test Objective: Cập nhật ghi chú bác sĩ thành công khi record thuộc đúng user
# Input: record_id hợp lệ, doctor_note mới, user_id chính chủ
# Expected Output: Trả về AnalysisResult có doctorNote đã cập nhật
# Notes: CheckDB – health_analysis.doctor_note == giá trị mới
def test_analysis_service_update_doctor_note_valid(app, db_session):
    """Cập nhật thành công khi record thuộc user."""
    entity = _seed_entity(db_session, user_id=10)
    record_id = entity.id

    with app.app_context():
        result = AnalysisService.update_doctor_note(
            record_id=record_id,
            doctor_note="Cần tái khám sau 2 tuần",
            user_id=10,
        )

        assert result is not None
        assert result.doctorNote == "Cần tái khám sau 2 tuần"

        refreshed = db_session.get(HealthAnalysis, record_id)
        assert refreshed.doctor_note == "Cần tái khám sau 2 tuần"
        assert refreshed.doctor_updated_at is not None


# Test Case ID: TC-FR-07-001nalysisService_update_doctor_note_002
# Test Objective: Trả None khi record_id không tồn tại
# Input: record_id=999999 (không tồn tại)
# Expected Output: None
# Notes: Không có thay đổi DB
def test_analysis_service_update_doctor_note_not_found(app, db_session):
    """record_id không tồn tại → trả None."""
    with app.app_context():
        result = AnalysisService.update_doctor_note(
            record_id=999999,
            doctor_note="note",
            user_id=1,
        )
        assert result is None


# Test Case ID: TC-FR-07-001nalysisService_update_doctor_note_003
# Test Objective: Trả None khi user_id không khớp chủ sở hữu record
# Input: record thuộc user_id=10, gọi với user_id=99
# Expected Output: None
# Notes: Bảo đảm logic phân quyền – user không sửa record người khác
def test_analysis_service_update_doctor_note_wrong_user(app, db_session):
    """User khác chủ sở hữu → trả None (unauthorized)."""
    entity = _seed_entity(db_session, user_id=10)

    with app.app_context():
        result = AnalysisService.update_doctor_note(
            record_id=entity.id,
            doctor_note="Hack note",
            user_id=99,
        )
        assert result is None

        refreshed = db_session.get(HealthAnalysis, entity.id)
        assert refreshed.doctor_note is None


# Test Case ID: TC-FR-07-001nalysisService_update_doctor_note_004
# Test Objective: Cập nhật lại doctor_note đã có (overwrite)
# Input: record đã có doctor_note, gọi lại với note mới
# Expected Output: doctor_note được ghi đè, doctor_updated_at cập nhật
# Notes: CheckDB – doctor_note chứa giá trị mới nhất
def test_analysis_service_update_doctor_note_overwrite(app, db_session):
    """Ghi đè ghi chú bác sĩ đã có sẵn."""
    entity = _seed_entity(db_session, user_id=20)
    entity.doctor_note = "Note cũ"
    db_session.flush()

    with app.app_context():
        result = AnalysisService.update_doctor_note(
            record_id=entity.id,
            doctor_note="Note mới",
            user_id=20,
        )
        assert result.doctorNote == "Note mới"


# ============================================================
# get_history_by_user
# ============================================================

# Test Case ID: TC-FR-07-001nalysisService_get_history_by_user_001
# Test Objective: Trả danh sách AnalysisResult khi user có lịch sử
# Input: user_id=30 có 2 bản ghi trong DB
# Expected Output: List 2 AnalysisResult, sắp xếp mới nhất trước
# Notes: CheckDB – đếm bản ghi trong health_analysis cho user_id=30
def test_analysis_service_get_history_by_user_with_data(app, db_session):
    """Trả danh sách đúng số lượng và thứ tự giảm dần theo created_at."""
    e1 = HealthAnalysis(
        user_id=30, analysis_image_url="url1",
        ai_diagnosis="acne", ai_confidence=0.9,
        suggestions={}, created_at=datetime(2025, 1, 1),
    )
    e2 = HealthAnalysis(
        user_id=30, analysis_image_url="url2",
        ai_diagnosis="eczema", ai_confidence=0.8,
        suggestions={}, created_at=datetime(2025, 6, 1),
    )
    db_session.add_all([e1, e2])
    db_session.flush()

    with app.app_context():
        results = AnalysisService.get_history_by_user(30)

        assert len(results) == 2
        # Bản ghi mới nhất trước (e2 tháng 6 > e1 tháng 1)
        assert results[0].aiDiagnosis == "eczema"
        assert results[1].aiDiagnosis == "acne"


# Test Case ID: TC-FR-07-001nalysisService_get_history_by_user_002
# Test Objective: Trả list rỗng khi user không có lịch sử
# Input: user_id=999 không có bản ghi
# Expected Output: []
# Notes: Không có bản ghi trong health_analysis cho user này
def test_analysis_service_get_history_by_user_empty(app, db_session):
    """User chưa có lịch sử → trả list rỗng."""
    with app.app_context():
        results = AnalysisService.get_history_by_user(999)
        assert results == []


# Test Case ID: TC-FR-07-001nalysisService_get_history_by_user_003
# Test Objective: Không trả bản ghi của user khác (isolation)
# Input: DB có bản ghi user_id=40 và user_id=41, query user_id=40
# Expected Output: Chỉ trả bản ghi của user 40
# Notes: Kiểm tra data isolation giữa các user
def test_analysis_service_get_history_by_user_isolation(app, db_session):
    """Chỉ trả bản ghi của đúng user_id, không lẫn user khác."""
    e1 = HealthAnalysis(
        user_id=40, analysis_image_url="url_40",
        ai_diagnosis="acne", ai_confidence=0.9,
        suggestions={}, created_at=datetime(2025, 3, 1),
    )
    e2 = HealthAnalysis(
        user_id=41, analysis_image_url="url_41",
        ai_diagnosis="eczema", ai_confidence=0.8,
        suggestions={}, created_at=datetime(2025, 3, 2),
    )
    db_session.add_all([e1, e2])
    db_session.flush()

    with app.app_context():
        results = AnalysisService.get_history_by_user(40)
        assert len(results) == 1
        assert results[0].aiDiagnosis == "acne"
