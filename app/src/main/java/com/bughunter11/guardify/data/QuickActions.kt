package com.bughunter11.guardify.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.VpnService
import android.service.quicksettings.TileService
import com.bughunter11.guardify.FirewallVpnService
import com.bughunter11.guardify.FocusTileService
import com.bughunter11.guardify.KillTileService
import com.bughunter11.guardify.RoamTileService
import com.bughunter11.guardify.widget.GuardifyWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Ek tap wale controls: "sab mobile data band" aur "focus shuru". Quick Settings tile, home widget
 * aur app, teeno yahi use karte hain, isliye teeno hamesha ek hi sach dikhate hain.
 * Sab Prefs par chalta hai, to chalti hui firewall service use turant utha leti hai.
 */
object QuickActions {

    const val EXTRA = "guardify_quick"
    const val KILL = "kill"
    const val FOCUS = "focus"
    const val ROAM = "roam"

    /** Tile / widget ko ek nazar me jo chahiye. */
    class State(
        val firewallOn: Boolean,
        val kill: Boolean,
        val focusUntil: Long,
        val focusMinutes: Int,
        /** Roaming mode ka manual switch aur auto setting, aur network abhi roaming par hai ya nahi. */
        val roamOn: Boolean,
        val roamAuto: Boolean,
        val networkRoaming: Boolean,
        /** VPN permission mil chuki hai? Nahi to tile / widget app kholte hain. */
        val vpnReady: Boolean,
    ) {
        /** Block tabhi sach me lag raha hai jab firewall bhi chal rahi ho. */
        val killActive: Boolean get() = kill && firewallOn

        /** Kill maanga gaya hai par firewall abhi start ho rahi hai (widget "Starting" dikhaye). */
        val killPending: Boolean get() = kill && !firewallOn
        val focusActive: Boolean get() = focusUntil > System.currentTimeMillis()

        /** Roaming mode tabhi sach me lag raha hai jab firewall chal rahi ho. */
        val roamActive: Boolean get() = firewallOn && (roamOn || (roamAuto && networkRoaming))
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun state(ctx: Context): State {
        val p = Prefs(ctx)
        return State(
            firewallOn = FirewallVpnService.running.value,
            kill = p.killMobile(),
            focusUntil = p.focusUntil(),
            focusMinutes = p.quickFocusMinutes(),
            roamOn = p.roamOn(),
            roamAuto = p.roamAuto(),
            networkRoaming = RoamingMode.isNetworkRoaming(ctx),
            vpnReady = runCatching { VpnService.prepare(ctx.applicationContext) == null }.getOrDefault(false),
        )
    }

    /** Sab mobile data block / allow. True = ab block hai. Permission mil chuki ho tabhi bulao. */
    fun toggleKill(ctx: Context): Boolean {
        val app = ctx.applicationContext
        val p = Prefs(app)
        val on = !(p.killMobile() && FirewallVpnService.running.value)
        p.setKillMobile(on)
        if (on) ensureService(app)
        refreshSurfaces(app)
        return on
    }

    /** Roaming mode on / off. True = ab on hai. Permission mil chuki ho tabhi bulao. */
    fun toggleRoam(ctx: Context): Boolean {
        val app = ctx.applicationContext
        val p = Prefs(app)
        val on = !(p.roamOn() && FirewallVpnService.running.value)
        p.setRoamOn(on)
        if (on) ensureService(app)
        refreshSurfaces(app)
        return on
    }

    /** Focus ko toggle nahi, seedha shuru karo (permission ke baad pending kaam ke liye). */
    fun perform(ctx: Context, which: String) {
        val app = ctx.applicationContext
        when (which) {
            KILL -> {
                Prefs(app).setKillMobile(true)
                ensureService(app)
                refreshSurfaces(app)
            }
            FOCUS -> if (Prefs(app).focusUntil() <= System.currentTimeMillis()) toggleFocus(app)
            ROAM -> {
                Prefs(app).setRoamOn(true)
                ensureService(app)
                refreshSurfaces(app)
            }
        }
    }

    /**
     * Focus shuru ya khatam. True = ab focus chal raha hai. Apps ki list load hoti hai,
     * isliye main thread par mat bulao.
     */
    fun toggleFocus(ctx: Context): Boolean {
        val app = ctx.applicationContext
        val p = Prefs(app)
        if (p.focusUntil() > System.currentTimeMillis()) {
            FocusMode.end(p)
            refreshSurfaces(app)
            return false
        }
        val pkgs = focusTargets(runCatching { AppRepository.load(app) }.getOrDefault(emptyList()), p)
        if (pkgs.isEmpty()) return false
        FocusMode.start(p, pkgs, System.currentTimeMillis() + p.quickFocusMinutes() * 60_000L)
        ensureService(app)
        refreshSurfaces(app)
        return true
    }

    /**
     * Quick focus me kaun si apps band hongi: tumhari chuni hui, warna Social + Games + Video.
     * Phone se hataayi hui apps apne aap chhoot jati hain.
     */
    fun focusTargets(apps: List<AppInfo>, p: Prefs): Set<String> {
        val installed = apps.mapTo(HashSet()) { it.pkg }
        val chosen = p.quickFocusPkgs().filterTo(HashSet()) { it in installed }
        if (chosen.isNotEmpty()) return chosen
        val cats = setOf(
            ApplicationInfo.CATEGORY_SOCIAL,
            ApplicationInfo.CATEGORY_GAME,
            ApplicationInfo.CATEGORY_VIDEO,
        )
        return apps.filter { !it.isSystem && it.category in cats }.mapTo(HashSet()) { it.pkg }
    }

    /** Kill on ho to mobile par kaun si apps band hain. Service aur alerts dono yahi dekhte hain. */
    fun killBlocked(apps: List<AppInfo>?, p: Prefs): Set<String> {
        if (apps == null || !p.killMobile()) return emptySet()
        val exempt = p.killExempt()
        val system = p.killSystem()
        return apps.filter { (system || !it.isSystem) && it.pkg !in exempt }.mapTo(HashSet()) { it.pkg }
    }

    /**
     * Service start hone tak (max ~2 sec) ruko. Widget ko tap ke turant baad redraw karne se pehle
     * isse bulao, warna widget purani "off" state dikhata rehta hai. Blocking, background thread par.
     */
    fun awaitFirewall(maxMs: Long = 2_000L) {
        var waited = 0L
        while (!FirewallVpnService.running.value && waited < maxMs) {
            try {
                Thread.sleep(100L)
            } catch (e: InterruptedException) {
                return
            }
            waited += 100L
        }
    }

    private fun ensureService(app: Context) {
        if (!FirewallVpnService.running.value) {
            runCatching { app.startService(Intent(app, FirewallVpnService::class.java)) }
        }
    }

    /** Tile aur widget ko turant naya haal dikhane ko kaho. Kahin se bhi, kisi bhi thread se. */
    fun refreshSurfaces(ctx: Context) {
        val app = ctx.applicationContext
        runCatching { TileService.requestListeningState(app, ComponentName(app, KillTileService::class.java)) }
        runCatching { TileService.requestListeningState(app, ComponentName(app, FocusTileService::class.java)) }
        runCatching { TileService.requestListeningState(app, ComponentName(app, RoamTileService::class.java)) }
        scope.launch { runCatching { GuardifyWidget().updateAll(app) } }
    }
}
