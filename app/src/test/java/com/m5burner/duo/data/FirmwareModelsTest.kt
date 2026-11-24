package com.m5burner.duo.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.m5burner.duo.data.model.CategoryFormatter
import com.m5burner.duo.data.model.FirmwareItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FirmwareModelsTest {

    @Test
    fun parsesRealM5BurnerApiFormatAndBuildsCdnUrls() {
        val sampleJson = """
            [
              {
                "_id": "64f06eac252d5fad136699d2",
                "fid": "39d70217ff0b53c368b0efbfbf2a11af",
                "name": "UIFlow2.0 StickC Plus2",
                "description": "For stickc plus2",
                "cover": "17c3a34b8f0578d104f295df92b6d02a.png",
                "category": "stickc",
                "author": "M5Stack",
                "download": 69564,
                "versions": [
                  {
                    "version": "v2.5.4",
                    "published_at": "2026-10-30",
                    "file": "05a12970181e913c95b02847630af2ca.bin",
                    "published": true
                  },
                  {
                    "version": "v2.5.5",
                    "published_at": "2026-11-18",
                    "file": "2d2254a124096866bf41f3a7dce4c24d.bin",
                    "published": true
                  }
                ]
              }
            ]
        """.trimIndent()

        val listType = object : TypeToken<List<FirmwareItem>>() {}.type
        val parsed: List<FirmwareItem> = Gson().fromJson(sampleJson, listType)

        assertEquals(1, parsed.size)
        val item = parsed[0]
        assertEquals("UIFlow2.0 StickC Plus2", item.name)
        assertEquals(
            "https://m5burner-cdn.m5stack.com/cover/17c3a34b8f0578d104f295df92b6d02a.png",
            item.coverUrl
        )
        assertNotNull(item.latestVersion)
        assertEquals("v2.5.5", item.latestVersion!!.version)
        assertEquals(
            "https://m5burner-cdn.m5stack.com/firmware/2d2254a124096866bf41f3a7dce4c24d.bin",
            item.latestVersion!!.downloadUrl
        )
        assertEquals("StickC / Plus2", CategoryFormatter.formatLabel(item.category))
    }
}
