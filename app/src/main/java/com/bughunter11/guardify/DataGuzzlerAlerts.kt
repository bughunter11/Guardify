package com.bughunter11.guardify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bughunter11.guardify.data.DataWatch
import com.bughunter11.guardify.data.Prefs

/**
 * "Instagram ne aaj 312 MB mobile data kha liya. Mobile par block karein?" (Block / Ignore today).
 * Manifest receivers hain, isliye Guardify band ho tab bhi buttons kaam karte hain.
 */
object GuzzlerAlerts {
    const val CHANNEL = "data_guzzler"
    const val ACTION_BLOCK = "com.bughunter11.guardify.GUZZLER_BLOCK"
    const val ACTION_MUTE = "com.bughunter11.guardify.GUZZLER_MUTE"
    const val EXTRA_PKG = "pkg"
    const val EXTRA_LABEL = "label"

    fun notifId(pkg: String): Int = ("guzzler:$pkg").hashCode()

    /** burstBytes > 0 ho to message "kam time me tez data" wala banta hai. */
    fun show(ctx: Context, pkg: String, label: String, todayBytes: Long, burstBytes: Long) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return

        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Data guzzler alerts", NotificationManager.IMPORTANCE_HIGH),
        )

        val today = Formatter.formatShortFileSize(ctx, todayBytes)
        val title: String
        val text: String
        if (burstBytes > 0L) {
            title = "$label is using data fast"
            text = buildString {
                append(Formatter.formatShortFileSize(ctx, burstBytes))
                append(" of mobile data in the last 10 minutes, $today today. Block it on mobile data?")
                if (!FirewallVpnService.running.value) append(" Firewall is off, turn it on to enforce.")
            }
        } else {
            title = "$label used $today of mobile data today"
            text = buildString {
                append("That is a lot for one day. Block it on mobile data?")
                if (!FirewallVpnService.running.value) append(" Firewall is off, turn it on to enforce.")
            }
        }

        val open = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_guardify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Block mobile", action(ctx, pkg, label, ACTION_BLOCK, 1))
            .addAction(0, "Ignore today", action(ctx, pkg, label, ACTION_MUTE, 0))
            .build()
        runCatching { nm.notify(notifId(pkg), n) } // permission na ho to crash nahi
    }

    private fun action(ctx: Context, pkg: String, label: String, act: String, idx: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            pkg.hashCode() * 2 + idx + 7,
            Intent(ctx, GuzzlerActionReceiver::class.java)
                .setAction(act)
                .putExtra(EXTRA_PKG, pkg)
                .putExtra(EXTRA_LABEL, label),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

/** Notification ke Block mobile / Ignore today buttons. */
class GuzzlerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra(GuzzlerAlerts.EXTRA_PKG) ?: return
        val label = intent.getStringExtra(GuzzlerAlerts.EXTRA_LABEL) ?: pkg
        val prefs = Prefs(context)
        val msg = when (intent.action) {
            GuzzlerAlerts.ACTION_BLOCK -> {
                prefs.set(pkg, mobile = true, blocked = true)
                "$label blocked on mobile data"
            }
            GuzzlerAlerts.ACTION_MUTE -> {
                val day = DataWatch.dayStart(System.currentTimeMillis())
                prefs.setGuzzlerDone(prefs.guzzlerDone() + "mute|$pkg|$day")
                "No more alerts for $label today"
            }
            else -> return
        }
        NotificationManagerCompat.from(context).cancel(GuzzlerAlerts.notifId(pkg))
        runCatching { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    }
}

/**
 * Firewall band ho tab bhi alert chale, isliye har ~15 min me ye alarm jagata hai.
 * Firewall chal rahi ho to service har minute khud dekh leti hai, yahan sirf agla alarm lagta hai.
 */
class DataWatchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        if (!prefs.guzzler()) return
        schedule(app) // agla chakkar pehle hi lagao, jaanch me der lage to bhi
        if (FirewallVpnService.running.value) return
        val pending = goAsync()
        Thread {
            try {
                runCatching { DataWatch.check(app, prefs, null) }
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        private const val REQ = 4207
        private const val INTERVAL_MS = 15L * 60 * 1000

        private fun pi(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
            ctx,
            REQ,
            Intent(ctx, DataWatchReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Inexact alarm: exact alarm ki permission nahi chahiye, Doze me bhi (thodi der se) chalta hai. */
        fun schedule(ctx: Context) {
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return
            runCatching {
                am.setAndAllowWhileIdle(AlarmManager.RTC, System.currentTimeMillis() + INTERVAL_MS, pi(ctx))
            }
        }

        fun cancel(ctx: Context) {
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return
            runCatching { am.cancel(pi(ctx)) }
        }
    }
}

/** Phone restart / app update ke baad alert ka alarm dobara lagao. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                // Restart ke baad firewall band hoti hai, to purana "sab mobile band" bhi hata do
                if (prefs.killMobile()) prefs.setKillMobile(false)
                if (prefs.roamOn()) prefs.setRoamOn(false) // roaming ka manual switch bhi, warna achanak lagta
                if (prefs.guzzler()) DataWatchReceiver.schedule(app)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> if (prefs.guzzler()) DataWatchReceiver.schedule(app)
        }
    }
}
