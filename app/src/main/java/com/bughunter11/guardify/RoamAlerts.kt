package com.bughunter11.guardify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Network roaming par aate hi (aur "Auto" on ho) apps achanak offline ho jati hain, isliye
 * ek notification batata hai ki kya hua. Tap karne par seedha Roaming sheet khulti hai.
 */
object RoamAlerts {
    const val CHANNEL = "roaming"
    const val EXTRA_SHEET = "guardify_sheet"
    const val SHEET_ROAMING = 6
    private const val NOTIF_ID = 6107

    fun show(ctx: Context, onlineCount: Int) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return

        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Roaming mode", NotificationManager.IMPORTANCE_DEFAULT),
        )

        val text = if (onlineCount == 1) {
            "You are roaming. Only 1 essential app can use mobile data. Tap to review."
        } else {
            "You are roaming. Only $onlineCount essential apps can use mobile data. Tap to review."
        }
        val open = PendingIntent.getActivity(
            ctx,
            NOTIF_ID,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_SHEET, SHEET_ROAMING),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_guardify)
            .setContentTitle("Roaming mode is on")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIF_ID, n) } // permission na ho to crash nahi
    }

    fun cancel(ctx: Context) {
        runCatching { NotificationManagerCompat.from(ctx).cancel(NOTIF_ID) }
    }
}
