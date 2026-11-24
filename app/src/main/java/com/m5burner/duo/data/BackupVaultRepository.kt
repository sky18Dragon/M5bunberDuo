package com.m5burner.duo.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.m5burner.duo.flasher.EspPartitionEntry
import com.m5burner.duo.flasher.EspPartitionTable
import com.m5burner.duo.flasher.PartitionCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class BackupSourceType(val label: String) {
    DEVICE_BACKUP("全片备份"),
    DATA_ONLY("纯数据备份"),
    LOCAL_IMPORT("本地导入")
}

data class SavedPartitionMeta(
    val name: String = "",
    val type: Int = 0,
    val subtype: Int = 0,
    val offset: Int = 0,
    val size: Int = 0,
    val categoryName: String = PartitionCategory.OTHER.name
) {
    fun toEntry(): EspPartitionEntry {
        val cat = runCatching { PartitionCategory.valueOf(categoryName) }.getOrDefault(PartitionCategory.OTHER)
        return EspPartitionEntry(name, type, subtype, offset, size, cat)
    }

    companion object {
        fun fromEntry(e: EspPartitionEntry) = SavedPartitionMeta(
            name = e.name,
            type = e.type,
            subtype = e.subtype,
            offset = e.offset,
            size = e.size,
            categoryName = e.category.name
        )
    }
}

data class FirmwareBackupRecord(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val chipName: String = "ESP32",
    val deviceLabel: String = "",
    val sourceType: BackupSourceType = BackupSourceType.DEVICE_BACKUP,
    val fileName: String = "",
    val sizeBytes: Long = 0L,
    val flashOffset: Int = 0x0,
    val md5: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val note: String = "",
    val contentTags: List<String>? = emptyList(),
    val partitions: List<SavedPartitionMeta>? = emptyList()
) {
    val safeTags: List<String> get() = contentTags ?: emptyList()

    val dataPartitions: List<EspPartitionEntry>
        get() = (partitions ?: emptyList())
            .map { it.toEntry() }
            .filter { it.category.isUserData }

    val hasUserDataPartitions: Boolean
        get() = dataPartitions.isNotEmpty()

    val formattedSize: String
        get() = when {
            sizeBytes >= 1024L * 1024L -> "%.2f MB".format(sizeBytes / (1024f * 1024f))
            sizeBytes >= 1024L -> "%.1f KB".format(sizeBytes / 1024f)
            else -> "$sizeBytes B"
        }

    val formattedDate: String
        get() = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(createdAtMillis))

    val shortMd5: String
        get() = if (md5.length >= 8) md5.take(8).uppercase() else md5

    val isDataOnlyBundle: Boolean
        get() = sourceType == BackupSourceType.DATA_ONLY || fileName.endsWith(".zip", ignoreCase = true)

    val exportMimeType: String
        get() = if (isDataOnlyBundle) "application/zip" else "application/octet-stream"

    val suggestedExportFileName: String
        get() {
            val safe = title.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5._-]"), "_").ifBlank { "m5_backup" }
            val ext = if (isDataOnlyBundle) ".zip" else ".bin"
            return if (safe.endsWith(ext, ignoreCase = true)) safe else "$safe$ext"
        }
}

class BackupVaultRepository(private val context: Context) {

    private val gson = Gson()

    private val vaultDir: File
        get() = File(context.filesDir, "vault").apply { if (!exists()) mkdirs() }

    private val binsDir: File
        get() = File(vaultDir, "bins").apply { if (!exists()) mkdirs() }

    private val indexFile: File
        get() = File(vaultDir, "vault_index.json")

