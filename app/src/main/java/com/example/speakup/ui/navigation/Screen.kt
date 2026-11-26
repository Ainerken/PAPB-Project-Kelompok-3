package com.example.speakup.ui.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Login : Screen("login")
    object Register : Screen("register")
    object SkillSurvey : Screen("skill_survey")
    object PreferencesSurvey : Screen("preferences_survey")
    object CreatingAccount : Screen("creating_account")
    object Welcome : Screen("welcome")
    object Dashboard : Screen("dashboard")
    object SessionSetup : Screen("session_setup")
    object Recording : Screen("recording")
    object Processing : Screen("processing")
    data class SessionEvaluation(val sessionId: Long = 0L) : Screen("session_evaluation/$sessionId") {
        companion object {
            const val ROUTE_PREFIX = "session_evaluation"
        }
    }
    object History : Screen("history")
    object Profile : Screen("profile")
    object ChangePassword : Screen("change_password")
    object ForgotPassword : Screen("forgot_password")
}
