package com.bughunter11.guardify.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Per-app block rules aur theme choices, phone ke andar hi save hote hain. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("rules", Context.MODE_PRIVATE)

    fun wifi(): Set<String> = (sp.getStringSet(KEY_WIFI, emptySet()) ?: emptySet()).toSet()
    fun mobile(): Set<String> = (sp.getStringSet(KEY_MOBILE, emptySet()) ?: emptySet()).toSet()

    fun set(pkg: String, mobile: Boolean, blocked: Boolean) {
        val key = if (mobile) KEY_MOBILE else KEY_WIFI
        val s = (sp.getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()
        if (blocked) s.add(pkg) else s.remove(pkg)
        sp.edit().putStringSet(key, s).apply()
    }

    /** Dono rule sets ek saath save (bulk action, undo, reset) */
    fun setRules(wifi: Set<String>, mobile: Set<String>) {
        sp.edit()
            .putStringSet(KEY_WIFI, HashSet(wifi))
            .putStringSet(KEY_MOBILE, HashSet(mobile))
            .apply()
    }

    /** SortMode ka ordinal */
    fun sort(): Int = sp.getInt(KEY_SORT, 0)
    fun setSort(v: Int) { sp.edit().putInt(KEY_SORT, v).apply() }

    /** 0 = wallpaper (Material You), warna ARGB color */
    fun accent(): Int = sp.getInt(KEY_ACCENT, 0)
    fun setAccent(v: Int) { sp.edit().putInt(KEY_ACCENT, v).apply() }

    /** 0 = system, 1 = light, 2 = dark */
    fun mode(): Int = sp.getInt(KEY_MODE, 0)
    fun setMode(v: Int) { sp.edit().putInt(KEY_MODE, v).apply() }

    fun amoled(): Boolean = sp.getBoolean(KEY_AMOLED, false)
    fun setAmoled(v: Boolean) { sp.edit().putBoolean(KEY_AMOLED, v).apply() }

    // ----- Focus mode: kuch apps kuch der ke liye block, timer khatam hote hi wapas allow -----

    /** Focus khatam hone ka time (epoch ms). 0 = focus chal hi nahi raha. */
    fun focusUntil(): Long = sp.getLong(KEY_FOCUS_UNTIL, 0L)

    /** Focus ne jo apps naye block kiye (pehle se block wale yaha nahi aate), taaki sirf wahi wapas khulein. */
    fun focusAddedWifi(): Set<String> = (sp.getStringSet(KEY_FOCUS_WIFI, emptySet()) ?: emptySet()).toSet()
    fun focusAddedMobile(): Set<String> = (sp.getStringSet(KEY_FOCUS_MOBILE, emptySet()) ?: emptySet()).toSet()

    fun setFocus(until: Long, addedWifi: Set<String>, addedMobile: Set<String>) {
        sp.edit()
            .putLong(KEY_FOCUS_UNTIL, until)
            .putStringSet(KEY_FOCUS_WIFI, HashSet(addedWifi))
            .putStringSet(KEY_FOCUS_MOBILE, HashSet(addedMobile))
            .apply()
    }

    fun clearFocus() {
        sp.edit()
            .remove(KEY_FOCUS_UNTIL)
            .remove(KEY_FOCUS_WIFI)
            .remove(KEY_FOCUS_MOBILE)
            .apply()
    }

    // ----- Nayi app ka alert -----

    fun newAppAlerts(): Boolean = sp.getBoolean(KEY_NEW_ALERTS, false)
    fun setNewAppAlerts(v: Boolean) { sp.edit().putBoolean(KEY_NEW_ALERTS, v).apply() }

    /** Nayi app faisla aane tak WiFi + mobile dono par block */
    fun holdNew(): Boolean = sp.getBoolean(KEY_HOLD_NEW, false)
    fun setHoldNew(v: Boolean) { sp.edit().putBoolean(KEY_HOLD_NEW, v).apply() }

    // Service ko rules badalte hi jagane ke liye (notification button, UI, kahin se bhi)
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun listen(onChange: () -> Unit) {
        stopListening()
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        listener = l
        sp.registerOnSharedPreferenceChangeListener(l)
    }

    fun stopListening() {
        listener?.let { sp.unregisterOnSharedPreferenceChangeListener(it) }
        listener = null
    }

    // ----- Data limits: app -> bytes (mahine me mobile data), limit paar apps alag set me -----

    fun limits(): Map<String, Long> {
        val raw = sp.getString(KEY_LIMITS, null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { o.getLong(it) }
        }.getOrDefault(emptyMap())
    }

    fun setLimits(m: Map<String, Long>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v) }
        sp.edit().putString(KEY_LIMITS, o.toString()).apply()
    }

    fun limitBlocked(): Set<String> =
        (sp.getStringSet(KEY_LIMIT_BLOCKED, emptySet()) ?: emptySet()).toSet()

    fun setLimitBlocked(s: Set<String>) {
        sp.edit().putStringSet(KEY_LIMIT_BLOCKED, HashSet(s)).apply()
    }

    /** Billing cycle kis tarikh (1..28) se shuru hota hai */
    fun cycleDay(): Int = sp.getInt(KEY_CYCLE_DAY, 1).coerceIn(1, 28)
    fun setCycleDay(v: Int) { sp.edit().putInt(KEY_CYCLE_DAY, v.coerceIn(1, 28)).apply() }

    // ----- Schedules -----

    fun schedules(): List<Schedule> {
        val raw = sp.getString(KEY_SCHEDULES, null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).mapNotNull { i ->
                runCatching { Schedule.fromJson(a.getJSONObject(i)) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    fun setSchedules(list: List<Schedule>) {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        sp.edit().putString(KEY_SCHEDULES, a.toString()).apply()
    }

    // ----- Background block: app khuli ho tabhi net -----

    fun bgWifi(): Set<String> = (sp.getStringSet(KEY_BG_WIFI, emptySet()) ?: emptySet()).toSet()
    fun bgMobile(): Set<String> = (sp.getStringSet(KEY_BG_MOBILE, emptySet()) ?: emptySet()).toSet()

    fun setBg(wifi: Set<String>, mobile: Set<String>) {
        sp.edit()
            .putStringSet(KEY_BG_WIFI, HashSet(wifi))
            .putStringSet(KEY_BG_MOBILE, HashSet(mobile))
            .apply()
    }

    /** App band hone ke kitne second baad net kate (0 = turant) */
    fun bgGrace(): Int = sp.getInt(KEY_BG_GRACE, 10)
    fun setBgGrace(v: Int) { sp.edit().putInt(KEY_BG_GRACE, v).apply() }

    // ----- Quick controls: ek tap me sab mobile data band (tile / widget / app) -----

    /** True = har app mobile data par block (exempt aur system apps ko chhodkar). */
    fun killMobile(): Boolean = sp.getBoolean(KEY_KILL, false)
    fun setKillMobile(v: Boolean) { sp.edit().putBoolean(KEY_KILL, v).apply() }

    /** Kill on hone par bhi mobile data wali apps (jaise messaging) */
    fun killExempt(): Set<String> = (sp.getStringSet(KEY_KILL_EXEMPT, emptySet()) ?: emptySet()).toSet()
    fun setKillExempt(s: Set<String>) { sp.edit().putStringSet(KEY_KILL_EXEMPT, HashSet(s)).apply() }

    /** System apps bhi block karni hain? Default off, taaki phone ki zaroori services chalti rahein. */
    fun killSystem(): Boolean = sp.getBoolean(KEY_KILL_SYSTEM, false)
    fun setKillSystem(v: Boolean) { sp.edit().putBoolean(KEY_KILL_SYSTEM, v).apply() }

    /** Quick focus ke apps. Khali = Social, Games aur Video category. */
    fun quickFocusPkgs(): Set<String> = (sp.getStringSet(KEY_QF_PKGS, emptySet()) ?: emptySet()).toSet()
    fun setQuickFocusPkgs(s: Set<String>) { sp.edit().putStringSet(KEY_QF_PKGS, HashSet(s)).apply() }

    fun quickFocusMinutes(): Int = sp.getInt(KEY_QF_MIN, 30).coerceIn(5, 600)
    fun setQuickFocusMinutes(v: Int) { sp.edit().putInt(KEY_QF_MIN, v).apply() }

    // ----- Roaming mode: bahar jao to sirf zaroori apps ko mobile data -----

    /** Manual switch. True = zaroori apps ke alawa sab mobile data par band. */
    fun roamOn(): Boolean = sp.getBoolean(KEY_ROAM_ON, false)
    fun setRoamOn(v: Boolean) { sp.edit().putBoolean(KEY_ROAM_ON, v).apply() }

    /** Network roaming par ho to mode apne aap lag jaye. */
    fun roamAuto(): Boolean = sp.getBoolean(KEY_ROAM_AUTO, false)
    fun setRoamAuto(v: Boolean) { sp.edit().putBoolean(KEY_ROAM_AUTO, v).apply() }

    /** Roaming me bhi online rehne wali apps. Khali = phone se live suggestion. */
    fun roamEssential(): Set<String> = (sp.getStringSet(KEY_ROAM_APPS, emptySet()) ?: emptySet()).toSet()
    fun setRoamEssential(s: Set<String>) { sp.edit().putStringSet(KEY_ROAM_APPS, HashSet(s)).apply() }

    /** System apps bhi block karni hain? Default off, taaki calls, SMS aur phone ki services chalti rahein. */
    fun roamSystem(): Boolean = sp.getBoolean(KEY_ROAM_SYSTEM, false)
    fun setRoamSystem(v: Boolean) { sp.edit().putBoolean(KEY_ROAM_SYSTEM, v).apply() }

    // ----- Data-guzzler alerts: koi app ek din me bahut data kha le to turant notification -----

    fun guzzler(): Boolean = sp.getBoolean(KEY_GUZ_ON, false)
    fun setGuzzler(v: Boolean) { sp.edit().putBoolean(KEY_GUZ_ON, v).apply() }

    /** Ek din (raat 12 se) me ek app ki mobile data limit, iske baad alert */
    fun guzzlerBytes(): Long = sp.getLong(KEY_GUZ_BYTES, 250_000_000L)
    fun setGuzzlerBytes(v: Long) { sp.edit().putLong(KEY_GUZ_BYTES, v).apply() }

    /** Achanak tez data (kam time me bahut zyada) par bhi alert */
    fun guzzlerSpike(): Boolean = sp.getBoolean(KEY_GUZ_SPIKE, true)
    fun setGuzzlerSpike(v: Boolean) { sp.edit().putBoolean(KEY_GUZ_SPIKE, v).apply() }

    /** "pkg|dinKaStart" = aaj ka alert ja chuka, "mute|pkg|dinKaStart" = aaj ke liye chup */
    fun guzzlerDone(): Set<String> = (sp.getStringSet(KEY_GUZ_DONE, emptySet()) ?: emptySet()).toSet()
    fun setGuzzlerDone(s: Set<String>) { sp.edit().putStringSet(KEY_GUZ_DONE, HashSet(s)).apply() }

    private companion object {
        const val KEY_ROAM_ON = "roam_on"
        const val KEY_ROAM_AUTO = "roam_auto"
        const val KEY_ROAM_APPS = "roam_apps"
        const val KEY_ROAM_SYSTEM = "roam_system"
        const val KEY_KILL = "kill_mobile"
        const val KEY_KILL_EXEMPT = "kill_exempt"
        const val KEY_KILL_SYSTEM = "kill_system"
        const val KEY_QF_PKGS = "quick_focus_pkgs"
        const val KEY_QF_MIN = "quick_focus_min"
        const val KEY_GUZ_ON = "guzzler_on"
        const val KEY_GUZ_BYTES = "guzzler_bytes"
        const val KEY_GUZ_SPIKE = "guzzler_spike"
        const val KEY_GUZ_DONE = "guzzler_done"
        const val KEY_NEW_ALERTS = "new_app_alerts"
        const val KEY_HOLD_NEW = "hold_new_apps"
        const val KEY_LIMITS = "limits"
        const val KEY_LIMIT_BLOCKED = "limit_blocked"
        const val KEY_CYCLE_DAY = "cycle_day"
        const val KEY_SCHEDULES = "schedules"
        const val KEY_BG_WIFI = "bg_wifi"
        const val KEY_BG_MOBILE = "bg_mobile"
        const val KEY_BG_GRACE = "bg_grace"
        const val KEY_WIFI = "blocked_wifi"
        const val KEY_MOBILE = "blocked_mobile"
        const val KEY_ACCENT = "accent"
        const val KEY_MODE = "theme_mode"
        const val KEY_AMOLED = "amoled"
        const val KEY_SORT = "sort_mode"
        const val KEY_FOCUS_UNTIL = "focus_until"
        const val KEY_FOCUS_WIFI = "focus_added_wifi"
        const val KEY_FOCUS_MOBILE = "focus_added_mobile"
    }
}
