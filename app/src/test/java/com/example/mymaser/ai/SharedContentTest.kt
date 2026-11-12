package com.example.mymaser.ai

import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedContentTest {

    @Test
    fun `photos and pdfs are supported, other files are not`() {
        assertTrue(isSupportedMimeType("image/jpeg"))
        assertTrue(isSupportedMimeType("application/pdf"))
        assertFalse(isSupportedMimeType("text/plain"))
        assertFalse(isSupportedMimeType("application/zip"))
        assertFalse(isSupportedMimeType(null))
    }

    @Test
    fun `reads up to the limit and rejects anything larger`() {
        val data = ByteArray(10_000) { it.toByte() }
        assertArrayEquals(data, readAtMost(ByteArrayInputStream(data), 10_000))
        assertNull(readAtMost(ByteArrayInputStream(data), 9_999))
        assertEquals(0, readAtMost(ByteArrayInputStream(ByteArray(0)), 10)!!.size)
    }

    @Test
    fun `document dates land at local noon, undated ones at now`() {
        val zone = ZoneId.of("Asia/Jerusalem")
        val expected = ZonedDateTime.of(2026, 10, 31, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, documentTimeStamp(LocalDate.of(2026, 10, 31), zone, nowMillis = 5))
        assertEquals(5L, documentTimeStamp(null, zone, nowMillis = 5))
    }
}
