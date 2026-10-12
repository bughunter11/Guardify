@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bughunter11.guardify.ui

import android.text.format.Formatter
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bughunter11.guardify.data.AppInfo
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------------------------
// Focus mode: category chuno, timer lagao, baaki Guardify sambhalta hai.
// Timer khatam hote hi apps apne aap wapas allow ho jate hain (app band ho tab bhi).
// ---------------------------------------------------------------------------------------------

/** Chalte hue focus ka live countdown. Time pura hote hi onExpired ek baar chalta hai. */
@Composable
internal fun FocusCard(
    untilMs: Long,
    onEnd: () -> Unit,
    onExpired: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    var now by remember(untilMs) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(untilMs) {
        while (System.currentTimeMillis() < untilMs) {
            now = System.currentTimeMillis()
            delay(1000)
        }
        onExpired()
    }
    val left = (untilMs - now).coerceAtLeast(0L)
    val h = left / 3_600_000L
    val m = (left / 60_000L) % 60L
    val s = (left / 1_000L) % 60L
    val clock = if (h > 0L) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)

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
            Icon(Icons.Default.Lock, contentDescription = null)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Focus mode", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Apps unblock by themselves when the timer ends.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                clock,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onEnd) { Text("End") }
        }
    }
}

/** Focus shuru karne se pehle ka dialog: bas time chuno. */
@Composable
internal fun FocusDialog(
    count: Int,
    onDismiss: () -> Unit,
    onStart: (Int) -> Unit,
) {
    var minutes by remember { mutableIntStateOf(30) }
    val options = remember {
        listOf(15 to "15 min", 30 to "30 min", 60 to "1 hour", 120 to "2 hours", 240 to "4 hours")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text(if (count == 1) "Focus on 1 app?" else "Focus on $count apps?") },
        text = {
            Column {
                Text("They are blocked on WiFi and mobile data, then allowed again when the timer ends.")
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    options.forEach { (min, label) ->
                        FilterChip(
                            selected = minutes == min,
                            onClick = { minutes = min },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onStart(minutes) }) { Text("Start focus") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------------------------
// Smart suggestion: jo app sabse zyada mobile data kha rahi hai, uspar ek tap me block ka sujhav.
// ---------------------------------------------------------------------------------------------

internal class Suggestion(val app: AppInfo, val mobileBytes: Long)

@Composable
internal fun SuggestionCard(
    suggestion: Suggestion,
    periodLabel: String,
    onBlock: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = cs.tertiaryContainer,
            contentColor = cs.onTertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                key(suggestion.app.pkg) { AppIcon(suggestion.app.pkg, 40.dp) }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${suggestion.app.label} used ${Formatter.formatShortFileSize(ctx, suggestion.mobileBytes)} of mobile data",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "in the last $periodLabel. Block it on mobile data?",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Not now") }
                FilledTonalButton(onClick = onBlock) { Text("Block on mobile") }
            }
        }
    }
}

