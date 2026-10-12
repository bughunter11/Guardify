package com.bughunter11.guardify.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** accent = 0 matlab wallpaper se Material You (Android 12+). Baaki sab ARGB color hain. */
const val ACCENT_DYNAMIC = 0

val AccentPresets: List<Pair<String, Int>> = listOf(
    "Blue" to 0xFF2E6BE6.toInt(),
    "Teal" to 0xFF00897B.toInt(),
    "Green" to 0xFF43A047.toInt(),
    "Orange" to 0xFFF57C00.toInt(),
    "Red" to 0xFFE53935.toInt(),
    "Pink" to 0xFFD81B60.toInt(),
    "Purple" to 0xFF8E24AA.toInt(),
)

/** Thode bade, naram corners: cards, chips, dialogs sab me same Material 3 look. */
private val GuardifyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val DefaultSeed = AccentPresets[0].second

@Composable
fun GuardifyTheme(
    darkTheme: Boolean,
    accent: Int,
    amoled: Boolean,
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val config = LocalConfiguration.current
    // Resources se colors padhna mehenga hai, isliye sirf theme/accent/wallpaper badalne par dobara banta hai
    val scheme = remember(ctx, config, darkTheme, accent, amoled) {
        val base = if (accent == ACCENT_DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: wallpaper se Material You dynamic colors
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        } else {
            // Android 10/11 par dynamic nahi hota, to chune hue accent (ya blue) se palette banta hai
            seedScheme(if (accent == ACCENT_DYNAMIC) DefaultSeed else accent, darkTheme)
        }
        if (darkTheme && amoled) base.toAmoled() else base
    }
    MaterialTheme(colorScheme = scheme.animated(), shapes = GuardifyShapes, content = content)
}

private fun hsl(h: Float, s: Float, l: Float): Color =
    Color.hsl(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

/** Ek seed color se poora Material 3 scheme (HSL tones se). */
private fun seedScheme(seedArgb: Int, dark: Boolean): ColorScheme {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seedArgb, hsv)
    val h = hsv[0]
    val h3 = h + 60f
    return if (!dark) {
        lightColorScheme(
            primary = hsl(h, 0.62f, 0.38f),
            onPrimary = Color.White,
            primaryContainer = hsl(h, 0.90f, 0.90f),
            onPrimaryContainer = hsl(h, 0.90f, 0.10f),
            inversePrimary = hsl(h, 0.90f, 0.80f),
            secondary = hsl(h, 0.25f, 0.40f),
            onSecondary = Color.White,
            secondaryContainer = hsl(h, 0.40f, 0.90f),
            onSecondaryContainer = hsl(h, 0.40f, 0.12f),
            tertiary = hsl(h3, 0.45f, 0.38f),
            onTertiary = Color.White,
            tertiaryContainer = hsl(h3, 0.65f, 0.89f),
            onTertiaryContainer = hsl(h3, 0.65f, 0.11f),
            background = hsl(h, 0.25f, 0.98f),
            onBackground = hsl(h, 0.15f, 0.10f),
            surface = hsl(h, 0.25f, 0.98f),
            onSurface = hsl(h, 0.15f, 0.10f),
            surfaceVariant = hsl(h, 0.25f, 0.90f),
            onSurfaceVariant = hsl(h, 0.15f, 0.30f),
            inverseSurface = hsl(h, 0.15f, 0.20f),
            inverseOnSurface = hsl(h, 0.20f, 0.95f),
            surfaceDim = hsl(h, 0.20f, 0.87f),
            surfaceBright = hsl(h, 0.25f, 0.98f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = hsl(h, 0.25f, 0.96f),
            surfaceContainer = hsl(h, 0.25f, 0.94f),
            surfaceContainerHigh = hsl(h, 0.22f, 0.92f),
            surfaceContainerHighest = hsl(h, 0.20f, 0.90f),
            outline = hsl(h, 0.10f, 0.46f),
            outlineVariant = hsl(h, 0.15f, 0.80f),
        )
    } else {
        darkColorScheme(
            primary = hsl(h, 0.90f, 0.80f),
            onPrimary = hsl(h, 0.90f, 0.18f),
            primaryContainer = hsl(h, 0.70f, 0.28f),
            onPrimaryContainer = hsl(h, 0.90f, 0.90f),
            inversePrimary = hsl(h, 0.62f, 0.38f),
            secondary = hsl(h, 0.35f, 0.78f),
            onSecondary = hsl(h, 0.40f, 0.18f),
            secondaryContainer = hsl(h, 0.25f, 0.28f),
            onSecondaryContainer = hsl(h, 0.40f, 0.90f),
            tertiary = hsl(h3, 0.60f, 0.78f),
            onTertiary = hsl(h3, 0.60f, 0.18f),
            tertiaryContainer = hsl(h3, 0.45f, 0.28f),
            onTertiaryContainer = hsl(h3, 0.65f, 0.90f),
            background = hsl(h, 0.15f, 0.07f),
            onBackground = hsl(h, 0.15f, 0.90f),
            surface = hsl(h, 0.15f, 0.07f),
            onSurface = hsl(h, 0.15f, 0.90f),
            surfaceVariant = hsl(h, 0.15f, 0.26f),
            onSurfaceVariant = hsl(h, 0.15f, 0.78f),
            inverseSurface = hsl(h, 0.15f, 0.90f),
            inverseOnSurface = hsl(h, 0.15f, 0.20f),
            surfaceDim = hsl(h, 0.15f, 0.07f),
            surfaceBright = hsl(h, 0.13f, 0.24f),
            surfaceContainerLowest = hsl(h, 0.15f, 0.05f),
            surfaceContainerLow = hsl(h, 0.15f, 0.10f),
            surfaceContainer = hsl(h, 0.15f, 0.12f),
            surfaceContainerHigh = hsl(h, 0.14f, 0.16f),
            surfaceContainerHighest = hsl(h, 0.13f, 0.20f),
            outline = hsl(h, 0.10f, 0.60f),
            outlineVariant = hsl(h, 0.15f, 0.30f),
        )
    }
}

private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = lerp(Color.Black, surfaceContainerLow, 0.6f),
    surfaceContainer = lerp(Color.Black, surfaceContainer, 0.7f),
    surfaceContainerHigh = lerp(Color.Black, surfaceContainerHigh, 0.8f),
)

