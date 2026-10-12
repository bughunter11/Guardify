@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bughunter11.guardify.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Settings: theme, accent, pure black, rules, community links.
 * Har section ek M3 card me. Koi bhi option badalte hi poori app crossfade hoti hai.
 */
@Composable
internal fun SettingsSheet(
    vm: MainViewModel,
    hasRules: Boolean,
    onShare: () -> Unit,
    onReset: () -> Unit,
    onChannel: () -> Unit,
    onGroup: () -> Unit,
    onGithub: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val dynamicOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
            .getOrNull().orEmpty()
    }
    val clear = ListItemDefaults.colors(containerColor = Color.Transparent)
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        vm.chooseNewAppAlerts(ok)
    }
    val toggleAlerts: (Boolean) -> Unit = { on ->
        val needsAsk = on && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsAsk) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.chooseNewAppAlerts(on)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GuardifyLogo(modifier = Modifier.height(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        SettingsGroup("Appearance") {
            val modes = listOf(0 to "System", 1 to "Light", 2 to "Dark")
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = vm.themeMode == mode,
                        onClick = { vm.chooseTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    ) { Text(label) }
                }
            }

            Text(
                "Accent color",
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
            )
            FlowRow(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (dynamicOk) {
                    Swatch("Wallpaper", null, vm.accent == ACCENT_DYNAMIC) { vm.pickAccent(ACCENT_DYNAMIC) }
                }
                AccentPresets.forEach { (name, argb) ->
                    Swatch(name, Color(argb), vm.accent == argb) { vm.pickAccent(argb) }
                }
            }
            if (!dynamicOk) {
                Text(
                    "Wallpaper colors need Android 12 or newer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp),
                color = cs.outlineVariant,
            )
            ListItem(
                modifier = Modifier.clickable { vm.chooseAmoled(!vm.amoled) },
                colors = clear,
                headlineContent = { Text("Pure black") },
                supportingContent = {
                    Text("Darker background in dark mode, saves battery on AMOLED screens.")
                },
                trailingContent = {
                    Switch(checked = vm.amoled, onCheckedChange = { vm.chooseAmoled(it) })
                },
            )
        }

        SettingsGroup("Privacy") {
            ListItem(
                modifier = Modifier.clickable { toggleAlerts(!vm.newAppAlerts) },
                colors = clear,
                headlineContent = { Text("New app alerts") },
                supportingContent = {
                    Text("Get a notification when an app is installed, with Block and Allow buttons.")
                },
                trailingContent = {
                    Switch(checked = vm.newAppAlerts, onCheckedChange = { toggleAlerts(it) })
                },
            )
            HorizontalDivider(color = cs.outlineVariant)
            ListItem(
                modifier = Modifier.clickable(enabled = vm.newAppAlerts) { vm.chooseHoldNew(!vm.holdNew) },
                colors = clear,
                headlineContent = { Text("Block new apps until I decide") },
                supportingContent = {
                    Text("New apps start blocked on WiFi and mobile data. Tap Allow in the alert to let them online.")
                },
                trailingContent = {
                    Switch(
                        checked = vm.holdNew && vm.newAppAlerts,
                        onCheckedChange = { vm.chooseHoldNew(it) },
                        enabled = vm.newAppAlerts,
                    )
                },
            )
        }

        SettingsGroup("Rules") {
            ListItem(
                modifier = Modifier.clickable(enabled = hasRules, onClick = onShare),
                colors = clear,
                leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                headlineContent = { Text("Share my rules") },
                supportingContent = { Text("Send the list of blocked apps as text.") },
            )
            HorizontalDivider(color = cs.outlineVariant)
            ListItem(
                modifier = Modifier.clickable(enabled = hasRules, onClick = onReset),
                colors = clear,
                leadingContent = {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = cs.error)
                },
                headlineContent = { Text("Reset all rules", color = cs.error) },
                supportingContent = { Text("Allow every app on WiFi and mobile data.") },
            )
        }

        SettingsGroup("About") {
            LinkRow(
                title = "Developer",
                subtitle = "Raj",
                icon = Icons.Default.Person,
                onClick = null,
            )
            HorizontalDivider(color = cs.outlineVariant)
            LinkRow(
                title = "Support",
                subtitle = "Like the app? Send any amount via UPI.",
                icon = Icons.Default.Favorite,
                onClick = { openUpi(ctx) },
            )
            HorizontalDivider(color = cs.outlineVariant)
            LinkRow(
                title = "Zyron Official",
                subtitle = "Telegram channel: news and updates",
                icon = Icons.AutoMirrored.Filled.Send,
                onClick = onChannel,
            )
            HorizontalDivider(color = cs.outlineVariant)
            LinkRow(
                title = "Zyron Community",
                subtitle = "Telegram group: chat, help and feedback",
                icon = Icons.Default.Info,
                onClick = onGroup,
            )
            if (onGithub != null) {
                HorizontalDivider(color = cs.outlineVariant)
                LinkRow(
                    title = "Source code",
                    subtitle = "Open source on GitHub. A star helps a lot.",
                    icon = Icons.Default.Star,
                    onClick = onGithub,
                )
            }
        }

        Text(
            if (version.isNotEmpty()) "Guardify $version" else "Guardify",
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Section ka label + uske neeche ek rounded card. */
@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp),
        )
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, icon: ImageVector, onClick: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    ListItem(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(cs.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = cs.onPrimaryContainer)
            }
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = if (onClick != null) {
            {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                )
            }
        } else {
            null
        },
    )
}

/** Accent ka gola. Select hone par halka bada hota hai, border aur tick fade/scale se aate hain. */
@Composable
private fun Swatch(label: String, color: Color?, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ring by animateColorAsState(
        if (selected) cs.onSurface else Color.Transparent,
        tween(200),
        label = "swatchRing",
    )
    val grow by animateFloatAsState(
        if (selected) 1.08f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "swatchScale",
    )
    val fill = if (color != null) {
        Modifier.background(color)
    } else {
        Modifier.background(
            Brush.sweepGradient(
                listOf(
                    Color(0xFF4285F4), Color(0xFF34A853), Color(0xFFFBBC05),
                    Color(0xFFEA4335), Color(0xFF4285F4),
                ),
            ),
        )
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer {
                    scaleX = grow
                    scaleY = grow
                }
                .clip(CircleShape)
                .then(fill)
                .border(3.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = selected,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
