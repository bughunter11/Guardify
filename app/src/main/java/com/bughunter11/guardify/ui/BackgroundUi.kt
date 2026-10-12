@file:OptIn(ExperimentalMaterial3Api::class)

package com.bughunter11.guardify.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bughunter11.guardify.data.AppInfo

// ---------------------------------------------------------------------------------------------
// Background block: app khuli ho tab net chalta hai, band hone par (grace ke baad) net band.
// Battery aur data dono bachte hain.
// ---------------------------------------------------------------------------------------------

private val GraceOptions = listOf(0 to "Instant", 10 to "10 sec", 30 to "30 sec", 60 to "1 min")

@Composable
internal fun BackgroundSheet(
    vm: MainViewModel,
    onGrantUsage: () -> Unit,
    onEnsureFirewall: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.88f).dp

    val list = remember(vm.apps, vm.bgWifi, vm.bgMobile, query, showSystem) {
        val q = query.trim()
        vm.apps
            .filter {
                (showSystem || !it.isSystem || it.pkg in vm.bgWifi || it.pkg in vm.bgMobile) &&
                    (q.isEmpty() || it.label.contains(q, true) || it.pkg.contains(q, true))
            }
            .sortedWith(
                compareByDescending<AppInfo> { it.pkg in vm.bgWifi || it.pkg in vm.bgMobile }
                    .thenBy { it.label.lowercase() },
            )
    }
    val ruled = (vm.bgWifi + vm.bgMobile).size

    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = maxH),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "~header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(ICON_LAYERS)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Background data", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (ruled > 0) "$ruled apps only get internet while open." else "Pick apps that should only use internet while open.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }

        if (!vm.hasUsageAccess) {
            item(key = "~usage") { UsageAccessCard(onGrantUsage) }
        }

        item(key = "~grace") {
            SheetCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cut internet after closing", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "A short delay avoids breaking uploads right after you leave an app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GraceOptions.forEach { (sec, label) ->
                            FilterChip(
                                selected = vm.bgGrace == sec,
                                onClick = { vm.chooseGrace(sec) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
        }

        item(key = "~search") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchField(value = query, onChange = { query = it })
                FilterChip(
                    selected = showSystem,
                    onClick = { showSystem = !showSystem },
                    label = { Text("System apps") },
                )
            }
        }

        items(list, key = { it.pkg }, contentType = { "bgApp" }) { app ->
            val wifi = app.pkg in vm.bgWifi
            val mobile = app.pkg in vm.bgMobile
            SheetCard(
                container = if (wifi || mobile) cs.secondaryContainer else cs.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app.pkg, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            app.label,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            when {
                                wifi && mobile -> "WiFi and mobile only while open"
                                wifi -> "WiFi only while open"
                                mobile -> "Mobile only while open"
                                else -> "Allowed in background"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (wifi || mobile) cs.onSecondaryContainer else cs.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    NetToggle(wifi = true, blocked = wifi, appLabel = app.label, what = "background block") {
                        vm.setBg(listOf(app.pkg), wifi = true, mobile = false, blocked = it)
                        if (it) onEnsureFirewall()
                    }
                    Spacer(Modifier.width(6.dp))
                    NetToggle(wifi = false, blocked = mobile, appLabel = app.label, what = "background block") {
                        vm.setBg(listOf(app.pkg), wifi = false, mobile = true, blocked = it)
                        if (it) onEnsureFirewall()
                    }
                }
            }
        }
    }
}
