package com.m5burner.duo.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.m5burner.duo.data.model.CategoryFormatter
import com.m5burner.duo.data.model.FirmwareVersion
import com.m5burner.duo.data.model.FlashConfig
import com.m5burner.duo.flasher.FlashStage
import com.m5burner.duo.ui.theme.DuoColors

/**
 * Bottom 4.5" (1280x960 4:3) Retro Handheld Tactile Control Console.
 * Uses tonal plastic layering, 3D beveled push-buttons, balanced spatial rhythm (zero hollow voids),
 * live Flash Memory Map signifier bar, and glass ABXY keycaps.
 */
@Composable
fun BottomTactileDeck(
    state: M5BurnerUiState,
    onSelectVersion: (FirmwareVersion) -> Unit,
    onSelectBaudRate: (Int) -> Unit,
    onSelectOffset: (Int) -> Unit,
    onToggleCompression: () -> Unit,
    onToggleAdvancedOptions: () -> Unit,
    onFlashClick: () -> Unit,
    onDownloadOnlyClick: () -> Unit,
    onEraseClick: () -> Unit,
    onCancelClick: () -> Unit,
    onSelectBackupSizeMb: (Int) -> Unit,
    onStartBackupClick: () -> Unit,
    onRestoreBackupClick: () -> Unit,
    onRestoreBackupDataOnlyClick: () -> Unit,
    onEditBackupClick: () -> Unit,
    onExportBackupClick: () -> Unit,
    onDeleteBackupClick: () -> Unit,
    onToggleSerialMonitor: () -> Unit,
    onSelectSerialBaud: (Int) -> Unit,
    onToggleSerialPause: () -> Unit,
    onToggleSerialTimestamps: () -> Unit,
    onClearSerial: () -> Unit,
    onSendSerialCommand: (String) -> Unit,
    onResetDeviceClick: () -> Unit,
    onScanUsbClick: () -> Unit,
    onRequestUsbPermission: () -> Unit,
    onToggleRawLogs: () -> Unit,
    onClearRawLogs: () -> Unit,
    onDismissOverlay: (() -> Unit)? = null
) {
    val view = LocalView.current
    fun haptic() = view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DuoColors.Canvas)
    ) {
        // 1. Matching Retro Hinge LED Bridge
        RetroHingeBridge(state = state, isBottomScreenTopEdge = true)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            // 2. Retro Handheld Link LED Bar
            RetroHardwareLinkBar(
                state = state,
                onScanUsbClick = { haptic(); onScanUsbClick() },
                onRequestUsbPermission = { haptic(); onRequestUsbPermission() },
                onToggleRawLogs = { haptic(); onToggleRawLogs() },
                onDismissOverlay = onDismissOverlay
            )

            // 3. Focal Tactile Console Body
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    state.showRawLogsDrawer -> {
                        RetroRawLogDrawer(
                            logs = state.rawLogs,
                            onClear = onClearRawLogs,
                            onClose = onToggleRawLogs
                        )
                    }
                    state.isBusy -> {
                        RetroProgressDeck(
                            state = state,
                            onCancelClick = { haptic(); onCancelClick() }
                        )
                    }
                    else -> {
                        when (state.mode) {
                            AppMode.DISCOVER -> RetroDiscoverDeck(
                                state = state,
                                onSelectVersion = { haptic(); onSelectVersion(it) },
                                onToggleAdvanced = { haptic(); onToggleAdvancedOptions() },
                                onSelectBaudRate = onSelectBaudRate,
                                onSelectOffset = onSelectOffset,
                                onToggleCompression = onToggleCompression,
                                onFlashClick = { haptic(); onFlashClick() },
                                onDownloadOnlyClick = { haptic(); onDownloadOnlyClick() },
                                onEraseClick = { haptic(); onEraseClick() }
                            )
                            AppMode.VAULT -> RetroVaultDeck(
                                state = state,
                                onSelectBackupSizeMb = { haptic(); onSelectBackupSizeMb(it) },
                                onStartBackupClick = { haptic(); onStartBackupClick() },
                                onRestoreBackupClick = { haptic(); onRestoreBackupClick() },
                                onRestoreBackupDataOnlyClick = { haptic(); onRestoreBackupDataOnlyClick() },
                                onEditBackupClick = { haptic(); onEditBackupClick() },
                                onExportBackupClick = { haptic(); onExportBackupClick() },
                                onDeleteBackupClick = { haptic(); onDeleteBackupClick() }
                            )
                            AppMode.MONITOR -> RetroSerialDeck(
                                state = state,
                                onToggleMonitor = { haptic(); onToggleSerialMonitor() },
                                onSelectBaud = { haptic(); onSelectSerialBaud(it) },
                                onResetDevice = { haptic(); onResetDeviceClick() },
                                onTogglePause = { haptic(); onToggleSerialPause() },
                                onToggleTimestamps = { haptic(); onToggleSerialTimestamps() },
                                onClearSerial = { haptic(); onClearSerial() },
                                onSendCommand = { haptic(); onSendSerialCommand(it) }
                            )
                        }
                    }
                }
            }

            // 4. Subtle Outcome Status Strip (only when Done / Error)
            if (!state.isBusy && (state.flashProgress.stage == FlashStage.DONE || state.flashProgress.stage == FlashStage.ERROR)) {
                val isError = state.flashProgress.stage == FlashStage.ERROR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isError) DuoColors.Coral.copy(alpha = 0.16f) else DuoColors.Emerald.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isError) DuoColors.Coral else DuoColors.Emerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = state.flashProgress.message,
                        color = DuoColors.TextPrimary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun RetroHardwareLinkBar(
    state: M5BurnerUiState,
    onScanUsbClick: () -> Unit,
    onRequestUsbPermission: () -> Unit,
    onToggleRawLogs: () -> Unit,
    onDismissOverlay: (() -> Unit)?
) {
    val usb = state.selectedUsbDevice
    val pulse = rememberInfiniteTransition(label = "led")
    val ledAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "ledAlpha"
    )

    val ledColor = when {
        state.isBusy -> DuoColors.RetroAmber
        usb == null -> DuoColors.TextMuted
        !usb.hasPermission -> DuoColors.RetroAmber
        else -> DuoColors.Emerald
    }

    val linkText = when {
        usb == null -> "LINK 待命 · 插入 M5Stack USB-C 线即可连接"
        !usb.hasPermission -> "发现 ${usb.driverLabel} (${usb.vidPidHex}) · 点击授权"
        else -> "LINK 已就绪 · ${usb.driverLabel} (${usb.vidPidHex})"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(11.dp))
                .background(DuoColors.Surface)
                .clickable {
                    if (usb != null && !usb.hasPermission) onRequestUsbPermission() else onScanUsbClick()
                }
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .alpha(if (usb == null || state.isBusy || !usb.hasPermission) ledAlpha else 1f)
                    .clip(CircleShape)
                    .background(ledColor)
            )
            Icon(Icons.Default.Usb, contentDescription = null, tint = DuoColors.RetroAmber, modifier = Modifier.size(16.dp))
            Text(
                text = linkText,
                color = DuoColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.Refresh, contentDescription = "Scan", tint = DuoColors.TextMuted, modifier = Modifier.size(15.dp))
        }

        // Subtle LOG button
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (state.showRawLogsDrawer) DuoColors.SurfaceElevated else DuoColors.Surface)
                .clickable(onClick = onToggleRawLogs)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                Icons.Default.Terminal,
                contentDescription = null,
                tint = if (state.showRawLogsDrawer) DuoColors.RetroAmber else DuoColors.TextSecondary,
                modifier = Modifier.size(15.dp)
            )
            Text("日志", color = DuoColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        if (onDismissOverlay != null) {
            IconButton(onClick = onDismissOverlay, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = DuoColors.TextSecondary)
            }
        }
    }
}

