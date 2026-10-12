@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bughunter11.guardify.ui

import android.Manifest
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bughunter11.guardify.FocusTileService
import com.bughunter11.guardify.KillTileService
import com.bughunter11.guardify.R
import com.bughunter11.guardify.RoamTileService
import com.bughunter11.guardify.data.LimitEngine
import com.bughunter11.guardify.widget.GuardifyWidgetReceiver

// Material icon paths (extended icons library ke bina, Pickers.kt jaise)
internal const val ICON_BLOCK =
    "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM4,12c0,-4.42 3.58,-8 8,-8 1.85,0 3.55,0.63 4.9,1.69L5.69,16.9C4.63,15.55 4,13.85 4,12zM12,20c-1.85,0 -3.55,-0.63 -4.9,-1.69L18.31,7.1C19.37,8.45 20,10.15 20,12 20,16.42 16.42,20 12,20z"
internal const val ICON_BELL =
    "M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z"
internal const val ICON_TIMER =
    "M15,1H9v2h6V1zM11,14h2V8h-2v6zM19.03,7.39l1.42,-1.42c-0.43,-0.51 -0.9,-0.99 -1.41,-1.41l-1.42,1.42C16.07,4.74 14.12,4 12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9 9,-4.03 9,-9c0,-2.12 -0.74,-4.08 -1.97,-5.61zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z"

private const val MB = LimitEngine.MB
private const val GB = LimitEngine.GB

private val FocusMinutes = listOf(15 to "15 min", 30 to "30 min", 60 to "1 hour", 120 to "2 hours")
private val GuzzlerPresets = listOf(
    "100 MB" to 100L * MB,
    "250 MB" to 250L * MB,
    "500 MB" to 500L * MB,
    "1 GB" to GB,
    "2 GB" to 2 * GB,
)

/** Tiles me dikhane ke liye: 250000000 -> "250 MB". */
internal fun guzzlerLabel(bytes: Long): String =
    GuzzlerPresets.firstOrNull { it.second == bytes }?.first ?: "${bytes / MB} MB"

// ---------------------------------------------------------------------------------------------
// Home par dikhne wala card jab "sab mobile data band" on ho.
// ---------------------------------------------------------------------------------------------

