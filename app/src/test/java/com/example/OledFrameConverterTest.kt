package com.example

import com.example.engine.Esp32FirmwareGenerator
import com.example.engine.OledFrameConverter
import com.example.model.BufferLayout
import com.example.model.Esp32Config
import com.example.model.OledController
import com.example.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OledFrameConverterTest {

    @Test
    fun packBuffer_horizontalMsb_encodesExact1024Bytes() {
        val pixels = BooleanArray(128 * 64)
        // Top-left pixel (0,0) -> byte 0, MSB (0x80)
        pixels[0] = true
        // Pixel (7,0) -> byte 0, LSB (0x01)
        pixels[7] = true
        // Pixel (0,1) -> byte 16, MSB (0x80)
        pixels[128] = true

        val packed = OledFrameConverter.packBuffer(pixels, BufferLayout.HORIZONTAL_MSB)
        assertEquals(1024, packed.size)
        assertEquals(0x81.toByte(), packed[0])
        assertEquals(0x80.toByte(), packed[16])
    }

    @Test
    fun packBuffer_pageVertical_encodesNativeSsd1306Gddram() {
        val pixels = BooleanArray(128 * 64)
        // Column 0, row 0 (page 0, bit 0) -> 0x01
        pixels[0] = true
        // Column 0, row 7 (page 0, bit 7) -> 0x80
        pixels[7 * 128] = true
        // Column 5, row 8 (page 1, bit 0) -> byte 128+5 = 133, value 0x01
        pixels[8 * 128 + 5] = true

        val packed = OledFrameConverter.packBuffer(pixels, BufferLayout.PAGE_VERTICAL)
        assertEquals(1024, packed.size)
        assertEquals(0x81.toByte(), packed[0])
        assertEquals(0x01.toByte(), packed[133])
    }

    @Test
    fun firmwareGenerator_includesSelectedPinsAndController() {
        val cfg = Esp32Config(
            transportMode = TransportMode.WIFI_UDP,
            udpPort = 4210,
            controller = OledController.SH1106_3C,
            sdaPin = 8,
            sclPin = 9
        )
        val sketch = Esp32FirmwareGenerator.generateSketch(cfg)
        assertTrue(sketch.contains("Adafruit_SH1106G"))
        assertTrue(sketch.contains("#define I2C_SDA 8"))
        assertTrue(sketch.contains("#define I2C_SCL 9"))
        assertTrue(sketch.contains("UDP_PORT = 4210"))
    }
}
