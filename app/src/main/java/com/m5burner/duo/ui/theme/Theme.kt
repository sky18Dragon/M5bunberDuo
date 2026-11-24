package com.m5burner.duo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class DuoPalette(
    val isDark: Boolean,
    val canvas: Color,
    val surface: Color,
    val surfaceSoft: Color,
    val surfaceElevated: Color,
    val hairline: Color,
    val topBevel: Color,
    val neutralLip: Color,

    val retroAmber: Color,
    val retroAmberDeep: Color,
    val m5Blue: Color,
    val m5BlueDeep: Color,
    val cyanGlow: Color,
    val emerald: Color,
    val emeraldDeep: Color,
    val coral: Color,
    val coralDeep: Color,

    val btnA: Color,
    val btnB: Color,
    val btnX: Color,
    val btnY: Color,

    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val onAmber: Color,
    val onEmerald: Color,
    val keycapBg: Color,
    val keycapText: Color,

    // CRT Terminal stays classic dark phosphor even in Retro Beige daylight mode!
    val terminalBg: Color,
    val terminalText: Color,
    val terminalMuted: Color
)

/**
 * Default Theme: Authentic Super Famicom / Game Boy DMG / NDS Lite Warm Retro Beige.
 * Harmonizes directly with the Retro-Beige Retroid Pocket Duo shell and SFC colored ABXY buttons.
 */
val RetroBeigePalette = DuoPalette(
    isDark = false,
    canvas = Color(0xFFE9E3D5),          // Classic SFC / DMG Warm Retro Beige
    surface = Color(0xFFF6F2E9),         // Crisp Ivory Cartridge / Panel Face
    surfaceSoft = Color(0xFFDDD6C6),     // Recessed Molded Dove-Gray Well
    surfaceElevated = Color(0xFFFFFDF8), // Raised Plastic Keycap
    hairline = Color(0xFFC5BDA9),        // Subtle Molded Seam
    topBevel = Color(0xD9FFFFFF),        // Clean Plastic Specular Top Edge
    neutralLip = Color(0xFFBEB5A1),      // 3D Plastic Keycap Bottom Shadow Lip

    retroAmber = Color(0xFFE08E0B),      // Rich Retro Ochre Gold (high contrast on ivory)
    retroAmberDeep = Color(0xFFA66400),
    m5Blue = Color(0xFF2C55DC),          // Classic SFC Cobalt Blue
    m5BlueDeep = Color(0xFF1B379B),
    cyanGlow = Color(0xFF0E7FA8),        // Deep Retro Teal
    emerald = Color(0xFF1B9A52),         // Classic SFC / DMG Emerald
    emeraldDeep = Color(0xFF106836),
    coral = Color(0xFFD93B35),           // Famicom Vermilion Red
    coralDeep = Color(0xFF96221E),

    btnA = Color(0xFFE53935),
    btnB = Color(0xFFF4B400),
    btnX = Color(0xFF2962FF),
    btnY = Color(0xFF1E9E56),

    textPrimary = Color(0xFF211E28),     // Deep Charcoal Ink
    textSecondary = Color(0xFF5A5464),   // Warm Slate Ink
    textMuted = Color(0xFF837C8E),       // Molded Stamp Gray
    onAmber = Color(0xFFFFFFFF),
    onEmerald = Color(0xFFFFFFFF),
    keycapBg = Color(0xFF2B2734),        // Crisp dark retro keycap badge
    keycapText = Color(0xFFF8F4EC),

    terminalBg = Color(0xFF141B16),      // Recessed Dark Phosphor CRT Display
    terminalText = Color(0xFF6EF2A0),    // Phosphor Mint Text
    terminalMuted = Color(0xFF6A8573)
)

/**
 * Optional Night Theme: Warm CRT Obsidian for dark-room handheld sessions.
 */
