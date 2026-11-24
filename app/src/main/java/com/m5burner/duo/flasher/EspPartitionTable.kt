package com.m5burner.duo.flasher

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class PartitionCategory(val label: String, val isUserData: Boolean) {
    APP("固件 App", false),
    NVS("NVS 配置", true),
    SPIFFS("SPIFFS 数据", true),
    LITTLEFS("LittleFS 数据", true),
    FATFS("FATFS 存储", true),
    OTADATA("OTA 状态", false),
    OTHER("其他分区", false)
}

data class EspPartitionEntry(
    val name: String,
    val type: Int,
    val subtype: Int,
    val offset: Int,
    val size: Int,
    val category: PartitionCategory
) {
    val endOffset: Int get() = offset + size
    val formattedSize: String
        get() = when {
            size >= 1024 * 1024 -> "%.1fMB".format(size / (1024f * 1024f))
            size >= 1024 -> "${size / 1024}KB"
            else -> "${size}B"
        }
}

/**
 * Pure Kotlin ESP-IDF Partition Table (0x8000) & Bootloader Header parser.
 * Allows inspecting exactly what firmware + user data regions live inside a device or .bin backup.
 */
object EspPartitionTable {
    const val TABLE_OFFSET = 0x8000
    const val TABLE_SIZE = 0xC00 // 3072 bytes (up to 95 entries)
    private const val ENTRY_SIZE = 32
    private const val MAGIC_0: Byte = 0xAA.toByte()
    private const val MAGIC_1: Byte = 0x50.toByte()

    fun parseTableBytes(tableBytes: ByteArray): List<EspPartitionEntry> {
        val result = mutableListOf<EspPartitionEntry>()
        val maxEntries = tableBytes.size / ENTRY_SIZE
        for (i in 0 until maxEntries) {
            val base = i * ENTRY_SIZE
            if (tableBytes[base] != MAGIC_0 || tableBytes[base + 1] != MAGIC_1) {
                // End marker (0xEB 0xEB MD5 entry or 0xFF padding)
                break
            }
            val type = tableBytes[base + 2].toInt() and 0xFF
            val subtype = tableBytes[base + 3].toInt() and 0xFF
            val bb = ByteBuffer.wrap(tableBytes, base + 4, 8).order(ByteOrder.LITTLE_ENDIAN)
            val offset = bb.int
            val size = bb.int

            val nameEnd = (0 until 16).firstOrNull { tableBytes[base + 12 + it] == 0.toByte() } ?: 16
            val name = String(tableBytes, base + 12, nameEnd, Charsets.US_ASCII).trim()
            if (size <= 0 || offset < 0) continue

            val category = classify(type, subtype, name)
            result.add(
                EspPartitionEntry(
                    name = name.ifBlank { category.label },
                    type = type,
                    subtype = subtype,
                    offset = offset,
                    size = size,
                    category = category
                )
            )
        }
        return result
    }

    fun parseFromBinaryFile(file: File): List<EspPartitionEntry> {
        if (!file.exists() || file.length() < TABLE_OFFSET + ENTRY_SIZE) return emptyList()
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val toRead = minOf(TABLE_SIZE.toLong(), file.length() - TABLE_OFFSET).toInt()
                val buf = ByteArray(toRead)
                raf.seek(TABLE_OFFSET.toLong())
                raf.readFully(buf)
                parseTableBytes(buf)
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Parses ESP image header magic (0xE9) at offset 0x0 (ESP32-S3/C3/C6/S2) or 0x1000 (ESP32)
     * to read the compiled flash size nibble.
     */
    fun parseFlashSizeFromBootHeader(headerBytes: ByteArray): Int? {
        for (candidateOffset in listOf(0x0, 0x1000)) {
            if (headerBytes.size > candidateOffset + 3 && headerBytes[candidateOffset] == 0xE9.toByte()) {
                val sizeCode = (headerBytes[candidateOffset + 3].toInt() and 0xF0) ushr 4
                return when (sizeCode) {
                    0 -> 1 * 1024 * 1024
                    1 -> 2 * 1024 * 1024
                    2 -> 4 * 1024 * 1024
                    3 -> 8 * 1024 * 1024
                    4 -> 16 * 1024 * 1024
                    5 -> 32 * 1024 * 1024
                    else -> null
                }
            }
        }
        return null
    }

    /**
     * Rounds a maximum partition end address up to the smallest standard SPI flash capacity (2/4/8/16/32MB).
     */
    fun inferFlashSizeFromPartitions(entries: List<EspPartitionEntry>): Int? {
        val maxEnd = entries.maxOfOrNull { it.endOffset } ?: return null
        val standardSizesMb = listOf(2, 4, 8, 16, 32)
        for (mb in standardSizesMb) {
            if (maxEnd <= mb * 1024 * 1024) return mb * 1024 * 1024
        }
        return 16 * 1024 * 1024
    }

    fun summarizeTags(entries: List<EspPartitionEntry>): List<String> {
        if (entries.isEmpty()) return emptyList()
        val tags = LinkedHashSet<String>()
        if (entries.any { it.category == PartitionCategory.APP }) tags.add("固件 App")
        if (entries.any { it.category == PartitionCategory.NVS }) tags.add("NVS 配置")
        if (entries.any { it.category == PartitionCategory.LITTLEFS }) tags.add("LittleFS 数据")
        if (entries.any { it.category == PartitionCategory.SPIFFS }) tags.add("SPIFFS 数据")
        if (entries.any { it.category == PartitionCategory.FATFS }) tags.add("FATFS 存储")
        return tags.toList()
    }

    private fun classify(type: Int, subtype: Int, name: String): PartitionCategory {
        val lower = name.lowercase()
        return when (type) {
            0x00 -> PartitionCategory.APP
            0x01 -> when (subtype) {
                0x00 -> PartitionCategory.OTADATA
                0x02, 0x04 -> PartitionCategory.NVS
                0x81 -> PartitionCategory.FATFS
                0x82 -> if (lower.contains("littlefs")) PartitionCategory.LITTLEFS else PartitionCategory.SPIFFS
                0x83 -> PartitionCategory.LITTLEFS
                else -> when {
                    lower.contains("nvs") -> PartitionCategory.NVS
                    lower.contains("littlefs") -> PartitionCategory.LITTLEFS
                    lower.contains("spiffs") -> PartitionCategory.SPIFFS
                    lower.contains("fat") || lower.contains("ffat") || lower.contains("storage") -> PartitionCategory.FATFS
                    else -> PartitionCategory.OTHER
                }
            }
            else -> PartitionCategory.OTHER
        }
    }
}
