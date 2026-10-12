package com.bughunter11.guardify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.bughunter11.guardify.data.AppInfo
import com.bughunter11.guardify.data.AppRepository
import com.bughunter11.guardify.data.DataWatch
import com.bughunter11.guardify.data.FocusMode
import com.bughunter11.guardify.data.ForegroundTracker
import com.bughunter11.guardify.data.LimitEngine
import com.bughunter11.guardify.data.Prefs
import com.bughunter11.guardify.data.QuickActions
import com.bughunter11.guardify.data.RoamingMode
import com.bughunter11.guardify.data.ScheduleEngine
import com.bughunter11.guardify.data.UsageRepository
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Local "blackhole" VPN. Sirf blocked apps ko VPN se guzara jata hai (addAllowedApplication)
 * aur tun interface se kuch padha nahi jata, to unke saare packets drop ho jate hain.
 * Baaki apps VPN ko bypass karte hain, unki speed par koi farq nahi.
 *
 * Blocked set ab 7 cheezon se banta hai (network ke hisaab se):
 *   1. tumhare manual rules  2. focus mode  3. schedules  4. monthly data limit  5. background block
 *   6. "sab mobile data band" (tile / widget / app)
 *   7. Roaming mode: roaming par sirf zaroori apps ko mobile data
 * Data-guzzler alert bhi yahi se chalta hai (mobile par, har minute).
 * Saara kaam ek worker thread par hota hai, app band ho tab bhi chalta rehta hai.
 */
class FirewallVpnService : VpnService() {

    private enum class Net { WIFI, MOBILE, NONE }

    private var tun: ParcelFileDescriptor? = null

    @Volatile
    private var net = Net.NONE
    private var registered = false
    private var screenRegistered = false
    private var listening = false
    private lateinit var prefs: Prefs
    private lateinit var tracker: ForegroundTracker
    private lateinit var thread: HandlerThread
    private lateinit var worker: Handler

    @Volatile
    private var destroyed = false

    @Volatile
    private var screenOn = true

    // Network abhi roaming par hai? (NET_CAPABILITY_NOT_ROAMING na ho). Sirf mobile par sach ho sakta hai.
    @Volatile
    private var roaming = false

    // Roaming mode ki live suggestion (default dialer, SMS, maps...). Apps list ke saath hi taza hoti hai.
    @Volatile
    private var roamSuggested: Set<String> = emptySet()

    // Auto-roaming ka notification abhi dikh raha hai? Sirf badlav par dobara bhejte hain.
    private var roamAnnounced = false

    // Schedules ko category / "all apps" samajhne ke liye app list (ek baar load, phir cache)
    @Volatile
    private var apps: List<AppInfo>? = null
    private var appsLoadedAt = 0L
    private var appsLoading = false

    private var lastLimitCheck = 0L
    private var lastGuzzlerCheck = 0L

    // Jo set abhi VPN me laga hai. Wahi dobara aaye to VPN dobara nahi banta.
    private var appliedSet: Set<String>? = null
    private var appliedNet = Net.NONE

