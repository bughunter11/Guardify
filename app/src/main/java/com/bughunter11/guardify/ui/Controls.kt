@file:OptIn(ExperimentalMaterial3Api::class)

package com.bughunter11.guardify.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bughunter11.guardify.data.AppCategories
import com.bughunter11.guardify.data.AppInfo
import com.bughunter11.guardify.data.SortMode

/** Search + ek line ke chips (period, sort, filters) + app count. */
@Composable
internal fun Controls(
    vm: MainViewModel,
    blockedAny: Int,
    shown: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SearchField(value = vm.query, onChange = { vm.query = it })
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PeriodChip(days = vm.days, onPick = vm::selectPeriod)
            SortChip(current = vm.sortMode, onPick = vm::chooseSort)
            CategoryChip(current = vm.category, apps = vm.apps, onPick = { vm.category = it })
            ToggleChip("Blocked ($blockedAny)", vm.onlyBlocked) { vm.onlyBlocked = !vm.onlyBlocked }
            if (vm.hasUsageAccess) {
                ToggleChip("With data", vm.onlyWithData) { vm.onlyWithData = !vm.onlyWithData }
            }
            ToggleChip("System apps", vm.showSystem) { vm.showSystem = !vm.showSystem }
        }
        Row(
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Apps · ",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Count badalne par number upar/neeche slide hota hai
            AnimatedContent(
                targetState = shown,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                        (slideOutVertically { if (up) -it else it } + fadeOut())
                },
                label = "appCount",
            ) { n ->
                Text(
                    n.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search apps") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            AnimatedVisibility(
                visible = value.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = cs.surfaceContainerHigh,
            unfocusedContainerColor = cs.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun PeriodChip(days: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val options = remember { listOf(1 to "24 hours", 7 to "7 days", 30 to "30 days") }
    val arrow by animateFloatAsState(if (open) 180f else 0f, tween(200), label = "periodArrow")
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text("Last ${options.firstOrNull { it.first == days }?.second ?: "7 days"}") },
            trailingIcon = {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize).rotate(arrow),
                )
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (d, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        open = false
                        onPick(d)
                    },
                    trailingIcon = if (d == days) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun SortChip(current: SortMode, onPick: (SortMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val arrow by animateFloatAsState(if (open) 180f else 0f, tween(200), label = "sortArrow")
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text("Sort: ${current.label}") },
            trailingIcon = {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize).rotate(arrow),
                )
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = {
                        open = false
                        onPick(mode)
                    },
                    trailingIcon = if (mode == current) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/** Category se filter (Social, Games...). Upar ke menu ka "Block all shown" ab poori category par chalta hai. */
@Composable
private fun CategoryChip(current: Int, apps: List<AppInfo>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val available = remember(apps) {
        AppCategories.all.filter { (id, _) -> apps.any { it.category == id } }
    }
    val arrow by animateFloatAsState(if (open) 180f else 0f, tween(200), label = "categoryArrow")
    if (available.isNotEmpty()) {
        val name = available.firstOrNull { it.first == current }?.second
        Box {
            FilterChip(
                selected = name != null,
                onClick = { open = true },
                label = { Text(name ?: "Category") },
                trailingIcon = {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize).rotate(arrow),
                    )
                },
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text("All apps") },
                    onClick = {
                        open = false
                        onPick(AppCategories.ALL)
                    },
                    trailingIcon = if (name == null) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
                available.forEach { (id, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            open = false
                            onPick(id)
                        },
                        trailingIcon = if (id == current) {
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

@Composable
private fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
    )
}

/** Khali list par seedha kaam ka button: filter hatao ya system apps dikhao. */
@Composable
internal fun EmptyState(
    filtered: Boolean,
    onClearFilters: () -> Unit,
    onShowSystem: () -> Unit,
    noRules: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer) {
            Icon(
                if (noRules) Icons.Default.Lock else Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.padding(20.dp).size(32.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            when {
                noRules -> "No blocked apps yet"
                filtered -> "No apps match"
                else -> "No apps to show"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                noRules -> "Tap the WiFi or mobile icon on any app to block it."
                filtered -> "Clear the search or turn off a filter."
                else -> "System apps are hidden right now."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = if (filtered || noRules) onClearFilters else onShowSystem) {
            Text(if (noRules) "Show all apps" else if (filtered) "Clear filters" else "Show system apps")
        }
    }
}

@Composable
internal fun UsageAccessCard(onGrant: () -> Unit, modifier: Modifier = Modifier) {
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
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Info, contentDescription = null)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("See data used by each app", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Allow usage access to show WiFi and mobile data per app.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onGrant) { Text("Allow") }
        }
    }
}
