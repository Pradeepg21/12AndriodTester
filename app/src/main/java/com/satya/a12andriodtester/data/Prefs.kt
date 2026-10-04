package com.satya.a12andriodtester.data

import android.content.Context

object Prefs {
    private const val FILE = "app_prefs"
    private const val ONBOARDING_DONE = "onboarding_done"

    fun onboardingDone(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(ONBOARDING_DONE, false)

    fun setOnboardingDone(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(ONBOARDING_DONE, true).apply()
    }
}
