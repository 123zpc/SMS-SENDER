package com.smsagent.state

import android.content.Context

object LaunchStateStore {

    private const val preferencesName = "launch_state"
    private const val onboardingCompletedKey = "onboarding_completed"

    fun hasCompletedOnboarding(context: Context): Boolean {
        return context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .getBoolean(onboardingCompletedKey, false)
    }

    fun markOnboardingCompleted(context: Context) {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(onboardingCompletedKey, true)
            .apply()
    }
}
