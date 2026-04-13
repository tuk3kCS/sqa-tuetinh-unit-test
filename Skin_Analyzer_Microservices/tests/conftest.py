"""
Shared pytest fixtures for Skin_Analyzer_Microservices unit tests.

Provides:
- Flask application and test client with in-memory SQLite DB
- Automatic DB creation and rollback per test (transaction isolation)
- Mocked JWT identity for deterministic testing
- Sample HealthAnalysis factory
"""
import sys
import types
from unittest.mock import MagicMock

# app.config imports torch at module load; routes pull ultralytics/onnxruntime/cv2/torchvision.
# Install mocks before any `app` import.
if "torch" not in sys.modules:
    _torch_mock = MagicMock()
    _torch_mock.cuda.is_available.return_value = False
    sys.modules["torch"] = _torch_mock
for _mod in ("onnxruntime", "cv2", "ultralytics"):
    if _mod not in sys.modules:
        sys.modules[_mod] = MagicMock()
if "torchvision" not in sys.modules:
    _tv = types.ModuleType("torchvision")
    _tvt = types.ModuleType("torchvision.transforms")
    for _name in ("Compose", "Resize", "ToTensor", "Normalize"):
        setattr(_tvt, _name, MagicMock())
    _tv.transforms = _tvt
    sys.modules["torchvision"] = _tv
    sys.modules["torchvision.transforms"] = _tvt

import os
import pytest
from unittest.mock import patch
from sqlalchemy.pool import StaticPool

os.environ.setdefault("SECRET_KEY", "test-secret-key")
os.environ.setdefault("JWT_SECRET_KEY", "test-jwt-secret-key")
os.environ.setdefault("DATABASE_URL", "sqlite:///:memory:")


@pytest.fixture(scope="session")
def app():
    """Create Flask application with in-memory SQLite for the entire test session."""
    from app import create_app
    from app.config import Config

    class TestConfig(Config):
        TESTING = True
        SQLALCHEMY_DATABASE_URI = "sqlite:///:memory:"
        SQLALCHEMY_ENGINE_OPTIONS = {
            "poolclass": StaticPool,
            "connect_args": {"check_same_thread": False},
        }
        SECRET_KEY = "test-secret-key"
        JWT_SECRET_KEY = "test-jwt-secret-key"

    application = create_app(TestConfig)
    yield application


@pytest.fixture(scope="session")
def _db(app):
    """Create all tables once for the session."""
    from app import db

    with app.app_context():
        db.create_all()
        yield db
        db.drop_all()


@pytest.fixture(autouse=True)
def db_session(app, _db):
    """
    Wrap each test in a transaction and ROLLBACK after completion.
    Uses a SAVEPOINT so code/tests that call session.commit() still roll back.
    """
    with app.app_context():
        connection = _db.engine.connect()
        transaction = connection.begin()
        options = {"bind": connection, "binds": {}}
        session = _db._make_scoped_session(dict(options))
        old_session = _db.session
        _db.session = session
        bind_session = session()
        bind_session.begin_nested()

        yield session

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
