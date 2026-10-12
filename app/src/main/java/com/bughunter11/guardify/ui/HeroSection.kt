@file:OptIn(ExperimentalMaterial3Api::class)

package com.bughunter11.guardify.ui

import android.text.format.Formatter
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bughunter11.guardify.data.NetType

/** Text badalte waqt halka fade + neeche se slide. Ek hi object, baar baar nahi banta. */
internal val FadeSlide: ContentTransform =
    (fadeIn(tween(220, delayMillis = 80)) + slideInVertically(tween(300)) { it / 4 }) togetherWith
        fadeOut(tween(90))

// ---------------------------------------------------------------------------------------------
// Hero card: firewall ka status ek nazar me. On/off par poora card rang badalta hai.
// ---------------------------------------------------------------------------------------------

@Composable
internal fun PowerHero(
    running: Boolean,
    net: NetType,
    blockedWifi: Int,
    blockedMobile: Int,
    totalWifi: Long,
    totalMobile: Long,
    showUsage: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val heroBg by animateColorAsState(
        if (running) cs.primaryContainer else cs.surfaceContainerHigh,
        tween(400),
        label = "heroBg",
    )
    val heroFg by animateColorAsState(
        if (running) cs.onPrimaryContainer else cs.onSurface,
        tween(400),
        label = "heroFg",
    )
    val wifiShown by animateIntAsState(blockedWifi, label = "wifiCount")
    val mobileShown by animateIntAsState(blockedMobile, label = "mobileCount")
    val total = totalWifi + totalMobile

    val subtitle = when {
        !running && blockedWifi + blockedMobile > 0 -> "Your rules are saved. Tap the shield to enforce them"
        !running -> "Tap the shield to turn it on"
        blockedWifi + blockedMobile == 0 -> "On, but no apps are blocked yet"
        else -> "Blocked apps can't reach the internet"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = heroBg, contentColor = heroFg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .animateContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PowerButton(running = running, onToggle = onToggle)
            Spacer(Modifier.height(8.dp))
            AnimatedContent(
                targetState = running,
                transitionSpec = { FadeSlide },
                label = "heroTitle",
            ) { on ->
                Text(
                    if (on) "Protected" else "Firewall is off",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(2.dp))
            AnimatedContent(
                targetState = subtitle,
                transitionSpec = { FadeSlide },
                label = "heroSubtitle",
            ) { s ->
                Text(
                    s,
                    style = MaterialTheme.typography.bodyMedium,
                    color = heroFg.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(12.dp))
            NetPill(net)

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard("WiFi blocked", wifiShown.toString(), Modifier.weight(1f))
                StatCard("Mobile blocked", mobileShown.toString(), Modifier.weight(1f))
                if (showUsage) {
                    StatCard(
                        "Data used",
                        Formatter.formatShortFileSize(ctx, total),
                        Modifier.weight(1f),
                    )
                }
            }

            if (showUsage && total > 0L) {
                Spacer(Modifier.height(16.dp))
                SplitBar(
                    wifi = totalWifi.toFloat() / total,
                    mobile = totalMobile.toFloat() / total,
                    height = 8.dp,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Legend(cs.primary, "WiFi ${Formatter.formatShortFileSize(ctx, totalWifi)}")
                    Legend(cs.tertiary, "Mobile ${Formatter.formatShortFileSize(ctx, totalMobile)}")
                }
            }
        }
    }
}

/**
 * Bada power button. Press par spring se dabta hai, on hone par squircle se gol ho jata hai
 * aur do pulse ring nikalte hain. Saari animation draw/layer phase me padhi jati hai.
 */
