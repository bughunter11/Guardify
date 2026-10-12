@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.bughunter11.guardify.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import com.bughunter11.guardify.FirewallVpnService
import com.bughunter11.guardify.data.AppCategories
import com.bughunter11.guardify.data.AppInfo
import com.bughunter11.guardify.data.NetType
import com.bughunter11.guardify.data.ScheduleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val FullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

/** Itna mobile data kharch ho to hi "block karein?" ka sujhav aata hai. */
private const val SUGGEST_MIN_BYTES = 100L * 1024 * 1024

private fun minutesText(m: Int) = when {
    m < 60 -> "$m min"
    m == 60 -> "1 hour"
    else -> "${m / 60} hours"
}

private fun appsText(n: Int) = if (n == 1) "1 app" else "$n apps"

/** Confirm dialog ke liye: bulk action ya poora reset. */
private sealed interface Pending {
    data class Bulk(
        val wifi: Boolean,
        val mobile: Boolean,
        val blocked: Boolean,
        val count: Int,
    ) : Pending

    data object Reset : Pending
}

private class ConfirmSpec(
    val title: String,
    val body: String,
    val confirm: String,
    val destructive: Boolean,
    val action: () -> Unit,
)

/** Rule badalo, phir Undo ka snackbar dikhao. Ek baar bana ke remember hota hai. */
private class UndoHelper(
    private val vm: MainViewModel,
    private val scope: CoroutineScope,
    private val snackbar: SnackbarHostState,
) {
    fun run(message: String, action: () -> Unit) {
        val before = vm.rules()
        action()
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) vm.restore(before)
        }
    }
}

private class ScrollTracker {
    var index = 0
    var offset = 0
}

/** Upar scroll ho raha ho to true (FAB tab poora text dikhata hai). */
@Composable
private fun LazyGridState.isScrollingUp(): Boolean {
    val tracker = remember(this) {
        ScrollTracker().also {
            it.index = firstVisibleItemIndex
            it.offset = firstVisibleItemScrollOffset
        }
    }
    return remember(this) {
        derivedStateOf {
            val i = firstVisibleItemIndex
            val o = firstVisibleItemScrollOffset
            val up = if (tracker.index != i) tracker.index > i else tracker.offset >= o
            tracker.index = i
            tracker.offset = o
            up
        }
    }.value
}

/**
 * Layout screen ke size se banta hai:
 *  - compact (<600dp): 1 column
 *  - medium (600-840dp): 2 columns
 *  - wide (>=840dp): do pane, left me status + filters, right me apps
 * Hero scroll ho kar nikal jaye to top bar me chhota live status aur neeche FAB aa jata hai.
 */
