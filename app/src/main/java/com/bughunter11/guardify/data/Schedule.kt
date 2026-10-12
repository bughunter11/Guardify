package com.bughunter11.guardify.data

import android.content.pm.ApplicationInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

enum class Scope { ALL, CATEGORY, APPS }

const val ALL_DAYS = 0b1111111      // bit0 = Mon ... bit6 = Sun
const val WEEKDAYS = 0b0011111
const val WEEKEND = 0b1100000

/**
 * Ek schedule rule. Raat 12 se subah 6 tak "sab apps mobile par band" ya padhai ke time "social apps band".
 * Start > end ho to rule raat paar karta hai (jaise 11 PM se 6 AM).
 */
data class Schedule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    val startMin: Int,
    val endMin: Int,
    val days: Int = ALL_DAYS,
    val wifi: Boolean = false,
    val mobile: Boolean = true,
    val scope: Scope = Scope.ALL,
    val category: Int = ApplicationInfo.CATEGORY_SOCIAL,
    val includeSystem: Boolean = false,
    val pkgs: Set<String> = emptySet(),
) {
    fun hasDay(dow: Int): Boolean = (days and (1 shl dow)) != 0

    fun isActiveAt(now: Long): Boolean {
        if (!enabled) return false
        val c = Calendar.getInstance().apply { timeInMillis = now }
        val today = dowIndex(c)
        val yesterday = (today + 6) % 7
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return when {
            startMin == endMin -> hasDay(today) // poora din
            startMin < endMin -> hasDay(today) && m >= startMin && m < endMin
            else -> (hasDay(today) && m >= startMin) || (hasDay(yesterday) && m < endMin)
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("enabled", enabled)
        put("start", startMin)
        put("end", endMin)
        put("days", days)
        put("wifi", wifi)
        put("mobile", mobile)
        put("scope", scope.name)
        put("category", category)
        put("system", includeSystem)
        put("pkgs", JSONArray().also { a -> pkgs.forEach { a.put(it) } })
    }

    companion object {
        /** Monday = 0 ... Sunday = 6 */
        fun dowIndex(c: Calendar): Int = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7

        fun fromJson(o: JSONObject): Schedule {
            val arr = o.optJSONArray("pkgs") ?: JSONArray()
            val pk = HashSet<String>()
            for (i in 0 until arr.length()) pk.add(arr.getString(i))
            return Schedule(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = o.optString("name", "Schedule"),
                enabled = o.optBoolean("enabled", true),
                startMin = o.optInt("start", 0),
                endMin = o.optInt("end", 360),
                days = o.optInt("days", ALL_DAYS),
                wifi = o.optBoolean("wifi", false),
                mobile = o.optBoolean("mobile", true),
                scope = runCatching { Scope.valueOf(o.optString("scope", "ALL")) }.getOrDefault(Scope.ALL),
                category = o.optInt("category", ApplicationInfo.CATEGORY_SOCIAL),
                includeSystem = o.optBoolean("system", false),
                pkgs = pk,
            )
        }
    }
}

object ScheduleEngine {

    /** Is network par abhi kin apps ko schedules ne block kiya hai. apps = null ho to sirf "Pick apps" rules lagte hain. */
    fun blockedNow(list: List<Schedule>, apps: List<AppInfo>?, mobile: Boolean, now: Long): Set<String> {
        val out = HashSet<String>()
        for (s in list) {
            if (!s.isActiveAt(now)) continue
            if (mobile && !s.mobile) continue
            if (!mobile && !s.wifi) continue
            when (s.scope) {
                Scope.APPS -> out.addAll(s.pkgs)
                Scope.CATEGORY -> apps?.forEach { if (it.category == s.category) out.add(it.pkg) }
                Scope.ALL -> apps?.forEach { if (s.includeSystem || !it.isSystem) out.add(it.pkg) }
            }
        }
        return out
    }

    fun activeCount(list: List<Schedule>, now: Long): Int = list.count { it.isActiveAt(now) }

    /** Agla time jab koi bhi schedule shuru ya khatam hoga. Koi nahi to Long.MAX_VALUE. */
    fun nextBoundary(list: List<Schedule>, now: Long): Long {
        var best = Long.MAX_VALUE
        val c = Calendar.getInstance()
        for (s in list) {
            if (!s.enabled) continue
            for (offset in 0..8) {
                for (min in intArrayOf(s.startMin, s.endMin)) {
                    c.timeInMillis = now
                    c.add(Calendar.DAY_OF_YEAR, offset)
                    c.set(Calendar.HOUR_OF_DAY, min / 60)
                    c.set(Calendar.MINUTE, min % 60)
                    c.set(Calendar.SECOND, 0)
                    c.set(Calendar.MILLISECOND, 0)
                    val t = c.timeInMillis
                    if (t > now && t < best) best = t
                }
            }
        }
        return best
    }
}

/** Ek tap me lagne wale ready-made rules (jo tumne examples me bataye the). */
object SchedulePresets {
    fun night() = Schedule(
        name = "Night block",
        startMin = 0,
        endMin = 6 * 60,
        mobile = true,
        wifi = false,
        scope = Scope.ALL,
    )

    fun study() = Schedule(
        name = "Study time",
        startMin = 16 * 60,
        endMin = 20 * 60,
        days = WEEKDAYS,
        mobile = true,
        wifi = true,
        scope = Scope.CATEGORY,
        category = ApplicationInfo.CATEGORY_SOCIAL,
    )
}
