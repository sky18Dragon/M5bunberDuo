package com.m5burner.duo.flasher

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EspSlipProtocolTest {

    @Test
    fun slipEncodeAndStreamingDecodeRoundTrip() {
        val raw = byteArrayOf(0x01, 0xC0.toByte(), 0x55, 0xDB.toByte(), 0xDC.toByte(), 0xDD.toByte(), 0xFF.toByte())
        val encoded = EspSlipProtocol.encodeSlip(raw)

        assertEquals(EspSlipProtocol.SLIP_END, encoded.first())
        assertEquals(EspSlipProtocol.SLIP_END, encoded.last())

        // Feed byte-by-byte with leading boot noise
        val decoder = SlipStreamDecoder()
        val noiseAndPacket = byteArrayOf(0x45, 0x53, 0x50) + encoded
        val frames = mutableListOf<ByteArray>()
        for (b in noiseAndPacket) {
            frames.addAll(decoder.feed(byteArrayOf(b), 1))
        }
        assertEquals(1, frames.size)
        assertArrayEquals(raw, frames[0])
    }

    @Test
    fun computeEspChecksumMatchesSeedXor() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        // 0xEF ^ 0x01 ^ 0x02 ^ 0x03 = 0xEF
        assertEquals(0xEF, EspSlipProtocol.computeChecksum(data))
        assertEquals(0xEF xor 0xAA, EspSlipProtocol.computeChecksum(byteArrayOf(0xAA.toByte())))
    }

    @Test
    fun flashBeginPayloadAdaptsToEsp32VsModernChips() {
        val esp32Payload = EspSlipProtocol.buildFlashBeginPayload(
            eraseSize = 0x10000,
            numBlocks = 16,
            blockSize = 0x400,
            offset = 0x0,
            includeEncryptFlag = false
        )
        assertEquals(16, esp32Payload.size)

        val esp32S3Payload = EspSlipProtocol.buildFlashBeginPayload(
            eraseSize = 0x10000,
            numBlocks = 16,
            blockSize = 0x400,
            offset = 0x0,
            includeEncryptFlag = true
        )
        assertEquals(20, esp32S3Payload.size)
        val bb = ByteBuffer.wrap(esp32S3Payload).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x10000, bb.int)
        assertEquals(16, bb.int)
        assertEquals(0x400, bb.int)
        assertEquals(0x0, bb.int)
        assertEquals(0, bb.int)
    }

    @Test
    fun parseResponseExtractsRomStatusAndChipMagic() {
        // Simulated READ_REG response: value=0x00F01D83 (ESP32), 4 trailing status bytes [0,0,0,0]
        val buf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(EspSlipProtocol.DIR_RESPONSE)
        buf.put(EspSlipProtocol.CMD_READ_REG)
        buf.putShort(4)
        buf.putInt(0x00F01D83)
        buf.put(byteArrayOf(0, 0, 0, 0))

        val resp = EspSlipProtocol.parseResponsePacket(buf.array())
        assertNotNull(resp)
        assertTrue(resp!!.isSuccess)
        assertEquals(EspChip.ESP32, EspChip.fromMagicRegister(resp.value))

        // Error response check
        val errBuf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        errBuf.put(EspSlipProtocol.DIR_RESPONSE)
        errBuf.put(EspSlipProtocol.CMD_FLASH_DEFL_DATA)
        errBuf.putShort(4)
        errBuf.putInt(0)
        errBuf.put(byteArrayOf(1, 0x07, 0, 0))

        val errResp = EspSlipProtocol.parseResponsePacket(errBuf.array())!!
        assertFalse(errResp.isSuccess)
        assertEquals(0x07, errResp.errorCode)
    }

    @Test
    fun chipDetectionMapsModernEspChips() {
        assertEquals(EspChip.ESP32_S3, EspChip.fromSecurityInfoChipId(9))
        assertEquals(EspChip.ESP32_C3, EspChip.fromSecurityInfoChipId(5))
        assertEquals(EspChip.ESP32_C6, EspChip.fromSecurityInfoChipId(13))
        assertEquals(EspChip.ESP32_P4, EspChip.fromSecurityInfoChipId(18))
        assertTrue(EspChip.ESP32_S3.usesEncryptParam)
        assertFalse(EspChip.ESP32.usesEncryptParam)
        assertEquals("esp32s3.json", EspChip.ESP32_S3.stubAssetName)
    }

    @Test
    fun stubReadFlashPayloadAndTwoByteStatusResponseWork() {
        val readPayload = EspSlipProtocol.buildReadFlashPayload(0x0, 0x400000, 0x1000, 1)
        assertEquals(16, readPayload.size)
        val bb = ByteBuffer.wrap(readPayload).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x0, bb.int)
        assertEquals(0x400000, bb.int)
        assertEquals(0x1000, bb.int)
        assertEquals(1, bb.int)

        // Stub response has 2 trailing status bytes [0, 0]
        val stubBuf = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
        stubBuf.put(EspSlipProtocol.DIR_RESPONSE)
        stubBuf.put(EspSlipProtocol.CMD_STUB_READ_FLASH)
        stubBuf.putShort(2)
        stubBuf.putInt(0)
        stubBuf.put(byteArrayOf(0, 0))

        val parsed = EspSlipProtocol.parseResponsePacket(stubBuf.array(), isStub = true)
        assertNotNull(parsed)
        assertTrue(parsed!!.isSuccess)
    }
}