/**
 * Mode 1 Bottom Deck: Cartridge Spotlight + Version Selector + Tactile 3D Push-Buttons.
 * Balanced vertical rhythm — zero awkward voids!
 */
@Composable
private fun RetroDiscoverDeck(
    state: M5BurnerUiState,
    onSelectVersion: (FirmwareVersion) -> Unit,
    onToggleAdvanced: () -> Unit,
    onSelectBaudRate: (Int) -> Unit,
    onSelectOffset: (Int) -> Unit,
    onToggleCompression: () -> Unit,
    onFlashClick: () -> Unit,
    onDownloadOnlyClick: () -> Unit,
    onEraseClick: () -> Unit
) {
    val fw = state.selectedFirmware
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(DuoColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (fw == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("在上屏选择一款 M5Stack 固件卡带", color = DuoColors.TextSecondary, fontSize = 14.sp)
            }
            return
        }

        // 1. Rich Cartridge Label Banner (fills upper visual space naturally)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(DuoColors.SurfaceSoft, DuoColors.SurfaceElevated.copy(alpha = 0.72f))
                    )
                )
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(136.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DuoColors.Canvas)
            ) {
                if (fw.coverUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(fw.coverUrl).crossfade(200).build(),
                        contentDescription = fw.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Memory,
                        contentDescription = null,
                        tint = DuoColors.TextMuted,
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.Center)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = CategoryFormatter.formatLabel(fw.category),
                            color = DuoColors.OnAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(DuoColors.RetroAmber)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Text(
                            text = "by ${fw.author.ifBlank { "M5Stack" }}",
                            color = DuoColors.TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = fw.name,
                        color = DuoColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = fw.description.ifBlank { "社区分享固件，支持一键高速烧录" },
                        color = DuoColors.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Meta strip
                val ver = state.selectedVersion
                val isCached = ver != null && state.downloadedFiles.contains(ver.file)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "发布 ${ver?.publishedAt?.ifBlank { "-" } ?: "-"}",
                        color = DuoColors.TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (isCached) {
                        Text("● 已本地缓存", color = DuoColors.Emerald, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // 2. Compact Version Strip + Collapsible DIP-Switch Params
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("固件版本", color = DuoColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    RetroGlassKeycap("X", DuoColors.BtnX)
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DuoColors.SurfaceSoft)
                        .clickable(onClick = onToggleAdvanced)
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = DuoColors.RetroAmber, modifier = Modifier.size(13.dp))
                    Text(
                        text = if (state.showAdvancedFlashOptions) "收起参数" else "${state.flashConfig.baudRate / 1000}k · 0x${state.flashConfig.flashAddress.toString(16)}",
                        color = DuoColors.TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                fw.publishedVersions.take(10).forEach { ver ->
                    val active = ver.file == state.selectedVersion?.file
                    val cached = state.downloadedFiles.contains(ver.file)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) DuoColors.RetroAmber else DuoColors.SurfaceSoft)
                            .clickable { onSelectVersion(ver) }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = ver.version.ifBlank { "latest" },
                            color = if (active) DuoColors.OnAmber else DuoColors.TextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        if (cached) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(if (active) Color(0xFF165B2E) else DuoColors.Emerald))
                        }
                    }
                }
            }

            if (state.showAdvancedFlashOptions) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FlashConfig.SUPPORTED_BAUD_RATES.forEach { baud ->
                        RetroDipChip("${baud / 1000}k", state.flashConfig.baudRate == baud) { onSelectBaudRate(baud) }
                    }
                    Spacer(Modifier.width(4.dp))
                    listOf(0x0 to "0x0", 0x10000 to "0x10000").forEach { (addr, label) ->
                        RetroDipChip(label, state.flashConfig.flashAddress == addr) { onSelectOffset(addr) }
                    }
                    RetroDipChip(if (state.flashConfig.useCompression) "ZLIB" else "RAW", state.flashConfig.useCompression, onToggleCompression)
                }
            }
        }

        // 3. Tactile 3D Action Dock
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RetroPushButton(
                label = "烧录卡带",
                icon = Icons.Default.Bolt,
                faceColor = DuoColors.M5Blue,
                lipColor = DuoColors.M5BlueDeep,
                contentColor = Color.White,
                keycapLetter = "A",
                keycapDotColor = DuoColors.BtnA,
                height = 54.dp,
                modifier = Modifier.weight(1.75f),
                onClick = onFlashClick
            )
            RetroPushButton(
                label = "缓存",
                icon = Icons.Default.CloudDownload,
                faceColor = DuoColors.SurfaceElevated,
                lipColor = DuoColors.NeutralLip,
                contentColor = DuoColors.TextPrimary,
                height = 54.dp,
                modifier = Modifier.weight(0.75f),
                onClick = onDownloadOnlyClick
            )
            RetroPushButton(
                label = "擦除",
                icon = Icons.Default.DeleteSweep,
                faceColor = DuoColors.SurfaceElevated,
                lipColor = DuoColors.NeutralLip,
                contentColor = DuoColors.RetroAmber,
                enabled = state.selectedUsbDevice != null,
                height = 54.dp,
                modifier = Modifier.weight(0.75f),
                onClick = onEraseClick
            )
        }
    }
}

