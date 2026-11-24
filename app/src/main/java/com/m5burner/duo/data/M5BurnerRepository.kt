package com.m5burner.duo.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.m5burner.duo.data.model.FirmwareItem
import com.m5burner.duo.data.model.FirmwareVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class M5BurnerRepository(private val context: Context) {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val catalogCacheFile: File
        get() = File(context.cacheDir, "m5burner_catalog_v1.json")

    private val firmwareCacheDir: File
        get() = File(context.filesDir, "firmwares").apply { if (!exists()) mkdirs() }

    suspend fun loadCachedCatalog(): List<FirmwareItem> = withContext(Dispatchers.IO) {
        val file = catalogCacheFile
        if (!file.exists() || file.length() == 0L) return@withContext emptyList()
        runCatching {
            parseCatalogJson(file.readText())
        }.getOrDefault(emptyList())
    }

    suspend fun fetchOnlineCatalog(): Result<List<FirmwareItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(FirmwareItem.API_FIRMWARE_URL)
                .header("User-Agent", "M5BurnerDuo/1.0 (Retroid Pocket Duo; Android)")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("M5Burner API HTTP ${response.code}: ${response.message}")
                }
                val body = response.body?.string()
                    ?: throw IOException("Empty response from M5Burner API")
                val parsed = parseCatalogJson(body)
                if (parsed.isNotEmpty()) {
                    catalogCacheFile.writeText(body)
                }
                parsed
            }
        }
    }

    fun isFirmwareDownloaded(version: FirmwareVersion): Boolean {
        val target = getFirmwareCacheFile(version.file)
        return target.exists() && target.length() > 0L
    }

    fun getFirmwareCacheFile(fileName: String): File {
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(firmwareCacheDir, safeName)
    }

    suspend fun downloadFirmware(
        version: FirmwareVersion,
        forceRedownload: Boolean = false,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val targetFile = getFirmwareCacheFile(version.file)
            if (!forceRedownload && targetFile.exists() && targetFile.length() > 0L) {
                val len = targetFile.length()
                onProgress(len, len)
                return@runCatching targetFile
            }

            val tmpFile = File(firmwareCacheDir, "${targetFile.name}.tmp")
            val request = Request.Builder()
                .url(version.downloadUrl)
                .header("User-Agent", "M5BurnerDuo/1.0 (Retroid Pocket Duo; Android)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Firmware CDN HTTP ${response.code}: ${response.message}")
                }
                val body = response.body ?: throw IOException("Empty firmware binary response")
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var lastReport = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            val now = System.currentTimeMillis()
                            if (now - lastReport >= 80L || (totalBytes > 0 && downloadedBytes == totalBytes)) {
                                lastReport = now
                                onProgress(downloadedBytes, totalBytes)
                            }
                        }
                        output.flush()
                    }
                }

                if (targetFile.exists()) targetFile.delete()
                if (!tmpFile.renameTo(targetFile)) {
                    tmpFile.copyTo(targetFile, overwrite = true)
                    tmpFile.delete()
                }
                onProgress(downloadedBytes, if (totalBytes > 0) totalBytes else downloadedBytes)
                targetFile
            }
        }
    }

    internal fun parseCatalogJson(json: String): List<FirmwareItem> {
        val listType = object : TypeToken<List<FirmwareItem>>() {}.type
        val raw: List<FirmwareItem> = gson.fromJson(json, listType) ?: emptyList()
        return raw.filter { it.name.isNotBlank() && it.publishedVersions.isNotEmpty() }
    }
}
