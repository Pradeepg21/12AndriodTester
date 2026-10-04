package com.satya.a12andriodtester

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.satya.a12andriodtester.data.AuthRepository

class TesterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel("updates", "Request updates", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Status changes, payment verification and chat replies"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        AuthRepository.start()
    }
}
