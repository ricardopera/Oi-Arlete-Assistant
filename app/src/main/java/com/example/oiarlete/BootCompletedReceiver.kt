package com.example.oiarlete

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, ArleteService::class.java)
            serviceIntent.action = "com.example.oiarlete.ACTION_START"
            context.startForegroundService(serviceIntent)
        }
    }
}
