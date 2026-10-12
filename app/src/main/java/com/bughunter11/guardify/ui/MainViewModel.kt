package com.bughunter11.guardify.ui

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bughunter11.guardify.DataWatchReceiver
import com.bughunter11.guardify.FirewallVpnService
import com.bughunter11.guardify.data.AppCategories
import com.bughunter11.guardify.data.AppInfo
import com.bughunter11.guardify.data.AppRepository
import com.bughunter11.guardify.data.DataWatch
import com.bughunter11.guardify.data.FocusMode
import com.bughunter11.guardify.data.LimitEngine
import com.bughunter11.guardify.data.NetType
import com.bughunter11.guardify.data.NetworkMonitor
import com.bughunter11.guardify.data.Prefs
import com.bughunter11.guardify.data.QuickActions
import com.bughunter11.guardify.data.RoamingMode
import com.bughunter11.guardify.data.Schedule
import com.bughunter11.guardify.data.SortMode
import com.bughunter11.guardify.data.Usage
import com.bughunter11.guardify.data.UsageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Undo ke liye: rules ki ek poori copy. */
class RuleSnapshot(val wifi: Set<String>, val mobile: Set<String>)

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val monitor = NetworkMonitor(app)

    /** Live: abhi WiFi, mobile data ya offline */
    val netType: StateFlow<NetType> = monitor.type

    /** Live: mobile data par ho aur network roaming par ho */
    val roaming: StateFlow<Boolean> = monitor.roaming

    var apps by mutableStateOf<List<AppInfo>>(emptyList()); private set
    var usage by mutableStateOf(Usage.EMPTY); private set
    var loading by mutableStateOf(true); private set
    var hasUsageAccess by mutableStateOf(false); private set

    // Sorting ka snapshot: live refresh me numbers badalte hain, par list ulti-seedhi nahi hoti.
    private var rankUsage by mutableStateOf(Usage.EMPTY)

    var query by mutableStateOf("")
    var showSystem by mutableStateOf(false)
    var onlyBlocked by mutableStateOf(false)
    var onlyWithData by mutableStateOf(false)
    var sortMode by mutableStateOf(SortMode.entries.getOrElse(prefs.sort()) { SortMode.TOTAL }); private set
    var days by mutableIntStateOf(7)
    var category by mutableIntStateOf(AppCategories.ALL)

    /** Focus khatam hone ka time (epoch ms), 0 = focus nahi chal raha */
    var focusUntil by mutableLongStateOf(prefs.focusUntil()); private set

    var blockedWifi by mutableStateOf(prefs.wifi()); private set
    var blockedMobile by mutableStateOf(prefs.mobile()); private set

    // ----- Naye features -----

    /** Per-app monthly mobile limit (pkg -> bytes) */
    var limits by mutableStateOf(prefs.limits()); private set

    /** Jin apps ne limit paar kar di (mobile par block) */
    var limitBlocked by mutableStateOf(prefs.limitBlocked()); private set

    /** Is billing cycle ka mobile data, uid ke hisaab se */
    var cycleUsage by mutableStateOf<Map<Int, Long>>(emptyMap()); private set
    var cycleDay by mutableIntStateOf(prefs.cycleDay()); private set

    var schedules by mutableStateOf(prefs.schedules()); private set

    /** Background me block hone wali apps (app band = net band) */
    var bgWifi by mutableStateOf(prefs.bgWifi()); private set
    var bgMobile by mutableStateOf(prefs.bgMobile()); private set
    var bgGrace by mutableIntStateOf(prefs.bgGrace()); private set

    // ----- Quick controls (tile / widget / app) -----

    /** Ek tap me sab mobile data band. Asli block tabhi lagta hai jab firewall bhi chal rahi ho. */
    var killMobile by mutableStateOf(prefs.killMobile()); private set
    var killSystem by mutableStateOf(prefs.killSystem()); private set
    var killExempt by mutableStateOf(prefs.killExempt()); private set
    var quickFocusPkgs by mutableStateOf(prefs.quickFocusPkgs()); private set
    var quickFocusMinutes by mutableIntStateOf(prefs.quickFocusMinutes()); private set

    // ----- Roaming mode: bahar jao to sirf zaroori apps ko mobile data -----

    /** Manual switch. Asli block tabhi lagta hai jab firewall bhi chal rahi ho. */
    var roamOn by mutableStateOf(prefs.roamOn()); private set
    var roamAuto by mutableStateOf(prefs.roamAuto()); private set
    var roamSystem by mutableStateOf(prefs.roamSystem()); private set

    /** Tumhari chuni hui zaroori apps. Khali = neeche wali live suggestion istemal hoti hai. */
    var roamEssential by mutableStateOf(prefs.roamEssential()); private set

    /** Phone se live suggestion (default dialer, SMS, maps, travel apps) */
    var roamSuggested by mutableStateOf<Set<String>>(emptySet()); private set

    // ----- Data-guzzler alerts -----

    var guzzler by mutableStateOf(prefs.guzzler()); private set
    var guzzlerBytes by mutableLongStateOf(prefs.guzzlerBytes()); private set
    var guzzlerSpike by mutableStateOf(prefs.guzzlerSpike()); private set

    /** Aaj (raat 12 se) ka mobile data, uid ke hisaab se. Alerts sheet me live dikhta hai. */
    var todayMobile by mutableStateOf<Map<Int, Long>>(emptyMap()); private set

    /** Kisi bhi jagah se (tile long-press) koi sheet kholne ka sandesha. 0 = kuch nahi. */
    var openSheet by mutableIntStateOf(0)

    var newAppAlerts by mutableStateOf(prefs.newAppAlerts()); private set
    var holdNew by mutableStateOf(prefs.holdNew()); private set

    // Theme: 0 system / 1 light / 2 dark, accent: 0 = wallpaper, warna ARGB
    var themeMode by mutableIntStateOf(prefs.mode()); private set
    var accent by mutableIntStateOf(prefs.accent()); private set
    var amoled by mutableStateOf(prefs.amoled()); private set

    private var liveJob: Job? = null
    private var manualJob: Job? = null

    /**
     * Sorting ek hi baar (apps ya ranking badalne par) hoti hai. Search me har akshar par
     * sirf filter chalta hai, isliye typing hamesha smooth rehti hai.
     */
    private val sortedApps by derivedStateOf {
        val rank = rankUsage
        val byName = compareBy<AppInfo> { it.label.lowercase() }
        when (sortMode) {
            SortMode.NAME -> apps.sortedWith(byName)
            SortMode.WIFI -> apps.sortedWith(compareByDescending<AppInfo> { rank.wifi(it.uid) }.then(byName))
            SortMode.MOBILE -> apps.sortedWith(compareByDescending<AppInfo> { rank.mobile(it.uid) }.then(byName))
            SortMode.TOTAL -> apps.sortedWith(compareByDescending<AppInfo> { rank.total(it.uid) }.then(byName))
        }
    }

    init {
        syncFocus()
        monitor.start()
        viewModelScope.launch {
            try {
                apps = withContext(Dispatchers.IO) { AppRepository.load(app) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                apps = emptyList()
            } finally {
                loading = false
            }
            refreshRoamSuggestions()
        }
    }

    override fun onCleared() {
        monitor.stop()
        super.onCleared()
    }

    // ----- Live usage: app screen par ho tab har 30 sec me apne aap refresh -----

    fun startLive() {
        if (liveJob?.isActive == true) return
        liveJob = viewModelScope.launch {
            var first = true
            while (true) {
                syncFocus()
                syncQuick()
                loadUsage(rerank = first)
                first = false
                delay(LIVE_INTERVAL_MS)
            }
        }
    }

    fun stopLive() {
        liveJob?.cancel()
        liveJob = null
    }

    /** Manual refresh / period badalna: pichla load cancel hota hai, tez tap par bhi bojh nahi. */
    fun refreshUsage() {
        manualJob?.cancel()
        manualJob = viewModelScope.launch { loadUsage(rerank = true) }
    }

    // Koi bhi error ho, app crash nahi hoti; cancel hone par coroutine sahi se ruk jata hai.
    private suspend fun loadUsage(rerank: Boolean) {
        try {
            val period = days
            val access = UsageRepository.hasAccess(app)
            hasUsageAccess = access
            val fresh = if (access) UsageRepository.query(app, period) else Usage.EMPTY
            if (period != days) return // beech me period badal gaya, purana data mat dikhao
            usage = fresh
            if (rerank) rankUsage = fresh
            loadCycle(access)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // agli baar dobara try hoga
        }
    }

    /** Billing cycle ka mobile usage lo aur limit paar apps ka set naya karo. */
    private suspend fun loadCycle(access: Boolean) {
        if (!access) {
            cycleUsage = emptyMap()
            todayMobile = emptyMap()
            return
        }
        val cycle = UsageRepository.mobileSince(app, LimitEngine.cycleStart(cycleDay))
        cycleUsage = cycle
        todayMobile = UsageRepository.mobileSince(app, DataWatch.dayStart(System.currentTimeMillis()))
        if (loading) return // app list aane tak uid pata nahi, galat set mat likho
        val uids = apps.associate { it.pkg to it.uid }
        val over = LimitEngine.overLimit(prefs.limits(), cycle) { uids[it] }
        if (over != prefs.limitBlocked()) {
            prefs.setLimitBlocked(over)
            limitBlocked = over
            pokeService()
        }
    }

    fun selectPeriod(d: Int) {
        if (d == days) return
        days = d
        refreshUsage()
    }

    fun setBlocked(pkg: String, mobile: Boolean, blocked: Boolean) =
        setBlockedBulk(listOf(pkg), wifi = !mobile, mobile = mobile, blocked = blocked)

    /** Ek ya zyada apps par WiFi / mobile rule ek saath lagao. */
    fun setBlockedBulk(pkgs: Collection<String>, wifi: Boolean, mobile: Boolean, blocked: Boolean) {
        val w = blockedWifi.toMutableSet()
        val m = blockedMobile.toMutableSet()
        if (wifi) {
            if (blocked) w.addAll(pkgs) else w.removeAll(pkgs.toSet())
        }
        if (mobile) {
            if (blocked) m.addAll(pkgs) else m.removeAll(pkgs.toSet())
        }
        applyRules(w, m)
    }

    fun resetRules() = applyRules(emptySet(), emptySet())

    fun rules() = RuleSnapshot(blockedWifi, blockedMobile)

    fun restore(snapshot: RuleSnapshot) = applyRules(snapshot.wifi, snapshot.mobile)

    private fun applyRules(wifi: Set<String>, mobile: Set<String>) {
        prefs.setRules(wifi, mobile)
        blockedWifi = prefs.wifi()
        blockedMobile = prefs.mobile()
        pokeService()
    }

    /** Firewall chal raha ho to naye rules / focus timer turant lagu karo */
    private fun pokeService() {
        if (FirewallVpnService.running.value) {
            runCatching { app.startService(Intent(app, FirewallVpnService::class.java)) }
        }
    }

    // ----- Focus mode -----

    /** Time pura ho gaya ho to focus khatam; service ne pehle khatam kiya ho to bhi UI sahi ho jati hai. */
    fun syncFocus() {
        FocusMode.endIfExpired(prefs)
        blockedWifi = prefs.wifi()
        blockedMobile = prefs.mobile()
        focusUntil = prefs.focusUntil()
    }

    fun startFocus(pkgs: Collection<String>, minutes: Int) {
        FocusMode.start(prefs, pkgs, System.currentTimeMillis() + minutes * 60_000L)
        syncFocus()
        pokeService()
        QuickActions.refreshSurfaces(app)
    }

    fun endFocus() {
        FocusMode.end(prefs)
        syncFocus()
        pokeService()
        QuickActions.refreshSurfaces(app)
    }

    // ----- Quick controls -----

    /** Tile / widget / notification ne prefs badle hon to UI ko bhi dikha do. */
    fun syncQuick() {
        killMobile = prefs.killMobile()
        roamOn = prefs.roamOn()
    }

    /** Sab mobile data band / allow. Firewall chalani ho to MainScreen alag se bulata hai. */
    fun chooseKill(on: Boolean) {
        prefs.setKillMobile(on)
        killMobile = on
        pokeService()
        QuickActions.refreshSurfaces(app)
    }

    fun chooseKillSystem(v: Boolean) {
        prefs.setKillSystem(v)
        killSystem = v
        pokeService()
    }

    fun chooseKillExempt(s: Set<String>) {
        prefs.setKillExempt(s)
        killExempt = prefs.killExempt()
        pokeService()
    }

    fun chooseQuickFocusMinutes(m: Int) {
        prefs.setQuickFocusMinutes(m)
        quickFocusMinutes = prefs.quickFocusMinutes()
        QuickActions.refreshSurfaces(app)
    }

    fun chooseQuickFocusPkgs(s: Set<String>) {
        prefs.setQuickFocusPkgs(s)
        quickFocusPkgs = prefs.quickFocusPkgs()
    }

    /** Quick focus abhi shuru. False = koi app nahi mili. */
    fun startQuickFocus(): Boolean {
        val pkgs = QuickActions.focusTargets(apps, prefs)
        if (pkgs.isEmpty()) return false
        startFocus(pkgs, quickFocusMinutes)
        return true
    }

    // ----- Roaming mode -----

    /** Zaroori apps jo roaming me bhi online rahengi (chuni hui, warna suggestion). Sirf installed. */
    fun roamKeep(): Set<String> {
        val installed = apps.mapTo(HashSet()) { it.pkg }
        return roamEssential.ifEmpty { roamSuggested }.filterTo(LinkedHashSet()) { it in installed }
    }

    /** Roaming lagu hone par kitni apps mobile data par band hongi (UI me dikhane ke liye). */
    fun roamBlockedCount(): Int {
        val keep = roamKeep()
        return apps.count { (roamSystem || !it.isSystem) && it.pkg !in keep }
    }

    /** Roaming ka asli haal: manual switch on, ya auto on aur network roaming par. */
    fun roamActiveNow(networkRoaming: Boolean): Boolean = roamOn || (roamAuto && networkRoaming)

    fun chooseRoam(on: Boolean) {
        prefs.setRoamOn(on)
        roamOn = on
        pokeService()
        QuickActions.refreshSurfaces(app)
    }

    fun chooseRoamAuto(v: Boolean) {
        prefs.setRoamAuto(v)
        roamAuto = v
        pokeService()
        QuickActions.refreshSurfaces(app)
    }

    fun chooseRoamSystem(v: Boolean) {
        prefs.setRoamSystem(v)
        roamSystem = v
        pokeService()
    }

    fun chooseRoamEssential(s: Set<String>) {
        prefs.setRoamEssential(s)
        roamEssential = prefs.roamEssential()
        pokeService()
    }

    /** Apni chuni hui list hata do, live suggestion wapas. */
    fun resetRoamEssential() = chooseRoamEssential(emptySet())

    /** Apps load hone ke baad (ya sheet khulne par) suggestion taza karo. */
    fun refreshRoamSuggestions() {
        if (apps.isEmpty()) return
        val list = apps
        viewModelScope.launch {
            val fresh = try {
                withContext(Dispatchers.IO) { RoamingMode.suggest(app, list) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch
            }
            roamSuggested = fresh
        }
    }

    // ----- Data-guzzler alerts -----

    fun chooseGuzzler(on: Boolean) {
        prefs.setGuzzler(on)
        guzzler = on
        if (on) DataWatchReceiver.schedule(app) else DataWatchReceiver.cancel(app)
        pokeService()
    }

    fun chooseGuzzlerBytes(b: Long) {
        prefs.setGuzzlerBytes(b)
        guzzlerBytes = b
        pokeService()
    }

    fun chooseGuzzlerSpike(v: Boolean) {
        prefs.setGuzzlerSpike(v)
        guzzlerSpike = v
    }

    // ----- Data limits -----

    fun setLimit(pkg: String, bytes: Long) = applyLimits(limits + (pkg to bytes))

    fun removeLimit(pkg: String) = applyLimits(limits - pkg)

    private fun applyLimits(m: Map<String, Long>) {
        prefs.setLimits(m)
        limits = prefs.limits()
        // hataye hue limits ka block turant hatao
        val keep = prefs.limitBlocked().filterTo(HashSet<String>()) { it in m.keys }
        prefs.setLimitBlocked(keep)
        limitBlocked = keep
        pokeService()
        viewModelScope.launch { loadCycle(UsageRepository.hasAccess(app)) }
    }

    fun chooseCycleDay(d: Int) {
        prefs.setCycleDay(d)
        cycleDay = prefs.cycleDay()
        viewModelScope.launch { loadCycle(UsageRepository.hasAccess(app)) }
    }

    fun cycleMobile(uid: Int): Long = cycleUsage[uid] ?: 0L

    // ----- Schedules -----

    fun saveSchedule(s: Schedule) {
        val l = schedules.toMutableList()
        val i = l.indexOfFirst { it.id == s.id }
        if (i >= 0) l[i] = s else l.add(s)
        applySchedules(l)
    }

    fun deleteSchedule(id: String) = applySchedules(schedules.filter { it.id != id })

    fun setScheduleEnabled(id: String, on: Boolean) =
        applySchedules(schedules.map { if (it.id == id) it.copy(enabled = on) else it })

    private fun applySchedules(l: List<Schedule>) {
        prefs.setSchedules(l)
        schedules = prefs.schedules()
        pokeService()
    }

    // ----- Background block -----

    fun setBg(pkgs: Collection<String>, wifi: Boolean, mobile: Boolean, blocked: Boolean) {
        val w = bgWifi.toMutableSet()
        val m = bgMobile.toMutableSet()
        if (wifi) {
            if (blocked) w.addAll(pkgs) else w.removeAll(pkgs.toSet())
        }
        if (mobile) {
            if (blocked) m.addAll(pkgs) else m.removeAll(pkgs.toSet())
        }
        prefs.setBg(w, m)
        bgWifi = prefs.bgWifi()
        bgMobile = prefs.bgMobile()
        pokeService()
    }

    fun chooseGrace(sec: Int) {
        prefs.setBgGrace(sec)
        bgGrace = sec
        pokeService()
    }

    fun chooseNewAppAlerts(v: Boolean) { newAppAlerts = v; prefs.setNewAppAlerts(v) }
    fun chooseHoldNew(v: Boolean) { holdNew = v; prefs.setHoldNew(v) }

    fun chooseSort(m: SortMode) { sortMode = m; prefs.setSort(m.ordinal) }
    fun chooseTheme(m: Int) { themeMode = m; prefs.setMode(m) }
    fun pickAccent(a: Int) { accent = a; prefs.setAccent(a) }
    fun chooseAmoled(v: Boolean) { amoled = v; prefs.setAmoled(v) }

    fun visibleApps(): List<AppInfo> {
        val q = query.trim()
        val wifi = blockedWifi
        val mobile = blockedMobile
        val used = usage
        return sortedApps.filter {
            (showSystem || !it.isSystem) &&
                (category == AppCategories.ALL || it.category == category) &&
                (!onlyBlocked || it.pkg in wifi || it.pkg in mobile) &&
                (!onlyWithData || used.total(it.uid) > 0L) &&
                (q.isEmpty() || it.label.contains(q, true) || it.pkg.contains(q, true))
        }
    }

    private companion object {
        const val LIVE_INTERVAL_MS = 30_000L
    }
}
