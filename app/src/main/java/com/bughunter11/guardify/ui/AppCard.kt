@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.bughunter11.guardify.ui

import android.text.format.Formatter
import android.util.LruCache
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.bughunter11.guardify.data.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------------------------
// App card. Tap = details khulte hain, long-press = system app info.
// Do chhote toggle se seedha WiFi / mobile block. Block hote hi card halka laal ho jata hai.
// ---------------------------------------------------------------------------------------------

@Composable
internal fun AppRow(
    app: AppInfo,
    wifiBlocked: Boolean,
    mobileBlocked: Boolean,
    wifiBytes: Long,
    mobileBytes: Long,
    maxBytes: Long,
    showUsage: Boolean,
    onRule: (AppInfo, Boolean, Boolean, Boolean) -> Unit,
    onInfo: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
    limitBytes: Long = 0L,
    cycleBytes: Long = 0L,
    limitReached: Boolean = false,
    bgWifi: Boolean = false,
    bgMobile: Boolean = false,
    onLimit: (AppInfo) -> Unit = {},
    onBg: (AppInfo, Boolean, Boolean) -> Unit = { _, _, _ -> },
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var expanded by rememberSaveable(app.pkg) { mutableStateOf(false) }
    val anyBlocked = wifiBlocked || mobileBlocked || limitReached

    val tint by animateFloatAsState(if (anyBlocked) 0.55f else 0f, tween(300), label = "rowTint")
    val container = lerp(cs.surfaceContainerLow, cs.errorContainer, tint)

    val status = when {
        wifiBlocked && mobileBlocked -> "Blocked on WiFi and mobile"
        wifiBlocked && limitReached -> "WiFi blocked · Mobile limit reached"
        wifiBlocked -> "WiFi blocked"
        mobileBlocked -> "Mobile data blocked"
        limitReached -> "Mobile limit reached"
        else -> null
    }
    val bgNote = when {
        bgWifi && bgMobile -> "Blocked in background"
        bgWifi -> "WiFi blocked in background"
        bgMobile -> "Mobile blocked in background"
        else -> null
    }
    val usedText = remember(wifiBytes, mobileBytes, showUsage, limitBytes) {
        val base = if (showUsage) "${Formatter.formatShortFileSize(ctx, wifiBytes + mobileBytes)} used" else "Tap for details"
        if (limitBytes > 0L) "$base · ${Formatter.formatShortFileSize(ctx, limitBytes)}/mo limit" else base
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = cs.onSurface),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { expanded = !expanded },
                        onClickLabel = if (expanded) "Hide details" else "Show details",
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onInfo(app)
                        },
                        onLongClickLabel = "App info",
                    )
                    .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(app.pkg)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        usedText,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                    )
                    AnimatedContent(
                        targetState = status,
                        transitionSpec = { FadeSlide },
                        label = "rowStatus",
                    ) { s ->
                        if (s != null) {
                            Text(
                                s,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.error,
                                maxLines = 1,
                            )
                        }
                    }
                    if (bgNote != null) {
                        Text(
                            bgNote,
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.tertiary,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                NetToggle(wifi = true, blocked = wifiBlocked, appLabel = app.label) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRule(app, true, false, it)
                }
                Spacer(Modifier.width(6.dp))
                NetToggle(wifi = false, blocked = mobileBlocked, appLabel = app.label) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRule(app, false, true, it)
                }
            }

            if (showUsage && maxBytes > 0L) {
                val max = maxBytes.toFloat()
                SplitBar(
                    wifi = wifiBytes / max,
                    mobile = mobileBytes / max,
                    height = 4.dp,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp)) {
                    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f))
                    BlockRow("WiFi", wifiBytes, showUsage, wifiBlocked) { onRule(app, true, false, it) }
                    BlockRow("Mobile data", mobileBytes, showUsage, mobileBlocked) { onRule(app, false, true, it) }
                    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f))
                    LimitInline(
                        limitBytes = limitBytes,
                        usedBytes = cycleBytes,
                        reached = limitReached,
                        showUsage = showUsage,
                        onClick = { onLimit(app) },
                    )
                    SwitchLine(
                        title = "WiFi only while app is open",
                        subtitle = if (bgWifi) "Blocked when the app is closed" else "Off",
                        checked = bgWifi,
                    ) { onBg(app, true, it) }
                    SwitchLine(
                        title = "Mobile data only while app is open",
                        subtitle = if (bgMobile) "Blocked when the app is closed" else "Off",
                        checked = bgMobile,
                    ) { onBg(app, false, it) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            app.pkg,
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onInfo(app) }) { Text("App info") }
                    }
                }
            }
        }
    }
}

/** Expanded card ki ek line: network ka naam, usage aur M3 switch (tick = allowed, cross = blocked). */
@Composable
private fun BlockRow(
    label: String,
    bytes: Long,
    showUsage: Boolean,
    blocked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .toggleable(value = blocked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                when {
                    blocked -> "Blocked"
                    showUsage -> "${Formatter.formatShortFileSize(ctx, bytes)} used"
                    else -> "Allowed"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (blocked) cs.error else cs.onSurfaceVariant,
            )
        }
        Switch(
            checked = blocked,
            onCheckedChange = null,
            thumbContent = {
                Icon(
                    if (blocked) Icons.Default.Close else Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = cs.onError,
                checkedTrackColor = cs.error,
                checkedIconColor = cs.error,
            ),
        )
    }
}

