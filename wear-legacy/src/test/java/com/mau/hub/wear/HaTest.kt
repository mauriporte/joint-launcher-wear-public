package com.mau.hub.wear

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class HaTest {
    @Test fun mergeAppliesStateAndAttributeDiff() {
        val old = Ent("light.a", "off", JSONObject("""{"friendly_name":"A","brightness":10}"""))
        val e = Ha.merge(old, JSONObject("""{"+":{"s":"on","a":{"brightness":200}},"-":{"a":["friendly_name"]}}"""))
        assertEquals("on", e.state)
        assertEquals(200, e.attrs.getInt("brightness"))
        assertEquals("a", e.name) // friendly_name removed → falls back to the id
    }

    @Test fun pagesKeepGuardedLastAndDropCameras() {
        val ids = listOf("switch.kill_switch_x", "light.a", "camera.c", "button.c_capture", "light.b", "light.c", "light.d")
        assertEquals(listOf(listOf("light.a", "light.b", "light.c"), listOf("light.d"), listOf("switch.kill_switch_x")), pagesOf(ids))
    }
}
