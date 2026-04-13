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
from sqlalchemy import Integer
from sqlalchemy.pool import StaticPool

os.environ.setdefault("FLASK_ENV", "testing")
os.environ.setdefault("LLM_PROVIDER", "openai")
os.environ.setdefault("OPENAI_API_KEY", "sk-test-fake-key")
os.environ.setdefault("JWT_SECRET_KEY", "test-jwt-secret-key")
os.environ.setdefault("DB_TYPE", "chroma")

# Config.SQLALCHEMY_DATABASE_URI is built from DB_* at class definition time, not from
# SQLALCHEMY_DATABASE_URI env. Replace Config before create_app() loads it.
import app.config as _fitness_config  # noqa: E402


class _TestingConfig(_fitness_config.Config):
    TESTING = True
    SQLALCHEMY_DATABASE_URI = "sqlite:///:memory:"
    # Single shared connection so create_all and per-test sessions see the same DB
    SQLALCHEMY_ENGINE_OPTIONS = {
        "poolclass": StaticPool,
        "connect_args": {"check_same_thread": False},
    }


_fitness_config.Config = _TestingConfig


def _patch_models_for_sqlite_pk_autoincrement():
    """SQLite only auto-fills INTEGER PRIMARY KEY; BigInteger PK inserts omit id without this."""
    from app.models.user_plan import UserPlan

    UserPlan.__table__.c.id.type = Integer()


@pytest.fixture(scope="session")
def app():
    """Create Flask application with in-memory SQLite for the entire test session."""
    from app import create_app

    application = create_app()
    application.config.update(
        {
            "TESTING": True,
            "SQLALCHEMY_DATABASE_URI": "sqlite:///:memory:",
            "JWT_SECRET_KEY": "test-jwt-secret-key",
        }
    )
    yield application


@pytest.fixture(scope="session")
def _db(app):
    """Create all tables once for the session."""
    from app import db

    with app.app_context():
        _patch_models_for_sqlite_pk_autoincrement()
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
        patch("flask_jwt_extended.get_jwt_identity", return_value="test-user-id-123"),
        patch(
            "flask_jwt_extended.get_jwt",
            return_value={"userId": 1, "sub": "test@test.com"},
        ),
        patch("flask_jwt_extended.verify_jwt_in_request", return_value=None),
    ):
        yield


@pytest.fixture()
def mock_llm():
    """Mock BaseLLM instance for deterministic LLM responses."""
    llm = MagicMock()
    llm.chat.return_value = '{"answer": "Mocked LLM response"}'
    llm.moderate.return_value = None
    return llm
