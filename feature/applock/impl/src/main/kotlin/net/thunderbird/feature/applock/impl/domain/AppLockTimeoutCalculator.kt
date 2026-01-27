package net.thunderbird.feature.applock.impl.domain

/**
 * Calculates whether the authentication timeout has been exceeded since the UI was hidden.
 *
 * @param clock Function that provides the current time in milliseconds.
 *              Defaults to [System.currentTimeMillis] but can be injected for testing.
 */
class AppLockTimeoutCalculator(
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    /**
     * Check if the timeout period has been exceeded since the last authentication.
     *
     * @param lastUiHiddenMillis Timestamp of the last UI-hidden event.
     * @param timeoutMillis The configured timeout duration in milliseconds.
     * @return `true` if re-authentication is required, `false` otherwise.
     */
    fun isTimeoutExceeded(lastUiHiddenMillis: Long, timeoutMillis: Long): Boolean {
        if (lastUiHiddenMillis == 0L) {
            return false
        }

        val currentTime = clock()
        val elapsed = currentTime - lastUiHiddenMillis
        return elapsed >= timeoutMillis
    }

    /**
     * Calculate the remaining time until timeout.
     *
     * @param lastUiHiddenMillis Timestamp of the last UI-hidden event.
     * @param timeoutMillis The configured timeout duration in milliseconds.
     * @return Remaining time in milliseconds, or 0 if already timed out.
     */
    fun getRemainingTimeMillis(lastUiHiddenMillis: Long, timeoutMillis: Long): Long {
        if (lastUiHiddenMillis == 0L) {
            return 0L
        }

        val currentTime = clock()
        val elapsed = currentTime - lastUiHiddenMillis
        val remaining = timeoutMillis - elapsed

        return if (remaining > 0) remaining else 0L
    }
}
