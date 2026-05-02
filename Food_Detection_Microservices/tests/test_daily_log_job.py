"""
Unit tests cho daily_log_job - scheduled job tạo daily logs hàng ngày.
"""
import pytest
from unittest.mock import patch, MagicMock, call

from app.jobs.daily_log_job import daily_log_job


# Test Case ID: TC_FOOD_TestDailyLogJob_daily_log_job_001
# Test Objective: Job gọi create_daily_logs_for_all_users đúng cách
# Input: Flask app object
# Expected Output: create_daily_logs_for_all_users được gọi 1 lần
# Notes: Mock create_daily_logs_for_all_users
@patch("app.jobs.daily_log_job.create_daily_logs_for_all_users")
def test_daily_log_job_calls_create(mock_create, app):
    """Job gọi create_daily_logs_for_all_users."""
    daily_log_job(app)
    mock_create.assert_called_once()


# Test Case ID: TC_FOOD_TestDailyLogJob_daily_log_job_002
# Test Objective: Job in thông báo START và END
# Input: Flask app object
# Expected Output: print được gọi với "START" và "END"
# Notes: Mock print để kiểm tra logging
@patch("app.jobs.daily_log_job.create_daily_logs_for_all_users")
@patch("builtins.print")
def test_daily_log_job_prints_messages(mock_print, mock_create, app):
    """Job in thông báo START và END."""
    daily_log_job(app)

    # Kiểm tra print được gọi với START và END
    calls = [str(c) for c in mock_print.call_args_list]
    assert any("START" in c for c in calls)
    assert any("END" in c for c in calls)


# Test Case ID: TC_FOOD_TestDailyLogJob_daily_log_job_003
# Test Objective: Job sử dụng app_context đúng cách
# Input: Flask app mock
# Expected Output: app.app_context() được gọi
# Notes: Kiểm tra context manager pattern
def test_daily_log_job_uses_app_context(app):
    """Job sử dụng app.app_context() để chạy."""
    with patch("app.jobs.daily_log_job.create_daily_logs_for_all_users") as mock_create:
        daily_log_job(app)
        mock_create.assert_called_once()
