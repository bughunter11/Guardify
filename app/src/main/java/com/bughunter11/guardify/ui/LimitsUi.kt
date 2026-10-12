@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bughunter11.guardify.ui

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bughunter11.guardify.data.AppInfo
import com.bughunter11.guardify.data.LimitEngine
import java.util.Date

// ---------------------------------------------------------------------------------------------
// Data limits: "Instagram ko mahine me 2 GB do, uske baad mobile par apne aap block."
// ---------------------------------------------------------------------------------------------

@Composable
internal fun LimitsSheet(
    vm: MainViewModel,
    onGrantUsage: () -> Unit,
    onEnsureFirewall: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val byPkg = remember(vm.apps) { vm.apps.associateBy { it.pkg } }
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }

    val entries = remember(vm.limits, vm.limitBlocked, vm.cycleUsage, vm.apps) {
        vm.limits.entries
            .map { (pkg, limit) ->
                val uid = byPkg[pkg]?.uid
                Triple(pkg, limit, if (uid != null) vm.cycleMobile(uid) else 0L)
            }
            .sortedWith(
                compareByDescending<Triple<String, Long, Long>> { it.first in vm.limitBlocked }
                    .thenByDescending { it.third.toFloat() / it.second },
            )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(ICON_USAGE)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Data limits", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Monthly mobile data cap for each app. Over the cap, the app is blocked on mobile data until the next cycle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }

        if (!vm.hasUsageAccess) UsageAccessCard(onGrantUsage)

        // Billing cycle
        SheetCard {
            var open by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Billing cycle", style = MaterialTheme.typography.titleSmall)
                    val next = remember(vm.cycleDay) {
                        DateFormat.getMediumDateFormat(ctx).format(Date(LimitEngine.nextCycleStart(vm.cycleDay)))
                    }
                    Text(
                        "Counters reset on $next",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
                Box {
                    AssistChip(
                        onClick = { open = true },
                        label = { Text("Day ${vm.cycleDay}") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                    )
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        (1..28).forEach { d ->
                            DropdownMenuItem(
                                text = { Text("Day $d") },
                                onClick = {
                                    open = false
                                    vm.chooseCycleDay(d)
                                },
                                trailingIcon = if (d == vm.cycleDay) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            SheetCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconBadge(ICON_USAGE, size = 56.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("No limits yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "For example: Instagram 2 GB per month, YouTube 5 GB per month.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        } else {
            entries.forEach { (pkg, limit, used) ->
                LimitRow(
                    pkg = pkg,
                    label = byPkg[pkg]?.label ?: pkg,
                    limit = limit,
                    used = used,
                    reached = pkg in vm.limitBlocked,
                    showUsage = vm.hasUsageAccess,
                    onEdit = { editing = pkg },
                )
            }
        }

        FilledTonalButton(
            onClick = { picking = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add app limit")
        }
    }

    if (picking) {
        AppPickerDialog(
            title = "Choose app",
            apps = vm.apps,
            initial = emptySet(),
            multi = false,
            excluded = vm.limits.keys,
            onDismiss = { picking = false },
            onConfirm = {
                picking = false
                editing = it.firstOrNull()
            },
        )
    }

    editing?.let { pkg ->
        val app = byPkg[pkg]
        LimitDialog(
            label = app?.label ?: pkg,
            currentBytes = vm.limits[pkg] ?: 0L,
            usedBytes = app?.let { vm.cycleMobile(it.uid) } ?: 0L,
            onDismiss = { editing = null },
            onSave = {
                editing = null
                vm.setLimit(pkg, it)
                onEnsureFirewall()
            },
            onRemove = if (vm.limits.containsKey(pkg)) {
                {
                    editing = null
                    vm.removeLimit(pkg)
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun LimitRow(
    pkg: String,
    label: String,
    limit: Long,
    used: Long,
    reached: Boolean,
    showUsage: Boolean,
    onEdit: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val frac by animateFloatAsState(
        (used.toFloat() / limit).coerceIn(0f, 1f),
        tween(500),
        label = "limitFrac",
    )
    SheetCard(container = if (reached) cs.errorContainer else cs.surfaceContainerHigh) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(pkg, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (showUsage) {
                        "${Formatter.formatShortFileSize(ctx, used)} of ${Formatter.formatShortFileSize(ctx, limit)}"
                    } else {
                        "${Formatter.formatShortFileSize(ctx, limit)} per month"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (reached) cs.onErrorContainer else cs.onSurfaceVariant,
                )
                if (showUsage) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { frac },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = if (reached) cs.error else if (frac > 0.8f) cs.tertiary else cs.primary,
                        trackColor = if (reached) cs.surface.copy(alpha = 0.5f) else cs.surfaceContainerHighest,
                    )
                }
                if (reached) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Limit reached. Blocked on mobile data.",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.error,
                    )
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit limit for $label") }
        }
    }
}

private fun trimNumber(d: Double): String =
    if (d % 1.0 == 0.0) d.toLong().toString() else "%.2f".format(d).trimEnd('0').trimEnd('.')

/** Limit set karne ka dialog: number + MB/GB + quick presets. */
@Composable
internal fun LimitDialog(
    label: String,
    currentBytes: Long,
    usedBytes: Long,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
    onRemove: (() -> Unit)?,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var gb by remember { mutableStateOf(currentBytes == 0L || currentBytes >= LimitEngine.GB) }
    var text by remember {
        mutableStateOf(
            when {
                currentBytes == 0L -> ""
                currentBytes >= LimitEngine.GB -> trimNumber(currentBytes.toDouble() / LimitEngine.GB)
                else -> trimNumber(currentBytes.toDouble() / LimitEngine.MB)
            },
        )
    }
    val value = text.replace(',', '.').toDoubleOrNull()
    val bytes = value?.let { (it * if (gb) LimitEngine.GB else LimitEngine.MB).toLong() } ?: 0L
    val valid = bytes in 1L..(1000L * LimitEngine.GB)
    val presets = remember {
        listOf("500 MB" to 500L * LimitEngine.MB, "1 GB" to LimitEngine.GB, "2 GB" to 2 * LimitEngine.GB, "5 GB" to 5 * LimitEngine.GB)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monthly limit for $label") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presets.forEach { (name, b) ->
                        FilterChip(
                            selected = valid && bytes == b,
                            onClick = {
                                gb = b >= LimitEngine.GB
                                text = trimNumber(b.toDouble() / if (gb) LimitEngine.GB else LimitEngine.MB)
                            },
                            label = { Text(name) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { s -> text = s.filter { it.isDigit() || it == '.' || it == ',' }.take(7) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Amount") },
                        isError = text.isNotEmpty() && !valid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = !gb, onClick = { gb = false }, label = { Text("MB") })
                    Spacer(Modifier.width(6.dp))
                    FilterChip(selected = gb, onClick = { gb = true }, label = { Text("GB") })
                }
                if (valid && usedBytes >= bytes) {
                    Text(
                        "${Formatter.formatShortFileSize(ctx, usedBytes)} already used this cycle, so it will be blocked right away.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.error,
                    )
                } else {
                    Text(
                        "Applies to mobile data only. WiFi is not counted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(bytes) }, enabled = valid) { Text("Save") } },
        dismissButton = {
            Row {
                if (onRemove != null) {
                    TextButton(onClick = onRemove) { Text("Remove", color = cs.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** Card se seedha limit set karne ke liye (MainScreen se). */
@Composable
internal fun AppLimitDialog(
    app: AppInfo,
    vm: MainViewModel,
    onDismiss: () -> Unit,
    onEnsureFirewall: () -> Unit,
) {
    LimitDialog(
        label = app.label,
        currentBytes = vm.limits[app.pkg] ?: 0L,
        usedBytes = vm.cycleMobile(app.uid),
        onDismiss = onDismiss,
        onSave = {
            vm.setLimit(app.pkg, it)
            onEnsureFirewall()
            onDismiss()
        },
        onRemove = if (vm.limits.containsKey(app.pkg)) {
            {
                vm.removeLimit(app.pkg)
                onDismiss()
            }
        } else {
            null
        },
    )
}
