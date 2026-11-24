package com.m5burner.duo.flasher

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.m5burner.duo.data.model.FlashConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Deflater

enum class FlashStage(val label: String, val stepIndex: Int) {
    IDLE("就绪", 0),
    DOWNLOADING("获取固件", 1),
    CONNECTING("连接芯片", 1),
    DETECTING("识别型号", 1),
    ERASING("准备闪存", 2),
    FLASHING("写入固件", 3),
    READING("读取备份", 3),
    VERIFYING("校验收尾", 4),
    RESETTING("重启设备", 4),
    DONE("操作完成", 4),
    ERROR("遇到问题", 0)
}

data class FlashProgress(
    val stage: FlashStage = FlashStage.IDLE,
    val progress: Float = 0f,
    val bytesProcessed: Long = 0L,
    val totalBytes: Long = 0L,
    val speedKbps: Float = 0f,
    val detectedChip: EspChip? = null,
    val message: String = "插入 M5Stack USB-C 线即可开始"
)

data class FlashBackupResult(
    val chip: EspChip,
    val outputFile: File,
    val sizeBytes: Long,
    val md5Hex: String,
    val md5Verified: Boolean,
    val partitions: List<EspPartitionEntry> = emptyList()
)

private data class StubJson(
    @SerializedName("entry") val entry: Long = 0L,
    @SerializedName("text") val text: String = "",
    @SerializedName("text_start") val textStart: Long = 0L,
    @SerializedName("data") val data: String = "",
    @SerializedName("data_start") val dataStart: Long = 0L
)

