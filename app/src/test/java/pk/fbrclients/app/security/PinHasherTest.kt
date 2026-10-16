package pk.fbrclients.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    // Fewer iterations keep the test quick; the logic is the same.
    private val iter = 1_000

    @Test fun correctPinMatches() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("4821", salt, iter)
        assertTrue(PinHasher.matches("4821", salt, hash, iter))
    }

    @Test fun wrongPinFails() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("4821", salt, iter)
        assertFalse(PinHasher.matches("4822", salt, hash, iter))
    }

    @Test fun saltChangesHash() {
        val a = PinHasher.hash("4821", PinHasher.newSalt(), iter)
        val b = PinHasher.hash("4821", PinHasher.newSalt(), iter)
        assertFalse(a.contentEquals(b))
    }

    @Test fun pinRules() {
        assertTrue(PinHasher.isValidPin("1234"))
        assertTrue(PinHasher.isValidPin("12345678"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("123456789"))
        assertFalse(PinHasher.isValidPin("12a4"))
    }
}

class LockoutPolicyTest {
    @Test fun firstFiveAttemptsAreFree() {
        for (n in 0 until LockoutPolicy.FREE_ATTEMPTS) assertEquals(0L, LockoutPolicy.delayAfter(n))
    }

    @Test fun delayDoublesThenCaps() {
        assertEquals(30_000L, LockoutPolicy.delayAfter(5))
        assertEquals(60_000L, LockoutPolicy.delayAfter(6))
        assertEquals(120_000L, LockoutPolicy.delayAfter(7))
        assertEquals(3_600_000L, LockoutPolicy.delayAfter(50))
    }
}
