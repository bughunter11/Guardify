package com.bughunter11.guardify.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Kaun si app abhi screen par khuli hai, UsageEvents se pata chalta hai (usage access wali wahi permission).
 * Background data block isi par chalta hai: app khuli ho tab net chalta hai, band hote hi (grace ke baad) band.
 * Activity level par track karte hain, isliye ek app ki kai activities ya split-screen me bhi sahi chalta hai.
 */
class ForegroundTracker(context: Context) {

    private val usm = context.applicationContext.getSystemService(UsageStatsManager::class.java)
    private val resumed = HashSet<String>()      // "pkg/class"
    private val leftAt = HashMap<String, Long>() // app kab foreground se gayi
    private var cursor = 0L

    @Volatile
    var foreground: Set<String> = emptySet()
        private set

    /** Naye events padho. Foreground set badla ho to true. */
    fun poll(now: Long = System.currentTimeMillis()): Boolean {
        val first = cursor == 0L
        val from = if (first) now - LOOKBACK_MS else cursor
        val events = try {
            usm.queryEvents(from, now + 1_000L)
        } catch (e: Exception) {
            return false
        }
        val e = UsageEvents.Event()
        var newest = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val key = e.packageName + "/" + (e.className ?: "")
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> resumed.add(key)
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> resumed.remove(key)
            }
            if (e.timeStamp > newest) newest = e.timeStamp
        }
        cursor = maxOf(cursor, newest + 1L)
        if (first) cursor = maxOf(cursor, now - 10_000L)
        return publish(now)
    }

    /** Screen band: sab apps background me maani jati hain. */
    fun screenOff(now: Long = System.currentTimeMillis()) {
        resumed.clear()
        publish(now)
    }

    private fun publish(now: Long): Boolean {
        val fresh = HashSet<String>()
        for (k in resumed) fresh.add(k.substringBefore('/'))
        val old = foreground
        if (fresh == old) return false
        for (p in old) if (p !in fresh) leftAt[p] = now
        for (p in fresh) leftAt.remove(p)
        foreground = fresh
        return true
    }

    /** True = ye app background me hai aur grace time bhi nikal chuka. */
    fun isBackground(pkg: String, now: Long, graceMs: Long): Boolean {
        if (pkg in foreground) return false
        val left = leftAt[pkg] ?: return true
        return now - left >= graceMs
    }

    /** Koi app abhi grace window me hai? (to service jaldi dobara check karti hai) */
    fun anyInGrace(pkgs: Collection<String>, now: Long, graceMs: Long): Boolean {
        if (graceMs <= 0L) return false
        for (p in pkgs) {
            if (p in foreground) continue
            val left = leftAt[p] ?: continue
            if (now - left < graceMs) return true
        }
        return false
    }

    private companion object {
        const val LOOKBACK_MS = 2L * 60 * 60 * 1000
    }
}
