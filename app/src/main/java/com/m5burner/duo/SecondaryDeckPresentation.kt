package com.m5burner.duo

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.m5burner.duo.ui.BottomTactileDeck
import com.m5burner.duo.ui.M5BurnerViewModel
import com.m5burner.duo.ui.theme.M5BurnerDuoTheme

/**
 * Native Android Presentation targeting the Retroid Pocket Duo's secondary
 * 4.5-inch 1280x960 (4:3) 120Hz AMOLED lower screen ("Tactile Control Deck").
 */
class SecondaryDeckPresentation(
    outerContext: Context,
    display: Display,
    private val viewModel: M5BurnerViewModel,
    private val onRequestExportBackup: () -> Unit
) : Presentation(outerContext, display),
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@SecondaryDeckPresentation)
            setViewTreeSavedStateRegistryOwner(this@SecondaryDeckPresentation)
            setViewTreeViewModelStoreOwner(this@SecondaryDeckPresentation)
            setContent {
                val state by viewModel.uiState.collectAsState()
                M5BurnerDuoTheme(darkTheme = state.isDarkTheme) {
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
                        onExportBackupClick = onRequestExportBackup,
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
                        onClearRawLogs = { viewModel.clearRawLogs() }
                    )
                }
            }
        }

        window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
        }

        setContentView(composeView)
    }

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onStop()
    }

    override fun dismiss() {
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        store.clear()
        super.dismiss()
    }
}
