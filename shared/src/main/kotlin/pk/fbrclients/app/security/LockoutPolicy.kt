package pk.fbrclients.app.security

/**
 * After 5 wrong PINs the keypad is paused: 30 s, then doubling each further
 * wrong attempt, up to 1 hour. Pure logic so it can be unit-tested.
 */
object LockoutPolicy {
    const val FREE_ATTEMPTS = 5
    private const val BASE_MS = 30_000L
    private const val MAX_MS = 60 * 60 * 1000L

    fun delayAfter(failedAttempts: Int): Long {
        if (failedAttempts < FREE_ATTEMPTS) return 0L
        val steps = (failedAttempts - FREE_ATTEMPTS).coerceAtMost(20)
        return (BASE_MS shl steps).coerceAtMost(MAX_MS)
    }
}
