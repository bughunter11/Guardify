@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bughunter11.guardify.ui

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bughunter11.guardify.data.ALL_DAYS
import com.bughunter11.guardify.data.AppCategories
import com.bughunter11.guardify.data.AppInfo
import com.bughunter11.guardify.data.Schedule
import com.bughunter11.guardify.data.SchedulePresets
import com.bughunter11.guardify.data.Scope
import com.bughunter11.guardify.data.WEEKDAYS
import com.bughunter11.guardify.data.WEEKEND
import kotlinx.coroutines.delay
import java.util.Calendar

// ---------------------------------------------------------------------------------------------
// Schedules: raat 12 se subah 6 tak sab band, ya padhai ke time social apps band.
// Service ye rules app band hone par bhi lagati rehti hai.
// ---------------------------------------------------------------------------------------------

private val DayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

internal fun formatMinutes(ctx: android.content.Context, min: Int): String {
    val c = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, min / 60)
        set(Calendar.MINUTE, min % 60)
    }
    return DateFormat.getTimeFormat(ctx).format(c.time)
}

private fun daysText(days: Int): String = when {
    days == ALL_DAYS -> "Every day"
    days == WEEKDAYS -> "Weekdays"
    days == WEEKEND -> "Weekends"
    days == 0 -> "No days"
    else -> DayNames.filterIndexed { i, _ -> (days and (1 shl i)) != 0 }.joinToString(", ")
}

private fun netsText(s: Schedule): String = when {
    s.wifi && s.mobile -> "WiFi and mobile"
    s.wifi -> "WiFi"
    s.mobile -> "Mobile data"
    else -> "No network"
}

private fun scopeText(s: Schedule): String = when (s.scope) {
    Scope.ALL -> if (s.includeSystem) "All apps incl. system" else "All apps"
    Scope.CATEGORY -> (AppCategories.all.firstOrNull { it.first == s.category }?.second ?: "Category") + " apps"
    Scope.APPS -> if (s.pkgs.size == 1) "1 app" else "${s.pkgs.size} apps"
}

@Composable
internal fun SchedulesSheet(
    vm: MainViewModel,
    running: Boolean,
    onTurnOn: () -> Unit,
    onEnsureFirewall: () -> Unit,
) {
    var editing by remember { mutableStateOf<Schedule?>(null) }
    var isNew by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = editing,
        contentKey = { it?.id },
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "scheduleScreens",
    ) { current ->
        if (current == null) {
            ScheduleList(
                vm = vm,
                running = running,
                onTurnOn = onTurnOn,
                onEdit = { isNew = false; editing = it },
                onNew = { s -> isNew = true; editing = s },
                onEnsureFirewall = onEnsureFirewall,
            )
        } else {
            ScheduleEditor(
                initial = current,
                isNew = isNew,
                apps = vm.apps,
                onBack = { editing = null },
                onSave = {
                    vm.saveSchedule(it)
                    if (it.enabled) onEnsureFirewall()
                    editing = null
                },
                onDelete = {
                    vm.deleteSchedule(current.id)
                    editing = null
                },
            )
        }
    }
}

