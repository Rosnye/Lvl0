package io.gymtracker.app.ui.common

import androidx.annotation.StringRes
import io.gymtracker.app.R
import io.gymtracker.domain.achievements.AchievementId

@StringRes
fun AchievementId.titleRes(): Int = when (this) {
    AchievementId.FIRST_SESSION -> R.string.badge_first_session
    AchievementId.SESSIONS_10 -> R.string.badge_sessions_10
    AchievementId.SESSIONS_50 -> R.string.badge_sessions_50
    AchievementId.SESSIONS_100 -> R.string.badge_sessions_100
    AchievementId.FIRST_PR -> R.string.badge_first_pr
    AchievementId.PRS_10 -> R.string.badge_prs_10
    AchievementId.WEEKLY_GOAL -> R.string.badge_weekly_goal
    AchievementId.STREAK_4 -> R.string.badge_streak_4
    AchievementId.STREAK_12 -> R.string.badge_streak_12
    AchievementId.STRENGTH_GOAL -> R.string.badge_strength_goal
    AchievementId.REPS_1K -> R.string.badge_volume_10t
    AchievementId.REPS_10K -> R.string.badge_volume_100t
    AchievementId.LEVEL_5 -> R.string.badge_level_5
    AchievementId.LEVEL_10 -> R.string.badge_level_10
    AchievementId.LONG_SESSION -> R.string.badge_long_session
}

@StringRes
fun AchievementId.descriptionRes(): Int = when (this) {
    AchievementId.FIRST_SESSION -> R.string.badge_first_session_desc
    AchievementId.SESSIONS_10 -> R.string.badge_sessions_10_desc
    AchievementId.SESSIONS_50 -> R.string.badge_sessions_50_desc
    AchievementId.SESSIONS_100 -> R.string.badge_sessions_100_desc
    AchievementId.FIRST_PR -> R.string.badge_first_pr_desc
    AchievementId.PRS_10 -> R.string.badge_prs_10_desc
    AchievementId.WEEKLY_GOAL -> R.string.badge_weekly_goal_desc
    AchievementId.STREAK_4 -> R.string.badge_streak_4_desc
    AchievementId.STREAK_12 -> R.string.badge_streak_12_desc
    AchievementId.STRENGTH_GOAL -> R.string.badge_strength_goal_desc
    AchievementId.REPS_1K -> R.string.badge_volume_10t_desc
    AchievementId.REPS_10K -> R.string.badge_volume_100t_desc
    AchievementId.LEVEL_5 -> R.string.badge_level_5_desc
    AchievementId.LEVEL_10 -> R.string.badge_level_10_desc
    AchievementId.LONG_SESSION -> R.string.badge_long_session_desc
}

/** Emoji de cada insignia: se ve a color cuando está desbloqueada y atenuado cuando no. */
fun AchievementId.emoji(): String = when (this) {
    AchievementId.FIRST_SESSION -> "👟"
    AchievementId.SESSIONS_10 -> "📅"
    AchievementId.SESSIONS_50 -> "🗓️"
    AchievementId.SESSIONS_100 -> "💯"
    AchievementId.FIRST_PR -> "⭐"
    AchievementId.PRS_10 -> "🌟"
    AchievementId.WEEKLY_GOAL -> "✅"
    AchievementId.STREAK_4 -> "🔥"
    AchievementId.STREAK_12 -> "⚡"
    AchievementId.STRENGTH_GOAL -> "🎯"
    AchievementId.REPS_1K -> "💪"
    AchievementId.REPS_10K -> "🦍"
    AchievementId.LEVEL_5 -> "🥈"
    AchievementId.LEVEL_10 -> "🥇"
    AchievementId.LONG_SESSION -> "⏱️"
}
