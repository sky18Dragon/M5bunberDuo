package com.m5burner.duo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.m5burner.duo.data.BackupSourceType
import com.m5burner.duo.data.FirmwareBackupRecord
import com.m5burner.duo.data.model.CategoryFormatter
import com.m5burner.duo.data.model.FirmwareItem
import com.m5burner.duo.ui.theme.DuoColors

/**
 * Top 5.5" (1920x1080 16:9) Retro Handheld Cartridge Shelf, Memory Card Vault & CRT Monitor.
 * Designed with warm obsidian tonal elevation, Amber Gold focus cursor, 120Hz spring lift,
 * and full-screen edge-to-edge console immersion.
 */
@Composable
fun TopObservationScreen(
    state: M5BurnerUiState,
    onSelectMode: (AppMode) -> Unit,
    onSelectCategory: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onCycleSort: () -> Unit,
    onRefreshCatalog: () -> Unit,
    onSelectFirmware: (FirmwareItem) -> Unit,
    onSelectBackup: (FirmwareBackupRecord) -> Unit,
    onSaveEditedBackup: (String, String) -> Unit,
    onDismissEditBackup: () -> Unit,
    onImportLocalBin: () -> Unit,
    onSerialFilterChange: (String) -> Unit,
    onToggleTheme: () -> Unit,
    onToggleSingleScreenDeck: () -> Unit,
    deckContent: @Composable (onDismiss: () -> Unit) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DuoColors.Canvas)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Retro Console Mode Header (Cartridge Tabs + L1/R1 Hardware Caps)
            RetroTopModeBar(
                state = state,
                onSelectMode = onSelectMode,
                onSearchChange = onSearchChange,
                onCycleSort = onCycleSort,
                onRefreshCatalog = onRefreshCatalog,
                onImportLocalBin = onImportLocalBin,
                onSerialFilterChange = onSerialFilterChange,
                onToggleTheme = onToggleTheme,
                onToggleSingleScreenDeck = onToggleSingleScreenDeck
            )

            // 2. Main Visual Stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (state.mode) {
                    AppMode.DISCOVER -> RetroCartridgeShelfStage(
                        state = state,
                        onSelectCategory = onSelectCategory,
                        onSelectFirmware = onSelectFirmware,
                        onRefreshCatalog = onRefreshCatalog
                    )
                    AppMode.VAULT -> RetroMemoryCardVaultStage(
                        state = state,
                        onSelectBackup = onSelectBackup
                    )
                    AppMode.MONITOR -> RetroCrtSerialStage(state = state)
                }
            }

            // 3. Classic Hinge LED Bridge
            RetroHingeBridge(state = state, isBottomScreenTopEdge = false)
        }

        // Single-screen fallback modal deck
        if (!state.hasSecondaryDisplay && state.showSingleScreenDeckOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(18.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, DuoColors.RetroAmber.copy(alpha = 0.5f)),
                    color = DuoColors.Canvas,
                    modifier = Modifier.fillMaxSize()
                ) {
                    deckContent(onToggleSingleScreenDeck)
                }
            }
        }

        // Edit Backup Name / Note Dialog
        state.editingBackupRecord?.let { editing ->
            var titleDraft by remember(editing.id) { mutableStateOf(editing.title) }
            var noteDraft by remember(editing.id) { mutableStateOf(editing.note) }
            AlertDialog(
                onDismissRequest = onDismissEditBackup,
                containerColor = DuoColors.Surface,
                title = {
                    Text(
                        "编辑存档名称与备注",
                        color = DuoColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = titleDraft,
                            onValueChange = { titleDraft = it },
                            label = { Text("存档名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = noteDraft,
                            onValueChange = { noteDraft = it },
                            label = { Text("备注（如：原厂固件+Wi-Fi配置/脚本）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onSaveEditedBackup(titleDraft, noteDraft) }) {
                        Text("保存", color = DuoColors.RetroAmber, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissEditBackup) {
                        Text("取消", color = DuoColors.TextSecondary)
                    }
                }
            )
        }
    }
}

@Composable
private fun RetroTopModeBar(
    state: M5BurnerUiState,
    onSelectMode: (AppMode) -> Unit,
    onSearchChange: (String) -> Unit,
    onCycleSort: () -> Unit,
    onRefreshCatalog: () -> Unit,
    onImportLocalBin: () -> Unit,
    onSerialFilterChange: (String) -> Unit,
    onToggleTheme: () -> Unit,
    onToggleSingleScreenDeck: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DuoColors.Surface)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Molded Retro Console Nameplate (SFC 4-Color Micro Dots + Stamped "M5 DUO")
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(DuoColors.SurfaceSoft)
                .border(1.dp, DuoColors.TopBevel, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // Iconic 2x2 SFC / Retroid ABXY color matrix motif
            Column(verticalArrangement = Arrangement.spacedBy(2.5.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
                    Box(Modifier.size(4.5.dp).clip(CircleShape).background(DuoColors.BtnX))
                    Box(Modifier.size(4.5.dp).clip(CircleShape).background(DuoColors.BtnA))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
                    Box(Modifier.size(4.5.dp).clip(CircleShape).background(DuoColors.BtnY))
                    Box(Modifier.size(4.5.dp).clip(CircleShape).background(DuoColors.BtnB))
                }
            }
            Text(
                text = "M5 DUO",
                color = DuoColors.TextPrimary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp
            )
        }

        // Tactile Console Mode Switcher with L1 / R1 Shoulder Keycaps
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(11.dp))
                .background(DuoColors.Canvas)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RetroShoulderCap("L1")
            AppMode.entries.forEach { tab ->
                val selected = state.mode == tab
                val bg by animateColorAsState(
                    if (selected) DuoColors.SurfaceElevated else Color.Transparent,
                    label = "retroTabBg"
                )
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .clickable { onSelectMode(tab) }
                        .padding(horizontal = 13.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = when (tab) {
                                AppMode.DISCOVER -> "固件卡带"
                                AppMode.VAULT -> "存档备份"
                                AppMode.MONITOR -> "串口示波"
                            },
                            color = if (selected) DuoColors.TextPrimary else DuoColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                        )
                        if (tab == AppMode.VAULT && state.backups.isNotEmpty()) {
                            Text(
                                text = state.backups.size.toString(),
                                color = if (selected) DuoColors.RetroAmber else DuoColors.TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (tab == AppMode.MONITOR && state.isSerialMonitorRunning) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(DuoColors.Emerald)
                            )
                        }
                    }
                    // Warm Amber active slot bar
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .width(if (selected) 26.dp else 0.dp)
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(DuoColors.RetroAmber)
                    )
                }
            }
            RetroShoulderCap("R1")
        }

        Spacer(Modifier.weight(1f))

        // Context-Only Right Tools (tonal elevation, no heavy borders!)
        when (state.mode) {
            AppMode.DISCOVER -> {
                RetroSearchPill(
                    value = state.searchQuery,
                    placeholder = "搜索 ${state.allFirmwares.size} 款社区固件...",
                    onValueChange = onSearchChange,
                    modifier = Modifier.width(235.dp)
                )
                IconButton(onClick = onCycleSort, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort", tint = DuoColors.TextSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onRefreshCatalog, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = DuoColors.TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
            AppMode.VAULT -> {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(DuoColors.SurfaceSoft)
                        .clickable(onClick = onImportLocalBin)
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = DuoColors.RetroAmber, modifier = Modifier.size(16.dp))
                    Text("导入本地 .bin", color = DuoColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            AppMode.MONITOR -> {
                RetroSearchPill(
                    value = state.serialFilterQuery,
                    placeholder = "过滤日志关键字...",
                    onValueChange = onSerialFilterChange,
                    modifier = Modifier.width(225.dp)
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (state.isSerialMonitorRunning) DuoColors.Emerald.copy(alpha = 0.16f) else DuoColors.SurfaceSoft)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (state.isSerialMonitorRunning) DuoColors.Emerald else DuoColors.TextMuted)
                    )
                    Text(
                        text = "${state.serialBaudRate} bps",
                        color = if (state.isSerialMonitorRunning) DuoColors.Emerald else DuoColors.TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 1-Tap Retro Beige / Night CRT Theme Switch
        IconButton(onClick = onToggleTheme, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = if (state.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                contentDescription = "Toggle Theme",
                tint = DuoColors.RetroAmber,
                modifier = Modifier.size(18.dp)
            )
        }

        if (!state.hasSecondaryDisplay) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(DuoColors.M5Blue)
                    .clickable(onClick = onToggleSingleScreenDeck)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(Icons.Default.Terminal, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text("操控台", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RetroCartridgeShelfStage(
    state: M5BurnerUiState,
    onSelectCategory: (String) -> Unit,
    onSelectFirmware: (FirmwareItem) -> Unit,
    onRefreshCatalog: () -> Unit
) {
    val gridState = rememberLazyGridState()
    val selectedIndex = state.filteredFirmwares.indexOfFirst { it.stableId == state.selectedFirmware?.stableId }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) {
            gridState.animateScrollToItem(selectedIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Sleek Tonal Device Category Pill Strip (no boxy borders!)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            RetroShoulderCap("L2/R2")
            state.categories.take(18).forEach { cat ->
                val active = cat.key == state.selectedCategory
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (active) DuoColors.RetroAmber else DuoColors.Surface)
                        .clickable { onSelectCategory(cat.key) }
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = cat.label,
                        color = if (active) DuoColors.OnAmber else DuoColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium
                    )
                    Text(
                        text = cat.count.toString(),
                        color = if (active) DuoColors.OnAmber.copy(alpha = 0.85f) else DuoColors.TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 3-Column Retro Cartridge Gallery
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoadingCatalog && state.filteredFirmwares.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = DuoColors.RetroAmber, strokeWidth = 3.dp)
                        Text("正在读取 M5Burner 固件卡带库...", color = DuoColors.TextSecondary, fontSize = 13.sp)
                    }
                }
                state.filteredFirmwares.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(state.catalogError ?: "未找到匹配的固件卡带", color = DuoColors.TextSecondary, fontSize = 13.sp)
                        OutlinedButton(onClick = onRefreshCatalog) {
                            Text("重新刷新", color = DuoColors.RetroAmber)
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.filteredFirmwares, key = { it.stableId }) { fw ->
                            val selected = fw.stableId == state.selectedFirmware?.stableId
                            val cached = fw.latestVersion?.let { state.downloadedFiles.contains(it.file) } == true
                            RetroCartridgeCard(
                                item = fw,
                                selected = selected,
                                cached = cached,
                                onClick = { onSelectFirmware(fw) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RetroCartridgeCard(
    item: FirmwareItem,
    selected: Boolean,
    cached: Boolean,
    onClick: () -> Unit
) {
    // 120Hz silky spring scale lift on selected cartridge
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.025f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "cardScale"
    )
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) DuoColors.SurfaceElevated else DuoColors.Surface)
            .then(
                if (selected) Modifier.border(2.dp, DuoColors.RetroAmber, RoundedCornerShape(14.dp))
                else Modifier.border(1.dp, DuoColors.TopBevel, RoundedCornerShape(14.dp))
            )
            .clickable(onClick = onClick)
    ) {
        // Subtle Retro Cartridge Grip Ridge at very top
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (selected) DuoColors.RetroAmber.copy(alpha = 0.14f) else DuoColors.SurfaceSoft.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = CategoryFormatter.formatLabel(item.category).uppercase(),
                color = if (selected) DuoColors.RetroAmber else DuoColors.TextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.6.sp,
                maxLines = 1
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (cached) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Cached", tint = DuoColors.Emerald, modifier = Modifier.size(12.dp))
                }
                // Classic cartridge 3-notch grip motif
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(if (selected) DuoColors.RetroAmber.copy(alpha = 0.7f) else DuoColors.Hairline)
                    )
                }
            }
        }

        // Cover Art with Warm Shimmer Placeholder + Smooth Crossfade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .background(DuoColors.SurfaceSoft)
        ) {
            if (item.coverUrl != null) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.coverUrl)
                        .crossfade(220)
                        .build(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = { CartridgeShimmerPlaceholder() },
                    error = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = DuoColors.TextMuted, modifier = Modifier.size(28.dp))
                        }
                    }
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = DuoColors.TextMuted, modifier = Modifier.size(28.dp))
                }
            }

            // Subtle bottom gradient scrim melting artwork into label body
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                (if (selected) DuoColors.SurfaceElevated else DuoColors.Surface).copy(alpha = 0.92f)
                            )
                        )
                    )
            )
        }

        // Cartridge Label Footer
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = item.name,
                color = DuoColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.author.ifBlank { "M5Stack" },
                    color = DuoColors.TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "★ ${formatDownloads(item.downloadCount)}",
                    color = if (selected) DuoColors.RetroAmber else DuoColors.TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CartridgeShimmerPlaceholder() {
    val inf = rememberInfiniteTransition(label = "shimmer")
    val alpha by inf.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "shimmerAlpha"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        DuoColors.SurfaceSoft,
                        DuoColors.SurfaceElevated.copy(alpha = alpha),
                        DuoColors.SurfaceSoft
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Memory, contentDescription = null, tint = DuoColors.TextMuted.copy(alpha = alpha), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun RetroMemoryCardVaultStage(
    state: M5BurnerUiState,
    onSelectBackup: (FirmwareBackupRecord) -> Unit
) {
    if (state.backups.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(DuoColors.SurfaceSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = DuoColors.RetroAmber, modifier = Modifier.size(34.dp))
                }
                Text(
                    text = "记忆卡存档槽为空",
                    color = DuoColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "插入 M5Stack 后，在下屏选择「自动全片」或「仅备数据」并按蓝色 X 键，\n即可将固件与板载存储数据保存为安全存档，随时一键刷回。",
                    color = DuoColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(state.backups, key = { _, rec -> rec.id }) { idx, record ->
            val selected = record.id == state.selectedBackup?.id
            val isDataOnly = record.sourceType == BackupSourceType.DATA_ONLY
            val slotLabel = "SLOT %02d".format(idx + 1)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) DuoColors.SurfaceElevated else DuoColors.Surface)
                    .then(
                        if (selected) Modifier.border(2.dp, DuoColors.RetroAmber, RoundedCornerShape(14.dp))
                        else Modifier.border(1.dp, DuoColors.TopBevel, RoundedCornerShape(14.dp))
                    )
                    .clickable { onSelectBackup(record) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Retro Memory Card Slot Tag
                Column(
                    modifier = Modifier
                        .width(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDataOnly) DuoColors.Emerald.copy(alpha = 0.16f) else DuoColors.M5Blue.copy(alpha = 0.22f))
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = when (record.sourceType) {
                            BackupSourceType.DATA_ONLY -> Icons.Default.Storage
                            BackupSourceType.DEVICE_BACKUP -> Icons.Default.Memory
                            BackupSourceType.LOCAL_IMPORT -> Icons.Default.FolderOpen
                        },
                        contentDescription = null,
                        tint = if (isDataOnly) DuoColors.Emerald else DuoColors.RetroAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = slotLabel,
                        color = if (selected) DuoColors.RetroAmber else DuoColors.TextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = record.title,
                            color = DuoColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = record.chipName,
                            color = DuoColors.RetroAmber,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(DuoColors.Canvas)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Text(
                            text = record.sourceType.label,
                            color = if (isDataOnly) DuoColors.Emerald else DuoColors.TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(DuoColors.SurfaceSoft)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (record.safeTags.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            record.safeTags.forEach { tag ->
                                val isDataTag = tag != "固件 App"
                                Text(
                                    text = "✓ $tag",
                                    color = if (isDataTag) DuoColors.Emerald else DuoColors.CyanGlow,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(if (isDataTag) DuoColors.Emerald.copy(alpha = 0.14f) else DuoColors.M5Blue.copy(alpha = 0.18f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(record.formattedSize, color = DuoColors.TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        if (record.shortMd5.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = DuoColors.Emerald, modifier = Modifier.size(12.dp))
                                Text("MD5 ${record.shortMd5}", color = DuoColors.TextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                        if (record.note.isNotBlank()) {
                            Text(record.note, color = DuoColors.TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(record.formattedDate, color = DuoColors.TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    if (selected) {
                        Text("已选中 ↓", color = DuoColors.RetroAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RetroCrtSerialStage(state: M5BurnerUiState) {
    val entries = state.filteredSerialEntries
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size, state.serialAutoScroll) {
        if (state.serialAutoScroll && entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.lastIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DuoColors.TerminalBg)
            .border(
                width = 1.5.dp,
                color = if (state.isSerialMonitorRunning) DuoColors.Emerald.copy(alpha = 0.45f) else DuoColors.SurfaceSoft,
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        if (entries.isEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = null,
                    tint = if (state.isSerialMonitorRunning) DuoColors.Emerald else DuoColors.RetroAmber.copy(alpha = 0.7f),
                    modifier = Modifier.size(34.dp)
                )
                Text(
                    text = if (state.isSerialMonitorRunning) "CRT 串口示波监听中 (${state.serialBaudRate} bps)..." else "串口诊断终端已就绪",
                    color = Color(0xFFF4EFE6),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "接入 M5Stack 后，在下屏按下「开始监听」或手柄 A 键即可实时查看启动与异常日志",
                    color = DuoColors.TerminalMuted,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                itemsIndexed(entries) { _, entry ->
                    val textColor = when {
                        entry.isTx -> DuoColors.RetroAmber
                        entry.text.contains("error", ignoreCase = true) ||
                            entry.text.contains("panic", ignoreCase = true) ||
                            entry.text.contains("Guru Meditation", ignoreCase = true) -> DuoColors.Coral
                        entry.text.contains("warn", ignoreCase = true) -> DuoColors.RetroAmber
                        entry.text.startsWith("---") -> DuoColors.TextSecondary
                        else -> DuoColors.TerminalText
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (state.showSerialTimestamps) {
                            Text(
                                text = entry.timestamp,
                                color = DuoColors.TerminalMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = entry.text,
                            color = textColor,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RetroSearchPill(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DuoColors.Canvas)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = DuoColors.TextMuted, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = DuoColors.TextPrimary, fontSize = 12.sp),
            cursorBrush = SolidColor(DuoColors.RetroAmber),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, color = DuoColors.TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                inner()
            }
        )
        if (value.isNotEmpty()) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Clear",
                tint = DuoColors.TextSecondary,
                modifier = Modifier
                    .size(15.dp)
                    .clickable { onValueChange("") }
            )
        }
    }
}

private fun formatDownloads(count: Int): String = when {
    count >= 100_000 -> "${count / 1000}k"
    count >= 1000 -> "%.1fk".format(count / 1000f)
    else -> count.toString()
}
