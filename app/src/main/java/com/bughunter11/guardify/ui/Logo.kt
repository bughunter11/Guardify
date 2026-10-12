package com.bughunter11.guardify.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

// Launcher icon wale same paths (108x108 viewport). Shield x: 30..78, y: 24..85
private const val SHIELD_OUTER =
    "M54,24 L78,33 V54 C78,69 68,79 54,85 C40,79 30,69 30,54 V33 Z"
private const val SHIELD_INNER =
    "M54,28 L74.5,35.7 V54 C74.5,66.5 66.5,75.5 54,81 C41.5,75.5 33.5,66.5 33.5,54 V35.7 Z"
private const val KEYHOLE =
    "M54,43.5 A5.5,5.5 0 0 1 57,53.6 L59,64 H49 L51,53.6 A5.5,5.5 0 0 1 54,43.5 Z"

private const val LOGO_W = 48f   // 78 - 30
private const val LOGO_H = 61f   // 85 - 24

/**
 * Guardify logo, poori tarah Material You se. Default colors MaterialTheme.colorScheme se aate hain,
 * to wallpaper/accent badalte hi logo ka rang bhi badal jata hai.
 */
@Composable
fun GuardifyLogo(
    modifier: Modifier = Modifier,
    outer: Color = MaterialTheme.colorScheme.primaryContainer,
    inner: Color = MaterialTheme.colorScheme.primary,
    keyhole: Color = MaterialTheme.colorScheme.tertiaryContainer,
) {
    val paths = remember {
        listOf(SHIELD_OUTER, SHIELD_INNER, KEYHOLE).map { PathParser().parsePathString(it).toPath() }
    }
    Canvas(modifier = modifier.aspectRatio(LOGO_W / LOGO_H)) {
        val s = size.height / LOGO_H
        scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            translate(-30f, -24f) {
                drawPath(paths[0], outer)
                drawPath(paths[1], inner)
                drawPath(paths[2], keyhole)
            }
        }
    }
}
