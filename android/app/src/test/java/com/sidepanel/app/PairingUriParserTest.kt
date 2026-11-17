package com.sidepanel.app

import com.sidepanel.app.data.pairing.PairingUriParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PairingUriParserTest {

    @Test
    fun testParseStandardSidePanelUri() {
        val uri = "sidepanel://192.168.0.105:8765?token=a4f89b6c12e84d7a9b0c2d3e4f5a6b7c"
        val parsed = PairingUriParser.parse(uri)

        assertNotNull(parsed)
        assertEquals("192.168.0.105", parsed?.host)
        assertEquals(8765, parsed?.port)
        assertEquals("a4f89b6c12e84d7a9b0c2d3e4f5a6b7c", parsed?.token)
    }

    @Test
    fun testParseCustomPort() {
        val uri = "sidepanel://10.0.0.50:9000?token=xyz123"
        val parsed = PairingUriParser.parse(uri)

        assertNotNull(parsed)
        assertEquals("10.0.0.50", parsed?.host)
        assertEquals(9000, parsed?.port)
        assertEquals("xyz123", parsed?.token)
    }

    @Test
    fun testParseMissingPortDefaultsTo8765() {
        val uri = "sidepanel://192.168.1.200?token=mytoken"
        val parsed = PairingUriParser.parse(uri)

        assertNotNull(parsed)
        assertEquals("192.168.1.200", parsed?.host)
        assertEquals(8765, parsed?.port)
        assertEquals("mytoken", parsed?.token)
    }

    @Test
    fun testParseInvalidMissingTokenReturnsNull() {
        val uri = "sidepanel://192.168.1.200:8765"
        val parsed = PairingUriParser.parse(uri)
        assertNull(parsed)
    }

    @Test
    fun testParseBlankStringReturnsNull() {
        assertNull(PairingUriParser.parse(""))
        assertNull(PairingUriParser.parse("   "))
    }
}