@Composable
private fun PowerButton(running: Boolean, onToggle: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "powerPress",
    )
    val corner by animateIntAsState(if (running) 50 else 32, tween(450), label = "powerCorner")
    val bg by animateColorAsState(
        if (running) cs.primary else cs.surfaceContainerHighest,
        tween(400),
        label = "powerBg",
    )
    val outer by animateColorAsState(
        if (running) cs.onPrimary else cs.onSurfaceVariant,
        tween(400),
        label = "powerOuter",
    )
    val key by animateColorAsState(
        if (running) cs.tertiaryContainer else cs.outline,
        tween(400),
        label = "powerKey",
    )
    val shape = RoundedCornerShape(percent = corner)

    Box(modifier = Modifier.size(184.dp), contentAlignment = Alignment.Center) {
        if (running) {
            PulseRing(color = cs.primary, size = 136.dp, offsetMillis = 0)
            PulseRing(color = cs.primary, size = 136.dp, offsetMillis = 1000)
        }
        Box(
            modifier = Modifier
                .size(136.dp)
                .graphicsLayer {
                    scaleX = press
                    scaleY = press
                }
                .clip(shape)
                .background(bg)
                .semantics { contentDescription = "Firewall" }
                .toggleable(
                    value = running,
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    role = Role.Switch,
                    onValueChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle(it)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            GuardifyLogo(
                modifier = Modifier.height(72.dp),
                outer = outer,
                inner = bg,
                keyhole = key,
            )
        }
    }
}

/**
 * Halka pulse sirf firewall on ho tab. `progress` graphicsLayer ke andar padha jata hai,
 * to har frame par recomposition nahi hota. FastForward offset se do ring alag-alag phase me chalte hain.
 */
@Composable
private fun PulseRing(color: Color, size: Dp, offsetMillis: Int) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(offsetMillis, StartOffsetType.FastForward),
        ),
        label = "ring",
    )
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                val p = progress
                val s = 1f + 0.35f * p
                scaleX = s
                scaleY = s
                alpha = (1f - p) * 0.35f
            }
            .background(color, CircleShape),
    )
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Live network: WiFi, mobile data ya offline. Badalte hi icon, rang aur text smoothly badalte hain. */
@Composable
internal fun NetPill(net: NetType, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val tint by animateColorAsState(
        when (net) {
            NetType.WIFI -> cs.primary
            NetType.MOBILE -> cs.tertiary
            NetType.OFFLINE -> cs.error
        },
        tween(300),
        label = "netTint",
    )
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = cs.surface.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NetGlyph(
                wifi = net != NetType.MOBILE,
                slash = net == NetType.OFFLINE,
                modifier = Modifier.size(16.dp),
                tint = tint,
            )
            Spacer(Modifier.width(8.dp))
            AnimatedContent(
                targetState = net,
                transitionSpec = { FadeSlide },
                label = "netText",
            ) { n ->
                Text(
                    when (n) {
                        NetType.WIFI -> "Connected via WiFi"
                        NetType.MOBILE -> "Connected via mobile data"
                        NetType.OFFLINE -> "Offline"
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * Do rang ki bar: WiFi (primary) aur mobile (tertiary). `wifi` / `mobile` poori chaudai ka
 * hissa (0..1) hain. Width animate hoti hai par sirf draw phase me, layout dobara nahi chalta.
 */
@Composable
internal fun SplitBar(
    wifi: Float,
    mobile: Float,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val w by animateFloatAsState(wifi.coerceIn(0f, 1f), tween(500, easing = FastOutSlowInEasing), label = "barWifi")
    val m by animateFloatAsState(mobile.coerceIn(0f, 1f), tween(500, easing = FastOutSlowInEasing), label = "barMobile")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .drawBehind {
                drawRect(cs.onSurface.copy(alpha = 0.08f))
                val wifiW = size.width * w
                if (wifiW > 0f) {
                    drawRect(cs.primary, size = Size(wifiW, size.height))
                }
                val mobileW = (size.width * m).coerceAtMost(size.width - wifiW)
                if (mobileW > 0f) {
                    drawRect(
                        cs.tertiary,
                        topLeft = Offset(wifiW, 0f),
                        size = Size(mobileW, size.height),
                    )
                }
            },
    )
}
