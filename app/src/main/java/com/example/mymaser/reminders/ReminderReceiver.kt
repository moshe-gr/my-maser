package com.example.mymaser.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.mymaser.settings.dismissPrediction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles "Not this month" on an expected-entry notification without opening the app. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SKIP) return
        val key = intent.getStringExtra(EXTRA_PREDICTION_KEY) ?: return
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                context.applicationContext.dismissPrediction(key)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SKIP = "com.example.mymaser.action.SKIP_PREDICTION"
        const val EXTRA_PREDICTION_KEY = "prediction_key"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
