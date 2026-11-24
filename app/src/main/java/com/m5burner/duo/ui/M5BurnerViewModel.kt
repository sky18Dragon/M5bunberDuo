package com.m5burner.duo.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.m5burner.duo.data.BackupVaultRepository
import com.m5burner.duo.data.FirmwareBackupRecord
import com.m5burner.duo.data.M5BurnerRepository
import com.m5burner.duo.data.model.CategoryFormatter
import com.m5burner.duo.data.model.CategoryInfo
import com.m5burner.duo.data.model.FirmwareItem
import com.m5burner.duo.data.model.FirmwareSortMode
import com.m5burner.duo.data.model.FirmwareVersion
import com.m5burner.duo.data.model.FlashConfig
import com.m5burner.duo.flasher.EspFlasherEngine
import com.m5burner.duo.flasher.FlashProgress
import com.m5burner.duo.flasher.FlashStage
import com.m5burner.duo.flasher.UsbDeviceUiInfo
import com.m5burner.duo.flasher.UsbSerialController
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The 3 focused, human-centered user scenarios.
 */
enum class AppMode(val title: String, val subtitle: String) {
    DISCOVER("固件广场", "发现并烧录 M5Burner 社区固件"),
    VAULT("固件备份", "读取设备镜像，随时一键刷回"),
    MONITOR("串口诊断", "实时观察设备输出，快速排查问题")
}

data class SerialEntry(
    val timestamp: String,
    val text: String,
    val isTx: Boolean = false
)

data class M5BurnerUiState(
    val mode: AppMode = AppMode.DISCOVER,

    // Scenario 1: Discover (M5Burner Community)
    val isLoadingCatalog: Boolean = true,
    val catalogError: String? = null,
    val allFirmwares: List<FirmwareItem> = emptyList(),
    val filteredFirmwares: List<FirmwareItem> = emptyList(),
    val categories: List<CategoryInfo> = emptyList(),
    val selectedCategory: String = "all",
    val searchQuery: String = "",
    val sortMode: FirmwareSortMode = FirmwareSortMode.POPULAR,
    val selectedFirmware: FirmwareItem? = null,
    val selectedVersion: FirmwareVersion? = null,
    val downloadedFiles: Set<String> = emptySet(),
    val flashConfig: FlashConfig = FlashConfig(),
    val showAdvancedFlashOptions: Boolean = false,

    // Scenario 2: Vault (Device Backup & Restore)
    val backups: List<FirmwareBackupRecord> = emptyList(),
    val selectedBackup: FirmwareBackupRecord? = null,
    val backupSizeMb: Int = 0, // 0 = AUTO full-chip, -1 = Data-Only, or 4/8/16 MB
    val editingBackupRecord: FirmwareBackupRecord? = null,

    // Scenario 3: Serial Monitor (Troubleshooting)
    val serialEntries: List<SerialEntry> = emptyList(),
    val isSerialMonitorRunning: Boolean = false,
    val isSerialPaused: Boolean = false,
    val serialBaudRate: Int = 115200,
    val serialFilterQuery: String = "",
    val serialAutoScroll: Boolean = true,
    val showSerialTimestamps: Boolean = true,

    // Shared Hardware & Dual-Screen State
    val usbDevices: List<UsbDeviceUiInfo> = emptyList(),
    val selectedUsbDevice: UsbDeviceUiInfo? = null,
    val flashProgress: FlashProgress = FlashProgress(),
    val isBusy: Boolean = false,
    val activeTaskLabel: String = "",
    val rawLogs: List<String> = emptyList(),
    val showRawLogsDrawer: Boolean = false,
    val hasSecondaryDisplay: Boolean = false,
    val secondaryDisplayInfo: String = "单屏模式",
    val showSingleScreenDeckOverlay: Boolean = false,
    val systemDarkTheme: Boolean = false,
    val themeOverride: Boolean? = null // null = follow system dark mode switch automatically
) {
    val isDarkTheme: Boolean get() = themeOverride ?: systemDarkTheme
    companion object {
        const val BACKUP_MODE_DATA_ONLY = -1
        const val BACKUP_MODE_AUTO_FULL = 0
        val BACKUP_SIZES_MB = listOf(BACKUP_MODE_AUTO_FULL, BACKUP_MODE_DATA_ONLY, 4, 8, 16)
        val SERIAL_BAUD_RATES = listOf(9600, 74880, 115200, 921600)
    }

    val filteredSerialEntries: List<SerialEntry>
        get() {
            val q = serialFilterQuery.trim().lowercase()
            return if (q.isEmpty()) serialEntries
            else serialEntries.filter { it.text.lowercase().contains(q) }
        }
}

