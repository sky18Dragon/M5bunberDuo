package com.m5burner.duo.flasher

import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class DataSegmentEntry(
    val name: String = "",
    val type: Int = 1,
    val subtype: Int = 0,
    val offset: Int = 0,
    val size: Int = 0,
    val categoryName: String = PartitionCategory.OTHER.name,
    val fileName: String = "",
    val md5: String = ""
)

data class DataBundleManifest(
    val formatVersion: Int = 1,
    val chipName: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val segments: List<DataSegmentEntry> = emptyList()
)

data class FlashSegmentData(
    val entry: EspPartitionEntry,
    val bytes: ByteArray,
    val md5Hex: String = ""
)

/**
 * Pure Kotlin reader/writer for self-contained M5Burner Duo Data-Only Backup bundles (.zip).
 * Each zip contains manifest.json plus <partition>_0x<offset>.bin files so users can restore on-device
 * or unzip on desktop to inspect individual NVS / LittleFS / SPIFFS / FATFS dumps.
 */
object DataBackupBundle {
    private const val MANIFEST_NAME = "manifest.json"
    private val gson = Gson()

    fun writeBundle(
        outputZip: File,
        chipName: String,
        segments: List<FlashSegmentData>
    ): DataBundleManifest {
        val manifestEntries = mutableListOf<DataSegmentEntry>()
        ZipOutputStream(FileOutputStream(outputZip)).use { zos ->
            segments.forEachIndexed { idx, seg ->
                val safeName = seg.entry.name.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "part$idx" }
                val binName = "${safeName}_0x${seg.entry.offset.toString(16)}.bin"
                zos.putNextEntry(ZipEntry(binName))
                zos.write(seg.bytes)
                zos.closeEntry()

                manifestEntries.add(
                    DataSegmentEntry(
                        name = seg.entry.name,
                        type = seg.entry.type,
                        subtype = seg.entry.subtype,
                        offset = seg.entry.offset,
                        size = seg.bytes.size,
                        categoryName = seg.entry.category.name,
                        fileName = binName,
                        md5 = seg.md5Hex
                    )
                )
            }

            val manifest = DataBundleManifest(
                chipName = chipName,
                createdAtMillis = System.currentTimeMillis(),
                segments = manifestEntries
            )
            zos.putNextEntry(ZipEntry(MANIFEST_NAME))
            zos.write(gson.toJson(manifest).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            return manifest
        }
    }

    fun readBundleSegments(zipFile: File): List<FlashSegmentData> {
        ZipFile(zipFile).use { zf ->
            val manifestEntry = zf.getEntry(MANIFEST_NAME)
                ?: throw IllegalArgumentException("无效的数据备份包（缺少 manifest.json）")
            val manifestJson = zf.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
            val manifest = gson.fromJson(manifestJson, DataBundleManifest::class.java)
                ?: throw IllegalArgumentException("无法解析备份清单")

            return manifest.segments.map { seg ->
                val ze = zf.getEntry(seg.fileName)
                    ?: throw IllegalArgumentException("备份包缺少分区文件: ${seg.fileName}")
                val raw = zf.getInputStream(ze).use { input ->
                    val out = ByteArrayOutputStream(seg.size.coerceAtLeast(1024))
                    input.copyTo(out)
                    out.toByteArray()
                }
                val cat = runCatching { PartitionCategory.valueOf(seg.categoryName) }
                    .getOrDefault(PartitionCategory.OTHER)
                FlashSegmentData(
                    entry = EspPartitionEntry(
                        name = seg.name,
                        type = seg.type,
                        subtype = seg.subtype,
                        offset = seg.offset,
                        size = raw.size,
                        category = cat
                    ),
                    bytes = raw,
                    md5Hex = seg.md5
                )
            }
        }
    }
}
