package com.m5burner.duo.flasher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EspPartitionTableTest {

    private fun makeEntry(type: Int, subtype: Int, offset: Int, size: Int, name: String): ByteArray {
        val bb = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
        bb.put(0xAA.toByte())
        bb.put(0x50.toByte())
        bb.put(type.toByte())
        bb.put(subtype.toByte())
        bb.putInt(offset)
        bb.putInt(size)
        val nameBytes = name.toByteArray(Charsets.US_ASCII)
        val padded = ByteArray(16)
        System.arraycopy(nameBytes, 0, padded, 0, minOf(nameBytes.size, 15))
        bb.put(padded)
        bb.putInt(0)
        return bb.array()
    }

    @Test
    fun parsesAppNvsAndLittleFsPartitionsAndInfers8MbFlash() {
        val table = makeEntry(0x01, 0x02, 0x9000, 0x5000, "nvs") +
            makeEntry(0x00, 0x10, 0x10000, 0x300000, "app0") +
            makeEntry(0x01, 0x82, 0x410000, 0x3F0000, "littlefs") +
            ByteArray(32) { 0xFF.toByte() }

        val entries = EspPartitionTable.parseTableBytes(table)
        assertEquals(3, entries.size)
        assertEquals(PartitionCategory.NVS, entries[0].category)
        assertEquals(PartitionCategory.APP, entries[1].category)
        assertEquals(PartitionCategory.LITTLEFS, entries[2].category)

        val tags = EspPartitionTable.summarizeTags(entries)
        assertTrue(tags.contains("固件 App"))
        assertTrue(tags.contains("NVS 配置"))
        assertTrue(tags.contains("LittleFS 数据"))

        // 0x410000 + 0x3F0000 = 0x800000 (8 MB)
        assertEquals(8 * 1024 * 1024, EspPartitionTable.inferFlashSizeFromPartitions(entries))
    }

    @Test
    fun decodesBootloaderHeaderFlashSizeForEsp32AndS3() {
        // ESP32-S3 header at 0x0: magic=0xE9, byte[3] upper nibble 4 -> 16MB
        val s3Header = ByteArray(16)
        s3Header[0] = 0xE9.toByte()
        s3Header[3] = 0x4F.toByte()
        assertEquals(16 * 1024 * 1024, EspPartitionTable.parseFlashSizeFromBootHeader(s3Header))

        // ESP32 header at 0x1000: padded 0xFF before 0x1000, upper nibble 3 -> 8MB
        val esp32Header = ByteArray(0x1010) { 0xFF.toByte() }
        esp32Header[0x1000] = 0xE9.toByte()
        esp32Header[0x1003] = 0x3F.toByte()
        assertEquals(8 * 1024 * 1024, EspPartitionTable.parseFlashSizeFromBootHeader(esp32Header))
    }

    @Test
    fun dataBackupBundleRoundTripsMultipleDataPartitions() {
        val tmpZip = java.io.File.createTempFile("m5data_test_", ".zip")
        try {
            val nvsEntry = EspPartitionEntry("nvs", 0x01, 0x02, 0x9000, 16, PartitionCategory.NVS)
            val lfsEntry = EspPartitionEntry("littlefs", 0x01, 0x82, 0x410000, 32, PartitionCategory.LITTLEFS)
            val nvsBytes = ByteArray(16) { (it + 1).toByte() }
            val lfsBytes = ByteArray(32) { (0xA0 + it).toByte() }

            DataBackupBundle.writeBundle(
                outputZip = tmpZip,
                chipName = "ESP32-S3",
                segments = listOf(
                    FlashSegmentData(nvsEntry, nvsBytes, "1111"),
                    FlashSegmentData(lfsEntry, lfsBytes, "2222")
                )
            )

            val restored = DataBackupBundle.readBundleSegments(tmpZip)
            assertEquals(2, restored.size)
            assertEquals(0x9000, restored[0].entry.offset)
            assertEquals(PartitionCategory.NVS, restored[0].entry.category)
            assertTrue(restored[0].bytes.contentEquals(nvsBytes))
            assertEquals(0x410000, restored[1].entry.offset)
            assertEquals(PartitionCategory.LITTLEFS, restored[1].entry.category)
            assertTrue(restored[1].bytes.contentEquals(lfsBytes))
        } finally {
            tmpZip.delete()
        }
    }
}