class M5BurnerViewModel(application: Application) : AndroidViewModel(application) {

    private val catalogRepo = M5BurnerRepository(application)
    private val vaultRepo = BackupVaultRepository(application)
    val usbController = UsbSerialController(application)
    private val flasherEngine = EspFlasherEngine(application, usbController)

    private val _uiState = MutableStateFlow(M5BurnerUiState())
    val uiState: StateFlow<M5BurnerUiState> = _uiState.asStateFlow()

    private var activeOperationJob: Job? = null
    private var serialMonitorJob: Job? = null
    private var serialTxChannel = Channel<ByteArray>(Channel.BUFFERED)
    private var pendingUsbAction: (() -> Unit)? = null

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    init {
        usbController.onUsbEventLog = { msg -> appendLog("[USB] $msg") }
        usbController.onPermissionResult = { _, granted ->
            if (granted) {
                val action = pendingUsbAction
                pendingUsbAction = null
                action?.invoke()
            } else {
                pendingUsbAction = null
            }
        }
        usbController.register()

        viewModelScope.launch {
            usbController.devices.collect { list ->
                _uiState.update { it.copy(usbDevices = list) }
            }
        }
        viewModelScope.launch {
            usbController.selectedDevice.collect { dev ->
                _uiState.update { current ->
                    val hint = when {
                        current.isBusy -> current.flashProgress.message
                        dev == null -> "插入 M5Stack USB-C 线即可就绪"
                        !dev.hasPermission -> "已发现 ${dev.driverLabel}，点此授权 USB"
                        else -> "已就绪 · ${dev.driverLabel}"
                    }
                    current.copy(
                        selectedUsbDevice = dev,
                        flashProgress = if (current.flashProgress.stage == FlashStage.IDLE) {
                            current.flashProgress.copy(message = hint)
                        } else current.flashProgress
                    )
                }
            }
        }

        appendLog("[SYS] M5Burner Duo 2.0 已就绪")
        loadBackups()
        loadCatalog(forceRefresh = false)
    }

    override fun onCleared() {
        super.onCleared()
        stopSerialMonitor()
        activeOperationJob?.cancel()
        usbController.unregister()
    }

    // --- Mode & Dual-Screen Navigation ---

    fun selectMode(mode: AppMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun cycleMode(delta: Int) {
        val modes = AppMode.entries
        val idx = modes.indexOf(_uiState.value.mode)
        val next = modes[(idx + delta + modes.size) % modes.size]
        selectMode(next)
    }

    fun updateSecondaryDisplayStatus(available: Boolean, info: String) {
        val current = _uiState.value
        val changed = current.hasSecondaryDisplay != available || current.secondaryDisplayInfo != info
        _uiState.update {
            it.copy(
                hasSecondaryDisplay = available,
                secondaryDisplayInfo = info,
                showSingleScreenDeckOverlay = if (available) false else it.showSingleScreenDeckOverlay
            )
        }
        if (changed) {
            appendLog("[DUO] $info")
        }
    }

    fun toggleSingleScreenDeck(show: Boolean? = null) {
        _uiState.update { it.copy(showSingleScreenDeckOverlay = show ?: !it.showSingleScreenDeckOverlay) }
    }

    fun toggleRawLogsDrawer(show: Boolean? = null) {
        _uiState.update { it.copy(showRawLogsDrawer = show ?: !it.showRawLogsDrawer) }
    }

    fun toggleAdvancedFlashOptions() {
        _uiState.update { it.copy(showAdvancedFlashOptions = !it.showAdvancedFlashOptions) }
    }

    fun updateSystemDarkTheme(isSystemDark: Boolean) {
        _uiState.update { current ->
            if (current.systemDarkTheme != isSystemDark) {
                current.copy(systemDarkTheme = isSystemDark, themeOverride = null)
            } else {
                current
            }
        }
    }

    fun toggleTheme() {
        _uiState.update { current ->
            // Toggle opposite of current effective theme; if it matches system again, clear override to keep following system
            val desired = !current.isDarkTheme
            val nextOverride = if (desired == current.systemDarkTheme) null else desired
            current.copy(themeOverride = nextOverride)
        }
    }

    // --- Scenario 1: Online Catalog ---

    fun loadCatalog(forceRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCatalog = true, catalogError = null) }