@Composable
internal fun KillCard(
    running: Boolean,
    onAllow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = cs.errorContainer,
            contentColor = cs.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(ICON_BLOCK, container = cs.error, content = cs.onError)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (running) "Mobile data is off for all apps" else "Mobile block is waiting",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    if (running) "WiFi still works. Allow mobile data when you need it." else "Turn the firewall on to enforce it.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onAllow) { Text("Allow") }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Quick controls: sab mobile data band, quick focus, tile / widget lagana.
// ---------------------------------------------------------------------------------------------

@Composable
internal fun QuickSheet(
    vm: MainViewModel,
    running: Boolean,
    onEnsureFirewall: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var pickExempt by remember { mutableStateOf(false) }
    var pickFocus by remember { mutableStateOf(false) }

    val killLive = vm.killMobile && running
    val killBg by animateColorAsState(
        if (killLive) cs.errorContainer else cs.surfaceContainerHigh,
        tween(300),
        label = "killBg",
    )
    val focusOn = vm.focusUntil > 0L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(ICON_BLOCK)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Quick controls", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "One tap from the Quick Settings panel or your home screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }

        // ----- Sab mobile data band -----
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = killBg),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(
                        ICON_BLOCK,
                        container = if (killLive) cs.error else cs.primaryContainer,
                        content = if (killLive) cs.onError else cs.onPrimaryContainer,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Block all mobile data", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                killLive -> "Every app is blocked on mobile data right now."
                                vm.killMobile -> "Waiting for the firewall to start."
                                else -> "Cuts mobile data for every app in one tap. WiFi stays on."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (killLive) cs.onErrorContainer else cs.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = vm.killMobile,
                        onCheckedChange = {
                            vm.chooseKill(it)
                            if (it) onEnsureFirewall()
                        },
                    )
                }
                OptionRow(
                    title = "Include system apps",
                    subtitle = "Off is safer. Calls, SMS and system services keep working.",
                    checked = vm.killSystem,
                    onChange = vm::chooseKillSystem,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { pickExempt = true }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Keep these apps online", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (vm.killExempt.isEmpty()) "None. Pick apps like your messenger." else "${vm.killExempt.size} apps stay online",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (killLive) cs.onErrorContainer else cs.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { pickExempt = true }) { Text("Choose") }
                }
            }
        }

        // ----- Quick focus -----
        SheetCard(container = if (focusOn) cs.secondaryContainer else cs.surfaceContainerHigh) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(ICON_TIMER)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Quick focus", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (focusOn) "Focus is on. Apps unblock when the timer ends." else "Blocks distracting apps on WiFi and mobile data for a while.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (focusOn) cs.onSecondaryContainer else cs.onSurfaceVariant,
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FocusMinutes.forEach { (m, label) ->
                        FilterChip(
                            selected = vm.quickFocusMinutes == m,
                            onClick = { vm.chooseQuickFocusMinutes(m) },
                            label = { Text(label) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (vm.quickFocusPkgs.isEmpty()) "Social, games and video apps" else "${vm.quickFocusPkgs.size} chosen apps",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { pickFocus = true }) { Text("Change") }
                }
                if (focusOn) {
                    FilledTonalButton(onClick = vm::endFocus, modifier = Modifier.fillMaxWidth()) {
                        Text("End focus")
                    }
                } else {
                    FilledTonalButton(
                        onClick = {
                            if (vm.startQuickFocus()) {
                                onEnsureFirewall()
                            } else {
                                Toast.makeText(ctx, "No apps to focus on yet. Tap Change and pick some.", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Start focus now") }
                }
            }
        }

        // ----- Tile / widget lagao -----
        SheetCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Add to your phone", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Quick Settings tiles and a home screen widget. Both use your Material You colors and show live status.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { addTiles(ctx) }, modifier = Modifier.weight(1f)) { Text("Quick tiles") }
                    FilledTonalButton(onClick = { addWidget(ctx) }, modifier = Modifier.weight(1f)) { Text("Home widget") }
                }
            }
        }
    }

    if (pickExempt) {
        AppPickerDialog(
            title = "Keep online",
            apps = vm.apps,
            initial = vm.killExempt,
            multi = true,
            onDismiss = { pickExempt = false },
            onConfirm = {
                vm.chooseKillExempt(it)
                pickExempt = false
            },
        )
    }
    if (pickFocus) {
        AppPickerDialog(
            title = "Apps for quick focus",
            apps = vm.apps,
            initial = vm.quickFocusPkgs,
            multi = true,
            onDismiss = { pickFocus = false },
            onConfirm = {
                vm.chooseQuickFocusPkgs(it)
                pickFocus = false
            },
        )
    }
}

@Composable
private fun OptionRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Android 13+ par system khud "tile add karein?" poochta hai. Purane phone par rasta batate hain. */
private fun addTiles(ctx: Context) {
    if (Build.VERSION.SDK_INT >= 33) {
        val sbm = ctx.getSystemService(StatusBarManager::class.java)
        val exec = ContextCompat.getMainExecutor(ctx)
        val killCn = ComponentName(ctx, KillTileService::class.java)
        val focusCn = ComponentName(ctx, FocusTileService::class.java)
        runCatching {
            sbm.requestAddTileService(
                killCn,
                ctx.getString(R.string.tile_kill_label),
                Icon.createWithResource(ctx, R.drawable.ic_tile_mobile),
                exec,
                java.util.function.Consumer<Int> {
                    // Dono ek saath nahi chalte, pehla khatam hone par doosra poochho
                    runCatching {
                        sbm.requestAddTileService(
                            focusCn,
                            ctx.getString(R.string.tile_focus_label),
                            Icon.createWithResource(ctx, R.drawable.ic_tile_focus),
                            exec,
                            java.util.function.Consumer<Int> {
                                runCatching {
                                    sbm.requestAddTileService(
                                        ComponentName(ctx, RoamTileService::class.java),
                                        ctx.getString(R.string.tile_roam_label),
                                        Icon.createWithResource(ctx, R.drawable.ic_tile_roam),
                                        exec,
                                        java.util.function.Consumer<Int> { },
                                    )
                                }
                            },
                        )
                    }
                },
            )
        }
    } else {
        Toast.makeText(
            ctx,
            "Open Quick Settings, tap the pencil and drag in the Guardify tiles.",
            Toast.LENGTH_LONG,
        ).show()
    }
}

