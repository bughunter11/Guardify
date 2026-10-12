package com.bughunter11.guardify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bughunter11.guardify.data.Prefs

/**
 * Nayi app install hote hi notification: "Isko net dena hai ya block?" (Block / Allow buttons).
 * Manifest receiver hai, isliye Guardify ya firewall band ho tab bhi alert aata hai.
 * "Hold" on ho to app decision aane tak WiFi aur mobile dono par block rehti hai.
 */
object NewAppAlerts {
    const val CHANNEL = "new_apps"
    const val ACTION_ALLOW = "com.bughunter11.guardify.ALLOW"
    const val ACTION_BLOCK = "com.bughunter11.guardify.BLOCK"
    const val EXTRA_PKG = "pkg"

    fun onInstalled(ctx: Context, pkg: String) {
        val prefs = Prefs(ctx)
        if (!prefs.newAppAlerts()) return
        // Pehle se rule wali app (reinstall) ka faisla badalna nahi
        if (pkg in prefs.wifi() || pkg in prefs.mobile()) return

        val pm = ctx.packageManager
        val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return
        val label = runCatching { info.loadLabel(pm).toString() }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: pkg

        val hold = prefs.holdNew()
        if (hold) prefs.setRules(prefs.wifi() + pkg, prefs.mobile() + pkg)

        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return

        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "New app alerts", NotificationManager.IMPORTANCE_HIGH),
        )

        val text = buildString {
            append("Allow internet for this app, or block it?")
            if (hold) append(" It is blocked until you decide.")
            if (!FirewallVpnService.running.value) append(" Firewall is off, turn it on to enforce.")
        }
        val open = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_guardify)
            .setContentTitle("New app: $label")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Block", action(ctx, pkg, ACTION_BLOCK, 1))
            .addAction(0, "Allow", action(ctx, pkg, ACTION_ALLOW, 0))
            .build()
        runCatching { nm.notify(pkg.hashCode(), n) } // permission na ho to crash nahi
    }

    private fun action(ctx: Context, pkg: String, act: String, idx: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            pkg.hashCode() * 2 + idx,
            Intent(ctx, NewAppActionReceiver::class.java).setAction(act).putExtra(EXTRA_PKG, pkg),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

class NewAppReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return // update hai, install nahi
        val pkg = intent.data?.schemeSpecificPart ?: return
        if (pkg == context.packageName) return
        runCatching { NewAppAlerts.onInstalled(context.applicationContext, pkg) }
    }
}

/** Notification ke Block / Allow buttons. Rule prefs me likhta hai, chalti service use khud utha leti hai. */
class NewAppActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra(NewAppAlerts.EXTRA_PKG) ?: return
        val prefs = Prefs(context)
        when (intent.action) {
            NewAppAlerts.ACTION_BLOCK -> prefs.setRules(prefs.wifi() + pkg, prefs.mobile() + pkg)
            NewAppAlerts.ACTION_ALLOW -> prefs.setRules(prefs.wifi() - pkg, prefs.mobile() - pkg)
            else -> return
        }
        NotificationManagerCompat.from(context).cancel(pkg.hashCode())
    }
}
