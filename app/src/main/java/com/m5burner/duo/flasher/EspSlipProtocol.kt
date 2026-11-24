package com.m5burner.duo.flasher

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure Kotlin implementation of Espressif ESP32 ROM Bootloader & Flasher Stub SLIP protocol.
 */
object EspSlipProtocol {
    const val SLIP_END: Byte = 0xC0.toByte()
    const val SLIP_ESC: Byte = 0xDB.toByte()
    const val SLIP_ESC_END: Byte = 0xDC.toByte()
    const val SLIP_ESC_ESC: Byte = 0xDD.toByte()

    const val DIR_REQUEST: Byte = 0x00
    const val DIR_RESPONSE: Byte = 0x01

    // ROM Bootloader Opcodes
    const val CMD_FLASH_BEGIN: Byte = 0x02
    const val CMD_FLASH_DATA: Byte = 0x03
    const val CMD_FLASH_END: Byte = 0x04
    const val CMD_MEM_BEGIN: Byte = 0x05
    const val CMD_MEM_END: Byte = 0x06
    const val CMD_MEM_DATA: Byte = 0x07
    const val CMD_SYNC: Byte = 0x08
    const val CMD_READ_REG: Byte = 0x0A
    const val CMD_SPI_SET_PARAMS: Byte = 0x0B
    const val CMD_SPI_ATTACH: Byte = 0x0D
    const val CMD_CHANGE_BAUDRATE: Byte = 0x0F
    const val CMD_FLASH_DEFL_BEGIN: Byte = 0x10
    const val CMD_FLASH_DEFL_DATA: Byte = 0x11
    const val CMD_FLASH_DEFL_END: Byte = 0x12
    const val CMD_GET_SECURITY_INFO: Byte = 0x14

    // Flasher Stub Opcodes
    const val CMD_STUB_READ_FLASH: Byte = 0xD2.toByte()

    const val CHIP_DETECT_MAGIC_REG_ADDR: Int = 0x40001000
    const val CHECKSUM_SEED: Int = 0xEF
    const val ROM_FLASH_BLOCK_SIZE: Int = 0x400 // 1024 bytes (safe for all ESP ROM RX buffers)
    const val MEM_WRITE_BLOCK_SIZE: Int = 0x1800 // 6144 bytes for stub RAM upload
    const val STUB_READ_BLOCK_SIZE: Int = 0x1000 // 4096 bytes per READ_FLASH packet
    const val FLASH_SECTOR_SIZE: Int = 0x1000

    val STUB_GREETING: ByteArray = "OHAI".toByteArray(Charsets.US_ASCII)

    val SYNC_PAYLOAD: ByteArray = ByteArray(36) { idx ->
        when (idx) {
            0 -> 0x07
            1 -> 0x07
            2 -> 0x12
            3 -> 0x20
            else -> 0x55
        }
    }

    fun encodeSlip(payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(payload.size + 16)
        out.write(SLIP_END.toInt())
        for (b in payload) {
            when (b) {
                SLIP_END -> {
                    out.write(SLIP_ESC.toInt())
                    out.write(SLIP_ESC_END.toInt())
                }
                SLIP_ESC -> {
                    out.write(SLIP_ESC.toInt())
                    out.write(SLIP_ESC_ESC.toInt())
                }
                else -> out.write(b.toInt() and 0xFF)
            }
        }
        out.write(SLIP_END.toInt())
        return out.toByteArray()
    }

    fun computeChecksum(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        var chk = CHECKSUM_SEED
        for (i in offset until (offset + length)) {
            chk = chk xor (data[i].toInt() and 0xFF)
        }
        return chk and 0xFF
    }

