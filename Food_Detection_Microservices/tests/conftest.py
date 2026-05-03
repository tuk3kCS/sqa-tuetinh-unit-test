"""
Shared pytest fixtures for Food_Detection_Microservices unit tests.

Provides:
- Flask application and test client with in-memory SQLite DB
- Per-test DB transaction: commit trong app → flush + expire_all; rollback sau mỗi test
- Mocked JWT identity for deterministic testing
- Sample data factories for UserProfile, FoodRecord, DailyEnergyLog
"""
import sys
from unittest.mock import MagicMock

# Pre-mock heavy deps before app import chain loads services_AI (cv2, onnxruntime, ...).
for _mod in ("cv2", "onnxruntime", "ultralytics", "torch"):
    if _mod not in sys.modules:
        sys.modules[_mod] = MagicMock()

import os
import pytest
from unittest.mock import patch
from datetime import date
from sqlalchemy import Integer
from sqlalchemy.orm import Session
from sqlalchemy.pool import StaticPool

os.environ.setdefault("JWT_SECRET_KEY", "test-jwt-secret-key")


def _patch_models_for_sqlite_pk_autoincrement():
    """SQLite only auto-fills INTEGER PRIMARY KEY; BigInteger PK inserts omit id without this."""
    from app.models.user_profile import UserProfile
    from app.models.daily_energy_log import DailyEnergyLog
    from app.models.food_record import FoodRecord
    from app.models.user_profile_weight_history import UserProfileWeightHistory

    for Model in (
        UserProfile,
        DailyEnergyLog,
        FoodRecord,
        UserProfileWeightHistory,
    ):
        Model.__table__.c.id.type = Integer()


@pytest.fixture(scope="session")
def app():
    """Create Flask application with in-memory SQLite for the entire test session."""
    _patch_models_for_sqlite_pk_autoincrement()

    from app import create_app
    from app.config import Config

    class TestConfig(Config):
        TESTING = True
        SQLALCHEMY_DATABASE_URI = "sqlite:///:memory:"
        JWT_SECRET_KEY = "test-jwt-secret-key"
        SQLALCHEMY_ENGINE_OPTIONS = {
            "poolclass": StaticPool,
            "connect_args": {"check_same_thread": False},
        }

    application = create_app(TestConfig)
    yield application


@pytest.fixture(scope="session")
def _db(app):
    """Create all tables once for the session."""
    from app.extensions import db

    with app.app_context():
        db.create_all()
        yield db
        db.drop_all()


@pytest.fixture(autouse=True)
def db_session(app, _db):
    """
    Mỗi test: một connection + transaction ngoài; rollback sau test.

    Service code gọi db.session.commit() — nếu commit thật sẽ kết thúc transaction
    và dữ liệu vẫn nằm trên SQLite (StaticPool dùng chung connection), test sau bị trùng PK.
    Tạm thời biến commit() thành flush() cho mọi Session gắn với connection test.
    """
    with app.app_context():
        connection = _db.engine.connect()
        transaction = connection.begin()
        options = {"bind": connection, "binds": {}}
        session = _db._make_scoped_session(dict(options))
        old_session = _db.session
        _db.session = session

        real_commit = Session.commit

        def commit_flush_only(self):
            if getattr(self, "bind", None) is connection:
                self.flush()
                # Giống sau commit thật: expire để đọc lại (SAEnum → instance, không còn str thuần)
                self.expire_all()
            else:
                real_commit(self)

        Session.commit = commit_flush_only
        try:
            yield session
        finally:
            Session.commit = real_commit
            session.remove()
            transaction.rollback()
            connection.close()
            _db.session = old_session


@pytest.fixture()
def client(app):
    """Flask test client for HTTP endpoint testing."""
    return app.test_client()


@pytest.fixture()
def mock_jwt_identity():
    """Mock flask_jwt_extended to return a fixed user identity."""
    with (
        patch("flask_jwt_extended.get_jwt_identity", return_value="test-user-1"),
        patch(
            "flask_jwt_extended.get_jwt",
            return_value={"userId": 1, "sub": "test@test.com"},
        ),
        patch("flask_jwt_extended.verify_jwt_in_request", return_value=None),
    ):
        yield


@pytest.fixture()
def sample_user_profile(app, _db):
    """Create a sample UserProfile for testing."""
    from app.models.user_profile import UserProfile

    with app.app_context():
        profile = UserProfile(
            user_id=1,
            gender="male",
            date_of_birth=date(1995, 6, 15),
            activity_level="MODERATELY_ACTIVE",
            goal_type="MAINTAIN",
        )
        _db.session.add(profile)
        _db.session.flush()
        return profile
