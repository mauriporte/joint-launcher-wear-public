package com.mau.hub.wear.phone

import com.mau.hub.wear.Chunks
import com.mau.hub.wear.RelayPaths
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChunksTest {
    @Test fun bigFrameRoundTripsOutOfOrder() {
        val data = ByteArray(Chunks.MAX * 2 + 123) { (it % 251).toByte() }
        val parts = Chunks.split("7", data)
        assertEquals(3, parts.size)
        val j = Chunks.Joiner()
        assertNull(j.add(parts[2]))
        assertNull(j.add(parts[0]))
        assertArrayEquals(data, j.add(parts[1]))
    }

    @Test fun smallFrameIsOnePart() {
        val data = "{\"type\":\"auth_ok\"}".toByteArray()
        assertArrayEquals(data, Chunks.Joiner().add(Chunks.split("1", data).single()))
    }

    @Test fun wsUrl() {
        assertEquals("ws://h:8123/api/websocket", RelayPaths.wsUrl("http://h:8123/"))
        assertEquals("wss://x.y/api/websocket", RelayPaths.wsUrl("https://x.y"))
    }
}
