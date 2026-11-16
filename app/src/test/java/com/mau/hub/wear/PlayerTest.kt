package com.mau.hub.wear

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerTest {
    private fun ent(id: String, state: String, attrs: String = "{}") = Ent(id, state, JSONObject(attrs))

    @Test
    fun playingBeatsPausedBeatsIdle() {
        val players = listOf("media_player.spotify", "media_player.tv")
        val states = mapOf("media_player.spotify" to ent("media_player.spotify", "paused"), "media_player.tv" to ent("media_player.tv", "playing"))
        assertEquals("media_player.tv", activePlayer(players, states)?.id)
        assertEquals("media_player.spotify", activePlayer(players, states + ("media_player.tv" to ent("media_player.tv", "idle")))?.id)
        assertNull(activePlayer(players, mapOf("media_player.tv" to ent("media_player.tv", "off"))))
    }

    @Test
    fun subtitleFallsBackToAppThenName() {
        assertEquals("Bad Bunny", subtitle(ent("media_player.s", "playing", """{"media_artist":"Bad Bunny","app_name":"Spotify"}""")))
        assertEquals("Netflix", subtitle(ent("media_player.tv", "playing", """{"app_name":"Netflix"}""")))
        assertEquals("Living TV", subtitle(ent("media_player.tv", "playing", """{"friendly_name":"Living TV"}""")))
    }

    @Test
    fun configCollectsEveryEntityOnce() {
        Config.apply(JSONObject("""{"players":["media_player.spotify","media_player.tv"],"tv":"media_player.tv","tvRemote":"remote.tv"}"""))
        assertEquals(listOf("media_player.spotify", "media_player.tv", "remote.tv"), Config.ids)
        assertEquals("wss://h.example/api/websocket", Ha.wsUrl("https://h.example/"))
    }
}
