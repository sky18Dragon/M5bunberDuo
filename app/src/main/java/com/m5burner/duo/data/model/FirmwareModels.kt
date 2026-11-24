package com.m5burner.duo.data.model

import com.google.gson.annotations.SerializedName

data class FirmwareItem(
    @SerializedName("_id") val id: String = "",
    @SerializedName("fid") val fid: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("cover") val cover: String = "",
    @SerializedName("category") val category: String = "",
    @SerializedName("tags") val tags: List<String>? = emptyList(),
    @SerializedName("author") val author: String = "",
    @SerializedName("github") val github: String = "",
    @SerializedName("download") val downloadCount: Int = 0,
    @SerializedName("versions") val versions: List<FirmwareVersion>? = emptyList(),
    @SerializedName("network") val network: String? = null,
    @SerializedName("odevice") val odevice: Boolean? = null
) {
    val stableId: String
        get() = id.ifBlank { fid.ifBlank { "${category}_${name}" } }

    val publishedVersions: List<FirmwareVersion>
        get() = (versions ?: emptyList())
            .filter { it.file.isNotBlank() && it.published != false }
            .reversed() // newest first

    val latestVersion: FirmwareVersion?
        get() = publishedVersions.firstOrNull()

    val coverUrl: String?
        get() = cover.takeIf { it.isNotBlank() }?.let {
            if (it.startsWith("http://") || it.startsWith("https://")) it
            else "$CDN_COVER_BASE$it"
        }

    val normalizedCategory: String
        get() = category.trim().lowercase()

    companion object {
        const val API_FIRMWARE_URL = "https://m5burner-api.m5stack.com/api/firmware"
        const val CDN_COVER_BASE = "https://m5burner-cdn.m5stack.com/cover/"
        const val CDN_FIRMWARE_BASE = "https://m5burner-cdn.m5stack.com/firmware/"

        fun firmwareFileUrl(file: String): String {
            return if (file.startsWith("http://") || file.startsWith("https://")) {
                file
            } else {
                "$CDN_FIRMWARE_BASE$file"
            }
        }
    }
}

data class FirmwareVersion(
    @SerializedName("version") val version: String = "",
    @SerializedName("published_at") val publishedAt: String = "",
    @SerializedName("file") val file: String = "",
    @SerializedName("published") val published: Boolean? = true,
    @SerializedName("change_log") val changeLog: String? = ""
) {
    val downloadUrl: String
        get() = FirmwareItem.firmwareFileUrl(file)
}

data class CategoryInfo(
    val key: String,
    val label: String,
    val count: Int
)

enum class FirmwareSortMode(val label: String) {
    POPULAR("Most Downloads"),
    RECENT("Recently Updated"),
    NAME("Name A-Z")
}

data class FlashConfig(
    val baudRate: Int = 921600,
    val flashAddress: Int = 0x0,
    val useCompression: Boolean = true,
    val autoResetAfterFlash: Boolean = true
) {
    companion object {
        val SUPPORTED_BAUD_RATES = listOf(115200, 460800, 921600, 1500000)
    }
}

object CategoryFormatter {
    private val displayMap = mapOf(
        "all" to "All Devices",
        "cardputer" to "Cardputer",
        "stickc" to "StickC / Plus2",
        "sticks3" to "StickS3",
        "core" to "Core / Basic",
        "core2 & tough" to "Core2 & Tough",
        "cores3" to "CoreS3",
        "atoms3" to "AtomS3",
        "atom" to "Atom",
        "paper" to "M5Paper",
        "tab5" to "Tab5",
        "stamps3" to "StampS3",
        "stamp" to "Stamp",
        "stackchan" to "StackChan",
        "dial" to "M5Dial",
        "nanoc6" to "NanoC6",
        "coreink" to "CoreInk",
        "dinmeter" to "DinMeter",
        "timercam" to "TimerCam",
        "capsule" to "Capsule",
        "station" to "Station",
        "stopwatch" to "Stopwatch",
        "airq" to "AirQ",
        "unitc6l" to "UnitC6L",
        "unit-poe-p4" to "Unit PoE P4",
        "esp32-h2" to "ESP32-H2"
    )

    fun formatLabel(rawKey: String): String {
        val k = rawKey.trim().lowercase()
        return displayMap[k] ?: rawKey.split(" ").joinToString(" ") { part ->
            part.replaceFirstChar { c -> c.uppercase() }
        }
    }
}