@Composable
private fun ScheduleList(
    vm: MainViewModel,
    running: Boolean,
    onTurnOn: () -> Unit,
    onEdit: (Schedule) -> Unit,
    onNew: (Schedule) -> Unit,
    onEnsureFirewall: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    // "Active now" badge har 20 sec me taza hota hai
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(20_000)
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
            IconBadge(ICON_SCHEDULE)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Schedules", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Block apps automatically at set times. Works even when Guardify is closed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }

        AnimatedVisibility(visible = !running && vm.schedules.any { it.enabled }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = cs.tertiaryContainer,
                    contentColor = cs.onTertiaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Firewall is off, so schedules are paused.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(onClick = onTurnOn) { Text("Turn on") }
                }
            }
        }

        vm.schedules.forEach { s ->
            val active = s.isActiveAt(now)
            SheetCard(container = if (active) cs.tertiaryContainer else cs.surfaceContainerHigh) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableCard { onEdit(s) }
                        .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                s.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (active) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = cs.tertiary,
                                    contentColor = cs.onTertiary,
                                ) {
                                    Text(
                                        "Active",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            "${formatMinutes(ctx, s.startMin)} – ${formatMinutes(ctx, s.endMin)} · ${daysText(s.days)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "${scopeText(s)} · ${netsText(s)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (active) cs.onTertiaryContainer else cs.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = s.enabled,
                        onCheckedChange = {
                            vm.setScheduleEnabled(s.id, it)
                            if (it) onEnsureFirewall()
                        },
                    )
                }
            }
        }

        if (vm.schedules.isEmpty()) {
            SheetCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconBadge(ICON_SCHEDULE, size = 56.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("No schedules yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Start with a ready-made rule below, or build your own.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }

        Text("Quick add", style = MaterialTheme.typography.labelLarge, color = cs.primary)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(onClick = { onNew(SchedulePresets.night()) }, label = { Text("Night block · 12–6 AM") })
            AssistChip(onClick = { onNew(SchedulePresets.study()) }, label = { Text("Study time · social apps") })
        }

        FilledTonalButton(
            onClick = {
                onNew(Schedule(name = "", startMin = 22 * 60, endMin = 7 * 60))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("New schedule")
        }
    }
}

/** Card pe tap, ripple ke saath. */
private fun Modifier.clickableCard(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

@Composable
private fun ScheduleEditor(
    initial: Schedule,
    isNew: Boolean,
    apps: List<AppInfo>,
    onBack: () -> Unit,
    onSave: (Schedule) -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current

    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var startMin by remember(initial.id) { mutableStateOf(initial.startMin) }
    var endMin by remember(initial.id) { mutableStateOf(initial.endMin) }
    var days by remember(initial.id) { mutableStateOf(initial.days) }
    var wifi by remember(initial.id) { mutableStateOf(initial.wifi) }
    var mobile by remember(initial.id) { mutableStateOf(initial.mobile) }
    var scope by remember(initial.id) { mutableStateOf(initial.scope) }
    var category by remember(initial.id) { mutableStateOf(initial.category) }
    var includeSystem by remember(initial.id) { mutableStateOf(initial.includeSystem) }
    var pkgs by remember(initial.id) { mutableStateOf(initial.pkgs) }

    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var pickApps by remember { mutableStateOf(false) }

    val categories = remember(apps) {
        AppCategories.all.filter { (id, _) -> apps.any { it.category == id } }
    }
    val problem = when {
        days == 0 -> "Pick at least one day."
        !wifi && !mobile -> "Pick WiFi, mobile data or both."
        scope == Scope.APPS && pkgs.isEmpty() -> "Choose at least one app."
        scope == Scope.CATEGORY && categories.none { it.first == category } -> "Choose a category."
        else -> null
    }
    val draft = Schedule(
        id = initial.id,
        name = name.trim().ifBlank { "Schedule" },
        enabled = initial.enabled,
        startMin = startMin,
        endMin = endMin,
        days = days,
        wifi = wifi,
        mobile = mobile,
        scope = scope,
        category = category,
        includeSystem = includeSystem,
        pkgs = pkgs,
    )
    val overnight = startMin > endMin

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                if (isNew) "New schedule" else "Edit schedule",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (!isNew) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete schedule", tint = cs.error)
                }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(30) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Name") },
            placeholder = { Text("Night block") },
        )

        EditorSection("Time") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeButton("From", formatMinutes(ctx, startMin), Modifier.weight(1f)) { pickStart = true }
                TimeButton("Until", formatMinutes(ctx, endMin), Modifier.weight(1f)) { pickEnd = true }
            }
            if (overnight) {
                Text(
                    "Ends the next morning.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }

        EditorSection("Days") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DayNames.forEachIndexed { i, n ->
                    FilterChip(
                        selected = (days and (1 shl i)) != 0,
                        onClick = { days = days xor (1 shl i) },
                        label = { Text(n) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { days = ALL_DAYS }) { Text("Every day") }
                TextButton(onClick = { days = WEEKDAYS }) { Text("Weekdays") }
                TextButton(onClick = { days = WEEKEND }) { Text("Weekends") }
            }
        }

        EditorSection("Block on") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mobile,
                    onClick = { mobile = !mobile },
                    label = { Text("Mobile data") },
                    leadingIcon = if (mobile) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                )
                FilterChip(
                    selected = wifi,
                    onClick = { wifi = !wifi },
                    label = { Text("WiFi") },
                    leadingIcon = if (wifi) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                )
            }
        }

        EditorSection("Which apps") {
            val options = listOf(Scope.ALL to "All apps", Scope.CATEGORY to "Category", Scope.APPS to "Pick apps")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (s, label) ->
                    SegmentedButton(
                        selected = scope == s,
                        onClick = { scope = s },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    ) { Text(label) }
                }
            }
            when (scope) {
                Scope.ALL -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Include system apps", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Off is safer. Calls, SMS and system services keep working.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    Switch(checked = includeSystem, onCheckedChange = { includeSystem = it })
                }

                Scope.CATEGORY -> {
                    if (categories.isEmpty()) {
                        Text(
                            "No app on this phone has declared a category.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            categories.forEach { (id, label) ->
                                FilterChip(
                                    selected = category == id,
                                    onClick = { category = id },
                                    label = { Text(label) },
                                )
                            }
                        }
                        val count = apps.count { it.category == category }
                        Text(
                            "$count apps on this phone are in this category.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }

                Scope.APPS -> {
                    if (pkgs.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            pkgs.take(12).forEach { AppIcon(it, 36.dp) }
                        }
                    }
                    FilledTonalButton(onClick = { pickApps = true }) {
                        Text(if (pkgs.isEmpty()) "Choose apps" else "${pkgs.size} selected · change")
                    }
                }
            }
        }

        // Ek line ka summary: kya hone wala hai
        Surface(shape = RoundedCornerShape(20.dp), color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer) {
            Text(
                "Blocks ${scopeText(draft).lowercase()} on ${netsText(draft).lowercase()}, ${daysText(days).lowercase()}, " +
                    "from ${formatMinutes(ctx, startMin)} to ${formatMinutes(ctx, endMin)}.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(14.dp),
            )
        }

        if (problem != null) {
            Text(problem, style = MaterialTheme.typography.bodySmall, color = cs.error)
        }
        FilledTonalButton(
            onClick = { onSave(draft) },
            enabled = problem == null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (isNew) "Create schedule" else "Save changes") }
    }

    if (pickStart) {
        TimeDialog("From", startMin, { pickStart = false }) { startMin = it; pickStart = false }
    }
    if (pickEnd) {
        TimeDialog("Until", endMin, { pickEnd = false }) { endMin = it; pickEnd = false }
    }
    if (pickApps) {
        AppPickerDialog(
            title = "Choose apps",
            apps = apps,
            initial = pkgs,
            multi = true,
            onDismiss = { pickApps = false },
            onConfirm = { pkgs = it; pickApps = false },
        )
    }
}

@Composable
private fun EditorSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun TimeButton(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = cs.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Material 3 time picker. Chhote phone par compact TimeInput aata hai taaki dialog screen se bahar na jaye. */
@Composable
private fun TimeDialog(
    title: String,
    initialMin: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    val wide = LocalConfiguration.current.screenWidthDp >= 400
    val state = rememberTimePickerState(
        initialHour = initialMin / 60,
        initialMinute = initialMin % 60,
        is24Hour = DateFormat.is24HourFormat(ctx),
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                )
                if (wide) TimePicker(state = state) else TimeInput(state = state)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") }
                }
            }
        }
    }
}
