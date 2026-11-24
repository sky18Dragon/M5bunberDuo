package com.m5burner.duo.flasher

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.Ch34xSerialDriver
import com.hoho.android.usbserial.driver.Cp21xxSerialDriver
import com.hoho.android.usbserial.driver.FtdiSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UsbDeviceUiInfo(
    val deviceId: Int,
    val deviceName: String,
    val productName: String,
    val vendorId: Int,
    val productId: Int,
    val driverLabel: String,
    val isNativeEspUsb: Boolean,
    val hasPermission: Boolean,
    val usbDevice: UsbDevice,
    val driver: UsbSerialDriver
) {
    val vidPidHex: String
        get() = "%04X:%04X".format(vendorId, productId)

    val friendlyTitle: String
        get() = productName.ifBlank { driverLabel }
}

class UsbSerialController(private val context: Context) {

    companion object {
        const val ACTION_USB_PERMISSION = "com.m5burner.duo.USB_PERMISSION"
        const val ESPRESSIF_VID = 0x303A
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val customProber: UsbSerialProber by lazy {
        val table = ProbeTable()
        // Espressif Native USB-Serial/JTAG & CDC (ESP32-S2/S3/C3/C6/H2/P4)
        table.addProduct(0x303A, 0x1001, CdcAcmSerialDriver::class.java)
        table.addProduct(0x303A, 0x0002, CdcAcmSerialDriver::class.java)
        table.addProduct(0x303A, 0x0009, CdcAcmSerialDriver::class.java)
        table.addProduct(0x303A, 0x80D1, CdcAcmSerialDriver::class.java)
        table.addProduct(0x303A, 0x80D4, CdcAcmSerialDriver::class.java)
        // WCH CH9102F / CH340 / CH341 used on M5Stack Core2 v1.1, StickC Plus2, Atom, Paper
        table.addProduct(0x1A86, 0x55D4, Ch34xSerialDriver::class.java)
        table.addProduct(0x1A86, 0x7523, Ch34xSerialDriver::class.java)
        table.addProduct(0x1A86, 0x5523, Ch34xSerialDriver::class.java)
        // Silicon Labs CP210x
        table.addProduct(0x10C4, 0xEA60, Cp21xxSerialDriver::class.java)
        table.addProduct(0x10C4, 0xEA70, Cp21xxSerialDriver::class.java)
        // FTDI
        table.addProduct(0x0403, 0x6001, FtdiSerialDriver::class.java)
        table.addProduct(0x0403, 0x6010, FtdiSerialDriver::class.java)
        table.addProduct(0x0403, 0x6014, FtdiSerialDriver::class.java)
        table.addProduct(0x0403, 0x6015, FtdiSerialDriver::class.java)
        UsbSerialProber(table)
    }

    private val _devices = MutableStateFlow<List<UsbDeviceUiInfo>>(emptyList())
    val devices: StateFlow<List<UsbDeviceUiInfo>> = _devices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<UsbDeviceUiInfo?>(null)
    val selectedDevice: StateFlow<UsbDeviceUiInfo?> = _selectedDevice.asStateFlow()

    var onPermissionResult: ((UsbDevice, Boolean) -> Unit)? = null
    var onUsbEventLog: ((String) -> Unit)? = null

    private var registered = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val device = extractUsbDevice(intent)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    onUsbEventLog?.invoke(
                        if (granted) "USB permission granted for ${device?.productName ?: "device"}"
                        else "USB permission denied"
                    )
                    refreshDevices()
                    if (device != null) {
                        onPermissionResult?.invoke(device, granted)
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = extractUsbDevice(intent)
                    onUsbEventLog?.invoke("USB attached: ${device?.productName ?: device?.deviceName ?: "Unknown"}")
                    refreshDevices()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = extractUsbDevice(intent)
                    onUsbEventLog?.invoke("USB detached: ${device?.productName ?: device?.deviceName ?: "Unknown"}")
                    refreshDevices()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun extractUsbDevice(intent: Intent): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    }

    fun register() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(usbReceiver, filter)
        }
        registered = true
        refreshDevices()
    }

    fun unregister() {
        if (!registered) return
        runCatching { context.unregisterReceiver(usbReceiver) }
        registered = false
    }

    fun refreshDevices(): List<UsbDeviceUiInfo> {
        val defaultDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val customDrivers = customProber.findAllDrivers(usbManager)

        val mergedById = LinkedHashMap<Int, UsbSerialDriver>()
        for (d in customDrivers) mergedById[d.device.deviceId] = d
        for (d in defaultDrivers) mergedById.putIfAbsent(d.device.deviceId, d)

        // Fallback: catch any Espressif 0x303A device as CDC-ACM even with unlisted PID
        for (usbDev in usbManager.deviceList.values) {
            if (!mergedById.containsKey(usbDev.deviceId) && usbDev.vendorId == ESPRESSIF_VID) {
                mergedById[usbDev.deviceId] = CdcAcmSerialDriver(usbDev)
            }
        }

        val uiList = mergedById.values.map { driver ->
            val dev = driver.device
            val isEspNative = dev.vendorId == ESPRESSIF_VID
            val driverName = when (driver) {
                is CdcAcmSerialDriver -> if (isEspNative) "ESP32 Native USB-JTAG/CDC" else "USB CDC-ACM"
                is Ch34xSerialDriver -> if (dev.productId == 0x55D4) "WCH CH9102F" else "WCH CH340/341"
                is Cp21xxSerialDriver -> "Silicon Labs CP210x"
                is FtdiSerialDriver -> "FTDI FT232"
                else -> driver.javaClass.simpleName.removeSuffix("SerialDriver")
            }
            UsbDeviceUiInfo(
                deviceId = dev.deviceId,
                deviceName = dev.deviceName,
                productName = dev.productName.orEmpty().trim(),
                vendorId = dev.vendorId,
                productId = dev.productId,
                driverLabel = driverName,
                isNativeEspUsb = isEspNative,
                hasPermission = usbManager.hasPermission(dev),
                usbDevice = dev,
                driver = driver
            )
        }

        _devices.value = uiList
        val currentId = _selectedDevice.value?.deviceId
        _selectedDevice.value = uiList.firstOrNull { it.deviceId == currentId } ?: uiList.firstOrNull()
        return uiList
    }

    fun selectDevice(deviceId: Int) {
        _selectedDevice.value = _devices.value.firstOrNull { it.deviceId == deviceId }
    }

    fun requestPermission(info: UsbDeviceUiInfo) {
        val intent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(context.packageName)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, info.deviceId, intent, flags)
        usbManager.requestPermission(info.usbDevice, pendingIntent)
    }

    fun openPort(info: UsbDeviceUiInfo, baudRate: Int = 115200): Pair<UsbDeviceConnection, UsbSerialPort> {
        val connection = usbManager.openDevice(info.usbDevice)
            ?: throw IllegalStateException("Unable to open USB device ${info.friendlyTitle}. Check OTG permission.")
        val port = info.driver.ports.firstOrNull()
            ?: run {
                connection.close()
                throw IllegalStateException("USB device has no serial ports.")
            }
        port.open(connection)
        port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        return connection to port
    }
}
