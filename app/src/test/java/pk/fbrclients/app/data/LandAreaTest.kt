package pk.fbrclients.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pk.fbrclients.app.data.LandArea.Parts

/** Examples agreed with the owner on 2026-10-17. */
class LandAreaTest {
    private fun total(p: Parts) = LandArea.total(p)?.toString()

    @Test fun ownersExamples() {
        assertEquals("1.25 Acre", total(Parts(acre = "1", kanal = "2", sft = "200")))
        assertEquals("1.26 Acre", total(Parts(acre = "1", kanal = "2", sft = "218")))
        assertEquals("4.43 Marla", total(Parts(marla = "4", sft = "118")))
        assertEquals("1.28 Acre", total(Parts(acre = "1", kanal = "2", marla = "5")))
        assertEquals("3.5 Kanal", total(Parts(kanal = "3", marla = "10")))
        assertEquals("4.33 Marla", total(Parts(marla = "4", sarsahi = "3")))
    }

    @Test fun sarsahiAndSqmCountAsSft() {
        assertEquals("190.67 Sft", total(Parts(sarsahi = "3", sft = "100")))
        assertEquals("107.64 Sft", total(Parts(sqm = "10")))
        assertEquals("2 Marla", total(Parts(marla = "1", sarsahi = "9")))
    }

    @Test fun conversions() {
        assertEquals("1 Acre", total(Parts(acre = "1")))
        assertEquals("8 Kanal", total(Parts(kanal = "8")))
        assertEquals("1 Kanal", total(Parts(kanal = "0.5", marla = "10")))
        assertEquals("5440 Sft", total(Parts(sft = "5440")))
        assertEquals("1.38 Acre", total(Parts(acre = "1.38")))
    }

    @Test fun emptyOrZero() {
        assertNull(total(Parts()))
        assertNull(total(Parts(acre = "0", marla = "0.")))
    }

    @Test fun typing() {
        assertTrue(LandArea.isTypable(""))
        assertTrue(LandArea.isTypable("12."))
        assertTrue(LandArea.isTypable("1.3825"))
        assertFalse(LandArea.isTypable("1.2.3"))
        assertFalse(LandArea.isTypable("1.23456"))
    }
}
