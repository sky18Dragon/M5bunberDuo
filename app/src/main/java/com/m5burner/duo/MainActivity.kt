package com.m5burner.duo

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Display
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.m5burner.duo.ui.BottomTactileDeck
import com.m5burner.duo.ui.M5BurnerViewModel
import com.m5burner.duo.ui.TopObservationScreen
import com.m5burner.duo.ui.theme.M5BurnerDuoTheme

class MainActivity : ComponentActivity() {

    private val viewModel: M5BurnerViewModel by viewModels()

    private lateinit var displayManager: DisplayManager
    private var secondaryPresentation: SecondaryDeckPresentation? = null

    private val importBinLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = resolveDisplayName(uri)
            viewModel.importLocalBinaryToVault(uri, fileName)
        }
    }

    private val exportBinLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportSelectedBackupToUri(uri)
        }
    }

    private val exportZipLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportSelectedBackupToUri(uri)
        }
    }

    private fun launchExportSelectedBackup() {
        val selected = viewModel.uiState.value.selectedBackup ?: return
        if (selected.isDataOnlyBundle) {
            exportZipLauncher.launch(selected.suggestedExportFileName)
        } else {
            exportBinLauncher.launch(selected.suggestedExportFileName)
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = syncSecondaryDisplay()
        override fun onDisplayRemoved(displayId: Int) = syncSecondaryDisplay()
        override fun onDisplayChanged(displayId: Int) = syncSecondaryDisplay()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveConsoleMode()
        syncSystemNightMode(resources.configuration)
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, null)

        setContent {
            val state by viewModel.uiState.collectAsState()
            M5BurnerDuoTheme(darkTheme = state.isDarkTheme) {
                LaunchedEffect(state.isBusy, state.isSerialMonitorRunning) {
                    if (state.isBusy || state.isSerialMonitorRunning) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                TopObservationScreen(
                    state = state,
                    onSelectMode = { viewModel.selectMode(it) },
                    onSelectCategory = { viewModel.selectCategory(it) },
                    onSearchChange = { viewModel.updateSearchQuery(it) },
                    onCycleSort = { viewModel.cycleSortMode() },
                    onRefreshCatalog = { viewModel.loadCatalog(forceRefresh = true) },
                    onSelectFirmware = { viewModel.selectFirmware(it) },
                    onSelectBackup = { viewModel.selectBackup(it) },
                    onSaveEditedBackup = { title, note -> viewModel.saveEditedBackup(title, note) },
                    onDismissEditBackup = { viewModel.dismissEditBackupDialog() },
                    onImportLocalBin = { importBinLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                    onSerialFilterChange = { viewModel.updateSerialFilter(it) },
                    onToggleTheme = { viewModel.toggleTheme() },
                    onToggleSingleScreenDeck = { viewModel.toggleSingleScreenDeck() },
                    deckContent = { onDismiss ->
                        BottomTactileDeck(
                            state = state,
                            onSelectVersion = { viewModel.selectVersion(it) },
                            onSelectBaudRate = { viewModel.updateBaudRate(it) },
                            onSelectOffset = { viewModel.updateFlashOffset(it) },
                            onToggleCompression = { viewModel.toggleCompression() },
                            onToggleAdvancedOptions = { viewModel.toggleAdvancedFlashOptions() },
                            onFlashClick = { viewModel.downloadAndFlashSelected() },
                            onDownloadOnlyClick = { viewModel.downloadSelectedOnly() },
                            onEraseClick = { viewModel.eraseConnectedFlash() },
                            onCancelClick = { viewModel.cancelActiveOperation() },
                            onSelectBackupSizeMb = { viewModel.selectBackupSizeMb(it) },
                            onStartBackupClick = { viewModel.startDeviceBackup() },
                            onRestoreBackupClick = { viewModel.restoreSelectedBackup() },
                            onRestoreBackupDataOnlyClick = { viewModel.restoreSelectedBackupDataOnly() },
                            onEditBackupClick = { viewModel.openEditBackupDialog() },
                            onExportBackupClick = { launchExportSelectedBackup() },
                            onDeleteBackupClick = { viewModel.deleteSelectedBackup() },
                            onToggleSerialMonitor = { viewModel.toggleSerialMonitor() },
                            onSelectSerialBaud = { viewModel.selectSerialBaudRate(it) },
                            onToggleSerialPause = { viewModel.toggleSerialPause() },
                            onToggleSerialTimestamps = { viewModel.toggleSerialTimestamps() },
                            onClearSerial = { viewModel.clearSerialEntries() },
                            onSendSerialCommand = { viewModel.sendSerialCommand(it) },
                            onResetDeviceClick = { viewModel.resetConnectedDevice() },
                            onScanUsbClick = { viewModel.scanUsbDevices() },
                            onRequestUsbPermission = { viewModel.requestSelectedUsbPermission() },
                            onToggleRawLogs = { viewModel.toggleRawLogsDrawer() },
                            onClearRawLogs = { viewModel.clearRawLogs() },
                            onDismissOverlay = onDismiss
                        )
                    }
                )
            }
        }

        handleUsbIntent(intent)
        syncSecondaryDisplay()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUsbIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveConsoleMode()
        syncSystemNightMode(resources.configuration)
        viewModel.scanUsbDevices()
        syncSecondaryDisplay()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        syncSystemNightMode(newConfig)
    }

    private fun syncSystemNightMode(config: Configuration) {
        val isNight = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        viewModel.updateSystemDarkTheme(isNight)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveConsoleMode()
        }
    }

    private fun enableImmersiveConsoleMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onDestroy() {
        displayManager.unregisterDisplayListener(displayListener)
        dismissSecondaryDisplay()
        super.onDestroy()
    }

    /**
     * Context-aware physical gamepad mapping for Retroid Pocket Duo.
     * Handled in dispatchKeyEvent so D-Pad never accidentally focuses text inputs or opens the soft keyboard.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val isGamepadKey = when (code) {
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_L2,
            KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_SELECT -> true
            KeyEvent.KEYCODE_BUTTON_B -> viewModel.uiState.value.showSingleScreenDeckOverlay || viewModel.uiState.value.showRawLogsDrawer
            else -> false
        }

        if (isGamepadKey) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (code) {
                    KeyEvent.KEYCODE_BUTTON_L1 -> viewModel.cycleMode(-1)
                    KeyEvent.KEYCODE_BUTTON_R1 -> viewModel.cycleMode(+1)
                    KeyEvent.KEYCODE_BUTTON_L2 -> viewModel.handleTriggerAction(-1)
                    KeyEvent.KEYCODE_BUTTON_R2 -> viewModel.handleTriggerAction(+1)
                    KeyEvent.KEYCODE_DPAD_UP -> viewModel.handleDpadMove(0, -1)
                    KeyEvent.KEYCODE_DPAD_DOWN -> viewModel.handleDpadMove(0, +1)
                    KeyEvent.KEYCODE_DPAD_LEFT -> viewModel.handleDpadMove(-1, 0)
                    KeyEvent.KEYCODE_DPAD_RIGHT -> viewModel.handleDpadMove(+1, 0)
                    KeyEvent.KEYCODE_BUTTON_A -> viewModel.handlePrimaryActionA()
                    KeyEvent.KEYCODE_BUTTON_X -> viewModel.handleSecondaryActionX()
                    KeyEvent.KEYCODE_BUTTON_Y -> viewModel.handleUtilityActionY()
                    KeyEvent.KEYCODE_BUTTON_SELECT -> viewModel.toggleRawLogsDrawer()
                    KeyEvent.KEYCODE_BUTTON_B -> {
                        if (viewModel.uiState.value.showRawLogsDrawer) viewModel.toggleRawLogsDrawer(false)
                        else viewModel.toggleSingleScreenDeck(false)
                    }
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleUsbIntent(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            viewModel.scanUsbDevices()
        }
    }

    private fun syncSecondaryDisplay() {
        val currentDisplayId = display?.displayId ?: Display.DEFAULT_DISPLAY
        val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .filter { it.displayId != currentDisplayId && it.isValid }
        val fallbackSecondary = displayManager.displays
            .filter { it.displayId != currentDisplayId && it.displayId != Display.DEFAULT_DISPLAY && it.isValid }

        val targetDisplay = presentationDisplays.firstOrNull() ?: fallbackSecondary.firstOrNull()

        if (targetDisplay == null) {
            dismissSecondaryDisplay()
            viewModel.updateSecondaryDisplayStatus(false, "单屏模式")
            return
        }

        val mode = targetDisplay.mode
        val info = "双屏联动 · 下屏 ${mode.physicalWidth}×${mode.physicalHeight}"

        if (secondaryPresentation?.display?.displayId == targetDisplay.displayId && secondaryPresentation?.isShowing == true) {
            viewModel.updateSecondaryDisplayStatus(true, info)
            return
        }

        dismissSecondaryDisplay()
        runCatching {
            val presentation = SecondaryDeckPresentation(
                outerContext = this,
                display = targetDisplay,
                viewModel = viewModel,
                onRequestExportBackup = { runOnUiThread { launchExportSelectedBackup() } }
            )
            presentation.show()
            secondaryPresentation = presentation
            viewModel.updateSecondaryDisplayStatus(true, info)
        }.onFailure {
            viewModel.updateSecondaryDisplayStatus(false, "单屏模式")
        }
    }

    private fun dismissSecondaryDisplay() {
        runCatching { secondaryPresentation?.dismiss() }
        secondaryPresentation = null
    }

    private fun resolveDisplayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0)
                if (!name.isNullOrBlank()) return name
            }
        }
        return uri.lastPathSegment ?: "firmware.bin"
    }
}
