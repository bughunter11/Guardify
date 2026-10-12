@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bughunter11.guardify.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bughunter11.guardify.data.AppInfo

// Material icons ke path data. Extended icons library add kiye bina yahi use hote hain.
internal const val ICON_USAGE =
    "M13,2.05v3.03c3.39,0.49 6,3.39 6,6.92 0,0.9 -0.18,1.75 -0.5,2.54l2.6,1.53c0.56,-1.24 0.9,-2.62 0.9,-4.07 0,-5.18 -3.95,-9.45 -9,-9.95zM12,19c-3.87,0 -7,-3.13 -7,-7 0,-3.53 2.61,-6.43 6,-6.92V2.05c-5.06,0.5 -9,4.76 -9,9.95 0,5.52 4.47,10 9.99,10 3.31,0 6.24,-1.61 8.06,-4.09l-2.6,-1.53C16.17,17.98 14.21,19 12,19z"
internal const val ICON_SCHEDULE =
    "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8zM12.5,7H11v6l5.25,3.15 0.75,-1.23 -4.5,-2.67z"
internal const val ICON_LAYERS =
    "M11.99,18.54l-7.37,-5.73L3,14.07l9,7 9,-7 -1.63,-1.27 -7.38,5.74zM12,16l7.36,-5.73L21,9l-9,-7 -9,7 1.63,1.27L12,16z"

/** 24x24 viewport ka koi bhi Material path, tint ke saath. */
@Composable
internal fun PathIcon(
    path: String,
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = LocalContentColor.current,
) {
    val p = remember(path) { PathParser().parsePathString(path).toPath() }
    Canvas(modifier) {
        val s = size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) { drawPath(p, tint) }
    }
}

/** Gol tonal badge ke andar icon (sheet headers aur tiles me). */
@Composable
internal fun IconBadge(
    path: String,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    size: Dp = 40.dp,
) {
    Surface(shape = CircleShape, color = container, contentColor = content, modifier = Modifier.size(size)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            PathIcon(path, Modifier.size(size * 0.55f), content)
        }
    }
}

/**
 * Apps chunne ka dialog. single = true me tap karte hi kaam ho jata hai,
 * multi me checkbox lagte hain aur Done dabane par sab ek saath milte hain.
 */
@Composable
internal fun AppPickerDialog(
    title: String,
    apps: List<AppInfo>,
    initial: Set<String>,
    multi: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
    excluded: Set<String> = emptySet(),
) {
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(initial) }

    val sorted = remember(apps, excluded) {
        apps.filter { it.pkg !in excluded }.sortedBy { it.label.lowercase() }
    }
    val shown = remember(sorted, query, showSystem, picked) {
        val q = query.trim()
        sorted.filter {
            (showSystem || !it.isSystem || it.pkg in picked) &&
                (q.isEmpty() || it.label.contains(q, true) || it.pkg.contains(q, true))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                SearchField(value = query, onChange = { query = it })
                Spacer(Modifier.height(8.dp))
                FilterChip(
                    selected = showSystem,
                    onClick = { showSystem = !showSystem },
                    label = { Text("System apps") },
                )
                Spacer(Modifier.height(4.dp))
                if (shown.isEmpty()) {
                    Text(
                        "No apps match",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
                        items(shown, key = { it.pkg }) { app ->
                            val on = app.pkg in picked
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        if (multi) {
                                            picked = if (on) picked - app.pkg else picked + app.pkg
                                        } else {
                                            onConfirm(setOf(app.pkg))
                                        }
                                    }
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(app.pkg, 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    app.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (multi) Checkbox(checked = on, onCheckedChange = null)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (multi) {
                TextButton(onClick = { onConfirm(picked) }) {
                    Text(if (picked.isEmpty()) "Done" else "Done (${picked.size})")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Sheets ke andar ka section card. */
@Composable
internal fun SheetCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit,
) {
    val bg by animateColorAsState(container, tween(300), label = "sheetCard")
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
    ) { content() }
}