    fun createTargetBackupFile(chipName: String, isDataOnly: Boolean = false): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeChip = chipName.lowercase().replace(Regex("[^a-z0-9]"), "")
        val prefix = if (isDataOnly) "data" else "backup"
        val ext = if (isDataOnly) "zip" else "bin"
        return File(binsDir, "${prefix}_${safeChip}_$stamp.$ext")
    }

    fun resolveBinaryFile(record: FirmwareBackupRecord): File = File(binsDir, record.fileName)

    suspend fun loadRecords(): List<FirmwareBackupRecord> = withContext(Dispatchers.IO) {
        if (!indexFile.exists()) return@withContext emptyList()
        runCatching {
            val type = object : TypeToken<List<FirmwareBackupRecord>>() {}.type
            val list: List<FirmwareBackupRecord> = gson.fromJson(indexFile.readText(), type) ?: emptyList()
            val valid = list.filter { resolveBinaryFile(it).exists() }
                .map { rec ->
                    // Backfill partition tags for older backups seamlessly
                    if (rec.partitions.isNullOrEmpty()) {
                        val parsed = EspPartitionTable.parseFromBinaryFile(resolveBinaryFile(rec))
                        if (parsed.isNotEmpty()) {
                            rec.copy(
                                contentTags = EspPartitionTable.summarizeTags(parsed),
                                partitions = parsed.map { SavedPartitionMeta.fromEntry(it) }
                            )
                        } else rec
                    } else rec
                }
                .sortedByDescending { it.createdAtMillis }
            if (valid != list) saveIndex(valid)
            valid
        }.getOrDefault(emptyList())
    }

    suspend fun registerDeviceBackup(
        binFile: File,
        chipName: String,
        deviceLabel: String,
        title: String,
        note: String = "",
        flashOffset: Int = 0x0,
        precomputedMd5: String? = null,
        parsedPartitions: List<EspPartitionEntry> = emptyList(),
        sourceType: BackupSourceType = BackupSourceType.DEVICE_BACKUP
    ): FirmwareBackupRecord = withContext(Dispatchers.IO) {
        val md5 = precomputedMd5 ?: computeMd5(binFile)
        val parts = if (parsedPartitions.isNotEmpty() || sourceType == BackupSourceType.DATA_ONLY) {
            parsedPartitions
        } else {
            EspPartitionTable.parseFromBinaryFile(binFile)
        }
        val tags = EspPartitionTable.summarizeTags(parts)

        val record = FirmwareBackupRecord(
            title = title.ifBlank {
                if (sourceType == BackupSourceType.DATA_ONLY) "$chipName 纯数据备份" else "$chipName 完整备份"
            },
            chipName = chipName,
            deviceLabel = deviceLabel,
            sourceType = sourceType,
            fileName = binFile.name,
            sizeBytes = binFile.length(),
            flashOffset = flashOffset,
            md5 = md5,
            createdAtMillis = System.currentTimeMillis(),
            note = note,
            contentTags = tags,
            partitions = parts.map { SavedPartitionMeta.fromEntry(it) }
        )
        val updated = listOf(record) + loadRecords()
        saveIndex(updated)
        record
    }

    suspend fun importLocalBinary(
        uri: Uri,
        displayName: String,
        flashOffset: Int = 0x0
    ): Result<FirmwareBackupRecord> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanBase = displayName.substringBeforeLast('.').ifBlank { "local_firmware" }
            val safeFile = "import_${System.currentTimeMillis()}_${cleanBase.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.bin"
            val dest = File(binsDir, safeFile)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("无法读取选中的本地文件")

            if (dest.length() == 0L) {
                dest.delete()
                throw IllegalStateException("文件为空")
            }

            val parts = EspPartitionTable.parseFromBinaryFile(dest)
            val tags = EspPartitionTable.summarizeTags(parts)
            val record = FirmwareBackupRecord(
                title = cleanBase,
                chipName = "本地镜像",
                deviceLabel = displayName,
                sourceType = BackupSourceType.LOCAL_IMPORT,
                fileName = dest.name,
                sizeBytes = dest.length(),
                flashOffset = flashOffset,
                md5 = computeMd5(dest),
                createdAtMillis = System.currentTimeMillis(),
                note = "本地导入 (.bin)",
                contentTags = tags,
                partitions = parts.map { SavedPartitionMeta.fromEntry(it) }
            )
            val updated = listOf(record) + loadRecords()
            saveIndex(updated)
            record
        }
    }

    suspend fun exportBackupToUri(record: FirmwareBackupRecord, destUri: Uri): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                val src = resolveBinaryFile(record)
                if (!src.exists()) throw IllegalStateException("备份文件不存在")
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    src.inputStream().use { input -> input.copyTo(out) }
                    out.flush()
                } ?: throw IllegalStateException("无法写入目标存储位置")
                src.length()
            }
        }

    suspend fun updateRecordMeta(id: String, newTitle: String, newNote: String): List<FirmwareBackupRecord> =
        withContext(Dispatchers.IO) {
            val updated = loadRecords().map {
                if (it.id == id) it.copy(title = newTitle.trim().ifBlank { it.title }, note = newNote.trim())
                else it
            }
            saveIndex(updated)
            updated
        }

    suspend fun deleteRecord(id: String): List<FirmwareBackupRecord> = withContext(Dispatchers.IO) {
        val current = loadRecords()
        val target = current.firstOrNull { it.id == id }
        if (target != null) resolveBinaryFile(target).delete()
        val remaining = current.filterNot { it.id == id }
        saveIndex(remaining)
        remaining
    }

    private fun saveIndex(records: List<FirmwareBackupRecord>) {
        indexFile.writeText(gson.toJson(records))
    }

    companion object {
        fun computeMd5(file: File): String {
            val digest = MessageDigest.getInstance("MD5")
            FileInputStream(file).use { fis ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val read = fis.read(buf)
                    if (read == -1) break
                    digest.update(buf, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
