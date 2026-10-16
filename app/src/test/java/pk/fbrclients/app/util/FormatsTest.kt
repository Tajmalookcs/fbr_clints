package pk.fbrclients.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatsTest {
    @Test fun cnicFormatting() {
        assertEquals("12345-1234567-1", Formats.formatCnic("1234512345671"))
        assertEquals("12345-12", Formats.formatCnic("1234512"))
        assertEquals("12345", Formats.formatCnic("12345"))
        assertEquals("12345-1234567-1", Formats.formatCnic("12345-1234567-1"))
    }

    @Test fun mobileFormatting() {
        assertEquals("0300-1234567", Formats.formatMobile("03001234567"))
        assertEquals("0300", Formats.formatMobile("0300"))
    }

    @Test fun validation() {
        assertTrue(Formats.isValidCnic("1234512345671"))
        assertFalse(Formats.isValidCnic("123451234567"))
        assertTrue(Formats.isValidMobile("03001234567"))
        assertFalse(Formats.isValidMobile("04001234567"))
        assertTrue(Formats.isValidEmail("ali.khan@example.pk"))
        assertFalse(Formats.isValidEmail("ali@khan"))
        assertFalse(Formats.isValidEmail("ali khan@example.com"))
    }

    @Test fun whatsApp() {
        assertEquals("923001234567", Formats.whatsAppNumber("0300-1234567"))
        assertNull(Formats.whatsAppNumber("12345"))
    }

    @Test fun cursorMappingRoundTrips() {
        for (raw in listOf("", "1", "12345", "123451", "1234512345671")) {
            val shown = Formats.withDashes(raw, Formats.CNIC_DASHES)
            for (o in 0..raw.length) {
                val s = Formats.shownOffset(o, Formats.CNIC_DASHES)
                assertTrue("in range for '$raw' at $o", s <= shown.length)
                assertEquals(o, Formats.rawOffset(shown, s))
            }
            for (s in 0..shown.length) {
                assertTrue(Formats.rawOffset(shown, s) <= raw.length)
            }
        }
    }
}
