"""
Unit tests cho ai_profile_mapper - mapping dictionaries cho AI features.
Kiểm tra tính đầy đủ của ACTIVITY_TO_EXPERIENCE, ACTIVITY_TO_DAYS,
ACTIVITY_TO_SESSION_DURATION, GOAL_MAPPING.
"""
import pytest

from app.enums.app_enum import ActivityLevelEnum, GoalTypeEnum
from app.mappers.ai_profile_mapper import (
    ACTIVITY_TO_EXPERIENCE,
    ACTIVITY_TO_DAYS,
    ACTIVITY_TO_SESSION_DURATION,
    GOAL_MAPPING,
)


# ============================================================
# ACTIVITY_TO_EXPERIENCE
# ============================================================

# Test Case ID: TC-FR-00-001iProfileMapper_ACTIVITY_TO_EXPERIENCE_001
# Test Objective: Kiểm tra đầy đủ mapping activity level → experience level
# Input: Tất cả ActivityLevelEnum members đang được map
# Expected Output: sedentary→beginner, lightly_active→beginner, moderately_active→intermediate, very_active→advanced
# Notes: extremely_active KHÔNG có trong mapping (by design)
def test_activity_to_experience_complete():
    """Kiểm tra mapping activity → experience đầy đủ."""
    assert ACTIVITY_TO_EXPERIENCE[ActivityLevelEnum.sedentary] == "beginner"
    assert ACTIVITY_TO_EXPERIENCE[ActivityLevelEnum.lightly_active] == "beginner"
    assert ACTIVITY_TO_EXPERIENCE[ActivityLevelEnum.moderately_active] == "intermediate"
    assert ACTIVITY_TO_EXPERIENCE[ActivityLevelEnum.very_active] == "advanced"

    # Kiểm tra có 4 entries
    assert len(ACTIVITY_TO_EXPERIENCE) == 4


# Test Case ID: TC-FR-00-001iProfileMapper_ACTIVITY_TO_EXPERIENCE_002
# Test Objective: Kiểm tra fallback cho activity level không có trong mapping
# Input: ActivityLevelEnum.extremely_active (không có trong dict)
# Expected Output: KeyError hoặc default value
# Notes: extremely_active không được map → cần dùng .get()
def test_activity_to_experience_missing_key():
    """extremely_active không có trong mapping."""
    assert ActivityLevelEnum.extremely_active not in ACTIVITY_TO_EXPERIENCE
    # Code sử dụng .get() với default "beginner"
    result = ACTIVITY_TO_EXPERIENCE.get(ActivityLevelEnum.extremely_active, "beginner")
    assert result == "beginner"


# ============================================================
# ACTIVITY_TO_DAYS
# ============================================================

# Test Case ID: TC-FR-00-001iProfileMapper_ACTIVITY_TO_DAYS_001
# Test Objective: Kiểm tra mapping activity level → số ngày tập
# Input: Tất cả keys trong ACTIVITY_TO_DAYS
# Expected Output: sedentary→3, lightly_active→4, moderately_active→5, very_active→6
# Notes: Số ngày tăng theo mức hoạt động
def test_activity_to_days_complete():
    """Kiểm tra mapping activity → days đầy đủ."""
    assert ACTIVITY_TO_DAYS[ActivityLevelEnum.sedentary] == 3
    assert ACTIVITY_TO_DAYS[ActivityLevelEnum.lightly_active] == 4
    assert ACTIVITY_TO_DAYS[ActivityLevelEnum.moderately_active] == 5
    assert ACTIVITY_TO_DAYS[ActivityLevelEnum.very_active] == 6
    assert len(ACTIVITY_TO_DAYS) == 4

    # Kiểm tra số ngày tăng dần theo activity level
    values = list(ACTIVITY_TO_DAYS.values())
    assert values == sorted(values)


# ============================================================
# ACTIVITY_TO_SESSION_DURATION
# ============================================================

# Test Case ID: TC-FR-00-001iProfileMapper_ACTIVITY_TO_SESSION_DURATION_001
# Test Objective: Kiểm tra mapping activity level → thời lượng tập (phút)
# Input: Tất cả keys trong ACTIVITY_TO_SESSION_DURATION
# Expected Output: sedentary→45, lightly_active→60, moderately_active→75, very_active→90
# Notes: Thời lượng tăng 15 phút mỗi mức
def test_activity_to_session_duration_complete():
    """Kiểm tra mapping activity → session duration đầy đủ."""
    assert ACTIVITY_TO_SESSION_DURATION[ActivityLevelEnum.sedentary] == 45
    assert ACTIVITY_TO_SESSION_DURATION[ActivityLevelEnum.lightly_active] == 60
    assert ACTIVITY_TO_SESSION_DURATION[ActivityLevelEnum.moderately_active] == 75
    assert ACTIVITY_TO_SESSION_DURATION[ActivityLevelEnum.very_active] == 90
    assert len(ACTIVITY_TO_SESSION_DURATION) == 4

    # Thời lượng tăng dần
    values = list(ACTIVITY_TO_SESSION_DURATION.values())
    assert values == sorted(values)


# ============================================================
# GOAL MAPPING
# ============================================================

# Test Case ID: TC-FR-00-001iProfileMapper_GOAL_MAPPING_001
# Test Objective: Kiểm tra mapping goal type → fitness goal string
# Input: Tất cả GoalTypeEnum members
# Expected Output: lose_weight→fat_loss, maintain→maintenance, gain_weight→hypertrophy
# Notes: Mapping 1-1 giữa GoalTypeEnum và fitness goals
def test_goal_mapping_complete():
    """Kiểm tra mapping goal type đầy đủ."""
    assert GOAL_MAPPING[GoalTypeEnum.lose_weight] == "fat_loss"
    assert GOAL_MAPPING[GoalTypeEnum.maintain] == "maintenance"
    assert GOAL_MAPPING[GoalTypeEnum.gain_weight] == "hypertrophy"

    # Kiểm tra đủ 3 entries (tất cả GoalTypeEnum)
    assert len(GOAL_MAPPING) == 3
    # Tất cả GoalTypeEnum đều được map
    for goal in GoalTypeEnum:
        assert goal in GOAL_MAPPING
