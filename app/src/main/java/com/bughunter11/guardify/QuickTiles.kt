package com.bughunter11.guardify

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.format.DateFormat
import com.bughunter11.guardify.data.QuickActions
import java.util.Date

/**
 * Quick Settings tiles. Ek tap me kaam, long-press par Guardify ka Quick controls sheet khulta hai.
 * VPN permission na mili ho to tile app kholta hai, permission ke baad kaam apne aap ho jata hai.
 */
abstract class BaseQuickTile : TileService() {

    protected abstract fun render(tile: Tile, s: QuickActions.State)

    override fun onStartListening() {
        refresh()
    }

    protected fun refresh() {
        val tile = qsTile ?: return
        render(tile, QuickActions.state(this))
        tile.updateTile()
    }

    @Suppress("DEPRECATION")
    protected fun openApp(which: String, requestCode: Int) {
        val intent = Intent(this, MainActivity::class.java)
            .setAction("guardify.quick.$which") // alag tile ke PendingIntent alag rahein
            .setData(Uri.parse("guardify://quick/$which"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(QuickActions.EXTRA, which)
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pi)
        } else {
            startActivityAndCollapse(intent)
        }
    }
}

/** Ek tap: har app mobile data par band / wapas. */
class KillTileService : BaseQuickTile() {

    override fun render(tile: Tile, s: QuickActions.State) {
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_mobile)
        tile.label = getString(R.string.tile_kill_label)
        when {
            !s.vpnReady -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = "Tap to set up"
            }
            s.killActive -> {
                tile.state = Tile.STATE_ACTIVE
                tile.subtitle = "All apps blocked"
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = "Tap to block"
            }
        }
    }

    override fun onClick() {
        unlockAndRun {
            val s = QuickActions.state(this)
            if (!s.vpnReady) {
                openApp(QuickActions.KILL, 1)
                return@unlockAndRun
            }
            QuickActions.toggleKill(this)
            refresh()
        }
    }
}

/** Ek tap: Focus shuru (chuni hui ya Social / Games / Video apps), dobara tap par khatam. */
class FocusTileService : BaseQuickTile() {

    override fun render(tile: Tile, s: QuickActions.State) {
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_focus)
        tile.label = getString(R.string.tile_focus_label)
        if (s.focusActive) {
            tile.state = Tile.STATE_ACTIVE
            tile.subtitle = "Until " + DateFormat.getTimeFormat(this).format(Date(s.focusUntil))
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.subtitle = if (s.vpnReady) "${s.focusMinutes} min" else "Tap to set up"
        }
    }

    override fun onClick() {
        unlockAndRun {
            val s = QuickActions.state(this)
            if (!s.vpnReady) {
                openApp(QuickActions.FOCUS, 2)
                return@unlockAndRun
            }
            val app = applicationContext
            val main = Handler(Looper.getMainLooper())
            // Apps ki list load hoti hai, isliye main thread se hatkar
            Thread {
                QuickActions.toggleFocus(app)
                main.post { refresh() }
            }.start()
        }
    }
}

/**
 * Ek tap: Roaming mode on / off. Auto-roaming se lagi ho to bhi "on" dikhta hai.
 * Tap karne par manual switch badalta hai. Auto se lagi mode ko off karna ho to app me Auto band karo.
 */
class RoamTileService : BaseQuickTile() {

    override fun render(tile: Tile, s: QuickActions.State) {
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_roam)
        tile.label = getString(R.string.tile_roam_label)
        when {
            !s.vpnReady -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = "Tap to set up"
            }
            s.roamActive -> {
                tile.state = Tile.STATE_ACTIVE
                tile.subtitle = if (s.roamOn) "Essential apps only" else "Auto, roaming"
            }
            s.networkRoaming -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = "Roaming, tap to save data"
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = "Travel mode"
            }
        }
    }

    override fun onClick() {
        unlockAndRun {
            val s = QuickActions.state(this)
            if (!s.vpnReady) {
                openApp(QuickActions.ROAM, 3)
                return@unlockAndRun
            }
            QuickActions.toggleRoam(this)
            refresh()
        }
    }
}
