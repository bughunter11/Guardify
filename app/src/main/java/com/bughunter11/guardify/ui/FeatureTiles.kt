@file:OptIn(ExperimentalMaterial3Api::class)

package com.bughunter11.guardify.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Hero ke neeche tiles: Data limits, Schedules, Background, phir Quick controls, Data alerts aur Roaming.
 * Har tile apna live status dikhata hai (kitne rules, kaun sa abhi active / limit paar).
 */
@Composable
internal fun FeatureTiles(
    limitCount: Int,
    reachedCount: Int,
    scheduleCount: Int,
    activeNow: Int,
    bgCount: Int,
    killOn: Boolean,
    focusOn: Boolean,
    guzzlerOn: Boolean,
    guzzlerLabel: String,
    roamActive: Boolean,
    roamAuto: Boolean,
    roaming: Boolean,
    roamKeep: Int,
    onLimits: () -> Unit,
    onSchedules: () -> Unit,
    onBackground: () -> Unit,
    onQuick: () -> Unit,
    onAlerts: () -> Unit,
    onRoaming: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FeatureTile(
            title = "Data limits",
            subtitle = when {
                reachedCount > 0 -> "$reachedCount reached"
                limitCount > 0 -> "$limitCount set"
                else -> "Monthly cap"
            },
            icon = ICON_USAGE,
            state = when {
                reachedCount > 0 -> TileState.Alert
                limitCount > 0 -> TileState.On
                else -> TileState.Off
            },
            onClick = onLimits,
            modifier = Modifier.weight(1f),
        )
        FeatureTile(
            title = "Schedules",
            subtitle = when {
                activeNow > 0 -> "$activeNow active now"
                scheduleCount > 0 -> "$scheduleCount rules"
                else -> "Auto block"
            },
            icon = ICON_SCHEDULE,
            state = when {
                activeNow > 0 -> TileState.Alert
                scheduleCount > 0 -> TileState.On
                else -> TileState.Off
            },
            onClick = onSchedules,
            modifier = Modifier.weight(1f),
        )
        FeatureTile(
            title = "Background",
            subtitle = if (bgCount > 0) "$bgCount apps" else "Open-only net",
            icon = ICON_LAYERS,
            state = if (bgCount > 0) TileState.On else TileState.Off,
            onClick = onBackground,
            modifier = Modifier.weight(1f),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FeatureTile(
            title = "Quick controls",
            subtitle = when {
                killOn -> "Mobile data off"
                focusOn -> "Focus is on"
                else -> "Tile and widget"
            },
            icon = ICON_BLOCK,
            state = when {
                killOn -> TileState.Alert
                focusOn -> TileState.On
                else -> TileState.Off
            },
            onClick = onQuick,
            modifier = Modifier.weight(1f),
        )
        FeatureTile(
            title = "Data alerts",
            subtitle = if (guzzlerOn) "Over $guzzlerLabel a day" else "Off",
            icon = ICON_BELL,
            state = if (guzzlerOn) TileState.On else TileState.Off,
            onClick = onAlerts,
            modifier = Modifier.weight(1f),
        )
        FeatureTile(
            title = "Roaming",
            subtitle = when {
                roamActive -> if (roamKeep == 1) "1 app online" else "$roamKeep apps online"
                roaming -> "Roaming detected"
                roamAuto -> "Auto is on"
                else -> "Travel mode"
            },
            icon = ICON_ROAM,
            state = when {
                roamActive || roaming -> TileState.Alert
                roamAuto -> TileState.On
                else -> TileState.Off
            },
            onClick = onRoaming,
            modifier = Modifier.weight(1f),
        )
    }
    }
}

internal enum class TileState { Off, On, Alert }

@Composable
private fun FeatureTile(
    title: String,
    subtitle: String,
    icon: String,
    state: TileState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        when (state) {
            TileState.Off -> cs.surfaceContainerHigh
            TileState.On -> cs.secondaryContainer
            TileState.Alert -> cs.tertiaryContainer
        },
        tween(300),
        label = "tileBg",
    )
    val fg by animateColorAsState(
        when (state) {
            TileState.Off -> cs.onSurface
            TileState.On -> cs.onSecondaryContainer
            TileState.Alert -> cs.onTertiaryContainer
        },
        tween(300),
        label = "tileFg",
    )
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            IconBadge(
                path = icon,
                container = cs.surface.copy(alpha = 0.55f),
                content = fg,
                size = 36.dp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AnimatedContent(targetState = subtitle, transitionSpec = { FadeSlide }, label = "tileSub") { s ->
                Text(
                    s,
                    style = MaterialTheme.typography.bodySmall,
                    color = fg.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
