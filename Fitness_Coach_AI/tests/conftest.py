"""
Shared pytest fixtures for Fitness_Coach_AI unit tests.

Provides:
- Flask application and test client with in-memory SQLite DB
- Automatic DB creation and rollback per test (transaction isolation)
- Mocked LLM; JWT qua auth_headers (token ký bằng JWT_SECRET_KEY test)
"""
import os
import pytest
from unittest.mock import MagicMock
from sqlalchemy import Integer
from sqlalchemy.pool import StaticPool

os.environ.setdefault("FLASK_ENV", "testing")
os.environ.setdefault("LLM_PROVIDER", "openai")
os.environ.setdefault("OPENAI_API_KEY", "sk-test-fake-key")
os.environ.setdefault(
    "JWT_SECRET_KEY",
    "test-jwt-secret-key-32bytes-minimum!!",
)
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
            "JWT_SECRET_KEY": "test-jwt-secret-key-32bytes-minimum!!",
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

        # Services call db.session.commit(); real commit ends the outer test transaction
        # and leaves rows visible across tests. Flush only so teardown rollback still clears DB.
        real_session_commit = session.commit

        def _commit_flush_only():
            session.flush()

        session.commit = _commit_flush_only
        try:
            yield session
        finally:
            session.commit = real_session_commit
            session.remove()
            transaction.rollback()
            connection.close()
            _db.session = old_session


@pytest.fixture()
def client(app):
    """Flask test client for HTTP endpoint testing."""
    return app.test_client()


@pytest.fixture()
def auth_headers(app):
    """Authorization header với JWT hợp lệ (userId=1) — dùng cho test client."""
    with app.app_context():
        from flask_jwt_extended import create_access_token

        token = create_access_token(
            identity="test@test.com",
            additional_claims={"userId": 1},
        )
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture()
def mock_llm():
    """Mock BaseLLM instance for deterministic LLM responses."""
    llm = MagicMock()
    llm.chat.return_value = '{"answer": "Mocked LLM response"}'
    llm.moderate.return_value = None
    return llm
