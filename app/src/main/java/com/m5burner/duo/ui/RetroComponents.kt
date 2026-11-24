package com.m5burner.duo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m5burner.duo.flasher.FlashStage
import com.m5burner.duo.ui.theme.DuoColors

/**
 * Tactile 3D Retro Console Push-Button with physical bottom lip & subtle top bevel highlight.
 * Gives unmistakable physical "press me" affordance without harsh wireframe borders.
 */
@Composable
fun RetroPushButton(
    label: String,
    icon: ImageVector? = null,
    faceColor: Color,
    lipColor: Color,
    contentColor: Color = Color.White,
    keycapLetter: String? = null,
    keycapDotColor: Color = DuoColors.BtnA,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val activeFace = if (enabled) faceColor else DuoColors.SurfaceSoft
    val activeLip = if (enabled) lipColor else DuoColors.NeutralLip
    val activeContent = if (enabled) contentColor else DuoColors.TextMuted

    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(activeLip)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(bottom = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height - 3.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(activeFace)
                .border(1.dp, DuoColors.TopBevel, RoundedCornerShape(11.dp))
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = activeContent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                color = activeContent,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (keycapLetter != null) {
                Spacer(Modifier.width(8.dp))
                RetroGlassKeycap(letter = keycapLetter, dotColor = keycapDotColor)
            }
        }
    }
}

/**
 * Refined Translucent Glass Hardware Keycap ([● A]).
 * Eliminates harsh red-on-green color clashes while keeping unmistakable Retroid Pocket Duo button mapping.
 */
@Composable
fun RetroGlassKeycap(
    letter: String,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(DuoColors.KeycapBg)
            .border(0.8.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = letter,
            color = DuoColors.KeycapText,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

/**
 * Mini beveled hardware shoulder keycap pill (L1 / R1 / L2 / R2).
 */
@Composable
fun RetroShoulderCap(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(DuoColors.NeutralLip)
            .padding(bottom = 1.5.dp)
    ) {
        Text(
            text = label,
            color = DuoColors.TextSecondary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(DuoColors.SurfaceElevated)
                .padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

/**
 * Classic Dual-Screen Hinge LED Bridge: subtle retro segmented status bar across the Retroid Pocket Duo hinge.
 */
@Composable
fun RetroHingeBridge(state: M5BurnerUiState, isBottomScreenTopEdge: Boolean) {
    val ledColor by animateColorAsState(
        targetValue = when {
            state.flashProgress.stage == FlashStage.ERROR -> DuoColors.Coral
            state.isBusy -> DuoColors.RetroAmber
            state.isSerialMonitorRunning || state.selectedUsbDevice?.hasPermission == true -> DuoColors.Emerald
            else -> DuoColors.RetroAmber.copy(alpha = 0.45f)
        },
        label = "hingeLed"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(DuoColors.Canvas),
        contentAlignment = if (isBottomScreenTopEdge) Alignment.TopCenter else Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.36f)
                .height(2.5.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, ledColor, Color.Transparent)
                    )
                )
        )
    }
}

/**
 * Visual Flash Memory Map Signifier Bar:
 * Instantly communicates Full-Chip vs Data-Only backup without reading a paragraph of text!
 */
@Composable
fun FlashMemoryMapBar(
    isDataOnly: Boolean,
    modifier: Modifier = Modifier
) {
    val fwAlpha = if (isDataOnly) 0.25f else 1f
    val fwColor by animateColorAsState(
        if (isDataOnly) DuoColors.SurfaceElevated else DuoColors.M5Blue,
        label = "fwSeg"
    )
    val dataColor by animateColorAsState(
        if (isDataOnly) DuoColors.Emerald else DuoColors.RetroAmber,
        label = "dataSeg"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(DuoColors.SurfaceSoft)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MemoryBlockPill(
            label = "BOOT",
            color = fwColor,
            textColor = if (isDataOnly) DuoColors.TextMuted else Color.White,
            modifier = Modifier.weight(0.55f).alpha(fwAlpha)
        )
        MemoryBlockPill(
            label = if (isDataOnly) "APP 跳过" else "APP 固件",
            color = fwColor,
            textColor = if (isDataOnly) DuoColors.TextMuted else Color.White,
            modifier = Modifier.weight(1.35f).alpha(fwAlpha)
        )
        MemoryBlockPill(
            label = "NVS 配置",
            color = dataColor,
            textColor = if (isDataOnly) DuoColors.OnEmerald else DuoColors.OnAmber,
            modifier = Modifier.weight(0.95f)
        )
        MemoryBlockPill(
            label = "FS 数据",
            color = dataColor,
            textColor = if (isDataOnly) DuoColors.OnEmerald else DuoColors.OnAmber,
            modifier = Modifier.weight(1.05f)
        )
    }
}

@Composable
private fun MemoryBlockPill(
    label: String,
    color: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}