/** Accent / theme badalte hi saare colors smoothly crossfade hote hain. */
@Composable
private fun ColorScheme.animated(): ColorScheme {
    val spec = tween<Color>(durationMillis = 450)

    @Composable
    fun a(c: Color): Color = animateColorAsState(c, spec, label = "scheme").value

    return copy(
        primary = a(primary),
        onPrimary = a(onPrimary),
        primaryContainer = a(primaryContainer),
        onPrimaryContainer = a(onPrimaryContainer),
        inversePrimary = a(inversePrimary),
        secondary = a(secondary),
        onSecondary = a(onSecondary),
        secondaryContainer = a(secondaryContainer),
        onSecondaryContainer = a(onSecondaryContainer),
        tertiary = a(tertiary),
        onTertiary = a(onTertiary),
        tertiaryContainer = a(tertiaryContainer),
        onTertiaryContainer = a(onTertiaryContainer),
        background = a(background),
        onBackground = a(onBackground),
        surface = a(surface),
        onSurface = a(onSurface),
        surfaceVariant = a(surfaceVariant),
        onSurfaceVariant = a(onSurfaceVariant),
        surfaceTint = a(surfaceTint),
        inverseSurface = a(inverseSurface),
        inverseOnSurface = a(inverseOnSurface),
        error = a(error),
        onError = a(onError),
        errorContainer = a(errorContainer),
        onErrorContainer = a(onErrorContainer),
        outline = a(outline),
        outlineVariant = a(outlineVariant),
        surfaceDim = a(surfaceDim),
        surfaceBright = a(surfaceBright),
        surfaceContainerLowest = a(surfaceContainerLowest),
        surfaceContainerLow = a(surfaceContainerLow),
        surfaceContainer = a(surfaceContainer),
        surfaceContainerHigh = a(surfaceContainerHigh),
        surfaceContainerHighest = a(surfaceContainerHighest),
    )
}