    fun buildCommandPacket(op: Byte, payload: ByteArray, checksum: Int = 0): ByteArray {
        val buf = ByteBuffer.allocate(8 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(DIR_REQUEST)
        buf.put(op)
        buf.putShort(payload.size.toShort())
        buf.putInt(checksum)
        buf.put(payload)
        return encodeSlip(buf.array())
    }

    fun buildReadRegPayload(address: Int): ByteArray = packUInt32s(address)

    fun buildSpiAttachPayload(): ByteArray = ByteArray(8)

    fun buildSpiSetParamsPayload(
        flashSizeBytes: Int,
        blockSize: Int = 64 * 1024,
        sectorSize: Int = FLASH_SECTOR_SIZE,
        pageSize: Int = 256
    ): ByteArray = packUInt32s(
        0,
        flashSizeBytes,
        blockSize,
        sectorSize,
        pageSize,
        0xFFFF
    )

    fun buildChangeBaudPayload(newBaud: Int, oldBaud: Int = 0): ByteArray =
        packUInt32s(newBaud, oldBaud)

    fun buildMemBeginPayload(size: Int, numBlocks: Int, blockSize: Int, offset: Int): ByteArray =
        packUInt32s(size, numBlocks, blockSize, offset)

    fun buildMemEndPayload(entryPoint: Int): ByteArray =
        packUInt32s(0, entryPoint) // 0 = execute stub immediately

    fun buildReadFlashPayload(
        offset: Int,
        length: Int,
        packetSize: Int = STUB_READ_BLOCK_SIZE,
        maxInFlight: Int = 1
    ): ByteArray = packUInt32s(offset, length, packetSize, maxInFlight)

    fun buildFlashBeginPayload(
        eraseSize: Int,
        numBlocks: Int,
        blockSize: Int,
        offset: Int,
        includeEncryptFlag: Boolean
    ): ByteArray {
        return if (includeEncryptFlag) {
            packUInt32s(eraseSize, numBlocks, blockSize, offset, 0)
        } else {
            packUInt32s(eraseSize, numBlocks, blockSize, offset)
        }
    }

    fun buildFlashDataPayload(blockData: ByteArray, seq: Int): ByteArray {
        val buf = ByteBuffer.allocate(16 + blockData.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(blockData.size)
        buf.putInt(seq)
        buf.putInt(0)
        buf.putInt(0)
        buf.put(blockData)
        return buf.array()
    }

    fun buildFlashEndPayload(reboot: Boolean): ByteArray =
        packUInt32s(if (reboot) 0 else 1)

    fun packUInt32s(vararg values: Int): ByteArray {
        val buf = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (v in values) {
            buf.putInt(v)
        }
        return buf.array()
    }

    fun parseResponsePacket(rawPacket: ByteArray, isStub: Boolean = false): EspResponse? {
        if (rawPacket.size < 8) return null
        val buf = ByteBuffer.wrap(rawPacket).order(ByteOrder.LITTLE_ENDIAN)
        val direction = buf.get()
        if (direction != DIR_RESPONSE) return null
        val command = buf.get()
        val size = buf.short.toInt() and 0xFFFF
        val value = buf.int
        val dataAvailable = (rawPacket.size - 8).coerceAtLeast(0)
        val dataLen = minOf(size, dataAvailable)
        val data = rawPacket.copyOfRange(8, 8 + dataLen)

        val statusLen = if (isStub) 2 else 4
        val statusOffset = when {
            data.size >= statusLen -> data.size - statusLen
            data.size >= 2 -> data.size - 2
            else -> -1
        }
        val status = if (statusOffset >= 0) data[statusOffset].toInt() and 0xFF else 0
        val errorCode = if (statusOffset >= 0 && statusOffset + 1 < data.size) {
            data[statusOffset + 1].toInt() and 0xFF
        } else 0

        val payloadWithoutStatus = if (statusOffset > 0) data.copyOfRange(0, statusOffset) else ByteArray(0)

        return EspResponse(
            command = command,
            value = value,
            data = payloadWithoutStatus,
            rawData = data,
            status = status,
            errorCode = errorCode
        )
    }

    fun alignUpToSector(size: Int): Int {
        if (size <= 0) return FLASH_SECTOR_SIZE
        return ((size + FLASH_SECTOR_SIZE - 1) / FLASH_SECTOR_SIZE) * FLASH_SECTOR_SIZE
    }
}

data class EspResponse(
    val command: Byte,
    val value: Int,
    val data: ByteArray,
    val rawData: ByteArray,
    val status: Int,
    val errorCode: Int
) {
    val isSuccess: Boolean get() = status == 0

    fun errorMessage(): String {
        val desc = when (errorCode) {
            0x05 -> "Received message is invalid"
            0x06 -> "Failed to act on received message"
            0x07 -> "Invalid CRC / Checksum"
            0x08 -> "Flash write error"
            0x09 -> "Flash read error"
            0x0A -> "Flash read length error"
            0x0B -> "Deflate error"
            else -> "ROM error 0x${errorCode.toString(16)}"
        }
        return "status=$status, error=0x${errorCode.toString(16)} ($desc)"
    }
}

enum class EspChip(
    val displayName: String,
    val chipId: Int,
    val stubAssetName: String?,
    val usesEncryptParam: Boolean,
    val needsSpiAttach: Boolean
) {
    ESP32("ESP32", 0, "esp32.json", usesEncryptParam = false, needsSpiAttach = true),
    ESP32_S2("ESP32-S2", 2, "esp32s2.json", usesEncryptParam = true, needsSpiAttach = true),
    ESP32_C3("ESP32-C3", 5, "esp32c3.json", usesEncryptParam = true, needsSpiAttach = true),
    ESP32_S3("ESP32-S3", 9, "esp32s3.json", usesEncryptParam = true, needsSpiAttach = true),
    ESP32_C2("ESP32-C2", 12, "esp32c2.json", usesEncryptParam = true, needsSpiAttach = true),
    ESP32_C6("ESP32-C6", 13, "esp32c6.json", usesEncryptParam = true, needsSpiAttach = true),
    ESP32_H2("ESP32-H2", 16, "esp32h2.json", usesEncryptParam = true, needsSpiAttach = true),
    ESP32_P4("ESP32-P4", 18, null, usesEncryptParam = true, needsSpiAttach = true),
    UNKNOWN("ESP32 (Generic)", -1, "esp32.json", usesEncryptParam = false, needsSpiAttach = true);

    companion object {
        fun fromSecurityInfoChipId(chipId: Int): EspChip? {
            return entries.firstOrNull { it.chipId == chipId }
        }

        fun fromMagicRegister(magic: Int): EspChip {
            return when (magic) {
                0x00F01D83 -> ESP32
                0x000007C6 -> ESP32_S2
                0x00000009 -> ESP32_S3
                0x6921506F, 0x1B31506F, 0x4881606F, 0x4361606F -> ESP32_C3
                0x2CE0806F -> ESP32_C6
                0xD7B73E80.toInt() -> ESP32_H2
                0x6F51306F, 0x7C41A06F -> ESP32_C2
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Stateful SLIP stream decoder that extracts complete SLIP frames from arbitrary USB chunk reads.
 */
class SlipStreamDecoder {
    private var inFrame = false
    private var escaping = false
    private val buffer = ByteArrayOutputStream(4096)

    fun feed(bytes: ByteArray, length: Int = bytes.size): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        for (i in 0 until length) {
            val b = bytes[i]
            if (b == EspSlipProtocol.SLIP_END) {
                if (inFrame && buffer.size() > 0) {
                    frames.add(buffer.toByteArray())
                    buffer.reset()
                }
                inFrame = true
                escaping = false
            } else if (inFrame) {
                if (escaping) {
                    when (b) {
                        EspSlipProtocol.SLIP_ESC_END -> buffer.write(EspSlipProtocol.SLIP_END.toInt())
                        EspSlipProtocol.SLIP_ESC_ESC -> buffer.write(EspSlipProtocol.SLIP_ESC.toInt())
                        else -> buffer.write(b.toInt() and 0xFF)
                    }
                    escaping = false
                } else if (b == EspSlipProtocol.SLIP_ESC) {
                    escaping = true
                } else {
                    buffer.write(b.toInt() and 0xFF)
                }
            }
        }
        return frames
    }

    fun reset() {
        inFrame = false
        escaping = false
        buffer.reset()
    }
}
