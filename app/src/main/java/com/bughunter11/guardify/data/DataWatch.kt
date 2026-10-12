package com.bughunter11.guardify.data

import android.content.Context
import com.bughunter11.guardify.GuzzlerAlerts
import java.util.Calendar

/**
 * Data-guzzler: koi app aaj (raat 12 se) bahut mobile data kha le, ya kam time me achanak
 * bahut zyada kha le, to turant notification. Suggestion card ka real-time version.
 *
 * Service chalti ho to har minute (mobile par hote hi) yahi chalta hai. Firewall band ho to
 * DataWatchReceiver ka alarm har ~15 min me chalata hai. Dono jagah ek hi dedupe Prefs me hota hai,
 * isliye ek app ka ek din me ek hi daily alert aata hai.
 */
object DataWatch {

    private const val MB = LimitEngine.MB

    /** Spike = itne time me ... */
    private const val SPIKE_WINDOW_MS = 10L * 60 * 1000

    /** ... itna data. */
    const val SPIKE_BYTES = 50L * MB

    /** Ek app ka spike alert iske baad hi dobara aa sakta hai. */
    private const val SPIKE_COOLDOWN_MS = 60L * 60 * 1000

    // uid -> (time, aaj ka total bytes). Sirf pichhle 10 min ke nishan rakhte hain.
    private val samples = HashMap<Int, ArrayDeque<Pair<Long, Long>>>()
    private val lastSpike = HashMap<String, Long>()

    fun dayStart(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /**
     * Ek jaanch. Blocking hai, background thread par chalao.
     * apps = null ho to list yahin load hoti hai (alarm wale raaste me).
     */
    @Synchronized
    fun check(ctx: Context, prefs: Prefs, apps: List<AppInfo>?, now: Long = System.currentTimeMillis()) {
        if (!prefs.guzzler() || !UsageRepository.hasAccess(ctx)) return
        val list = apps ?: AppRepository.load(ctx)
        val day = dayStart(now)
        val usage = UsageRepository.mobileSinceBlocking(ctx, day)

        val threshold = prefs.guzzlerBytes()
        val spikeOn = prefs.guzzlerSpike()
        val saved = prefs.guzzlerDone()
        // Purane dino ki entries yahin saaf ho jati hain
        val done = saved.filterTo(HashSet()) { it.endsWith("|$day") }
        // Jo pehle se mobile par band hain unka alert bekaar hai
        val roamBlocked = if (RoamingMode.isActive(prefs, RoamingMode.isNetworkRoaming(ctx))) {
            RoamingMode.blocked(list, prefs, RoamingMode.suggest(ctx, list))
        } else {
            emptySet()
        }
        val skip = prefs.mobile() + prefs.limitBlocked() + QuickActions.killBlocked(list, prefs) + roamBlocked

        val seen = HashSet<Int>()
        for (app in list) {
            if (app.isSystem || app.pkg in skip) continue
            val bytes = usage[app.uid] ?: 0L
            if (bytes <= 0L) continue
            if (!seen.add(app.uid)) continue // ek uid par kai package ho to ek hi baar

            val q = samples.getOrPut(app.uid) { ArrayDeque() }
            if (q.isNotEmpty() && bytes < q.last().second) q.clear() // naya din, counter ghata
            q.addLast(now to bytes)
            while (q.size > 1 && now - q.first().first > SPIKE_WINDOW_MS) q.removeFirst()
            val burst = bytes - q.first().second

            if ("mute|${app.pkg}|$day" in done) continue
            val key = "${app.pkg}|$day"
            val spiking = spikeOn && burst >= SPIKE_BYTES &&
                now - (lastSpike[app.pkg] ?: 0L) >= SPIKE_COOLDOWN_MS
            val heavy = bytes >= threshold && key !in done
            if (spiking || heavy) {
                if (spiking) lastSpike[app.pkg] = now
                done += key
                GuzzlerAlerts.show(ctx, app.pkg, app.label, bytes, if (spiking) burst else 0L)
            }
        }
        if (done != saved) prefs.setGuzzlerDone(done)
    }
}