@Composable
fun MainScreen(
    vm: MainViewModel,
    onToggleFirewall: (Boolean) -> Unit,
    onGrantUsage: () -> Unit,
) {
    val ctx = LocalContext.current
    val dir = LocalLayoutDirection.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val undo = remember(vm, scope, snackbar) { UndoHelper(vm, scope, snackbar) }
    val running by FirewallVpnService.running.collectAsState()
    val net by vm.netType.collectAsState()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var focusAsk by remember { mutableStateOf<List<String>?>(null) }
    var dismissed by remember { mutableStateOf(emptySet<String>()) }

    // Naye features: 0 = kuch nahi, 1 = Data limits, 2 = Schedules, 3 = Background,
    // 4 = Quick controls (tile / widget), 5 = Data alerts, 6 = Roaming mode
    var sheet by rememberSaveable { mutableStateOf(0) }
    // Tile par long-press se app khule to seedha Quick controls
    LaunchedEffect(vm.openSheet) {
        if (vm.openSheet != 0) {
            sheet = vm.openSheet
            vm.openSheet = 0
        }
    }
    var limitFor by remember { mutableStateOf<AppInfo?>(null) }
    // "Active now" ke liye: schedules ghadi ke saath badalte hain, isliye har 20 sec me taza
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowTick = System.currentTimeMillis()
            delay(20_000)
        }
    }
    val activeSchedules by remember { derivedStateOf { ScheduleEngine.activeCount(vm.schedules, nowTick) } }
    val ensureFirewall = { if (!running) onToggleFirewall(true) }

    // Roaming mode: network sach me roaming par hai, aur mode lag raha hai ya nahi (manual ya auto)
    val roaming by vm.roaming.collectAsState()
    val roamActive = vm.roamActiveNow(roaming)
    var roamNudgeHidden by remember { mutableStateOf(false) }
    LaunchedEffect(roaming) { if (!roaming) roamNudgeHidden = false } // roaming khatam, agli baar phir poochho
    val roamNudge = roaming && !roamActive && !roamNudgeHidden && net != NetType.WIFI
    val roamKeepCount by remember { derivedStateOf { vm.roamKeep().size } }

    val list by remember { derivedStateOf { vm.visibleApps() } }
    val maxBytes by remember {
        derivedStateOf { vm.apps.maxOfOrNull { vm.usage.total(it.uid) } ?: 0L }
    }
    val totalWifi by remember { derivedStateOf { vm.apps.sumOf { vm.usage.wifi(it.uid) } } }
    val totalMobile by remember { derivedStateOf { vm.apps.sumOf { vm.usage.mobile(it.uid) } } }
    val blockedAny by remember { derivedStateOf { (vm.blockedWifi + vm.blockedMobile).size } }
    // Sabse zyada mobile data khane wali app jo abhi mobile par block nahi hai
    val suggestion by remember {
        derivedStateOf<Suggestion?> {
            vm.apps.asSequence()
                .filter { vm.hasUsageAccess && !it.isSystem && it.pkg !in vm.blockedMobile && it.pkg !in dismissed }
                .map { Suggestion(it, vm.usage.mobile(it.uid)) }
                .filter { it.mobileBytes >= SUGGEST_MIN_BYTES }
                .maxByOrNull { it.mobileBytes }
        }
    }

    // Ye lambdas ek baar bante hain, isliye har card skip ho sakta hai (scroll smooth rehta hai)
    val onRule = remember(undo) {
        { app: AppInfo, wifi: Boolean, mobile: Boolean, blocked: Boolean ->
            val what = when {
                wifi && mobile -> "WiFi and mobile"
                wifi -> "WiFi"
                else -> "Mobile data"
            }
            undo.run("${app.label}: $what ${if (blocked) "blocked" else "allowed"}") {
                vm.setBlockedBulk(listOf(app.pkg), wifi, mobile, blocked)
            }
        }
    }
    val onInfo = remember(ctx) {
        { app: AppInfo ->
            runCatching {
                ctx.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", app.pkg, null),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            Unit
        }
    }
    val onLimit = remember { { app: AppInfo -> limitFor = app } }
    val onBg = remember(vm) {
        { app: AppInfo, mobile: Boolean, blocked: Boolean ->
            vm.setBg(listOf(app.pkg), wifi = !mobile, mobile = mobile, blocked = blocked)
        }
    }
    val clearFilters = remember(vm) {
        {
            vm.query = ""
            vm.onlyBlocked = false
            vm.onlyWithData = false
            vm.category = AppCategories.ALL
        }
    }
    val showSystemApps = remember(vm) { { vm.showSystem = true } }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenW = maxWidth
        val wide = screenW >= 840.dp
        val medium = screenW >= 600.dp

        val scroll = TopAppBarDefaults.pinnedScrollBehavior()
        val gridState = rememberLazyGridState()
        val heroGone by remember(wide) {
            derivedStateOf { !wide && gridState.firstVisibleItemIndex > 0 }
        }
        val scrollingUp = gridState.isScrollingUp()

        val hero: @Composable () -> Unit = {
            PowerHero(
                running = running,
                net = net,
                blockedWifi = vm.blockedWifi.size,
                blockedMobile = vm.blockedMobile.size,
                totalWifi = totalWifi,
                totalMobile = totalMobile,
                showUsage = vm.hasUsageAccess,
                onToggle = onToggleFirewall,
            )
        }

        val tiles: @Composable () -> Unit = {
            FeatureTiles(
                limitCount = vm.limits.size,
                reachedCount = vm.limitBlocked.size,
                scheduleCount = vm.schedules.size,
                activeNow = activeSchedules,
                bgCount = (vm.bgWifi + vm.bgMobile).size,
                killOn = vm.killMobile,
                focusOn = vm.focusUntil > 0L,
                guzzlerOn = vm.guzzler,
                guzzlerLabel = guzzlerLabel(vm.guzzlerBytes),
                roamActive = roamActive,
                roamAuto = vm.roamAuto,
                roaming = roaming,
                roamKeep = roamKeepCount,
                onLimits = { sheet = 1 },
                onSchedules = { sheet = 2 },
                onBackground = { sheet = 3 },
                onQuick = { sheet = 4 },
                onAlerts = { sheet = 5 },
                onRoaming = { sheet = 6 },
            )
        }

        val roamCard = roamActive && !vm.killMobile
        val hasExtras = vm.focusUntil > 0L || suggestion != null || vm.killMobile || roamCard || roamNudge
        val extras: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (vm.killMobile) {
                    KillCard(running = running, onAllow = { vm.chooseKill(false) })
                }
                if (roamCard) {
                    RoamCard(
                        running = running,
                        manual = vm.roamOn,
                        keepCount = roamKeepCount,
                        onEdit = { sheet = 6 },
                        onTurnOff = { vm.chooseRoam(false) },
                    )
                }
                if (roamNudge) {
                    RoamNudgeCard(
                        onTurnOn = {
                            vm.chooseRoam(true)
                            ensureFirewall()
                        },
                        onDismiss = { roamNudgeHidden = true },
                    )
                }
                if (vm.focusUntil > 0L) {
                    val ended = {
                        scope.launch {
                            snackbar.currentSnackbarData?.dismiss()
                            snackbar.showSnackbar("Focus ended. Apps are allowed again.")
                        }
                        Unit
                    }
                    FocusCard(
                        untilMs = vm.focusUntil,
                        onEnd = {
                            vm.endFocus()
                            ended()
                        },
                        onExpired = {
                            vm.syncFocus()
                            ended()
                        },
                    )
                }
                suggestion?.let { s ->
                    SuggestionCard(
                        suggestion = s,
                        periodLabel = if (vm.days == 1) "24 hours" else "${vm.days} days",
                        onBlock = { onRule(s.app, false, true, true) },
                        onDismiss = { dismissed = dismissed + s.app.pkg },
                    )
                }
            }
        }

        Scaffold(
            modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
            containerColor = cs.surface,
            topBar = {
                GuardifyTopBar(
                    shownCount = list.size,
                    hasRules = blockedAny > 0,
                    running = running,
                    showStatus = heroGone,
                    scroll = scroll,
                    onRefresh = { vm.refreshUsage() },
                    onSettings = { showSettings = true },
                    onPending = { pending = it },
                    onFocus = { focusAsk = list.map { it.pkg } },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = heroGone,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                ) {
                    FirewallFab(
                        running = running,
                        expanded = scrollingUp,
                        onToggle = onToggleFirewall,
                    )
                }
            },
        ) { pad ->
            // Side insets (notch / nav bar landscape me) bhi lete hain, warna content kat jata hai
            val startP = pad.calculateStartPadding(dir)
            val endP = pad.calculateEndPadding(dir)
            val topP = pad.calculateTopPadding() + 8.dp
            val botP = pad.calculateBottomPadding() + if (wide) 24.dp else 96.dp

            if (wide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .width((screenW * 0.38f).coerceIn(340.dp, 440.dp))
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(start = startP + 16.dp, end = 8.dp, top = topP, bottom = botP),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        hero()
                        tiles()
                        if (hasExtras) extras()
                        if (!vm.hasUsageAccess) UsageAccessCard(onGrantUsage)
                        Controls(vm, blockedAny, list.size)
                    }
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 300.dp),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            end = endP + 16.dp,
                            top = topP,
                            bottom = botP,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        appItems(vm, list, maxBytes, onRule, onInfo, onLimit, onBg, clearFilters, showSystemApps)
                    }
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(if (medium) 2 else 1),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = startP + 16.dp,
                        end = endP + 16.dp,
                        top = topP,
                        bottom = botP,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "~hero", span = FullSpan, contentType = "hero") { hero() }
                    item(key = "~tiles", span = FullSpan, contentType = "tiles") { tiles() }
                    if (hasExtras) {
                        item(key = "~extras", span = FullSpan, contentType = "extras") { extras() }
                    }
                    if (!vm.hasUsageAccess) {
                        item(key = "~usage", span = FullSpan, contentType = "usage") {
                            UsageAccessCard(onGrantUsage)
                        }
                    }
                    item(key = "~controls", span = FullSpan, contentType = "controls") {
                        Controls(vm, blockedAny, list.size)
                    }
                    appItems(vm, list, maxBytes, onRule, onInfo, onLimit, onBg, clearFilters, showSystemApps)
                }
            }
        }
    }

    // ----- Data limits / Schedules / Background sheets -----
    if (sheet != 0) {
        ModalBottomSheet(
            onDismissRequest = { sheet = 0 },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            when (sheet) {
                1 -> LimitsSheet(vm = vm, onGrantUsage = onGrantUsage, onEnsureFirewall = ensureFirewall)
                2 -> SchedulesSheet(
                    vm = vm,
                    running = running,
                    onTurnOn = { onToggleFirewall(true) },
                    onEnsureFirewall = ensureFirewall,
                )
                3 -> BackgroundSheet(vm = vm, onGrantUsage = onGrantUsage, onEnsureFirewall = ensureFirewall)
                4 -> QuickSheet(vm = vm, running = running, onEnsureFirewall = ensureFirewall)
                5 -> AlertsSheet(vm = vm, onGrantUsage = onGrantUsage)
                6 -> RoamingSheet(vm = vm, running = running, onEnsureFirewall = ensureFirewall)
            }
        }
    }

    limitFor?.let { app ->
        AppLimitDialog(
            app = app,
            vm = vm,
            onDismiss = { limitFor = null },
            onEnsureFirewall = ensureFirewall,
        )
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            SettingsSheet(
                vm = vm,
                hasRules = blockedAny > 0,
                onShare = { shareRules(ctx, vm) },
                onReset = {
                    showSettings = false
                    pending = Pending.Reset
                },
                onChannel = { openLink(ctx, Links.CHANNEL) },
                onGroup = { openLink(ctx, Links.GROUP) },
                onGithub = if (Links.GITHUB.isNotBlank()) ({ openLink(ctx, Links.GITHUB) }) else null,
            )
        }
    }

    focusAsk?.let { pkgs ->
        FocusDialog(
            count = pkgs.size,
            onDismiss = { focusAsk = null },
            onStart = { minutes ->
                focusAsk = null
                vm.startFocus(pkgs, minutes)
                if (!running) onToggleFirewall(true)
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar("Focus started. Apps unblock in ${minutesText(minutes)}.")
                }
            },
        )
    }

    pending?.let { p ->
        val spec = when (p) {
            is Pending.Bulk -> {
                val where = when {
                    p.wifi && p.mobile -> "on WiFi and mobile data"
                    p.wifi -> "on WiFi"
                    else -> "on mobile data"
                }
                if (p.blocked) {
                    ConfirmSpec(
                        title = "Block ${appsText(p.count)}?",
                        body = "They won't reach the internet $where while the firewall is on. You can undo this right after.",
                        confirm = "Block",
                        destructive = true,
                        action = {
                            val pkgs = list.map { it.pkg }
                            undo.run("Blocked ${appsText(pkgs.size)} $where") {
                                vm.setBlockedBulk(pkgs, p.wifi, p.mobile, true)
                            }
                        },
                    )
                } else {
                    ConfirmSpec(
                        title = "Allow ${appsText(p.count)}?",
                        body = "They'll be able to use the internet again $where.",
                        confirm = "Allow",
                        destructive = false,
                        action = {
                            val pkgs = list.map { it.pkg }
                            undo.run("Allowed ${appsText(pkgs.size)} $where") {
                                vm.setBlockedBulk(pkgs, p.wifi, p.mobile, false)
                            }
                        },
                    )
                }
            }

            Pending.Reset -> ConfirmSpec(
                title = "Reset all rules?",
                body = "Every app will be allowed on WiFi and mobile data. You can undo this right after.",
                confirm = "Reset",
                destructive = true,
                action = { undo.run("All rules cleared") { vm.resetRules() } },
            )
        }
        AlertDialog(
            onDismissRequest = { pending = null },
            icon = {
                Icon(
                    if (spec.destructive) Icons.Default.Warning else Icons.Default.Check,
                    contentDescription = null,
                )
            },
            title = { Text(spec.title) },
            text = { Text(spec.body) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pending = null
                        spec.action()
                    },
                ) { Text(spec.confirm) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}

/** Rules ko plain text me share karo (backup / dosto ko bhejne ke liye). */
private fun shareRules(ctx: Context, vm: MainViewModel) {
    val labels = vm.apps.associate { it.pkg to it.label }
    fun block(title: String, set: Set<String>) = buildString {
        append(title).append(" (").append(set.size).append(")\n")
        set.sorted().forEach { append("- ").append(labels[it] ?: it).append(" [").append(it).append("]\n") }
    }
    val text = "Guardify rules\n\n" + block("Blocked on WiFi", vm.blockedWifi) +
        "\n" + block("Blocked on mobile data", vm.blockedMobile)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    runCatching {
        ctx.startActivity(Intent.createChooser(send, "Share rules").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** App list: loading skeleton / empty state / cards. Dono layouts (1-pane aur 2-pane) yahi use karte hain. */
private fun LazyGridScope.appItems(
    vm: MainViewModel,
    list: List<AppInfo>,
    maxBytes: Long,
    onRule: (AppInfo, Boolean, Boolean, Boolean) -> Unit,
    onInfo: (AppInfo) -> Unit,
    onLimit: (AppInfo) -> Unit,
    onBg: (AppInfo, Boolean, Boolean) -> Unit,
    onClearFilters: () -> Unit,
    onShowSystem: () -> Unit,
) {
    if (vm.loading) {
        item(key = "~skeleton", span = FullSpan, contentType = "skeleton") { SkeletonList() }
    } else if (list.isEmpty()) {
        item(key = "~empty", span = FullSpan, contentType = "empty") {
            EmptyState(
                filtered = vm.query.isNotBlank() || vm.onlyBlocked || vm.onlyWithData ||
                    vm.category != AppCategories.ALL,
                noRules = vm.onlyBlocked && vm.blockedWifi.isEmpty() && vm.blockedMobile.isEmpty(),
                onClearFilters = onClearFilters,
                onShowSystem = onShowSystem,
            )
        }
    }

    items(list, key = { it.pkg }, contentType = { "app" }) { app ->
        AppRow(
            app = app,
            wifiBlocked = app.pkg in vm.blockedWifi,
            mobileBlocked = app.pkg in vm.blockedMobile,
            wifiBytes = vm.usage.wifi(app.uid),
            mobileBytes = vm.usage.mobile(app.uid),
            maxBytes = maxBytes,
            showUsage = vm.hasUsageAccess,
            onRule = onRule,
            onInfo = onInfo,
            modifier = Modifier.animateItemPlacement(),
            limitBytes = vm.limits[app.pkg] ?: 0L,
            cycleBytes = vm.cycleMobile(app.uid),
            limitReached = app.pkg in vm.limitBlocked,
            bgWifi = app.pkg in vm.bgWifi,
            bgMobile = app.pkg in vm.bgMobile,
            onLimit = onLimit,
            onBg = onBg,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Top bar: logo + naam. Hero scroll ho kar chala jaye to chhota live status (dot + On/Off) khulta hai.
// ---------------------------------------------------------------------------------------------

@Composable
private fun GuardifyTopBar(
    shownCount: Int,
    hasRules: Boolean,
    running: Boolean,
    showStatus: Boolean,
    scroll: TopAppBarScrollBehavior,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onPending: (Pending) -> Unit,
    onFocus: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val spin = remember { Animatable(0f) }
    val dot by animateColorAsState(
        if (running) cs.primary else cs.outline,
        tween(300),
        label = "topDot",
    )

    TopAppBar(
        navigationIcon = {
            Box(
                modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                GuardifyLogo(
                    modifier = Modifier.height(30.dp),
                    outer = cs.primary,
                    inner = cs.primaryContainer,
                    keyhole = cs.onPrimaryContainer,
                )
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Guardify",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                AnimatedVisibility(
                    visible = showStatus,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (running) "On" else "Off",
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(
                onClick = {
                    onRefresh()
                    scope.launch { spin.animateTo(spin.value + 360f, tween(600)) }
                },
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh usage",
                    modifier = Modifier.graphicsLayer { rotationZ = spin.value },
                )
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Block all shown") },
                        enabled = shownCount > 0,
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        onClick = {
                            menu = false
                            onPending(Pending.Bulk(wifi = true, mobile = true, blocked = true, count = shownCount))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Block shown on WiFi only") },
                        enabled = shownCount > 0,
                        onClick = {
                            menu = false
                            onPending(Pending.Bulk(wifi = true, mobile = false, blocked = true, count = shownCount))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Block shown on mobile only") },
                        enabled = shownCount > 0,
                        onClick = {
                            menu = false
                            onPending(Pending.Bulk(wifi = false, mobile = true, blocked = true, count = shownCount))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Allow all shown") },
                        enabled = shownCount > 0,
                        leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                        onClick = {
                            menu = false
                            onPending(Pending.Bulk(wifi = true, mobile = true, blocked = false, count = shownCount))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Focus on shown apps") },
                        enabled = shownCount > 0,
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        onClick = {
                            menu = false
                            onFocus()
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Reset all rules") },
                        enabled = hasRules,
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            menu = false
                            onPending(Pending.Reset)
                        },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = cs.surface,
            scrolledContainerColor = cs.surfaceContainer,
        ),
        scrollBehavior = scroll,
    )
}

/** Hero nikal jaye to bhi firewall ek tap door. Neeche scroll karte hi text chhupta hai, sirf icon rehta hai. */
@Composable
private fun FirewallFab(
    running: Boolean,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(
        if (running) cs.primaryContainer else cs.primary,
        tween(300),
        label = "fabBg",
    )
    val fg by animateColorAsState(
        if (running) cs.onPrimaryContainer else cs.onPrimary,
        tween(300),
        label = "fabFg",
    )
    ExtendedFloatingActionButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggle(!running)
        },
        expanded = expanded,
        containerColor = bg,
        contentColor = fg,
        icon = {
            GuardifyLogo(
                modifier = Modifier.height(22.dp),
                outer = fg,
                inner = bg,
                keyhole = cs.tertiary,
            )
        },
        text = {
            AnimatedContent(
                targetState = running,
                transitionSpec = { FadeSlide },
                label = "fabText",
            ) { on ->
                Text(if (on) "Turn off" else "Turn on")
            }
        },
    )
}