    private val loop = object : Runnable {
        override fun run() {
            val delay = try {
                step()
            } catch (e: Exception) {
                IDLE_MS
            }
            if (destroyed) return
            worker.removeCallbacks(this)
            worker.postDelayed(this, delay)
        }
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val n = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Net.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Net.MOBILE
                else -> Net.NONE
            }
            val r = n == Net.MOBILE && RoamingMode.isRoaming(caps)
            if (n != net || r != roaming) {
                net = n
                roaming = r
                if (n == Net.MOBILE) {
                    lastLimitCheck = 0L // mobile par aate hi limit dobara jaanch
                    lastGuzzlerCheck = 0L // aur data-guzzler bhi
                }
                kick()
            }
        }

        override fun onLost(network: Network) {
            net = Net.NONE
            roaming = false
            kick()
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> worker.post {
                    screenOn = false
                    tracker.screenOff()
                    loop.run()
                }
                Intent.ACTION_SCREEN_ON -> worker.post {
                    screenOn = true
                    loop.run()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        tracker = ForegroundTracker(this)
        thread = HandlerThread("guardify-worker").also { it.start() }
        worker = Handler(thread.looper)
        screenOn = getSystemService(PowerManager::class.java)?.isInteractive ?: true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!registered) {
            registered = runCatching {
                getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(callback)
            }.isSuccess
        }
        if (!screenRegistered) {
            screenRegistered = runCatching {
                ContextCompat.registerReceiver(
                    this,
                    screenReceiver,
                    IntentFilter().apply {
                        addAction(Intent.ACTION_SCREEN_ON)
                        addAction(Intent.ACTION_SCREEN_OFF)
                    },
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            }.isSuccess
        }
        if (!listening) {
            prefs.listen { kick() } // rules kahin se bhi badlen (notification button tak), turant lagu
            listening = true
        }
        if (!running.value) {
            running.value = true
            QuickActions.refreshSurfaces(this) // tile aur widget ko "on" dikhao
        }
        loadAppsIfStale()
        kick() // rules badle hon to bhi yahi chalta hai
        return START_STICKY
    }

    /** Loop turant chalao (worker par). */
    private fun kick() {
        if (destroyed) return
        worker.removeCallbacks(loop)
        worker.post(loop)
    }

    private fun loadAppsIfStale() {
        val now = System.currentTimeMillis()
        if (appsLoading || (apps != null && now - appsLoadedAt < APPS_TTL_MS)) return
        appsLoading = true
        worker.post {
            apps = runCatching { AppRepository.load(this) }.getOrNull() ?: apps
            apps?.let { list ->
                roamSuggested = runCatching { RoamingMode.suggest(this, list) }.getOrDefault(roamSuggested)
            }
            appsLoadedAt = System.currentTimeMillis()
            appsLoading = false
            loop.run()
        }
    }

    /** Ek chakkar: focus / limit / foreground / VPN, aur agla jaagne ka time (ms) wapas. */
    private fun step(): Long {
        val now = System.currentTimeMillis()
        // Focus ka time pura hua to tile / widget bhi taza karo
        if (FocusMode.endIfExpired(prefs, now)) QuickActions.refreshSurfaces(this)

        if (net == Net.MOBILE && now - lastLimitCheck >= LIMIT_INTERVAL_MS) {
            lastLimitCheck = now
            runCatching { LimitEngine.evaluate(this, prefs) }
        }

        if (net == Net.MOBILE && now - lastGuzzlerCheck >= GUZZLER_INTERVAL_MS && prefs.guzzler()) {
            lastGuzzlerCheck = now
            runCatching { DataWatch.check(this, prefs, apps, now) }
        }

        val bgPkgs = prefs.bgWifi() + prefs.bgMobile()
        val bgActive = bgPkgs.isNotEmpty() && UsageRepository.hasAccess(this)
        if (bgActive && screenOn) tracker.poll(now)

        rebuild(now, bgActive)
        announceRoaming()

        var delay = IDLE_MS
        val graceMs = prefs.bgGrace() * 1000L
        if (bgActive && screenOn) {
            delay = POLL_MS
        } else if (bgActive && tracker.anyInGrace(bgPkgs, now, graceMs)) {
            delay = 1_000L
        }
        var boundary = ScheduleEngine.nextBoundary(prefs.schedules(), now)
        val focusUntil = prefs.focusUntil()
        if (focusUntil in 1 until boundary) boundary = focusUntil
        if (boundary != Long.MAX_VALUE) {
            delay = minOf(delay, (boundary - now + 300L).coerceAtLeast(500L))
        }
        return delay.coerceAtLeast(500L)
    }

    /** Is network par abhi kaun si apps block honi chahiye. */
    private fun effectiveBlocked(now: Long, bgActive: Boolean): Set<String> {
        val out = HashSet<String>()
        val graceMs = prefs.bgGrace() * 1000L
        val schedules = prefs.schedules()
        when (net) {
            Net.NONE -> return out
            Net.WIFI -> {
                out += prefs.wifi()
                out += ScheduleEngine.blockedNow(schedules, apps, mobile = false, now = now)
                if (bgActive) prefs.bgWifi().filterTo(out) { tracker.isBackground(it, now, graceMs) }
            }
            Net.MOBILE -> {
                out += prefs.mobile()
                out += ScheduleEngine.blockedNow(schedules, apps, mobile = true, now = now)
                out += prefs.limitBlocked()
                out += QuickActions.killBlocked(apps, prefs) // "sab mobile data band"
                if (RoamingMode.isActive(prefs, roaming)) {
                    out += RoamingMode.blocked(apps, prefs, roamSuggested) // roaming: sirf zaroori apps online
                }
                if (bgActive) prefs.bgMobile().filterTo(out) { tracker.isBackground(it, now, graceMs) }
            }
        }
        return out
    }

    @Synchronized
    private fun rebuild(now: Long = System.currentTimeMillis(), bgActive: Boolean = false) {
        if (destroyed) return
        try {
            val blocked = effectiveBlocked(now, bgActive)
            if (appliedSet == blocked && appliedNet == net) return // kuch badla nahi
            appliedSet = null

            if (blocked.isEmpty()) {
                closeTun()
                appliedSet = blocked
                appliedNet = net
                return
            }

            val builder = Builder()
                .setSession("Guardify")
                .setBlocking(true)
                .addAddress("10.255.0.1", 32)
                .addRoute("0.0.0.0", 0)
                .addAddress("fd00::1", 128)
                .addRoute("::", 0)

            var added = 0
            for (pkg in blocked) {
                try {
                    builder.addAllowedApplication(pkg)
                    added++
                } catch (e: PackageManager.NameNotFoundException) {
                    // app uninstall ho chuka, skip
                }
            }
            if (added == 0) {
                closeTun()
                appliedSet = blocked
                appliedNet = net
                return
            }

            val fresh = try {
                builder.establish()
            } catch (e: Exception) {
                null
            }
            val old = tun
            tun = fresh
            runCatching { old?.close() }
            if (fresh == null) {
                stopSelf() // VPN permission chali gayi
            } else {
                appliedSet = blocked
                appliedNet = net
            }
        } catch (e: Exception) {
            // koi bhi ajeeb error aaye to app crash nahi hogi, agla chakkar dobara try karega
        }
    }

    /**
     * Auto-roaming apne aap lagi ho to ek notification (kyunki apps achanak offline ho jati hain).
     * Manual switch par nahi, kyunki wahan tumne khud dabaya hai. Roaming khatam hote hi hat jata hai.
     */
    private fun announceRoaming() {
        val auto = net == Net.MOBILE && roaming && prefs.roamAuto() && !prefs.roamOn()
        if (auto == roamAnnounced) return
        roamAnnounced = auto
        if (auto) {
            val kept = RoamingMode.essentials(prefs, roamSuggested).size
            RoamAlerts.show(this, kept)
        } else {
            RoamAlerts.cancel(this)
        }
    }

    @Synchronized
    private fun closeTun() {
        runCatching { tun?.close() }
        tun = null
    }

    override fun onRevoke() {
        stopSelf()
    }

    override fun onDestroy() {
        destroyed = true
        prefs.stopListening()
        worker.removeCallbacksAndMessages(null)
        thread.quitSafely()
        if (registered) {
            runCatching {
                getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(callback)
            }
            registered = false
        }
        if (screenRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            screenRegistered = false
        }
        closeTun()
        runCatching { RoamAlerts.cancel(applicationContext) }
        running.value = false
        runCatching { QuickActions.refreshSurfaces(applicationContext) } // tile aur widget "off" dikhao
        super.onDestroy()
    }

    companion object {
        val running = MutableStateFlow(false)

        private const val POLL_MS = 1_500L          // app khuli hai ya nahi, screen on ho tab
        private const val IDLE_MS = 30_000L
        private const val LIMIT_INTERVAL_MS = 60_000L
        private const val GUZZLER_INTERVAL_MS = 60_000L
        private const val APPS_TTL_MS = 10L * 60 * 1000
    }
}