            if (!forceRefresh && _uiState.value.allFirmwares.isEmpty()) {
                val cached = catalogRepo.loadCachedCatalog()
                if (cached.isNotEmpty()) {
                    applyCatalog(cached, fromCache = true)
                }
            }

            appendLog("[API] 正在同步 M5Burner 固件目录...")
            catalogRepo.fetchOnlineCatalog()
                .onSuccess { items ->
                    appendLog("[API] 已同步 ${items.size} 款社区固件")
                    applyCatalog(items, fromCache = false)
                }
                .onFailure { err ->
                    val hasData = _uiState.value.allFirmwares.isNotEmpty()
                    appendLog("[API] 同步失败: ${err.message}")
                    _uiState.update {
                        it.copy(
                            isLoadingCatalog = false,
                            catalogError = if (hasData) null else "网络加载失败，请检查 Wi-Fi 后重试"
                        )
                    }
                }
        }
    }

    private fun applyCatalog(items: List<FirmwareItem>, fromCache: Boolean) {
        val counts = items.groupingBy { it.normalizedCategory }.eachCount()
        val categories = buildList {
            add(CategoryInfo("all", "全部设备", items.size))
            counts.entries
                .filter { it.key.isNotBlank() }
                .sortedByDescending { it.value }
                .forEach { (key, count) ->
                    add(CategoryInfo(key, CategoryFormatter.formatLabel(key), count))
                }
        }

        val downloaded = items.asSequence()
            .flatMap { it.publishedVersions.asSequence() }
            .filter { catalogRepo.isFirmwareDownloaded(it) }
            .map { it.file }
            .toSet()

        _uiState.update { current ->
            val filtered = filterAndSort(items, current.selectedCategory, current.searchQuery, current.sortMode)
            val keptSelected = current.selectedFirmware?.let { sel ->
                filtered.firstOrNull { it.stableId == sel.stableId }
            } ?: filtered.firstOrNull()
            val keptVersion = keptSelected?.publishedVersions?.firstOrNull {
                it.file == current.selectedVersion?.file
            } ?: keptSelected?.latestVersion

            current.copy(
                isLoadingCatalog = fromCache && items.isEmpty(),
                catalogError = null,
                allFirmwares = items,
                filteredFirmwares = filtered,
                categories = categories,
                selectedFirmware = keptSelected,
                selectedVersion = keptVersion,
                downloadedFiles = downloaded
            )
        }
    }

    fun selectCategory(categoryKey: String) {
        _uiState.update { current ->
            val filtered = filterAndSort(current.allFirmwares, categoryKey, current.searchQuery, current.sortMode)
            val first = filtered.firstOrNull()
            current.copy(
                selectedCategory = categoryKey,
                filteredFirmwares = filtered,
                selectedFirmware = first,
                selectedVersion = first?.latestVersion
            )
        }
    }

    fun cycleCategory(delta: Int) {
        val cats = _uiState.value.categories
        if (cats.isEmpty()) return
        val idx = cats.indexOfFirst { it.key == _uiState.value.selectedCategory }.coerceAtLeast(0)
        selectCategory(cats[(idx + delta + cats.size) % cats.size].key)
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { current ->
            val filtered = filterAndSort(current.allFirmwares, current.selectedCategory, query, current.sortMode)
            val selected = filtered.firstOrNull { it.stableId == current.selectedFirmware?.stableId } ?: filtered.firstOrNull()
            current.copy(
                searchQuery = query,
                filteredFirmwares = filtered,
                selectedFirmware = selected,
                selectedVersion = selected?.latestVersion
            )
        }
    }

    fun cycleSortMode() {
        val modes = FirmwareSortMode.entries
        val next = modes[(modes.indexOf(_uiState.value.sortMode) + 1) % modes.size]
        _uiState.update { current ->
            current.copy(
                sortMode = next,
                filteredFirmwares = filterAndSort(current.allFirmwares, current.selectedCategory, current.searchQuery, next)
            )
        }
    }

    fun selectFirmware(item: FirmwareItem) {
        _uiState.update { it.copy(selectedFirmware = item, selectedVersion = item.latestVersion) }
    }

    fun selectVersion(version: FirmwareVersion) {
        _uiState.update { it.copy(selectedVersion = version) }
    }

    fun cycleVersion(delta: Int) {
        val versions = _uiState.value.selectedFirmware?.publishedVersions ?: return
        if (versions.isEmpty()) return
        val idx = versions.indexOfFirst { it.file == _uiState.value.selectedVersion?.file }.coerceAtLeast(0)
        selectVersion(versions[(idx + delta + versions.size) % versions.size])
    }

    fun updateBaudRate(baud: Int) {
        _uiState.update { it.copy(flashConfig = it.flashConfig.copy(baudRate = baud)) }
    }

    fun updateFlashOffset(offset: Int) {
        _uiState.update { it.copy(flashConfig = it.flashConfig.copy(flashAddress = offset)) }
    }

    fun toggleCompression() {
        _uiState.update { it.copy(flashConfig = it.flashConfig.copy(useCompression = !it.flashConfig.useCompression)) }
    }

    // --- Scenario 2: Firmware Vault (Read Backup & One-Tap Restore) ---

    fun loadBackups() {
        viewModelScope.launch {
            val list = vaultRepo.loadRecords()
            _uiState.update { current ->
                val kept = current.selectedBackup?.let { sel -> list.firstOrNull { it.id == sel.id } } ?: list.firstOrNull()
                current.copy(backups = list, selectedBackup = kept)
            }
        }
    }

    fun selectBackup(record: FirmwareBackupRecord) {
        _uiState.update { it.copy(selectedBackup = record) }
    }

    fun selectBackupSizeMb(sizeMb: Int) {
        _uiState.update { it.copy(backupSizeMb = sizeMb) }
    }

    fun cycleBackupSize(delta: Int) {
        val sizes = M5BurnerUiState.BACKUP_SIZES_MB
        val idx = sizes.indexOf(_uiState.value.backupSizeMb).coerceAtLeast(0)
        selectBackupSizeMb(sizes[(idx + delta + sizes.size) % sizes.size])
    }

    fun startDeviceBackup(customTitle: String = "") {
        if (_uiState.value.isBusy) return
        val usb = ensureUsbReady { startDeviceBackup(customTitle) } ?: return

        stopSerialMonitor()
        val modeVal = _uiState.value.backupSizeMb
        val isDataOnly = modeVal == M5BurnerUiState.BACKUP_MODE_DATA_ONLY
        val sizeBytes = if (modeVal <= 0) 0 else modeVal * 1024 * 1024

        activeOperationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    activeTaskLabel = when {
                        isDataOnly -> "极速备份存储数据（NVS/FS）"
                        modeVal == 0 -> "自动全片备份（固件+数据）"
                        else -> "读取设备固件 (${modeVal}MB)"
                    },
                    showSingleScreenDeckOverlay = !it.hasSecondaryDisplay
                )
            }

            val targetFile = vaultRepo.createTargetBackupFile("esp", isDataOnly = isDataOnly)
            val result = if (isDataOnly) {
                flasherEngine.readDataPartitionsBackup(
                    deviceInfo = usb,
                    outputZipFile = targetFile,
                    baudRate = _uiState.value.flashConfig.baudRate,
                    onProgress = { fp -> _uiState.update { it.copy(flashProgress = fp) } },
                    onLog = { appendLog(it) }
                )
            } else {
                flasherEngine.readFlashBackup(
                    deviceInfo = usb,
                    outputFile = targetFile,
                    offset = 0x0,
                    lengthBytes = sizeBytes,
                    baudRate = _uiState.value.flashConfig.baudRate,
                    onProgress = { fp -> _uiState.update { it.copy(flashProgress = fp) } },
                    onLog = { appendLog(it) }
                )
            }

            result.onSuccess { backupRes ->
                val actualMb = (backupRes.sizeBytes / (1024L * 1024L)).coerceAtLeast(1L)
                val autoTitle = customTitle.ifBlank {
                    if (isDataOnly) "${backupRes.chip.displayName} 存储数据备份"
                    else "${backupRes.chip.displayName} ${actualMb}MB 完整备份"
                }
                val saved = vaultRepo.registerDeviceBackup(
                    binFile = backupRes.outputFile,
                    chipName = backupRes.chip.displayName,
                    deviceLabel = usb.driverLabel,
                    title = autoTitle,
                    note = if (isDataOnly) "仅数据分区 (.zip) · 不含固件"
                        else if (backupRes.md5Verified) "全片含数据 · MD5 已校验"
                        else "设备全片备份",
                    flashOffset = 0x0,
                    precomputedMd5 = backupRes.md5Hex,
                    parsedPartitions = backupRes.partitions,
                    sourceType = if (isDataOnly) com.m5burner.duo.data.BackupSourceType.DATA_ONLY
                    else com.m5burner.duo.data.BackupSourceType.DEVICE_BACKUP
                )
                val all = vaultRepo.loadRecords()
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        backups = all,
                        selectedBackup = saved
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun restoreSelectedBackup() {
        val record = _uiState.value.selectedBackup ?: return
        if (_uiState.value.isBusy) return
        if (record.isDataOnlyBundle) {
            restoreSelectedBackupDataOnly()
            return
        }
        val usb = ensureUsbReady { restoreSelectedBackup() } ?: return
        val file = vaultRepo.resolveBinaryFile(record)
        if (!file.exists()) {
            appendLog("[ERROR] 备份文件不存在，请刷新列表")
            loadBackups()
            return
        }

        stopSerialMonitor()
        activeOperationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    activeTaskLabel = "完整刷回: ${record.title}",
                    showSingleScreenDeckOverlay = !it.hasSecondaryDisplay
                )
            }
            val config = _uiState.value.flashConfig.copy(flashAddress = record.flashOffset)
            flasherEngine.flashFirmwareFile(
                deviceInfo = usb,
                firmwareFile = file,
                config = config,
                onProgress = { fp -> _uiState.update { it.copy(flashProgress = fp) } },
                onLog = { appendLog(it) }
            )
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    fun restoreSelectedBackupDataOnly() {
        val record = _uiState.value.selectedBackup ?: return
        if (_uiState.value.isBusy) return
        val usb = ensureUsbReady { restoreSelectedBackupDataOnly() } ?: return
        val file = vaultRepo.resolveBinaryFile(record)
        if (!file.exists()) {
            loadBackups()
            return
        }

        if (!record.isDataOnlyBundle && record.dataPartitions.isEmpty()) {
            appendLog("[WARN] 该备份不含独立的用户数据分区，请使用「完整刷回」")
            _uiState.update {
                it.copy(flashProgress = FlashProgress(FlashStage.ERROR, 0f, message = "该备份未检测到独立数据分区"))
            }
            return
        }

        stopSerialMonitor()
        activeOperationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    activeTaskLabel = "恢复数据: ${record.title}",
                    showSingleScreenDeckOverlay = !it.hasSecondaryDisplay
                )
            }
            if (record.isDataOnlyBundle) {
                flasherEngine.restoreDataBundleZip(
                    deviceInfo = usb,
                    bundleZipFile = file,
                    config = _uiState.value.flashConfig,
                    onProgress = { fp -> _uiState.update { it.copy(flashProgress = fp) } },
                    onLog = { appendLog(it) }
                )
            } else {
                flasherEngine.restoreDataPartitionsOnly(
                    deviceInfo = usb,
                    backupFile = file,
                    dataPartitions = record.dataPartitions,
                    config = _uiState.value.flashConfig,
                    onProgress = { fp -> _uiState.update { it.copy(flashProgress = fp) } },
                    onLog = { appendLog(it) }
                )
            }
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    fun exportSelectedBackupToUri(destUri: Uri) {
        val record = _uiState.value.selectedBackup ?: return
        viewModelScope.launch {
            vaultRepo.exportBackupToUri(record, destUri)
                .onSuccess { bytes ->
                    val mb = "%.2f MB".format(bytes / (1024f * 1024f))
                    appendLog("[VAULT] 已导出备份: ${record.title} ($mb)")
                    _uiState.update {
                        it.copy(
                            flashProgress = FlashProgress(
                                FlashStage.DONE, 1f,
                                message = "已导出「${record.title}」($mb) 到本地/TF卡"
                            )
                        )
                    }
                }
                .onFailure { err ->
                    appendLog("[ERROR] 导出失败: ${err.message}")
                    _uiState.update {
                        it.copy(flashProgress = FlashProgress(FlashStage.ERROR, 0f, message = "导出失败: ${err.message}"))
                    }
                }
        }
    }

    fun openEditBackupDialog() {
        val sel = _uiState.value.selectedBackup ?: return
        _uiState.update { it.copy(editingBackupRecord = sel) }
    }

    fun dismissEditBackupDialog() {
        _uiState.update { it.copy(editingBackupRecord = null) }
    }

    fun saveEditedBackup(newTitle: String, newNote: String) {
        val target = _uiState.value.editingBackupRecord ?: return
        viewModelScope.launch {
            val updated = vaultRepo.updateRecordMeta(target.id, newTitle, newNote)
            appendLog("[VAULT] 已更新备份备注: $newTitle")
            _uiState.update {
                it.copy(
                    backups = updated,
                    selectedBackup = updated.firstOrNull { r -> r.id == target.id },
                    editingBackupRecord = null
                )
            }
        }
    }

    fun importLocalBinaryToVault(uri: Uri, displayName: String) {
        viewModelScope.launch {
            vaultRepo.importLocalBinary(uri, displayName, _uiState.value.flashConfig.flashAddress)
                .onSuccess { saved ->
                    appendLog("[VAULT] 已导入本地镜像: ${saved.title} (${saved.formattedSize})")
                    val all = vaultRepo.loadRecords()
                    _uiState.update {
                        it.copy(
                            mode = AppMode.VAULT,
                            backups = all,
                            selectedBackup = saved
                        )
                    }
                }
                .onFailure { err ->
                    appendLog("[ERROR] 导入失败: ${err.message}")
                }
        }
    }

    fun deleteSelectedBackup() {
        val target = _uiState.value.selectedBackup ?: return
        viewModelScope.launch {
            val remaining = vaultRepo.deleteRecord(target.id)
            appendLog("[VAULT] 已删除备份: ${target.title}")
            _uiState.update {
                it.copy(
                    backups = remaining,
                    selectedBackup = remaining.firstOrNull()
                )
            }
        }
    }

    // --- Scenario 1 Actions: Download & Flash Community Firmware ---

    fun downloadAndFlashSelected() {
        val fw = _uiState.value.selectedFirmware ?: return
        val ver = _uiState.value.selectedVersion ?: return
        if (_uiState.value.isBusy) return
        val usb = ensureUsbReady { downloadAndFlashSelected() } ?: return

        stopSerialMonitor()
        activeOperationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    activeTaskLabel = "${fw.name} (${ver.version})",
                    showSingleScreenDeckOverlay = !it.hasSecondaryDisplay
                )
            }

            val cachedAlready = catalogRepo.isFirmwareDownloaded(ver)
            appendLog(if (cachedAlready) "[DL] 复用本地缓存 ${ver.file}" else "[DL] 正在下载 ${fw.name} ${ver.version}...")

            val dlResult = catalogRepo.downloadFirmware(ver) { dl, total ->
                val pct = if (total > 0) dl.toFloat() / total.toFloat() else 0f
                _uiState.update {
                    it.copy(
                        flashProgress = FlashProgress(
                            stage = FlashStage.DOWNLOADING,
                            progress = pct * 0.25f,
                            bytesProcessed = dl,
                            totalBytes = total,
                            message = "正在下载 ${ver.version} · ${(pct * 100).toInt()}%"
                        )
                    )
                }
            }

            val binFile = dlResult.getOrElse { err ->
                appendLog("[ERROR] 下载失败: ${err.message}")
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        flashProgress = FlashProgress(FlashStage.ERROR, 0f, message = "下载失败: ${err.message}")
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(downloadedFiles = it.downloadedFiles + ver.file) }

            val freshUsb = usbController.refreshDevices().firstOrNull { it.deviceId == usb.deviceId } ?: usb
            flasherEngine.flashFirmwareFile(
                deviceInfo = freshUsb,
                firmwareFile = binFile,
                config = _uiState.value.flashConfig,
                onProgress = { fp ->
                    val scaled = if (fp.stage == FlashStage.DONE) 1f else 0.25f + fp.progress * 0.75f
                    _uiState.update { it.copy(flashProgress = fp.copy(progress = scaled)) }
                },
                onLog = { appendLog(it) }
            )
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    fun downloadSelectedOnly() {
        val fw = _uiState.value.selectedFirmware ?: return
        val ver = _uiState.value.selectedVersion ?: return
        if (_uiState.value.isBusy) return

        activeOperationJob = viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, activeTaskLabel = "缓存 ${fw.name}") }
            catalogRepo.downloadFirmware(ver) { dl, total ->
                val pct = if (total > 0) dl.toFloat() / total.toFloat() else 0f
                _uiState.update {
                    it.copy(
                        flashProgress = FlashProgress(
                            stage = FlashStage.DOWNLOADING,
                            progress = pct,
                            bytesProcessed = dl,
                            totalBytes = total,
                            message = "正在缓存 ${ver.version} · ${(pct * 100).toInt()}%"
                        )
                    )
                }
            }.onSuccess { file ->
                appendLog("[DL] 固件已缓存 (${file.length() / 1024} KB)")
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        downloadedFiles = it.downloadedFiles + ver.file,
                        flashProgress = FlashProgress(FlashStage.DONE, 1f, message = "已缓存 ${fw.name} (${ver.version})")
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        flashProgress = FlashProgress(FlashStage.ERROR, 0f, message = "缓存失败: ${err.message}")
                    )
                }
            }
        }
    }

    // --- Scenario 3: Serial Monitor ---

    fun toggleSerialMonitor() {
        if (_uiState.value.isSerialMonitorRunning) {
            stopSerialMonitor()
            return
        }
        if (_uiState.value.isBusy) return
        val usb = ensureUsbReady { toggleSerialMonitor() } ?: return

        serialTxChannel = Channel(Channel.BUFFERED)
        val baud = _uiState.value.serialBaudRate
        _uiState.update { it.copy(isSerialMonitorRunning = true, isSerialPaused = false) }

        serialMonitorJob = viewModelScope.launch {
            try {
                flasherEngine.runSerialMonitor(usb, baud, serialTxChannel) { line ->
                    if (!_uiState.value.isSerialPaused) {
                        appendSerialEntry(line, isTx = false)
                    }
                }
            } finally {
                _uiState.update { it.copy(isSerialMonitorRunning = false) }
            }
        }
    }

    fun stopSerialMonitor() {
        serialMonitorJob?.cancel()
        serialMonitorJob = null
        _uiState.update { it.copy(isSerialMonitorRunning = false) }
    }

    fun selectSerialBaudRate(baud: Int) {
        val wasRunning = _uiState.value.isSerialMonitorRunning
        _uiState.update { it.copy(serialBaudRate = baud) }
        if (wasRunning) {
            stopSerialMonitor()
            toggleSerialMonitor()
        }
    }

    fun cycleSerialBaudRate(delta: Int) {
        val rates = M5BurnerUiState.SERIAL_BAUD_RATES
        val idx = rates.indexOf(_uiState.value.serialBaudRate).coerceAtLeast(0)
        selectSerialBaudRate(rates[(idx + delta + rates.size) % rates.size])
    }

    fun toggleSerialPause() {
        _uiState.update { it.copy(isSerialPaused = !it.isSerialPaused) }
    }

    fun toggleSerialTimestamps() {
        _uiState.update { it.copy(showSerialTimestamps = !it.showSerialTimestamps) }
    }

    fun updateSerialFilter(query: String) {
        _uiState.update { it.copy(serialFilterQuery = query) }
    }

    fun sendSerialCommand(command: String) {
        if (!_uiState.value.isSerialMonitorRunning) return
        val payload = (command + "\r\n").toByteArray(Charsets.UTF_8)
        serialTxChannel.trySend(payload)
        appendSerialEntry("> $command", isTx = true)
    }

    fun clearSerialEntries() {
        _uiState.update { it.copy(serialEntries = emptyList()) }
    }

    // --- Shared Hardware & Gamepad Actions ---

    fun scanUsbDevices() {
        val list = usbController.refreshDevices()
        appendLog("[USB] 扫描到 ${list.size} 个串口设备")
        val first = list.firstOrNull()
        if (first != null && !first.hasPermission) {
            usbController.requestPermission(first)
        }
    }

    fun requestSelectedUsbPermission() {
        val dev = _uiState.value.selectedUsbDevice ?: return
        usbController.requestPermission(dev)
    }

    fun eraseConnectedFlash() {
        if (_uiState.value.isBusy) return
        val usb = ensureUsbReady { eraseConnectedFlash() } ?: return
        stopSerialMonitor()
        activeOperationJob = viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, activeTaskLabel = "擦除设备闪存 (4MB)") }
            flasherEngine.eraseFlashRegion(
                deviceInfo = usb,
                sizeBytes = 4 * 1024 * 1024,
                onProgress = { fp -> _uiState.update { it.copy(flashProgress = fp) } },
                onLog = { appendLog(it) }
            )
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    fun resetConnectedDevice() {
        if (_uiState.value.isBusy) return
        val usb = ensureUsbReady { resetConnectedDevice() } ?: return
        val resumeMonitor = _uiState.value.isSerialMonitorRunning
        stopSerialMonitor()
        viewModelScope.launch {
            flasherEngine.resetDeviceOnly(usb) { appendLog(it) }
            if (resumeMonitor) {
                toggleSerialMonitor()
            }
        }
    }

    fun cancelActiveOperation() {
        activeOperationJob?.cancel()
        activeOperationJob = null
        _uiState.update {
            it.copy(
                isBusy = false,
                flashProgress = FlashProgress(FlashStage.IDLE, 0f, message = "操作已取消")
            )
        }
    }

    fun clearRawLogs() {
        _uiState.update { it.copy(rawLogs = emptyList()) }
    }

    // --- Context-Aware Gamepad Handlers ---

    fun handleDpadMove(dx: Int, dy: Int) {
        when (_uiState.value.mode) {
            AppMode.DISCOVER -> {
                val list = _uiState.value.filteredFirmwares
                if (list.isEmpty()) return
                val idx = list.indexOfFirst { it.stableId == _uiState.value.selectedFirmware?.stableId }
                val step = dx + dy * 3
                val next = if (idx < 0) 0 else (idx + step).coerceIn(0, list.lastIndex)
                selectFirmware(list[next])
            }
            AppMode.VAULT -> {
                val list = _uiState.value.backups
                if (list.isEmpty()) return
                val idx = list.indexOfFirst { it.id == _uiState.value.selectedBackup?.id }
                val step = if (dy != 0) dy else dx
                val next = if (idx < 0) 0 else (idx + step).coerceIn(0, list.lastIndex)
                selectBackup(list[next])
            }
            AppMode.MONITOR -> {
                if (dx != 0) cycleSerialBaudRate(dx)
            }
        }
    }

    fun handlePrimaryActionA() {
        if (_uiState.value.isBusy) return
        when (_uiState.value.mode) {
            AppMode.DISCOVER -> downloadAndFlashSelected()
            AppMode.VAULT -> {
                if (_uiState.value.selectedBackup != null) restoreSelectedBackup()
                else startDeviceBackup()
            }
            AppMode.MONITOR -> toggleSerialMonitor()
        }
    }

    fun handleSecondaryActionX() {
        if (_uiState.value.isBusy) return
        when (_uiState.value.mode) {
            AppMode.DISCOVER -> cycleVersion(+1)
            AppMode.VAULT -> startDeviceBackup()
            AppMode.MONITOR -> resetConnectedDevice()
        }
    }

    fun handleUtilityActionY() {
        when (_uiState.value.mode) {
            AppMode.MONITOR -> clearSerialEntries()
            else -> scanUsbDevices()
        }
    }

    fun handleTriggerAction(delta: Int) {
        when (_uiState.value.mode) {
            AppMode.DISCOVER -> cycleCategory(delta)
            AppMode.VAULT -> cycleBackupSize(delta)
            AppMode.MONITOR -> cycleSerialBaudRate(delta)
        }
    }

    private fun ensureUsbReady(onReadyAfterPermission: () -> Unit): UsbDeviceUiInfo? {
        val usb = usbController.refreshDevices().firstOrNull { it.deviceId == _uiState.value.selectedUsbDevice?.deviceId }
            ?: usbController.refreshDevices().firstOrNull()
        if (usb == null) {
            appendLog("[WARN] 未检测到 USB-C M5Stack 设备，请插入 OTG 数据线")
            _uiState.update {
                it.copy(
                    flashProgress = FlashProgress(FlashStage.ERROR, 0f, message = "请插入 M5Stack USB-C 数据线")
                )
            }
            return null
        }
        if (!usb.hasPermission) {
            appendLog("[USB] 正在请求授权 ${usb.friendlyTitle}...")
            pendingUsbAction = onReadyAfterPermission
            usbController.requestPermission(usb)
            return null
        }
        return usb
    }

    private fun appendLog(message: String) {
        val ts = timeFormat.format(Date())
        _uiState.update { current ->
            current.copy(rawLogs = (current.rawLogs + "[$ts] $message").takeLast(200))
        }
    }

    private fun appendSerialEntry(line: String, isTx: Boolean) {
        val ts = timeFormat.format(Date())
        _uiState.update { current ->
            current.copy(serialEntries = (current.serialEntries + SerialEntry(ts, line, isTx)).takeLast(600))
        }
    }

    private fun filterAndSort(
        items: List<FirmwareItem>,
        category: String,
        query: String,
        sort: FirmwareSortMode
    ): List<FirmwareItem> {
        val q = query.trim().lowercase()
        val filtered = items.filter { item ->
            val catMatch = category == "all" || item.normalizedCategory == category
            val queryMatch = q.isEmpty() ||
                item.name.lowercase().contains(q) ||
                item.description.lowercase().contains(q) ||
                item.author.lowercase().contains(q) ||
                item.normalizedCategory.contains(q)
            catMatch && queryMatch
        }
        return when (sort) {
            FirmwareSortMode.POPULAR -> filtered.sortedByDescending { it.downloadCount }
            FirmwareSortMode.RECENT -> filtered.sortedByDescending { it.latestVersion?.publishedAt.orEmpty() }
            FirmwareSortMode.NAME -> filtered.sortedBy { it.name.lowercase() }
        }
    }
}
