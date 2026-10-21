package com.example.cuidaleapp.utils

import android.content.Context
import android.content.Intent
import com.example.cuidaleapp.activities.MenuActivity

object InAppNotificationNavigator {

    fun navigate(context: Context, notification: InAppNotification) {
        val intent = Intent(context, MenuActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("TARGET_SCREEN", notification.targetScreen)
            putExtra("FROM_LOCAL_PUSH", true)
            notification.payload.forEach { (key, value) -> putExtra(key, value) }
        }
        context.startActivity(intent)
    }
}