private fun addWidget(ctx: Context) {
    val awm = AppWidgetManager.getInstance(ctx)
    val ok = runCatching {
        awm.isRequestPinAppWidgetSupported &&
            awm.requestPinAppWidget(ComponentName(ctx, GuardifyWidgetReceiver::class.java), null, null)
    }.getOrDefault(false)
    if (!ok) {
        Toast.makeText(
            ctx,
            "Long-press your home screen, open Widgets and pick Guardify.",
            Toast.LENGTH_LONG,
        ).show()
    }
}

// ---------------------------------------------------------------------------------------------
// Data-guzzler alerts: koi app ek din me bahut mobile data kha le to turant notification.
// ---------------------------------------------------------------------------------------------

@Composable
internal fun AlertsSheet(
    vm: MainViewModel,
    onGrantUsage: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        vm.chooseGuzzler(ok)
    }
    val toggle: (Boolean) -> Unit = { on ->
        val needsAsk = on && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsAsk) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.chooseGuzzler(on)
    }

    // Aaj ki sabse zyada mobile data khane wali apps (system apps nahi)
    val top = remember(vm.todayMobile, vm.apps) {
        vm.apps.asSequence()
            .filter { !it.isSystem }
            .map { it to (vm.todayMobile[it.uid] ?: 0L) }
            .filter { it.second > 0L }
            .sortedByDescending { it.second }
            .take(4)
            .toList()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(ICON_BELL)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Data alerts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Get told right away when an app eats a lot of mobile data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }

        if (!vm.hasUsageAccess) UsageAccessCard(onGrantUsage)

        SheetCard(container = if (vm.guzzler) cs.secondaryContainer else cs.surfaceContainerHigh) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Data-guzzler alerts", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (vm.guzzler) "You get a notification with Block and Ignore buttons." else "Off",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (vm.guzzler) cs.onSecondaryContainer else cs.onSurfaceVariant,
                        )
                    }
                    Switch(checked = vm.guzzler, onCheckedChange = toggle)
                }

                if (vm.guzzler) {
                    Text("Alert when one app passes, in a day", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GuzzlerPresets.forEach { (label, bytes) ->
                            FilterChip(
                                selected = vm.guzzlerBytes == bytes,
                                onClick = { vm.chooseGuzzlerBytes(bytes) },
                                label = { Text(label) },
                            )
                        }
                    }
                    OptionRow(
                        title = "Sudden spikes too",
                        subtitle = "Also alert when an app uses 50 MB or more in 10 minutes. Works while the firewall is on.",
                        checked = vm.guzzlerSpike,
                        onChange = vm::chooseGuzzlerSpike,
                    )
                    Text(
                        "Only mobile data counts, and a day starts at midnight. Apps already blocked on mobile data are skipped.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSecondaryContainer,
                    )
                }
            }
        }

        // Live: aaj kaun kitna kha chuka, limit ke saath
        if (vm.guzzler && vm.hasUsageAccess) {
            SheetCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Mobile data today", style = MaterialTheme.typography.titleSmall)
                    if (top.isEmpty()) {
                        Text(
                            "No app has used mobile data today yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                        )
                    } else {
                        top.forEach { (app, bytes) ->
                            TodayRow(
                                pkg = app.pkg,
                                label = app.label,
                                bytes = bytes,
                                limit = vm.guzzlerBytes,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayRow(pkg: String, label: String, bytes: Long, limit: Long) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val over = bytes >= limit
    val frac by animateFloatAsState(
        (bytes.toFloat() / limit).coerceIn(0f, 1f),
        tween(500),
        label = "todayFrac",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIcon(pkg, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    Formatter.formatShortFileSize(ctx, bytes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (over) cs.error else cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { frac },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = if (over) cs.error else if (frac > 0.7f) cs.tertiary else cs.primary,
                trackColor = cs.surfaceContainerHighest,
            )
        }
    }
}