val RetroNightPalette = DuoPalette(
    isDark = true,
    canvas = Color(0xFF0E0D12),
    surface = Color(0xFF17151F),
    surfaceSoft = Color(0xFF211E2C),
    surfaceElevated = Color(0xFF2B2739),
    hairline = Color(0xFF342F44),
    topBevel = Color(0x1AFFFFFF),
    neutralLip = Color(0xFF0A090E),

    retroAmber = Color(0xFFF5B841),
    retroAmberDeep = Color(0xFF8C6214),
    m5Blue = Color(0xFF476BFF),
    m5BlueDeep = Color(0xFF2841B5),
    cyanGlow = Color(0xFF6CD4FF),
    emerald = Color(0xFF4ADE80),
    emeraldDeep = Color(0xFF1D6E3B),
    coral = Color(0xFFEF5350),
    coralDeep = Color(0xFF9E2624),

    btnA = Color(0xFFEA4335),
    btnB = Color(0xFFFABB05),
    btnX = Color(0xFF4285F4),
    btnY = Color(0xFF34A853),

    textPrimary = Color(0xFFF4EFE6),
    textSecondary = Color(0xFFA8A1B4),
    textMuted = Color(0xFF6F687D),
    onAmber = Color(0xFF1A1408),
    onEmerald = Color(0xFF071A0E),
    keycapBg = Color(0x70000000),
    keycapText = Color(0xFFF4EFE6),

    terminalBg = Color(0xFF0A0B0E),
    terminalText = Color(0xFF7BF1A8),
    terminalMuted = Color(0xFF6F687D)
)

val LocalDuoPalette = staticCompositionLocalOf { RetroBeigePalette }

object DuoColors {
    val palette: DuoPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalDuoPalette.current

    val Canvas: Color @Composable @ReadOnlyComposable get() = palette.canvas
    val Surface: Color @Composable @ReadOnlyComposable get() = palette.surface
    val SurfaceSoft: Color @Composable @ReadOnlyComposable get() = palette.surfaceSoft
    val SurfaceElevated: Color @Composable @ReadOnlyComposable get() = palette.surfaceElevated
    val Hairline: Color @Composable @ReadOnlyComposable get() = palette.hairline
    val TopBevel: Color @Composable @ReadOnlyComposable get() = palette.topBevel
    val NeutralLip: Color @Composable @ReadOnlyComposable get() = palette.neutralLip

    val RetroAmber: Color @Composable @ReadOnlyComposable get() = palette.retroAmber
    val RetroAmberDeep: Color @Composable @ReadOnlyComposable get() = palette.retroAmberDeep
    val M5Blue: Color @Composable @ReadOnlyComposable get() = palette.m5Blue
    val M5BlueDeep: Color @Composable @ReadOnlyComposable get() = palette.m5BlueDeep
    val CyanGlow: Color @Composable @ReadOnlyComposable get() = palette.cyanGlow
    val Emerald: Color @Composable @ReadOnlyComposable get() = palette.emerald
    val EmeraldDeep: Color @Composable @ReadOnlyComposable get() = palette.emeraldDeep
    val Coral: Color @Composable @ReadOnlyComposable get() = palette.coral
    val CoralDeep: Color @Composable @ReadOnlyComposable get() = palette.coralDeep

    val BtnA: Color @Composable @ReadOnlyComposable get() = palette.btnA
    val BtnB: Color @Composable @ReadOnlyComposable get() = palette.btnB
    val BtnX: Color @Composable @ReadOnlyComposable get() = palette.btnX
    val BtnY: Color @Composable @ReadOnlyComposable get() = palette.btnY

    val TextPrimary: Color @Composable @ReadOnlyComposable get() = palette.textPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = palette.textSecondary
    val TextMuted: Color @Composable @ReadOnlyComposable get() = palette.textMuted
    val OnAmber: Color @Composable @ReadOnlyComposable get() = palette.onAmber
    val OnEmerald: Color @Composable @ReadOnlyComposable get() = palette.onEmerald
    val KeycapBg: Color @Composable @ReadOnlyComposable get() = palette.keycapBg
    val KeycapText: Color @Composable @ReadOnlyComposable get() = palette.keycapText

    val TerminalBg: Color @Composable @ReadOnlyComposable get() = palette.terminalBg
    val TerminalText: Color @Composable @ReadOnlyComposable get() = palette.terminalText
    val TerminalMuted: Color @Composable @ReadOnlyComposable get() = palette.terminalMuted
}

@Composable
fun M5BurnerDuoTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) RetroNightPalette else RetroBeigePalette
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.m5Blue,
            onPrimary = Color.White,
            secondary = palette.retroAmber,
            onSecondary = palette.onAmber,
            tertiary = palette.emerald,
            onTertiary = palette.onEmerald,
            background = palette.canvas,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceSoft,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.hairline,
            error = palette.coral,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = palette.m5Blue,
            onPrimary = Color.White,
            secondary = palette.retroAmber,
            onSecondary = palette.onAmber,
            tertiary = palette.emerald,
            onTertiary = palette.onEmerald,
            background = palette.canvas,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceSoft,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.hairline,
            error = palette.coral,
            onError = Color.White
        )
    }

    CompositionLocalProvider(LocalDuoPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
