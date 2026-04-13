"""
Shared pytest fixtures for Fitness_Coach_AI unit tests.

Provides:
- Flask application and test client with in-memory SQLite DB
- Automatic DB creation and rollback per test (transaction isolation)
- Mocked LLM and JWT identity for deterministic testing
"""
import os
import pytest
from unittest.mock import patch, MagicMock

os.environ.setdefault("FLASK_ENV", "testing")
os.environ.setdefault("LLM_PROVIDER", "openai")
os.environ.setdefault("OPENAI_API_KEY", "sk-test-fake-key")
os.environ.setdefault("JWT_SECRET_KEY", "test-jwt-secret-key")
os.environ.setdefault("DB_TYPE", "chroma")


@pytest.fixture(scope="session")
def app():
    """Create Flask application with in-memory SQLite for the entire test session."""
    os.environ["SQLALCHEMY_DATABASE_URI"] = "sqlite:///:memory:"
    from app import create_app
    application = create_app()
    application.config.update({
        "TESTING": True,
        "SQLALCHEMY_DATABASE_URI": "sqlite:///:memory:",
        "JWT_SECRET_KEY": "test-jwt-secret-key",
    })
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
    Ensures DB returns to pre-test state (Rollback requirement).
    """
    with app.app_context():
        connection = _db.engine.connect()
        transaction = connection.begin()
        options = dict(bind=connection, binds={})
        session = _db.create_scoped_session(options=options)
        old_session = _db.session
        _db.session = session

        yield session

        transaction.rollback()
        connection.close()
        session.remove()
        _db.session = old_session


@pytest.fixture()
def client(app):
    """Flask test client for HTTP endpoint testing."""
    return app.test_client()


@pytest.fixture()
def mock_jwt_identity():
    """Mock flask_jwt_extended to return a fixed user identity."""
    with patch("flask_jwt_extended.get_jwt_identity", return_value="test-user-id-123"), \
         patch("flask_jwt_extended.get_jwt", return_value={"userId": 1, "sub": "test@test.com"}), \
         patch("flask_jwt_extended.verify_jwt_in_request", return_value=None):
        yield


@pytest.fixture()
def mock_llm():
    """Mock BaseLLM instance for deterministic LLM responses."""
    llm = MagicMock()
    llm.chat.return_value = '{"answer": "Mocked LLM response"}'
    llm.moderate.return_value = None
    return llm
