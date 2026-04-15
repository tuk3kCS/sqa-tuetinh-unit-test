"""
Shared pytest fixtures for Food_Detection_Microservices unit tests.

Provides:
- Flask application and test client with in-memory SQLite DB
- Automatic DB creation and rollback per test (transaction isolation)
- Mocked JWT identity for deterministic testing
- Sample data factories for UserProfile, FoodRecord, DailyEnergyLog
"""
import os
import pytest
from unittest.mock import patch
from datetime import date

os.environ.setdefault("JWT_SECRET_KEY", "test-jwt-secret-key")


@pytest.fixture(scope="session")
def app():
    """Create Flask application with in-memory SQLite for the entire test session."""
    from app import create_app
    from app.config import Config

    class TestConfig(Config):
        TESTING = True
        SQLALCHEMY_DATABASE_URI = "sqlite:///:memory:"
        JWT_SECRET_KEY = "test-jwt-secret-key"

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
    with patch("flask_jwt_extended.get_jwt_identity", return_value="test-user-1"), \
         patch("flask_jwt_extended.get_jwt", return_value={"userId": 1, "sub": "test@test.com"}), \
         patch("flask_jwt_extended.verify_jwt_in_request", return_value=None):
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
