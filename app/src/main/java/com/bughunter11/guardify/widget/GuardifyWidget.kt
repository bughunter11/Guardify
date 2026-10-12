package com.bughunter11.guardify.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bughunter11.guardify.MainActivity
import com.bughunter11.guardify.R
import com.bughunter11.guardify.data.QuickActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

private val Compact = DpSize(110.dp, 40.dp)
private val Full = DpSize(180.dp, 110.dp)

/**
 * Home screen widget (Jetpack Glance + Material 3). Do bade button: sab mobile data band, Focus.
 * Rang wallpaper se aate hain (Material You), haal firewall / kill / focus ke saath badalta rehta hai.
 * Chhota (2x1) kheencho to sirf do button, bada (3x2) me header aur status bhi.
 */
class GuardifyWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(Compact, Full))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = withContext(Dispatchers.IO) { QuickActions.state(context) }
        provideContent {
            GlanceTheme {
                WidgetContent(context, state)
            }
        }
    }
}

class GuardifyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GuardifyWidget()
}

/** Widget ka "sab mobile data band" button. */
class KillWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withContext(Dispatchers.IO) {
            if (QuickActions.toggleKill(context)) QuickActions.awaitFirewall()
        }
        GuardifyWidget().update(context, glanceId) // turant naya haal dikhao
    }
}

/** Widget ka Focus button. */
class FocusWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withContext(Dispatchers.IO) {
            if (QuickActions.toggleFocus(context)) QuickActions.awaitFirewall()
        }
        GuardifyWidget().update(context, glanceId) // turant naya haal dikhao
    }
}

/** VPN permission baaki ho to button app kholta hai, wahan permission ke baad kaam ho jata hai. */
private fun openApp(ctx: Context, which: String): Action = actionStartActivity(
    Intent(ctx, MainActivity::class.java)
        .setAction("guardify.quick.$which") // dono button ke PendingIntent alag rahein
        .setData(Uri.parse("guardify://quick/$which"))
        .putExtra(QuickActions.EXTRA, which),
)

@Composable
private fun WidgetContent(ctx: Context, s: QuickActions.State) {
    val c = GlanceTheme.colors
    val compact = LocalSize.current.height < 96.dp

    val killAction: Action =
        if (s.vpnReady) actionRunCallback<KillWidgetAction>() else openApp(ctx, QuickActions.KILL)
    val focusAction: Action =
        if (s.vpnReady) actionRunCallback<FocusWidgetAction>() else openApp(ctx, QuickActions.FOCUS)

    val status = when {
        !s.vpnReady -> "Tap a button to set up"
        s.killPending -> "Starting..."
        !s.firewallOn -> "Firewall OFF"
        s.killActive -> "Mobile data blocked"
        s.focusActive -> "Focus ON"
        else -> "Firewall ON"
    }
    val focusSub = if (s.focusActive) {
        "ON till " + DateFormat.getTimeFormat(ctx).format(Date(s.focusUntil))
    } else {
        "OFF, ${s.focusMinutes} min"
    }
    val killSub = when {
        s.killActive -> "ON, tap to allow"
        s.killPending -> "Starting..."
        else -> "OFF, all apps"
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(c.widgetBackground)
            .cornerRadius(28.dp)
            .padding(if (compact) 8.dp else 12.dp),
    ) {
        if (!compact) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_stat_guardify),
                    contentDescription = null,
                    modifier = GlanceModifier.size(18.dp),
                    colorFilter = ColorFilter.tint(c.primary),
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    "Guardify",
                    style = TextStyle(color = c.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    status,
                    style = TextStyle(
                        color = if (s.firewallOn) c.primary else c.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }
            Spacer(modifier = GlanceModifier.height(8.dp))
        }

        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            ActionTile(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                iconRes = R.drawable.ic_tile_mobile,
                title = "Block mobile",
                sub = killSub,
                active = s.killActive || s.killPending,
                container = if (s.killActive || s.killPending) c.error else c.surfaceVariant,
                content = if (s.killActive || s.killPending) c.onError else c.onSurfaceVariant,
                action = killAction,
                compact = compact,
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            ActionTile(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                iconRes = R.drawable.ic_tile_focus,
                title = "Focus",
                sub = focusSub,
                active = s.focusActive,
                container = if (s.focusActive) c.primary else c.surfaceVariant,
                content = if (s.focusActive) c.onPrimary else c.onSurfaceVariant,
                action = focusAction,
                compact = compact,
            )
        }
    }
}

@Composable
private fun ActionTile(
    modifier: GlanceModifier,
    iconRes: Int,
    title: String,
    sub: String,
    active: Boolean,
    container: ColorProvider,
    content: ColorProvider,
    action: Action,
    compact: Boolean,
) {
    val base = modifier
        .background(container)
        .cornerRadius(20.dp)
        .clickable(action)

    if (compact) {
        // Chhota widget: ek line me icon + "Block ON / OFF", taaki haal hamesha dikhe
        Row(
            modifier = base.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
                colorFilter = ColorFilter.tint(content),
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                "${title.substringBefore(' ')} ${if (active) "ON" else "OFF"}",
                style = TextStyle(color = content, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
    } else {
        Column(
            modifier = base.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                modifier = GlanceModifier.size(26.dp),
                colorFilter = ColorFilter.tint(content),
            )
            Text(
                title,
                style = TextStyle(color = content, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Text(
                sub,
                style = TextStyle(
                    color = content,
                    fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                ),
                maxLines = 1,
            )
        }
    }
}
