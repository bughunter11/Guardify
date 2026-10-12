package com.bughunter11.guardify.data

import android.content.Context
import java.util.Calendar

/**
 * Per-app monthly mobile data limit. Usage billing cycle ke start se gina jata hai
 * (default: mahine ki 1 tarikh). Limit paar hote hi app mobile data par apne aap block ho jati hai,
 * naye cycle me apne aap wapas khul jati hai (kyunki har baar nayi gintee hoti hai).
 */
object LimitEngine {

    // Phone ka data usage screen bhi decimal (1 GB = 1000 MB) dikhata hai, isliye wahi use karte hain
    const val MB = 1_000_000L
    const val GB = 1_000_000_000L

    /** Cycle ka pichla start (epoch ms). day = 1..28 */
    fun cycleStart(day: Int, now: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, day.coerceIn(1, 28))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis > now) c.add(Calendar.MONTH, -1)
        return c.timeInMillis
    }

    /** Agla cycle kab shuru hoga (counter reset hone ka time) */
    fun nextCycleStart(day: Int, now: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = cycleStart(day, now)
            add(Calendar.MONTH, 1)
        }
        return c.timeInMillis
    }

    /** Jin apps ne limit paar kar di unke package names. */
    fun overLimit(
        limits: Map<String, Long>,
        usageByUid: Map<Int, Long>,
        uidOf: (String) -> Int?,
    ): Set<String> {
        if (limits.isEmpty()) return emptySet()
        val out = HashSet<String>()
        for ((pkg, limit) in limits) {
            val uid = uidOf(pkg) ?: continue
            if ((usageByUid[uid] ?: 0L) >= limit) out.add(pkg)
        }
        return out
    }

    /**
     * Blocking version, service background thread par chalata hai (app band ho tab bhi).
     * Rules badle to true.
     */
    fun evaluate(ctx: Context, prefs: Prefs): Boolean {
        val limits = prefs.limits()
        val old = prefs.limitBlocked()
        if (limits.isEmpty()) {
            if (old.isEmpty()) return false
            prefs.setLimitBlocked(emptySet())
            return true
        }
        if (!UsageRepository.hasAccess(ctx)) return false
        val usage = UsageRepository.mobileSinceBlocking(ctx, cycleStart(prefs.cycleDay()))
        val pm = ctx.packageManager
        val over = overLimit(limits, usage) { pkg ->
            runCatching { pm.getApplicationInfo(pkg, 0).uid }.getOrNull()
        }
        if (over == old) return false
        prefs.setLimitBlocked(over)
        return true
    }
}
