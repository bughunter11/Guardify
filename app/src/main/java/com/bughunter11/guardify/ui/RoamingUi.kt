@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bughunter11.guardify.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bughunter11.guardify.data.AppInfo
import com.bughunter11.guardify.data.NetType

// Material "public" (globe) icon, extended icons library ke bina (Pickers.kt jaise)
internal const val ICON_ROAM =
    "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM11,19.93c-3.95,-0.49 -7,-3.85 -7,-7.93 0,-0.62 0.08,-1.21 0.21,-1.79L9,15v1c0,1.1 0.9,2 2,2v1.93zM17.9,17.39c-0.26,-0.81 -1,-1.39 -1.9,-1.39h-1v-3c0,-0.55 -0.45,-1 -1,-1L8,12v-2h2c0.55,0 1,-0.45 1,-1L11,7h2c1.1,0 2,-0.9 2,-2v-0.41c2.93,1.19 5,4.06 5,7.41 0,2.08 -0.8,3.97 -2.1,5.39z"

private fun roamApps(n: Int) = if (n == 1) "1 app" else "$n apps"

// ---------------------------------------------------------------------------------------------
// Roaming mode sheet: ek switch, live network status, auto-switch aur zaroori apps ki list.
// ---------------------------------------------------------------------------------------------

@Composable
internal fun RoamingSheet(
    vm: MainViewModel,
    running: Boolean,
    onEnsureFirewall: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val net by vm.netType.collectAsState()
    val networkRoaming by vm.roaming.collectAsState()
    var pickApps by remember { mutableStateOf(false) }

    // Sheet khulte hi suggestion taza (default apps badal gayi hon to)
    LaunchedEffect(Unit) { vm.refreshRoamSuggestions() }

    val active = vm.roamActiveNow(networkRoaming)
    val live = active && running
    val keep = remember(vm.apps, vm.roamEssential, vm.roamSuggested) { vm.roamKeep() }
    val blockedCount = remember(vm.apps, keep, vm.roamSystem) { vm.roamBlockedCount() }
    val keepApps = remember(keep, vm.apps) {
        vm.apps.filter { it.pkg in keep }.sortedBy { it.label.lowercase() }
    }
    val extraSuggestions = remember(vm.apps, vm.roamSuggested, keep, vm.roamEssential) {
        if (vm.roamEssential.isEmpty()) {
            emptyList<AppInfo>()
        } else {
            vm.apps.filter { it.pkg in vm.roamSuggested && it.pkg !in keep }.sortedBy { it.label.lowercase() }
        }
    }

    // Auto-switch on karte waqt notification ki permission (na mile to bhi auto kaam karta hai)
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.chooseRoamAuto(true)
        onEnsureFirewall()
    }
    val toggleAuto: (Boolean) -> Unit = { on ->
        val needsAsk = on && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsAsk) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.chooseRoamAuto(on)
            if (on) onEnsureFirewall() // roaming pakadne ke liye service chalni chahiye
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(ICON_ROAM)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Roaming mode", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                AnimatedContent(
                    targetState = if (live) "Active. Only essential apps use mobile data." else "One switch for when you travel.",
                    transitionSpec = { FadeSlide },
                    label = "roamSub",
                ) { t ->
                    Text(t, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
        }

        // ----- Ek switch -----
        RoamHero(
            on = vm.roamOn,
            active = active,
            live = live,
            auto = vm.roamAuto,
            keepCount = keep.size,
            blockedCount = blockedCount,
            loading = vm.loading,
            onToggle = {
                vm.chooseRoam(it)
                if (it) onEnsureFirewall()
            },
        )

        // ----- Abhi kaun sa network (live) -----
        NetworkChip(net = net, roaming = networkRoaming)

        if (networkRoaming && !active) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = cs.tertiaryContainer,
                    contentColor = cs.onTertiaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("You are roaming right now", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Mobile data can get expensive. Keep only your essential apps online?",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        vm.chooseRoam(true)
                        onEnsureFirewall()
                    }) { Text("Turn on") }
                }
            }
        }

        // ----- Auto -----
        SheetCard(container = if (vm.roamAuto) cs.secondaryContainer else cs.surfaceContainerHigh) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RoamOption(
                    title = "Switch on automatically",
                    subtitle = "Turns on by itself when your phone joins a roaming network, and off when you leave.",
                    checked = vm.roamAuto,
                    onChange = toggleAuto,
                    subtitleColor = if (vm.roamAuto) cs.onSecondaryContainer else cs.onSurfaceVariant,
                )
                if (vm.roamAuto) {
                    Text(
                        if (running) {
                            "Watching your network. You get a notification when it switches on."
                        } else {
                            "The firewall must be on to detect roaming."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSecondaryContainer,
                    )
                    if (!running) {
                        TextButton(onClick = onEnsureFirewall) { Text("Turn on firewall") }
                    }
                }
            }
        }

        // ----- Zaroori apps -----
        SheetCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text("Essential apps", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            vm.loading -> "Loading your apps..."
                            vm.roamEssential.isEmpty() -> "Smart picks: ${roamApps(keep.size)} stay online. Based on your phone, dialer, SMS and maps."
                            else -> "${roamApps(keep.size)} stay online. Everything else goes offline on mobile data."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }

                if (!vm.loading && keepApps.isEmpty()) {
                    Text(
                        "No app is online yet. Add at least one, or every app loses mobile data.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.error,
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    keepApps.forEach { app ->
                        InputChip(
                            selected = false,
                            onClick = {
                                if (keep.size <= 1) {
                                    Toast.makeText(
                                        ctx,
                                        "Keep at least one app. To cut everything, use Block all mobile data in Quick controls.",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                } else {
                                    vm.chooseRoamEssential(keep - app.pkg)
                                }
                            },
                            label = {
                                Text(
                                    app.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 200.dp),
                                )
                            },
                            avatar = { AppIcon(app.pkg, 24.dp) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove ${app.label}",
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = { pickApps = true }) { Text("Add or change apps") }
                    if (vm.roamEssential.isNotEmpty()) {
                        TextButton(onClick = vm::resetRoamEssential) { Text("Smart picks") }
                    }
                }

                if (extraSuggestions.isNotEmpty()) {
                    Text("Suggested for travel", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        extraSuggestions.forEach { app ->
                            SuggestionChip(
                                onClick = { vm.chooseRoamEssential(keep + app.pkg) },
                                label = {
                                    Text(
                                        app.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 200.dp),
                                    )
                                },
                                icon = {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                            )
                        }
                    }
                }
            }
        }

        // ----- System apps -----
        SheetCard {
            Column(modifier = Modifier.padding(16.dp)) {
                RoamOption(
                    title = "Include system apps",
                    subtitle = "Off is safer. Calls, SMS and phone services keep working. Turn on to save even more data.",
                    checked = vm.roamSystem,
                    onChange = vm::chooseRoamSystem,
                    subtitleColor = cs.onSurfaceVariant,
                )
            }
        }

        Text(
            "Roaming mode only touches mobile data. WiFi is never blocked. Calls and SMS use the carrier network, " +
                "not data, so they always work. It keeps working when Guardify is closed.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    if (pickApps) {
        AppPickerDialog(
            title = "Apps that stay online",
            apps = vm.apps,
            initial = keep,
            multi = true,
            onDismiss = { pickApps = false },
            onConfirm = {
                vm.chooseRoamEssential(it)
                pickApps = false
            },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Hero: bada switch card. Rang aur text haal ke hisaab se badalte hain, live hone par halka pulse.
// ---------------------------------------------------------------------------------------------

@Composable
private fun RoamHero(
    on: Boolean,
    active: Boolean,
    live: Boolean,
    auto: Boolean,
    keepCount: Int,
    blockedCount: Int,
    loading: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        when {
            live -> cs.tertiaryContainer
            active -> cs.secondaryContainer
            else -> cs.surfaceContainerHigh
        },
        tween(350),
        label = "roamBg",
    )
    val fg by animateColorAsState(
        when {
            live -> cs.onTertiaryContainer
            active -> cs.onSecondaryContainer
            else -> cs.onSurface
        },
        tween(350),
        label = "roamFg",
    )

    val title = when {
        live && on -> "Roaming mode is on"
        live -> "On automatically"
        active -> "Waiting for the firewall"
        else -> "Roaming mode is off"
    }
    val body = when {
        live && on -> "Only ${roamApps(keepCount)} can use mobile data. WiFi is untouched."
        live -> "Switched on because you are roaming. It turns off when you leave. Flip the switch to keep it on."
        active -> "Turn the firewall on to enforce it."
        else -> "Flip this when you travel. Everything except your essential apps goes offline on mobile data."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    if (live) RoamPulseRing(color = cs.tertiary, size = 52.dp)
                    IconBadge(
                        ICON_ROAM,
                        container = if (live) cs.tertiary else cs.primaryContainer,
                        content = if (live) cs.onTertiary else cs.onPrimaryContainer,
                        size = 48.dp,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    AnimatedContent(targetState = title, transitionSpec = { FadeSlide }, label = "roamTitle") { t ->
                        Text(t, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    AnimatedContent(targetState = body, transitionSpec = { FadeSlide }, label = "roamBody") { t ->
                        Text(t, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.85f))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = on, onCheckedChange = onToggle)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = if (loading) 0 else keepCount,
                    label = if (live) "online" else "will stay online",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = if (loading) 0 else blockedCount,
                    label = if (live) "offline on mobile" else "will go offline",
                    modifier = Modifier.weight(1f),
                )
            }
            if (auto && !on && !live) {
                Text(
                    "Auto is on. This will start by itself when you roam.",
                    style = MaterialTheme.typography.bodySmall,
                    color = fg.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun RoamPulseRing(color: Color, size: Dp) {
    val transition = rememberInfiniteTransition(label = "roamPulse")
    val p by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Restart),
        label = "roamPulseP",
    )
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                val s = 1f + 0.5f * p
                scaleX = s
                scaleY = s
                alpha = 0.4f * (1f - p)
            }
            .background(color, CircleShape),
    )
}

@Composable
private fun StatTile(value: Int, label: String, modifier: Modifier = Modifier) {
    val shown by animateIntAsState(value, tween(450), label = "roamStat")
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("$shown", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Abhi phone kis network par hai, live. Roaming mile to tertiary rang se alag dikhta hai. */
@Composable
private fun NetworkChip(net: NetType, roaming: Boolean) {
    val cs = MaterialTheme.colorScheme
    val label: String
    val dotColor: Color
    when {
        net == NetType.MOBILE && roaming -> {
            label = "Roaming network detected"
            dotColor = cs.tertiary
        }
        net == NetType.MOBILE -> {
            label = "On your home mobile network"
            dotColor = cs.primary
        }
        net == NetType.WIFI -> {
            label = "On WiFi, roaming mode is not needed"
            dotColor = cs.secondary
        }
        else -> {
            label = "No connection"
            dotColor = cs.outline
        }
    }
    val dot by animateColorAsState(dotColor, tween(300), label = "roamDot")
    Surface(shape = CircleShape, color = cs.surfaceContainerHighest) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
            AnimatedContent(targetState = label, transitionSpec = { FadeSlide }, label = "roamNet") { t ->
                Text(t, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RoamOption(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitleColor: Color,
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
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---------------------------------------------------------------------------------------------
// Home par dikhne wale cards: mode on ho to status, roaming mile par (mode off) sujhav.
// ---------------------------------------------------------------------------------------------

@Composable
internal fun RoamCard(
    running: Boolean,
    manual: Boolean,
    keepCount: Int,
    onEdit: () -> Unit,
    onTurnOff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = cs.tertiaryContainer,
            contentColor = cs.onTertiaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(ICON_ROAM, container = cs.tertiary, content = cs.onTertiary)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        !running -> "Roaming mode is waiting"
                        manual -> "Roaming mode is on"
                        else -> "Roaming mode is on (auto)"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    if (running) {
                        "Only ${roamApps(keepCount)} can use mobile data."
                    } else {
                        "Turn the firewall on to enforce it."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onEdit) { Text("Edit") }
            if (manual) TextButton(onClick = onTurnOff) { Text("Off") }
        }
    }
}

@Composable
internal fun RoamNudgeCard(
    onTurnOn: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = cs.secondaryContainer,
            contentColor = cs.onSecondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(ICON_ROAM, container = cs.secondary, content = cs.onSecondary)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("You are roaming", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Keep only essential apps online to save data?",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onDismiss) { Text("Not now") }
            TextButton(onClick = onTurnOn) { Text("Turn on") }
        }
    }
}