/** Expanded card me monthly limit ki line: progress + Set / Edit. */
@Composable
private fun LimitInline(
    limitBytes: Long,
    usedBytes: Long,
    reached: Boolean,
    showUsage: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val has = limitBytes > 0L
    val frac by animateFloatAsState(
        if (has) (usedBytes.toFloat() / limitBytes).coerceIn(0f, 1f) else 0f,
        tween(500),
        label = "limitInline",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Monthly mobile limit", style = MaterialTheme.typography.bodyLarge)
            Text(
                when {
                    !has -> "No limit set"
                    reached -> "Reached · blocked on mobile data"
                    showUsage -> "${Formatter.formatShortFileSize(ctx, usedBytes)} of ${Formatter.formatShortFileSize(ctx, limitBytes)} this cycle"
                    else -> "${Formatter.formatShortFileSize(ctx, limitBytes)} per month"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (reached) cs.error else cs.onSurfaceVariant,
            )
            if (has && showUsage) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { frac },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = if (reached) cs.error else cs.primary,
                    trackColor = cs.surfaceContainerHighest,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onClick) { Text(if (has) "Edit" else "Set") }
    }
}

/** Simple M3 switch line (background block jaise options ke liye). */
@Composable
private fun SwitchLine(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (checked) cs.tertiary else cs.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

// ---------------------------------------------------------------------------------------------
// WiFi / signal glyph (extended icons library ke bina)
// ---------------------------------------------------------------------------------------------

private const val WIFI_PATH =
    "M1,9l2,2c4.97,-4.97 13.03,-4.97 18,0l2,-2C16.93,2.93 7.08,2.93 1,9zM9,17l3,3 3,-3c-1.65,-1.66 -4.34,-1.66 -6,0zM5,13l2,2c2.76,-2.76 7.24,-2.76 10,0l2,-2C15.14,9.14 8.87,9.14 5,13z"
private const val CELL_PATH = "M2,22h20V2z"

/** `slash` true hote hi cut-line animate hoke khinchti hai. */
@Composable
internal fun NetGlyph(
    wifi: Boolean,
    slash: Boolean,
    modifier: Modifier = Modifier.size(22.dp),
    tint: Color = LocalContentColor.current,
) {
    val path = remember(wifi) {
        PathParser().parsePathString(if (wifi) WIFI_PATH else CELL_PATH).toPath()
    }
    val cut by animateFloatAsState(if (slash) 1f else 0f, tween(250), label = "glyphSlash")
    Canvas(modifier) {
        val s = size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) { drawPath(path, tint) }
        val progress = cut
        if (progress > 0f) {
            val start = Offset(size.width * 0.10f, size.height * 0.10f)
            val end = Offset(size.width * 0.92f, size.height * 0.92f)
            drawLine(
                color = tint,
                start = start,
                end = Offset(
                    start.x + (end.x - start.x) * progress,
                    start.y + (end.y - start.y) * progress,
                ),
                strokeWidth = 2.6.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Ek tap me WiFi ya mobile block. Blocked = laal squircle + slash, allowed = tonal gol. */
@Composable
internal fun NetToggle(
    wifi: Boolean,
    blocked: Boolean,
    appLabel: String,
    what: String = "block",
    onChange: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (blocked) cs.error else cs.surfaceContainerHighest,
        tween(250),
        label = "toggleBg",
    )
    val fg by animateColorAsState(
        if (blocked) cs.onError else cs.onSurfaceVariant,
        tween(250),
        label = "toggleFg",
    )
    val corner by animateIntAsState(if (blocked) 30 else 50, tween(250), label = "toggleCorner")
    val name = if (wifi) "WiFi" else "Mobile data"

    FilledIconToggleButton(
        checked = blocked,
        onCheckedChange = onChange,
        modifier = Modifier
            .size(44.dp)
            .semantics { contentDescription = "$name $what for $appLabel" },
        shape = RoundedCornerShape(percent = corner),
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = bg,
            contentColor = fg,
            checkedContainerColor = bg,
            checkedContentColor = fg,
        ),
    ) {
        NetGlyph(wifi = wifi, slash = blocked)
    }
}

// ---------------------------------------------------------------------------------------------
// App icon: ek baar load, phir memory se. Late load hone par halka fade-in.
// ---------------------------------------------------------------------------------------------

private object IconCache {
    private val cache = LruCache<String, ImageBitmap>(150)
    fun get(pkg: String): ImageBitmap? = cache.get(pkg)
    fun put(pkg: String, bmp: ImageBitmap) {
        cache.put(pkg, bmp)
    }
}

@Composable
internal fun AppIcon(pkg: String, size: Dp = 48.dp) {
    val ctx = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val icon by produceState<ImageBitmap?>(initialValue = IconCache.get(pkg), pkg) {
        // pkg badla to purani app ka icon hatao (produceState purani value rakhta hai)
        value = IconCache.get(pkg)
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                ctx.packageManager.getApplicationIcon(pkg).toBitmap(px, px).asImageBitmap()
            }.getOrNull()?.also { IconCache.put(pkg, it) }
        }
    }
    val bmp = icon
    val fade by animateFloatAsState(if (bmp != null) 1f else 0f, tween(200), label = "iconFade")
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = fade },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Loading skeleton: list aane tak khali spinner nahi, card jaise placeholder halka dhadakte hain.
// ---------------------------------------------------------------------------------------------

@Composable
internal fun SkeletonList(count: Int = 6) {
    val cs = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonPulse",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = pulse },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(count) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(cs.surfaceContainerLow)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(cs.surfaceContainerHighest),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.55f)
                            .height(14.dp)
                            .clip(CircleShape)
                            .background(cs.surfaceContainerHighest),
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.35f)
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(cs.surfaceContainerHighest),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(44.dp).clip(CircleShape).background(cs.surfaceContainerHighest))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(44.dp).clip(CircleShape).background(cs.surfaceContainerHighest))
            }
        }
    }
}