/**
 * Mode 2 Bottom Deck: Live Flash Memory Map Signifier Bar + Memory Card Backup & Restore Controls.
 */
@Composable
private fun RetroVaultDeck(
    state: M5BurnerUiState,
    onSelectBackupSizeMb: (Int) -> Unit,
    onStartBackupClick: () -> Unit,
    onRestoreBackupClick: () -> Unit,
    onRestoreBackupDataOnlyClick: () -> Unit,
    onEditBackupClick: () -> Unit,
    onExportBackupClick: () -> Unit,
    onDeleteBackupClick: () -> Unit
) {
    val isDataOnlyMode = state.backupSizeMb == M5BurnerUiState.BACKUP_MODE_DATA_ONLY
    val isAutoFullMode = state.backupSizeMb == M5BurnerUiState.BACKUP_MODE_AUTO_FULL

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Signature Visual Signifier: Live ESP32 Flash Memory Map Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(DuoColors.Surface)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isDataOnlyMode) "闪存范围示意 · 仅读取绿色存储数据区" else "闪存范围示意 · 完整读取固件与全部数据",
                    color = if (isDataOnlyMode) DuoColors.Emerald else DuoColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                RetroShoulderCap("L2/R2")
            }
            FlashMemoryMapBar(isDataOnly = isDataOnlyMode)
        }

        // Balanced 2-Column Tactile Memory Card Console
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            // Left: Read & Backup Mode Selector
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(15.dp))
                    .background(DuoColors.Surface)
                    .padding(13.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isDataOnlyMode) "新建纯数据存档" else "新建完整设备存档",
                        color = DuoColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    // Two Main Mode Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RetroModeChip(
                            label = "自动全片",
                            active = isAutoFullMode,
                            activeBg = DuoColors.M5Blue,
                            activeFg = Color.White,
                            modifier = Modifier.weight(1f)
                        ) { onSelectBackupSizeMb(M5BurnerUiState.BACKUP_MODE_AUTO_FULL) }

                        RetroModeChip(
                            label = "仅备数据",
                            active = isDataOnlyMode,
                            activeBg = DuoColors.Emerald,
                            activeFg = DuoColors.OnEmerald,
                            modifier = Modifier.weight(1f)
                        ) { onSelectBackupSizeMb(M5BurnerUiState.BACKUP_MODE_DATA_ONLY) }
                    }

                    // Compact Manual Sizes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        listOf(4, 8, 16).forEach { mb ->
                            val active = state.backupSizeMb == mb
                            RetroModeChip(
                                label = "手动 ${mb}M",
                                active = active,
                                activeBg = DuoColors.RetroAmber,
                                activeFg = DuoColors.OnAmber,
                                small = true,
                                modifier = Modifier.weight(1f)
                            ) { onSelectBackupSizeMb(mb) }
                        }
                    }
                }

                RetroPushButton(
                    label = if (isDataOnlyMode) "仅备份数据" else "读取并备份",
                    icon = if (isDataOnlyMode) Icons.Default.Storage else Icons.Default.Backup,
                    faceColor = if (isDataOnlyMode) DuoColors.Emerald else DuoColors.SurfaceElevated,
                    lipColor = if (isDataOnlyMode) DuoColors.EmeraldDeep else DuoColors.NeutralLip,
                    contentColor = if (isDataOnlyMode) DuoColors.OnEmerald else DuoColors.TextPrimary,
                    keycapLetter = "X",
                    keycapDotColor = DuoColors.BtnX,
                    height = 48.dp,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onStartBackupClick
                )
            }

            // Right: Selected Memory Card Slot Actions (or helpful guide when empty)
            val selected = state.selectedBackup
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(15.dp))
                    .background(DuoColors.Surface)
                    .padding(13.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                if (selected == null) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, tint = DuoColors.RetroAmber.copy(alpha = 0.7f), modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("随时一键回档", color = DuoColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "全片存档可恢复整机状态，\n纯数据存档(.zip)可在升级固件后\n单独找回 NVS 配置与文件数据。",
                            color = DuoColors.TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("当前存档槽", color = DuoColors.RetroAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(onClick = onEditBackupClick, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = DuoColors.TextSecondary, modifier = Modifier.size(15.dp))
                                }
                                IconButton(onClick = onExportBackupClick, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.FileDownload, contentDescription = "Export", tint = DuoColors.RetroAmber, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = onDeleteBackupClick, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = DuoColors.Coral, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Text(
                            text = selected.title,
                            color = DuoColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${selected.chipName} · ${selected.formattedSize} · ${selected.formattedDate}",
                            color = DuoColors.TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        if (selected.safeTags.isNotEmpty()) {
                            Text(
                                text = selected.safeTags.joinToString(" · "),
                                color = DuoColors.Emerald,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selected.isDataOnlyBundle) {
                            RetroPushButton(
                                label = "恢复数据分区",
                                icon = Icons.Default.Storage,
                                faceColor = DuoColors.Emerald,
                                lipColor = DuoColors.EmeraldDeep,
                                contentColor = DuoColors.OnEmerald,
                                keycapLetter = "A",
                                keycapDotColor = DuoColors.BtnA,
                                height = 48.dp,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onRestoreBackupDataOnlyClick
                            )
                        } else {
                            RetroPushButton(
                                label = "完整刷回",
                                icon = Icons.Default.Restore,
                                faceColor = DuoColors.M5Blue,
                                lipColor = DuoColors.M5BlueDeep,
                                contentColor = Color.White,
                                keycapLetter = "A",
                                keycapDotColor = DuoColors.BtnA,
                                height = 48.dp,
                                modifier = Modifier.weight(if (selected.hasUserDataPartitions) 1.2f else 1f),
                                onClick = onRestoreBackupClick
                            )
                            if (selected.hasUserDataPartitions) {
                                RetroPushButton(
                                    label = "仅恢数据",
                                    icon = Icons.Default.Storage,
                                    faceColor = DuoColors.SurfaceElevated,
                                    lipColor = DuoColors.NeutralLip,
                                    contentColor = DuoColors.Emerald,
                                    height = 48.dp,
                                    modifier = Modifier.weight(0.95f),
                                    onClick = onRestoreBackupDataOnlyClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Mode 3 Bottom Deck: Real Handheld Diagnostic Oscilloscope Console.
 * Eliminates empty space with live RX/Error telemetry + 1-tap quick debug TX chips!
 */
@Composable
private fun RetroSerialDeck(
    state: M5BurnerUiState,
    onToggleMonitor: () -> Unit,
    onSelectBaud: (Int) -> Unit,
    onResetDevice: () -> Unit,
    onTogglePause: () -> Unit,
    onToggleTimestamps: () -> Unit,
    onClearSerial: () -> Unit,
    onSendCommand: (String) -> Unit
) {
    var cmdInput by remember { mutableStateOf("") }
    val errorCount = remember(state.serialEntries) {
        state.serialEntries.count {
            it.text.contains("error", ignoreCase = true) ||
                it.text.contains("panic", ignoreCase = true) ||
                it.text.contains("Guru Meditation", ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(DuoColors.Surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Live Signal Telemetry Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DuoColors.Canvas)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (state.isSerialMonitorRunning) DuoColors.Emerald else DuoColors.TextMuted)
                )
                Text(
                    text = if (state.isSerialMonitorRunning) "SIGNAL LIVE" else "STANDBY",
                    color = if (state.isSerialMonitorRunning) DuoColors.Emerald else DuoColors.TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Text(
                text = "RX ${state.serialEntries.size} 行",
                color = DuoColors.TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "ERR $errorCount",
                color = if (errorCount > 0) DuoColors.Coral else DuoColors.TextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${state.serialBaudRate} bps",
                color = DuoColors.RetroAmber,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        // 2. Tactile 3D Hero Controls (No red-on-green clash thanks to RetroGlassKeycap!)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RetroPushButton(
                label = if (state.isSerialMonitorRunning) "停止监听" else "开始监听",
                icon = if (state.isSerialMonitorRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                faceColor = if (state.isSerialMonitorRunning) DuoColors.Coral else DuoColors.M5Blue,
                lipColor = if (state.isSerialMonitorRunning) DuoColors.CoralDeep else DuoColors.M5BlueDeep,
                contentColor = Color.White,
                keycapLetter = "A",
                keycapDotColor = DuoColors.BtnA,
                height = 54.dp,
                modifier = Modifier.weight(1.45f),
                onClick = onToggleMonitor
            )
            RetroPushButton(
                label = "重启设备",
                icon = Icons.Default.RestartAlt,
                faceColor = DuoColors.SurfaceElevated,
                lipColor = DuoColors.NeutralLip,
                contentColor = DuoColors.TextPrimary,
                keycapLetter = "X",
                keycapDotColor = DuoColors.BtnX,
                enabled = state.selectedUsbDevice != null,
                height = 54.dp,
                modifier = Modifier.weight(1f),
                onClick = onResetDevice
            )
        }

        // 3. Beveled Baud Rate Keycaps + Quick Debug Command Chips
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                M5BurnerUiState.SERIAL_BAUD_RATES.forEach { baud ->
                    val active = state.serialBaudRate == baud
                    RetroModeChip(
                        label = baud.toString(),
                        active = active,
                        activeBg = DuoColors.RetroAmber,
                        activeFg = DuoColors.OnAmber,
                        mono = true,
                        modifier = Modifier.weight(1f)
                    ) { onSelectBaud(baud) }
                }
            }

            // Quick 1-Tap Diagnostic TX Shortcuts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("快捷发送", color = DuoColors.TextMuted, fontSize = 10.sp)
                listOf("回车" to "", "help" to "help", "status" to "status", "reboot" to "reboot").forEach { (label, cmd) ->
                    Text(
                        text = label,
                        color = if (state.isSerialMonitorRunning) DuoColors.TextPrimary else DuoColors.TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(DuoColors.SurfaceSoft)
                            .clickable(enabled = state.isSerialMonitorRunning) { onSendCommand(cmd) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // 4. Bottom Utility & Manual Command Input Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RetroMiniToolChip(
                icon = if (state.isSerialPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                label = if (state.isSerialPaused) "继续" else "暂停",
                active = state.isSerialPaused,
                onClick = onTogglePause
            )
            RetroMiniToolChip(
                icon = Icons.Default.Schedule,
                label = "时间",
                active = state.showSerialTimestamps,
                onClick = onToggleTimestamps
            )
            RetroMiniToolChip(
                icon = Icons.Default.DeleteOutline,
                label = "清屏",
                active = false,
                keycap = "Y",
                keycapColor = DuoColors.BtnY,
                onClick = onClearSerial
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DuoColors.Canvas)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = cmdInput,
                    onValueChange = { cmdInput = it },
                    singleLine = true,
                    textStyle = TextStyle(color = DuoColors.TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(DuoColors.RetroAmber),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (cmdInput.isEmpty()) {
                            Text("输入串口指令...", color = DuoColors.TextMuted, fontSize = 12.sp)
                        }
                        inner()
                    }
                )
                IconButton(
                    onClick = {
                        if (cmdInput.isNotBlank()) {
                            onSendCommand(cmdInput)
                            cmdInput = ""
                        }
                    },
                    enabled = state.isSerialMonitorRunning,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (state.isSerialMonitorRunning) DuoColors.RetroAmber else DuoColors.TextMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RetroProgressDeck(
    state: M5BurnerUiState,
    onCancelClick: () -> Unit
) {
    val fp = state.flashProgress
    val steps = listOf("连接", "准备", "传输", "重启")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(DuoColors.Surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.activeTaskLabel.ifBlank { fp.stage.label },
                    color = DuoColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(fp.message, color = DuoColors.TextSecondary, fontSize = 12.sp)
            }
            Text(
                text = "${(fp.progress * 100).toInt()}%",
                color = DuoColors.RetroAmber,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
        }

        // 4-Step Console Timeline
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            steps.forEachIndexed { idx, label ->
                val num = idx + 1
                val done = fp.stage.stepIndex > num || fp.stage == FlashStage.DONE
                val active = fp.stage.stepIndex == num
                Text(
                    text = "$num.$label",
                    color = when {
                        done -> DuoColors.OnEmerald
                        active -> DuoColors.OnAmber
                        else -> DuoColors.TextMuted
                    },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                done -> DuoColors.Emerald
                                active -> DuoColors.RetroAmber
                                else -> DuoColors.SurfaceSoft
                            }
                        )
                        .padding(vertical = 8.dp)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LinearProgressIndicator(
                progress = { fp.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = DuoColors.RetroAmber,
                trackColor = DuoColors.Canvas
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = fp.detectedChip?.displayName ?: "自动识别芯片中",
                    color = DuoColors.TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (fp.speedKbps > 0f) {
                    Text(
                        text = "${"%.0f".format(fp.speedKbps)} KB/s",
                        color = DuoColors.Emerald,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        RetroPushButton(
            label = "取消当前任务",
            icon = Icons.Default.Close,
            faceColor = DuoColors.Coral,
            lipColor = DuoColors.CoralDeep,
            contentColor = Color.White,
            height = 46.dp,
            modifier = Modifier.fillMaxWidth(),
            onClick = onCancelClick
        )
    }
}

@Composable
private fun RetroRawLogDrawer(
    logs: List<String>,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(DuoColors.TerminalBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DuoColors.Surface)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("底层通信日志 (${logs.size})", color = DuoColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("清空", color = DuoColors.TextMuted, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onClear))
                Text("返回操控台", color = DuoColors.RetroAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onClose))
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(logs) { line ->
                val color = when {
                    line.contains("[ERROR]") -> DuoColors.Coral
                    line.contains("[WARN]") -> DuoColors.RetroAmber
                    line.contains("[DONE]") -> DuoColors.TerminalText
                    else -> Color(0xFFC5D6CB)
                }
                Text(line, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun RetroModeChip(
    label: String,
    active: Boolean,
    activeBg: Color,
    activeFg: Color,
    small: Boolean = false,
    mono: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(if (small) 7.dp else 9.dp))
            .background(if (active) activeBg else DuoColors.SurfaceSoft)
            .clickable(onClick = onClick)
            .padding(vertical = if (small) 6.dp else 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) activeFg else DuoColors.TextSecondary,
            fontSize = if (small) 10.sp else 12.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun RetroDipChip(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (active) DuoColors.OnAmber else DuoColors.TextSecondary,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (active) DuoColors.RetroAmber else DuoColors.SurfaceSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    )
}

@Composable
private fun RetroMiniToolChip(
    icon: ImageVector,
    label: String,
    active: Boolean,
    keycap: String? = null,
    keycapColor: Color = DuoColors.BtnY,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) DuoColors.RetroAmber else DuoColors.SurfaceSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) DuoColors.OnAmber else DuoColors.TextSecondary,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = label,
            color = if (active) DuoColors.OnAmber else DuoColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
        )
        if (keycap != null) {
            RetroGlassKeycap(keycap, keycapColor)
        }
    }
}