class EspFlasherEngine(
    private val context: Context,
    private val usbController: UsbSerialController
) {
    private val gson = Gson()

    suspend fun flashFirmwareFile(
        deviceInfo: UsbDeviceUiInfo,
        firmwareFile: File,
        config: FlashConfig,
        onProgress: (FlashProgress) -> Unit,
        onLog: (String) -> Unit
    ): Result<EspChip> = withContext(Dispatchers.IO) {
        val firmwareBytes = try {
            firmwareFile.readBytes()
        } catch (e: Exception) {
            return@withContext Result.failure(IOException("无法读取固件文件: ${e.message}", e))
        }
        flashFirmwareBytes(deviceInfo, firmwareBytes, config, onProgress, onLog)
    }

    suspend fun flashFirmwareBytes(
        deviceInfo: UsbDeviceUiInfo,
        firmwareBytes: ByteArray,
        config: FlashConfig,
        onProgress: (FlashProgress) -> Unit,
        onLog: (String) -> Unit
    ): Result<EspChip> = withContext(Dispatchers.IO) {
        if (firmwareBytes.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("固件文件为空"))
        }

        var connection: android.hardware.usb.UsbDeviceConnection? = null
        var port: UsbSerialPort? = null

        try {
            onProgress(FlashProgress(FlashStage.CONNECTING, 0.03f, message = "正在连接 ${deviceInfo.friendlyTitle}..."))
            onLog("[USB] 打开设备 ${deviceInfo.friendlyTitle} (${deviceInfo.vidPidHex})")

            val opened = usbController.openPort(deviceInfo, 115200)
            connection = opened.first
            port = opened.second
            val decoder = SlipStreamDecoder()

            syncBootloader(port, decoder, deviceInfo.isNativeEspUsb, onLog)

            onProgress(FlashProgress(FlashStage.DETECTING, 0.08f, message = "正在识别芯片型号..."))
            val chip = detectChip(port, decoder, onLog)
            onLog("[CHIP] 识别到 ${chip.displayName}")

            if (chip.needsSpiAttach) {
                sendCommand(port, decoder, EspSlipProtocol.CMD_SPI_ATTACH, EspSlipProtocol.buildSpiAttachPayload(), timeoutMs = 3000)
            }

            val flashSizeConfig = maxOf(16 * 1024 * 1024, EspSlipProtocol.alignUpToSector(firmwareBytes.size + config.flashAddress))
            runCatching {
                sendCommand(port, decoder, EspSlipProtocol.CMD_SPI_SET_PARAMS, EspSlipProtocol.buildSpiSetParamsPayload(flashSizeConfig), timeoutMs = 3000)
            }

            val targetBaud = config.baudRate
            if (!deviceInfo.isNativeEspUsb && targetBaud > 115200) {
                onLog("[UART] 切换高速波特率: $targetBaud")
                val baudResult = runCatching {
                    sendCommand(port, decoder, EspSlipProtocol.CMD_CHANGE_BAUDRATE, EspSlipProtocol.buildChangeBaudPayload(targetBaud, 0), timeoutMs = 2000)
                    delay(50)
                    port.setParameters(targetBaud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                    delay(50)
                    drainInput(port, 120)
                }
                if (baudResult.isFailure) {
                    onLog("[UART] 高速切换未响应，回落到 115200")
                    port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                }
            }

            val blockSize = EspSlipProtocol.ROM_FLASH_BLOCK_SIZE
            val eraseSize = EspSlipProtocol.alignUpToSector(firmwareBytes.size)

            if (config.useCompression) {
                onProgress(FlashProgress(FlashStage.ERASING, 0.14f, detectedChip = chip, message = "正在压缩并擦除目标扇区..."))
                val compressed = compressZlib(firmwareBytes)
                val numBlocks = (compressed.size + blockSize - 1) / blockSize
                onLog("[FLASH] ZLIB 压缩完成: ${formatSize(firmwareBytes.size.toLong())} -> ${formatSize(compressed.size.toLong())} ($numBlocks 块)")

                val beginTimeout = (15_000 + (eraseSize / (1024 * 1024)) * 4_000).coerceIn(15_000, 60_000)
                sendCommand(
                    port, decoder,
                    EspSlipProtocol.CMD_FLASH_DEFL_BEGIN,
                    EspSlipProtocol.buildFlashBeginPayload(eraseSize, numBlocks, blockSize, config.flashAddress, chip.usesEncryptParam),
                    timeoutMs = beginTimeout
                )

                val startTime = System.currentTimeMillis()
                var sentBytes = 0L
                var lastUiUpdate = 0L

                for (seq in 0 until numBlocks) {
                    if (!currentCoroutineContext().isActive) throw CancellationException("已取消烧录")
                    val start = seq * blockSize
                    val end = minOf(start + blockSize, compressed.size)
                    val chunk = compressed.copyOfRange(start, end)
                    sendCommand(
                        port, decoder,
                        EspSlipProtocol.CMD_FLASH_DEFL_DATA,
                        EspSlipProtocol.buildFlashDataPayload(chunk, seq),
                        checksum = EspSlipProtocol.computeChecksum(chunk),
                        timeoutMs = 10_000
                    )
                    sentBytes += chunk.size
                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdate >= 60L || seq == numBlocks - 1) {
                        lastUiUpdate = now
                        val elapsedSec = ((now - startTime).coerceAtLeast(1L)) / 1000f
                        val kbps = (sentBytes / 1024f) / elapsedSec
                        val fraction = sentBytes.toFloat() / compressed.size.toFloat()
                        onProgress(
                            FlashProgress(
                                stage = FlashStage.FLASHING,
                                progress = 0.15f + fraction * 0.80f,
                                bytesProcessed = sentBytes,
                                totalBytes = compressed.size.toLong(),
                                speedKbps = kbps,
                                detectedChip = chip,
                                message = "正在写入 ${chip.displayName} · ${(fraction * 100).toInt()}%"
                            )
                        )
                    }
                }

                onProgress(FlashProgress(FlashStage.VERIFYING, 0.96f, sentBytes, compressed.size.toLong(), detectedChip = chip, message = "正在完成写入收尾..."))
                sendCommand(port, decoder, EspSlipProtocol.CMD_FLASH_DEFL_END, EspSlipProtocol.buildFlashEndPayload(reboot = false), timeoutMs = 10_000)
            } else {
                val numBlocks = (firmwareBytes.size + blockSize - 1) / blockSize
                onProgress(FlashProgress(FlashStage.ERASING, 0.14f, detectedChip = chip, message = "正在擦除目标扇区..."))
                val beginTimeout = (15_000 + (eraseSize / (1024 * 1024)) * 4_000).coerceIn(15_000, 60_000)
                sendCommand(
                    port, decoder,
                    EspSlipProtocol.CMD_FLASH_BEGIN,
                    EspSlipProtocol.buildFlashBeginPayload(eraseSize, numBlocks, blockSize, config.flashAddress, chip.usesEncryptParam),
                    timeoutMs = beginTimeout
                )

                val startTime = System.currentTimeMillis()
                var sentBytes = 0L
                var lastUiUpdate = 0L

                for (seq in 0 until numBlocks) {
                    if (!currentCoroutineContext().isActive) throw CancellationException("已取消烧录")
                    val start = seq * blockSize
                    val end = minOf(start + blockSize, firmwareBytes.size)
                    val chunk = ByteArray(blockSize) { 0xFF.toByte() }
                    System.arraycopy(firmwareBytes, start, chunk, 0, end - start)
                    sendCommand(
                        port, decoder,
                        EspSlipProtocol.CMD_FLASH_DATA,
                        EspSlipProtocol.buildFlashDataPayload(chunk, seq),
                        checksum = EspSlipProtocol.computeChecksum(chunk),
                        timeoutMs = 10_000
                    )
                    sentBytes += (end - start)
                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdate >= 60L || seq == numBlocks - 1) {
                        lastUiUpdate = now
                        val elapsedSec = ((now - startTime).coerceAtLeast(1L)) / 1000f
                        val kbps = (sentBytes / 1024f) / elapsedSec
                        val fraction = sentBytes.toFloat() / firmwareBytes.size.toFloat()
                        onProgress(
                            FlashProgress(
                                stage = FlashStage.FLASHING,
                                progress = 0.15f + fraction * 0.80f,
                                bytesProcessed = sentBytes,
                                totalBytes = firmwareBytes.size.toLong(),
                                speedKbps = kbps,
                                detectedChip = chip,
                                message = "正在写入 ${chip.displayName} · ${(fraction * 100).toInt()}%"
                            )
                        )
                    }
                }
                onProgress(FlashProgress(FlashStage.VERIFYING, 0.96f, sentBytes, firmwareBytes.size.toLong(), detectedChip = chip, message = "正在完成写入收尾..."))
                sendCommand(port, decoder, EspSlipProtocol.CMD_FLASH_END, EspSlipProtocol.buildFlashEndPayload(reboot = false), timeoutMs = 5_000)
            }

            if (config.autoResetAfterFlash) {
                onProgress(FlashProgress(FlashStage.RESETTING, 0.98f, detectedChip = chip, message = "正在重启设备..."))
                hardReset(port)
            }

            onLog("[DONE] 烧录成功！${chip.displayName} 已重启")
            onProgress(
                FlashProgress(
                    stage = FlashStage.DONE,
                    progress = 1f,
                    bytesProcessed = firmwareBytes.size.toLong(),
                    totalBytes = firmwareBytes.size.toLong(),
                    detectedChip = chip,
                    message = "烧录完成！${chip.displayName} 已自动重启"
                )
            )
            Result.success(chip)
        } catch (ce: CancellationException) {
            onLog("[CANCEL] 操作已取消")
            throw ce
        } catch (e: Exception) {
            onLog("[ERROR] ${e.message ?: e.javaClass.simpleName}")
            onProgress(FlashProgress(FlashStage.ERROR, 0f, message = e.message ?: "烧录失败"))
            Result.failure(e)
        } finally {
            runCatching { port?.close() }
            runCatching { connection?.close() }
        }
    }

    /**
     * Reads flash contents off a connected M5Stack device into a local .bin backup file.
     * When [lengthBytes] <= 0 (AUTO), probes the bootloader header (0x0/0x1000) and partition table (0x8000)
     * first so 100% of firmware + NVS + SPIFFS/LittleFS/FATFS user data is captured without truncation.
     */
    suspend fun readFlashBackup(
        deviceInfo: UsbDeviceUiInfo,
        outputFile: File,
        offset: Int = 0x0,
        lengthBytes: Int = 0, // <= 0 means AUTO detect full flash size
        baudRate: Int = 921600,
        onProgress: (FlashProgress) -> Unit,
        onLog: (String) -> Unit
    ): Result<FlashBackupResult> = withContext(Dispatchers.IO) {
        var connection: android.hardware.usb.UsbDeviceConnection? = null
        var port: UsbSerialPort? = null

        try {
            onProgress(FlashProgress(FlashStage.CONNECTING, 0.03f, message = "正在连接设备准备读取固件..."))
            val opened = usbController.openPort(deviceInfo, 115200)
            connection = opened.first
            port = opened.second
            val decoder = SlipStreamDecoder()

            syncBootloader(port, decoder, deviceInfo.isNativeEspUsb, onLog)

            onProgress(FlashProgress(FlashStage.DETECTING, 0.08f, message = "正在识别芯片并探测真实 Flash 容量..."))
            val chip = detectChip(port, decoder, onLog)
            onLog("[BACKUP] 识别芯片: ${chip.displayName}")

            val stubAsset = chip.stubAssetName
                ?: throw IOException("${chip.displayName} 暂未内置高速回读 Stub")

            uploadFlasherStub(port, decoder, stubAsset, onLog)

            // Configure SPI flash params up to 16MB/32MB so probing & reading work across all boards
            val maxConfigSize = maxOf(16 * 1024 * 1024, if (lengthBytes > 0) EspSlipProtocol.alignUpToSector(offset + lengthBytes) else 16 * 1024 * 1024)
            runCatching {
                sendCommand(
                    port, decoder,
                    EspSlipProtocol.CMD_SPI_SET_PARAMS,
                    EspSlipProtocol.buildSpiSetParamsPayload(maxConfigSize),
                    timeoutMs = 3000,
                    isStub = true
                )
            }

            // Boost UART baud rate on stub (pass oldBaud=115200 for stub)
            if (!deviceInfo.isNativeEspUsb && baudRate > 115200) {
                onLog("[UART] Stub 切换高速回读波特率: $baudRate")
                val ok = runCatching {
                    sendCommand(
                        port, decoder,
                        EspSlipProtocol.CMD_CHANGE_BAUDRATE,
                        EspSlipProtocol.buildChangeBaudPayload(baudRate, 115200),
                        timeoutMs = 2000,
                        isStub = true
                    )
                    delay(50)
                    port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                    delay(50)
                    drainInput(port, 80)
                }
                if (ok.isFailure) {
                    port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                }
            }

            // Auto-detect real flash size by probing first 36KB (bootloader header + 0x8000 partition table)
            val resolvedLengthBytes = if (lengthBytes <= 0) {
                val probeLen = 0x9000 // 36 KB
                onLog("[BACKUP] 正在探测引导头与 0x8000 分区表以确定全片容量...")
                val probeBytes = readFlashBytesInternal(port, decoder, 0x0, probeLen)
                val headerSize = EspPartitionTable.parseFlashSizeFromBootHeader(probeBytes)
                val tableSlice = if (probeBytes.size >= EspPartitionTable.TABLE_OFFSET + 32) {
                    probeBytes.copyOfRange(EspPartitionTable.TABLE_OFFSET, probeBytes.size)
                } else ByteArray(0)
                val probeParts = EspPartitionTable.parseTableBytes(tableSlice)
                val partSize = EspPartitionTable.inferFlashSizeFromPartitions(probeParts)
                val detected = maxOf(headerSize ?: 0, partSize ?: 0).takeIf { it >= 2 * 1024 * 1024 } ?: (4 * 1024 * 1024)
                val tags = EspPartitionTable.summarizeTags(probeParts)
                onLog("[BACKUP] 自动识别全片容量: ${detected / (1024 * 1024)}MB (分区: ${tags.joinToString(" / ").ifBlank { "标准镜像" }})")

                // Ensure SPI_SET_PARAMS covers full detected size (e.g. 32MB if ever present)
                if (detected > maxConfigSize) {
                    runCatching {
                        sendCommand(
                            port, decoder,
                            EspSlipProtocol.CMD_SPI_SET_PARAMS,
                            EspSlipProtocol.buildSpiSetParamsPayload(detected),
                            timeoutMs = 3000,
                            isStub = true
                        )
                    }
                }
                detected
            } else {
                lengthBytes
            }

            onProgress(
                FlashProgress(
                    stage = FlashStage.READING,
                    progress = 0.14f,
                    totalBytes = resolvedLengthBytes.toLong(),
                    detectedChip = chip,
                    message = "开始全片备份 ${formatSize(resolvedLengthBytes.toLong())}（含固件与存储数据）..."
                )
            )
            onLog("[BACKUP] 开始读取 0x${offset.toString(16)} (${formatSize(resolvedLengthBytes.toLong())})...")

            val packetSize = EspSlipProtocol.STUB_READ_BLOCK_SIZE
            sendCommand(
                port, decoder,
                EspSlipProtocol.CMD_STUB_READ_FLASH,
                EspSlipProtocol.buildReadFlashPayload(offset, resolvedLengthBytes, packetSize, maxInFlight = 1),
                timeoutMs = 5000,
                isStub = true
            )

            val md5Digest = MessageDigest.getInstance("MD5")
            var receivedBytes = 0
            val startTime = System.currentTimeMillis()
            var lastUiUpdate = 0L
            val readBuf = ByteArray(4096)
            val pendingFrames = ArrayDeque<ByteArray>()

            FileOutputStream(outputFile).use { fos ->
                while (receivedBytes < resolvedLengthBytes) {
                    if (!currentCoroutineContext().isActive) throw CancellationException("已取消备份")

                    val frame = nextSlipFrame(port, decoder, readBuf, pendingFrames, timeoutMs = 8000)
                        ?: throw IOException("读取闪存超时（已读取 ${formatSize(receivedBytes.toLong())}）")

                    val toWrite = minOf(frame.size, resolvedLengthBytes - receivedBytes)
                    fos.write(frame, 0, toWrite)
                    md5Digest.update(frame, 0, toWrite)
                    receivedBytes += toWrite

                    // Send 4-byte cumulative ACK SLIP packet back to stub
                    val ackPacket = EspSlipProtocol.encodeSlip(EspSlipProtocol.packUInt32s(receivedBytes))
                    port.write(ackPacket, 1500)

                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdate >= 70L || receivedBytes == resolvedLengthBytes) {
                        lastUiUpdate = now
                        val elapsedSec = ((now - startTime).coerceAtLeast(1L)) / 1000f
                        val kbps = (receivedBytes / 1024f) / elapsedSec
                        val fraction = receivedBytes.toFloat() / resolvedLengthBytes.toFloat()
                        onProgress(
                            FlashProgress(
                                stage = FlashStage.READING,
                                progress = 0.14f + fraction * 0.80f,
                                bytesProcessed = receivedBytes.toLong(),
                                totalBytes = resolvedLengthBytes.toLong(),
                                speedKbps = kbps,
                                detectedChip = chip,
                                message = "正在备份 ${chip.displayName} (${resolvedLengthBytes / (1024 * 1024)}MB) · ${(fraction * 100).toInt()}%"
                            )
                        )
                    }
                }
                fos.flush()
            }

            // Read trailing 16-byte hardware MD5 frame from stub
            onProgress(FlashProgress(FlashStage.VERIFYING, 0.96f, resolvedLengthBytes.toLong(), resolvedLengthBytes.toLong(), detectedChip = chip, message = "正在校验 MD5 与分区表..."))
            val localMd5Bytes = md5Digest.digest()
            val localMd5Hex = localMd5Bytes.joinToString("") { "%02x".format(it) }
            val chipMd5Frame = nextSlipFrame(port, decoder, readBuf, pendingFrames, timeoutMs = 4000)
            val md5Verified = chipMd5Frame != null && chipMd5Frame.size == 16 && chipMd5Frame.contentEquals(localMd5Bytes)

            val parsedPartitions = EspPartitionTable.parseFromBinaryFile(outputFile)
            val tags = EspPartitionTable.summarizeTags(parsedPartitions)
            onLog("[BACKUP] MD5=${localMd5Hex.take(12).uppercase()} (verified=$md5Verified), 包含: ${tags.joinToString(", ").ifBlank { "整片镜像" }}")

            onProgress(FlashProgress(FlashStage.RESETTING, 0.98f, detectedChip = chip, message = "备份完成，正在重启设备..."))
            hardReset(port)

            onLog("[DONE] 完整备份已保存 (${formatSize(outputFile.length())})")
            onProgress(
                FlashProgress(
                    stage = FlashStage.DONE,
                    progress = 1f,
                    bytesProcessed = outputFile.length(),
                    totalBytes = outputFile.length(),
                    detectedChip = chip,
                    message = "完整备份成功！已保存 ${formatSize(outputFile.length())}（${tags.joinToString("+").ifBlank { "全片镜像" }}）"
                )
            )

            Result.success(
                FlashBackupResult(
                    chip = chip,
                    outputFile = outputFile,
                    sizeBytes = outputFile.length(),
                    md5Hex = localMd5Hex,
                    md5Verified = md5Verified,
                    partitions = parsedPartitions
                )
            )
        } catch (ce: CancellationException) {
            outputFile.delete()
            onLog("[CANCEL] 备份已取消")
            throw ce
        } catch (e: Exception) {
            outputFile.delete()
            onLog("[ERROR] 备份失败: ${e.message}")
            onProgress(FlashProgress(FlashStage.ERROR, 0f, message = "备份失败: ${e.message}"))
            Result.failure(e)
        } finally {
            runCatching { port?.close() }
            runCatching { connection?.close() }
        }
    }

    /**
     * Fast Data-Only Backup: reads ONLY the 0x8000 partition table and user-data partitions
     * (NVS / LittleFS / SPIFFS / FATFS), skipping app firmware completely, and packs them into a .zip bundle.
     */
    suspend fun readDataPartitionsBackup(
        deviceInfo: UsbDeviceUiInfo,
        outputZipFile: File,
        baudRate: Int = 921600,
        onProgress: (FlashProgress) -> Unit,
        onLog: (String) -> Unit
    ): Result<FlashBackupResult> = withContext(Dispatchers.IO) {
        var connection: android.hardware.usb.UsbDeviceConnection? = null
        var port: UsbSerialPort? = null

        try {
            onProgress(FlashProgress(FlashStage.CONNECTING, 0.04f, message = "正在连接设备准备只读取数据分区..."))
            val opened = usbController.openPort(deviceInfo, 115200)
            connection = opened.first
            port = opened.second
            val decoder = SlipStreamDecoder()

            syncBootloader(port, decoder, deviceInfo.isNativeEspUsb, onLog)

            onProgress(FlashProgress(FlashStage.DETECTING, 0.10f, message = "正在读取 0x8000 分区表..."))
            val chip = detectChip(port, decoder, onLog)
            val stubAsset = chip.stubAssetName
                ?: throw IOException("${chip.displayName} 暂未内置高速回读 Stub")
            uploadFlasherStub(port, decoder, stubAsset, onLog)

            runCatching {
                sendCommand(
                    port, decoder,
                    EspSlipProtocol.CMD_SPI_SET_PARAMS,
                    EspSlipProtocol.buildSpiSetParamsPayload(16 * 1024 * 1024),
                    timeoutMs = 3000,
                    isStub = true
                )
            }

            if (!deviceInfo.isNativeEspUsb && baudRate > 115200) {
                runCatching {
                    sendCommand(
                        port, decoder,
                        EspSlipProtocol.CMD_CHANGE_BAUDRATE,
                        EspSlipProtocol.buildChangeBaudPayload(baudRate, 115200),
                        timeoutMs = 2000,
                        isStub = true
                    )
                    delay(50)
                    port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                    delay(50)
                    drainInput(port, 80)
                }.onFailure {
                    port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                }
            }

            // 1. Read ONLY 3KB partition table at 0x8000
            val tableBytes = readFlashBytesInternal(port, decoder, EspPartitionTable.TABLE_OFFSET, EspPartitionTable.TABLE_SIZE)
            val allPartitions = EspPartitionTable.parseTableBytes(tableBytes)
            val dataPartitions = allPartitions.filter { it.category.isUserData }

            if (dataPartitions.isEmpty()) {
                throw IOException("未在设备分区表中发现独立数据分区（NVS/LittleFS/SPIFFS/FATFS），请改用「自动全片」备份")
            }

            val totalDataBytes = dataPartitions.sumOf { it.size.toLong() }.coerceAtLeast(1L)
            val maxEnd = dataPartitions.maxOf { it.endOffset }
            if (maxEnd > 16 * 1024 * 1024) {
                runCatching {
                    sendCommand(
                        port, decoder,
                        EspSlipProtocol.CMD_SPI_SET_PARAMS,
                        EspSlipProtocol.buildSpiSetParamsPayload(EspSlipProtocol.alignUpToSector(maxEnd)),
                        timeoutMs = 3000,
                        isStub = true
                    )
                }
            }

            onLog("[DATA] 发现 ${dataPartitions.size} 个数据分区 (${dataPartitions.joinToString { "${it.name}:${it.formattedSize}" }})，跳过固件区")

            // 2. Read ONLY each user-data partition sequentially
            val collected = mutableListOf<FlashSegmentData>()
            var doneBytes = 0L
            val startTime = System.currentTimeMillis()
            var allMd5Ok = true

            for ((idx, part) in dataPartitions.withIndex()) {
                if (!currentCoroutineContext().isActive) throw CancellationException("已取消数据备份")
                onLog("[DATA] (${idx + 1}/${dataPartitions.size}) 读取 ${part.name} @ 0x${part.offset.toString(16)} (${part.formattedSize})...")

                val (segBytes, md5Hex, md5Ok) = readFlashSegmentWithProgress(
                    port = port,
                    decoder = decoder,
                    offset = part.offset,
                    lengthBytes = part.size
                ) { segRead ->
                    val overall = (doneBytes + segRead).toFloat() / totalDataBytes.toFloat()
                    val elapsedSec = ((System.currentTimeMillis() - startTime).coerceAtLeast(1L)) / 1000f
                    val kbps = ((doneBytes + segRead) / 1024f) / elapsedSec
                    onProgress(
                        FlashProgress(
                            stage = FlashStage.READING,
                            progress = 0.14f + overall * 0.80f,
                            bytesProcessed = doneBytes + segRead,
                            totalBytes = totalDataBytes,
                            speedKbps = kbps,
                            detectedChip = chip,
                            message = "正在备份 ${part.name} (${idx + 1}/${dataPartitions.size}) · ${(overall * 100).toInt()}%"
                        )
                    )
                }
                if (!md5Ok) allMd5Ok = false
                collected.add(FlashSegmentData(part, segBytes, md5Hex))
                doneBytes += part.size
            }

            // 3. Package segments + manifest.json into compact .zip bundle
            onProgress(FlashProgress(FlashStage.VERIFYING, 0.96f, totalDataBytes, totalDataBytes, detectedChip = chip, message = "正在打包纯数据备份 (.zip)..."))
            DataBackupBundle.writeBundle(outputZipFile, chip.displayName, collected)
            val bundleMd5 = com.m5burner.duo.data.BackupVaultRepository.computeMd5(outputZipFile)

            onProgress(FlashProgress(FlashStage.RESETTING, 0.98f, detectedChip = chip, message = "数据备份完成，正在重启设备..."))
            hardReset(port)

            val tags = EspPartitionTable.summarizeTags(dataPartitions)
            onLog("[DONE] 纯数据备份完成: ${formatSize(outputZipFile.length())} (${tags.joinToString("+")})")
            onProgress(
                FlashProgress(
                    stage = FlashStage.DONE,
                    progress = 1f,
                    bytesProcessed = outputZipFile.length(),
                    totalBytes = outputZipFile.length(),
                    detectedChip = chip,
                    message = "极速数据备份完成！已保存 ${tags.joinToString("+")}（${formatSize(outputZipFile.length())}）"
                )
            )

            Result.success(
                FlashBackupResult(
                    chip = chip,
                    outputFile = outputZipFile,
                    sizeBytes = outputZipFile.length(),
                    md5Hex = bundleMd5,
                    md5Verified = allMd5Ok,
                    partitions = dataPartitions
                )
            )
        } catch (ce: CancellationException) {
            outputZipFile.delete()
            onLog("[CANCEL] 数据备份已取消")
            throw ce
        } catch (e: Exception) {
            outputZipFile.delete()
            onLog("[ERROR] 数据备份失败: ${e.message}")
            onProgress(FlashProgress(FlashStage.ERROR, 0f, message = "数据备份失败: ${e.message}"))
            Result.failure(e)
        } finally {
            runCatching { port?.close() }
            runCatching { connection?.close() }
        }
    }

    /**
     * Restores a Data-Only .zip bundle back to the exact original partition offsets on the device,
     * leaving the currently installed application firmware untouched.
     */
    suspend fun restoreDataBundleZip(
        deviceInfo: UsbDeviceUiInfo,
        bundleZipFile: File,
        config: FlashConfig,
        onProgress: (FlashProgress) -> Unit,
        onLog: (String) -> Unit
    ): Result<EspChip> = withContext(Dispatchers.IO) {
        val segments = try {
            DataBackupBundle.readBundleSegments(bundleZipFile)
        } catch (e: Exception) {
            return@withContext Result.failure(IOException("读取纯数据备份包失败: ${e.message}", e))
        }
        flashSegmentsInternal(deviceInfo, segments, config, onProgress, onLog)
    }

    /**
     * Restores ONLY the user-data partitions (NVS / LittleFS / SPIFFS / FATFS) sliced from a full backup .bin,
     * preserving whatever newer application firmware is currently installed on the device.
     */
    suspend fun restoreDataPartitionsOnly(
        deviceInfo: UsbDeviceUiInfo,
        backupFile: File,
        dataPartitions: List<EspPartitionEntry>,
        config: FlashConfig,
        onProgress: (FlashProgress) -> Unit,
        onLog: (String) -> Unit
    ): Result<EspChip> = withContext(Dispatchers.IO) {
        if (dataPartitions.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("该备份中未解析出独立的用户数据分区"))
        }
        val fileBytes = try {
            backupFile.readBytes()
        } catch (e: Exception) {
            return@withContext Result.failure(IOException("无法读取备份文件: ${e.message}", e))
        }
        val segments = dataPartitions
            .filter { it.offset >= 0 && it.endOffset <= fileBytes.size }
            .map { part -> FlashSegmentData(part, fileBytes.copyOfRange(part.offset, part.endOffset)) }
        if (segments.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("备份文件容量不足，无法截取数据分区"))
        }
        flashSegmentsInternal(deviceInfo, segments, config, onProgress, onLog)
    }

    private suspend fun flashSegmentsInternal(
        deviceInfo: UsbDeviceUiInfo,
        segments: List<FlashSegmentData>,
        config: FlashConfig,
        onProgress: (FlashProgress) -> Unit,
        onLog: (String) -> Unit
    ): Result<EspChip> = withContext(Dispatchers.IO) {
        var connection: android.hardware.usb.UsbDeviceConnection? = null
        var port: UsbSerialPort? = null

        try {
            onProgress(FlashProgress(FlashStage.CONNECTING, 0.04f, message = "正在连接设备准备恢复数据分区..."))
            val opened = usbController.openPort(deviceInfo, 115200)
            connection = opened.first
            port = opened.second
            val decoder = SlipStreamDecoder()

            syncBootloader(port, decoder, deviceInfo.isNativeEspUsb, onLog)
            val chip = detectChip(port, decoder, onLog)
            onLog("[RESTORE] 仅恢复数据分区 -> ${chip.displayName} (${segments.joinToString { it.entry.name }})")

            if (chip.needsSpiAttach) {
                sendCommand(port, decoder, EspSlipProtocol.CMD_SPI_ATTACH, EspSlipProtocol.buildSpiAttachPayload(), timeoutMs = 3000)
            }
            val maxEnd = segments.maxOf { it.entry.offset + it.bytes.size }
            runCatching {
                sendCommand(
                    port, decoder,
                    EspSlipProtocol.CMD_SPI_SET_PARAMS,
                    EspSlipProtocol.buildSpiSetParamsPayload(maxOf(16 * 1024 * 1024, EspSlipProtocol.alignUpToSector(maxEnd))),
                    timeoutMs = 3000
                )
            }

            if (!deviceInfo.isNativeEspUsb && config.baudRate > 115200) {
                runCatching {
                    sendCommand(port, decoder, EspSlipProtocol.CMD_CHANGE_BAUDRATE, EspSlipProtocol.buildChangeBaudPayload(config.baudRate, 0), timeoutMs = 2000)
                    delay(50)
                    port.setParameters(config.baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                    delay(50)
                    drainInput(port, 100)
                }.onFailure {
                    port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                }
            }

            val totalRawBytes = segments.sumOf { it.bytes.size.toLong() }.coerceAtLeast(1L)
            var doneRawBytes = 0L
            val blockSize = EspSlipProtocol.ROM_FLASH_BLOCK_SIZE

            for ((idx, seg) in segments.withIndex()) {
                val segBytes = seg.bytes
                val compressed = compressZlib(segBytes)
                val numBlocks = (compressed.size + blockSize - 1) / blockSize
                val eraseSize = EspSlipProtocol.alignUpToSector(segBytes.size)
                onLog("[RESTORE] (${idx + 1}/${segments.size}) 写入 ${seg.entry.name} @ 0x${seg.entry.offset.toString(16)} (${seg.entry.formattedSize})")

                val beginTimeout = (15_000 + (eraseSize / (1024 * 1024)) * 4_000).coerceIn(15_000, 60_000)
                sendCommand(
                    port, decoder,
                    EspSlipProtocol.CMD_FLASH_DEFL_BEGIN,
                    EspSlipProtocol.buildFlashBeginPayload(eraseSize, numBlocks, blockSize, seg.entry.offset, chip.usesEncryptParam),
                    timeoutMs = beginTimeout
                )

                for (seq in 0 until numBlocks) {
                    if (!currentCoroutineContext().isActive) throw CancellationException("已取消恢复")
                    val start = seq * blockSize
                    val end = minOf(start + blockSize, compressed.size)
                    val chunk = compressed.copyOfRange(start, end)
                    sendCommand(
                        port, decoder,
                        EspSlipProtocol.CMD_FLASH_DEFL_DATA,
                        EspSlipProtocol.buildFlashDataPayload(chunk, seq),
                        checksum = EspSlipProtocol.computeChecksum(chunk),
                        timeoutMs = 10_000
                    )
                    if (seq % 8 == 0 || seq == numBlocks - 1) {
                        val segFrac = (seq + 1).toFloat() / numBlocks.toFloat()
                        val overall = (doneRawBytes + segBytes.size * segFrac) / totalRawBytes.toFloat()
                        onProgress(
                            FlashProgress(
                                stage = FlashStage.FLASHING,
                                progress = 0.12f + overall * 0.84f,
                                detectedChip = chip,
                                message = "正在恢复 ${seg.entry.name} (${idx + 1}/${segments.size}) · ${(overall * 100).toInt()}%"
                            )
                        )
                    }
                }
                doneRawBytes += segBytes.size
            }

            sendCommand(port, decoder, EspSlipProtocol.CMD_FLASH_DEFL_END, EspSlipProtocol.buildFlashEndPayload(reboot = false), timeoutMs = 8000)
            onProgress(FlashProgress(FlashStage.RESETTING, 0.98f, detectedChip = chip, message = "数据恢复完成，正在重启设备..."))
            hardReset(port)

            onLog("[DONE] 已成功恢复 ${segments.size} 个用户数据分区")
            onProgress(
                FlashProgress(
                    stage = FlashStage.DONE,
                    progress = 1f,
                    detectedChip = chip,
                    message = "数据恢复完成（${segments.joinToString("+") { it.entry.name }}），当前固件保持不变"
                )
            )
            Result.success(chip)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            onLog("[ERROR] 数据分区恢复失败: ${e.message}")
            onProgress(FlashProgress(FlashStage.ERROR, 0f, message = "恢复失败: ${e.message}"))
            Result.failure(e)
        } finally {
            runCatching { port?.close() }
            runCatching { connection?.close() }
        }
    }

    private suspend fun readFlashSegmentWithProgress(
        port: UsbSerialPort,
        decoder: SlipStreamDecoder,
        offset: Int,
        lengthBytes: Int,
        onBytes: (Int) -> Unit
    ): Triple<ByteArray, String, Boolean> {
        val packetSize = EspSlipProtocol.STUB_READ_BLOCK_SIZE
        sendCommand(
            port, decoder,
            EspSlipProtocol.CMD_STUB_READ_FLASH,
            EspSlipProtocol.buildReadFlashPayload(offset, lengthBytes, packetSize, maxInFlight = 1),
            timeoutMs = 5000,
            isStub = true
        )
        val out = ByteArrayOutputStream(lengthBytes)
        val md5 = MessageDigest.getInstance("MD5")
        val readBuf = ByteArray(4096)
        val pending = ArrayDeque<ByteArray>()
        var received = 0
        var lastCb = 0L

        while (received < lengthBytes) {
            if (!currentCoroutineContext().isActive) throw CancellationException()
            val frame = nextSlipFrame(port, decoder, readBuf, pending, timeoutMs = 8000)
                ?: throw IOException("读取数据分区超时")
            val toWrite = minOf(frame.size, lengthBytes - received)
            out.write(frame, 0, toWrite)
            md5.update(frame, 0, toWrite)
            received += toWrite
            port.write(EspSlipProtocol.encodeSlip(EspSlipProtocol.packUInt32s(received)), 1500)

            val now = System.currentTimeMillis()
            if (now - lastCb >= 60L || received == lengthBytes) {
                lastCb = now
                onBytes(received)
            }
        }
        val localMd5 = md5.digest()
        val chipMd5 = nextSlipFrame(port, decoder, readBuf, pending, timeoutMs = 4000)
        val verified = chipMd5 != null && chipMd5.size == 16 && chipMd5.contentEquals(localMd5)
        val hex = localMd5.joinToString("") { "%02x".format(it) }
        return Triple(out.toByteArray(), hex, verified)
    }

    private suspend fun readFlashBytesInternal(
        port: UsbSerialPort,
        decoder: SlipStreamDecoder,
        offset: Int,
        lengthBytes: Int
    ): ByteArray {
        val packetSize = EspSlipProtocol.STUB_READ_BLOCK_SIZE
        sendCommand(
            port, decoder,
            EspSlipProtocol.CMD_STUB_READ_FLASH,
            EspSlipProtocol.buildReadFlashPayload(offset, lengthBytes, packetSize, maxInFlight = 1),
            timeoutMs = 5000,
            isStub = true
        )
        val out = ByteArrayOutputStream(lengthBytes)
        val readBuf = ByteArray(4096)
        val pending = ArrayDeque<ByteArray>()
        var received = 0
        while (received < lengthBytes) {
            val frame = nextSlipFrame(port, decoder, readBuf, pending, timeoutMs = 5000)
                ?: throw IOException("探测闪存超时")
            val toWrite = minOf(frame.size, lengthBytes - received)
            out.write(frame, 0, toWrite)
            received += toWrite
            port.write(EspSlipProtocol.encodeSlip(EspSlipProtocol.packUInt32s(received)), 1500)
        }
        // Consume trailing 16-byte MD5 frame from stub before next command
        nextSlipFrame(port, decoder, readBuf, pending, timeoutMs = 3000)
        return out.toByteArray()
    }

    suspend fun eraseFlashRegion(
        deviceInfo: UsbDeviceUiInfo,
        sizeBytes: Int = 4 * 1024 * 1024,
        onProgress: (FlashProgress) -> Unit,
        onLog: (String) -> Unit
    ): Result<EspChip> = withContext(Dispatchers.IO) {
        var connection: android.hardware.usb.UsbDeviceConnection? = null
        var port: UsbSerialPort? = null
        try {
            onProgress(FlashProgress(FlashStage.CONNECTING, 0.1f, message = "正在连接设备..."))
            val opened = usbController.openPort(deviceInfo, 115200)
            connection = opened.first
            port = opened.second
            val decoder = SlipStreamDecoder()

            syncBootloader(port, decoder, deviceInfo.isNativeEspUsb, onLog)
            val chip = detectChip(port, decoder, onLog)

            if (chip.needsSpiAttach) {
                sendCommand(port, decoder, EspSlipProtocol.CMD_SPI_ATTACH, EspSlipProtocol.buildSpiAttachPayload(), timeoutMs = 3000)
            }
            runCatching {
                sendCommand(port, decoder, EspSlipProtocol.CMD_SPI_SET_PARAMS, EspSlipProtocol.buildSpiSetParamsPayload(16 * 1024 * 1024), timeoutMs = 3000)
            }

            val alignedSize = EspSlipProtocol.alignUpToSector(sizeBytes)
            onProgress(FlashProgress(FlashStage.ERASING, 0.45f, detectedChip = chip, message = "正在擦除 ${formatSize(alignedSize.toLong())}..."))
            sendCommand(
                port, decoder,
                EspSlipProtocol.CMD_FLASH_BEGIN,
                EspSlipProtocol.buildFlashBeginPayload(alignedSize, 0, EspSlipProtocol.ROM_FLASH_BLOCK_SIZE, 0, chip.usesEncryptParam),
                timeoutMs = 45_000
            )
            hardReset(port)
            onLog("[ERASE] 闪存擦除完成")
            onProgress(FlashProgress(FlashStage.DONE, 1f, detectedChip = chip, message = "已擦除 ${chip.displayName} 闪存"))
            Result.success(chip)
        } catch (e: Exception) {
            onLog("[ERROR] 擦除失败: ${e.message}")
            onProgress(FlashProgress(FlashStage.ERROR, 0f, message = "擦除失败: ${e.message}"))
            Result.failure(e)
        } finally {
            runCatching { port?.close() }
            runCatching { connection?.close() }
        }
    }

    suspend fun resetDeviceOnly(
        deviceInfo: UsbDeviceUiInfo,
        onLog: (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var connection: android.hardware.usb.UsbDeviceConnection? = null
        var port: UsbSerialPort? = null
        try {
            val opened = usbController.openPort(deviceInfo, 115200)
            connection = opened.first
            port = opened.second
            hardReset(port)
            onLog("[RESET] 已向 ${deviceInfo.friendlyTitle} 发送硬件复位信号")
            Result.success(Unit)
        } catch (e: Exception) {
            onLog("[ERROR] 复位失败: ${e.message}")
            Result.failure(e)
        } finally {
            runCatching { port?.close() }
            runCatching { connection?.close() }
        }
    }

    suspend fun runSerialMonitor(
        deviceInfo: UsbDeviceUiInfo,
        baudRate: Int = 115200,
        txChannel: Channel<ByteArray>,
        onLine: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        var connection: android.hardware.usb.UsbDeviceConnection? = null
        var port: UsbSerialPort? = null
        try {
            val opened = usbController.openPort(deviceInfo, baudRate)
            connection = opened.first
            port = opened.second
            runCatching {
                port.dtr = false
                port.rts = false
            }
            onLine("--- 已连接 ${deviceInfo.friendlyTitle} @ $baudRate bps ---")

            val readBuf = ByteArray(1024)
            val lineBuilder = StringBuilder()

            while (currentCoroutineContext().isActive) {
                // Drain outgoing TX commands if user sent any
                while (true) {
                    val outgoing = txChannel.tryReceive().getOrNull() ?: break
                    runCatching { port.write(outgoing, 500) }
                }

                val n = runCatching { port.read(readBuf, 100) }.getOrDefault(-1)
                if (n > 0) {
                    val text = String(readBuf, 0, n, Charsets.UTF_8)
                    for (ch in text) {
                        when (ch) {
                            '\n' -> {
                                onLine(lineBuilder.toString().trimEnd('\r'))
                                lineBuilder.clear()
                            }
                            '\r' -> Unit
                            else -> {
                                if (ch.code >= 32 || ch == '\t') {
                                    lineBuilder.append(ch)
                                }
                            }
                        }
                        if (lineBuilder.length >= 240) {
                            onLine(lineBuilder.toString())
                            lineBuilder.clear()
                        }
                    }
                } else if (n < 0) {
                    delay(80)
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            onLine("--- 串口连接中断: ${e.message} ---")
        } finally {
            runCatching { port?.close() }
            runCatching { connection?.close() }
            onLine("--- 串口监视已停止 ---")
        }
    }

    private suspend fun uploadFlasherStub(
        port: UsbSerialPort,
        decoder: SlipStreamDecoder,
        assetFileName: String,
        onLog: (String) -> Unit
    ) {
        val jsonStr = context.assets.open("stubs/$assetFileName").bufferedReader().use { it.readText() }
        val stub = gson.fromJson(jsonStr, StubJson::class.java)
        val textBytes = Base64.decode(stub.text, Base64.DEFAULT)
        val dataBytes = if (stub.data.isNotBlank()) Base64.decode(stub.data, Base64.DEFAULT) else ByteArray(0)

        onLog("[STUB] 正在上传高速引导程序 ($assetFileName, ${textBytes.size + dataBytes.size} B)...")
        uploadRamSegment(port, decoder, textBytes, stub.textStart.toInt())
        if (dataBytes.isNotEmpty()) {
            uploadRamSegment(port, decoder, dataBytes, stub.dataStart.toInt())
        }

        sendCommand(port, decoder, EspSlipProtocol.CMD_MEM_END, EspSlipProtocol.buildMemEndPayload(stub.entry.toInt()), timeoutMs = 3000)

        // Wait for "OHAI" SLIP frame from running stub
        val readBuf = ByteArray(512)
        val pending = ArrayDeque<ByteArray>()
        val deadline = System.currentTimeMillis() + 4000
        while (System.currentTimeMillis() < deadline) {
            val frame = nextSlipFrame(port, decoder, readBuf, pending, timeoutMs = 500) ?: continue
            if (frame.contentEquals(EspSlipProtocol.STUB_GREETING)) {
                onLog("[STUB] 高速引导程序就绪 (OHAI)")
                return
            }
        }
        throw IOException("等待 Stub 握手 (OHAI) 超时")
    }

    private suspend fun uploadRamSegment(
        port: UsbSerialPort,
        decoder: SlipStreamDecoder,
        segment: ByteArray,
        ramOffset: Int
    ) {
        val blockSize = EspSlipProtocol.MEM_WRITE_BLOCK_SIZE
        val numBlocks = (segment.size + blockSize - 1) / blockSize
        sendCommand(
            port, decoder,
            EspSlipProtocol.CMD_MEM_BEGIN,
            EspSlipProtocol.buildMemBeginPayload(segment.size, numBlocks, blockSize, ramOffset),
            timeoutMs = 3000
        )
        for (seq in 0 until numBlocks) {
            val start = seq * blockSize
            val end = minOf(start + blockSize, segment.size)
            val chunk = segment.copyOfRange(start, end)
            sendCommand(
                port, decoder,
                EspSlipProtocol.CMD_MEM_DATA,
                EspSlipProtocol.buildFlashDataPayload(chunk, seq),
                checksum = EspSlipProtocol.computeChecksum(chunk),
                timeoutMs = 3000
            )
        }
    }

    private suspend fun nextSlipFrame(
        port: UsbSerialPort,
        decoder: SlipStreamDecoder,
        readBuf: ByteArray,
        pending: ArrayDeque<ByteArray>,
        timeoutMs: Int
    ): ByteArray? {
        if (pending.isNotEmpty()) return pending.removeFirst()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!currentCoroutineContext().isActive) throw CancellationException()
            val rem = (deadline - System.currentTimeMillis()).coerceIn(20L, 200L).toInt()
            val n = runCatching { port.read(readBuf, rem) }.getOrDefault(0)
            if (n > 0) {
                val frames = decoder.feed(readBuf, n)
                if (frames.isNotEmpty()) {
                    pending.addAll(frames)
                    return pending.removeFirst()
                }
            }
        }
        return null
    }

    private suspend fun syncBootloader(
        port: UsbSerialPort,
        decoder: SlipStreamDecoder,
        isNativeEspUsb: Boolean,
        onLog: (String) -> Unit
    ) {
        val syncPacket = EspSlipProtocol.buildCommandPacket(EspSlipProtocol.CMD_SYNC, EspSlipProtocol.SYNC_PAYLOAD)
        for (attempt in 1..4) {
            if (!currentCoroutineContext().isActive) throw CancellationException()
            onLog("[SYNC] 尝试进入下载模式 ($attempt/4)...")
            if (isNativeEspUsb) enterBootloaderUsbJtag(port)
            else enterBootloaderClassic(port, alternateDelay = attempt % 2 == 0)
            drainInput(port, 120)
            decoder.reset()

            for (pulse in 1..7) {
                port.write(syncPacket, 500)
                val resp = readResponse(port, decoder, EspSlipProtocol.CMD_SYNC, timeoutMs = 250, isStub = false)
                if (resp != null && resp.isSuccess) {
                    drainInput(port, 150)
                    decoder.reset()
                    onLog("[SYNC] 已进入 ESP ROM Bootloader")
                    return
                }
                delay(50)
            }
        }
        throw IOException("未能与芯片同步。请按住设备上的 BOOT/G0 键插入 USB-C 后重试。")
    }

    private suspend fun detectChip(
        port: UsbSerialPort,
        decoder: SlipStreamDecoder,
        onLog: (String) -> Unit
    ): EspChip {
        val secInfo = runCatching {
            sendCommand(port, decoder, EspSlipProtocol.CMD_GET_SECURITY_INFO, ByteArray(0), timeoutMs = 1500)
        }.getOrNull()

        if (secInfo != null && secInfo.isSuccess && secInfo.data.size >= 16) {
            val chipId = ByteBuffer.wrap(secInfo.data, 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val detected = EspChip.fromSecurityInfoChipId(chipId)
            if (detected != null) return detected
        }

        val regResp = runCatching {
            sendCommand(port, decoder, EspSlipProtocol.CMD_READ_REG, EspSlipProtocol.buildReadRegPayload(EspSlipProtocol.CHIP_DETECT_MAGIC_REG_ADDR), timeoutMs = 1500)
        }.getOrNull()

        if (regResp != null && regResp.isSuccess) {
            return EspChip.fromMagicRegister(regResp.value)
        }
        onLog("[CHIP] 使用通用 ESP32 配置")
        return EspChip.UNKNOWN
    }

    private suspend fun sendCommand(
        port: UsbSerialPort,
        decoder: SlipStreamDecoder,
        op: Byte,
        payload: ByteArray,
        checksum: Int = 0,
        timeoutMs: Int = 3000,
        isStub: Boolean = false
    ): EspResponse {
        val packet = EspSlipProtocol.buildCommandPacket(op, payload, checksum)
        var offset = 0
        while (offset < packet.size) {
            val chunkLen = minOf(512, packet.size - offset)
            val slice = if (offset == 0 && chunkLen == packet.size) packet else packet.copyOfRange(offset, offset + chunkLen)
            port.write(slice, 2000)
            offset += chunkLen
        }

        val resp = readResponse(port, decoder, op, timeoutMs, isStub)
            ?: throw IOException("等待芯片响应超时 (cmd 0x${(op.toInt() and 0xFF).toString(16)})")
        if (!resp.isSuccess) {
            throw IOException("命令执行失败: ${resp.errorMessage()}")
        }
        return resp
    }

    private suspend fun readResponse(
        port: UsbSerialPort,
        decoder: SlipStreamDecoder,
        expectedOp: Byte,
        timeoutMs: Int,
        isStub: Boolean
    ): EspResponse? {
        val deadline = System.currentTimeMillis() + timeoutMs
        val readBuf = ByteArray(512)
        while (System.currentTimeMillis() < deadline) {
            if (!currentCoroutineContext().isActive) throw CancellationException()
            val remaining = (deadline - System.currentTimeMillis()).coerceIn(20L, 200L).toInt()
            val n = runCatching { port.read(readBuf, remaining) }.getOrDefault(0)
            if (n > 0) {
                val frames = decoder.feed(readBuf, n)
                for (frame in frames) {
                    val parsed = EspSlipProtocol.parseResponsePacket(frame, isStub)
                    if (parsed != null && parsed.command == expectedOp) {
                        return parsed
                    }
                }
            }
        }
        return null
    }

    private fun drainInput(port: UsbSerialPort, durationMs: Long) {
        val end = System.currentTimeMillis() + durationMs
        val buf = ByteArray(512)
        while (System.currentTimeMillis() < end) {
            val n = runCatching { port.read(buf, 30) }.getOrDefault(0)
            if (n <= 0) break
        }
    }

    private suspend fun enterBootloaderClassic(port: UsbSerialPort, alternateDelay: Boolean) {
        runCatching {
            port.dtr = false
            port.rts = true
            delay(if (alternateDelay) 150 else 100)
            port.dtr = true
            port.rts = false
            delay(if (alternateDelay) 100 else 50)
            port.dtr = false
        }
    }

    private suspend fun enterBootloaderUsbJtag(port: UsbSerialPort) {
        runCatching {
            port.rts = false
            port.dtr = false
            delay(100)
            port.dtr = true
            port.rts = false
            delay(100)
            port.rts = true
            port.dtr = false
            port.rts = true
            delay(100)
            port.dtr = false
            port.rts = false
            delay(100)
        }
    }

    private suspend fun hardReset(port: UsbSerialPort) {
        runCatching {
            port.dtr = false
            port.rts = true
            delay(100)
            port.rts = false
        }
    }

    private fun compressZlib(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_SPEED)
        deflater.setInput(input)
        deflater.finish()
        val out = ByteArrayOutputStream(input.size / 2)
        val buffer = ByteArray(16 * 1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            if (count > 0) out.write(buffer, 0, count)
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> "%.2f MB".format(bytes / (1024f * 1024f))
            bytes >= 1024 -> "%.1f KB".format(bytes / 1024f)
            else -> "$bytes B"
        }
    }
}
