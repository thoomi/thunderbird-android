package net.thunderbird.feature.applock.api

/**
 * Configuration settings for app lock.
 *
 * @property isEnabled Whether biometric/device authentication is enabled.
 * @property timeoutMillis Timeout in milliseconds after which re-authentication is required
 *                         when the app returns from background. Use 0 for immediate re-authentication.
 */
data class AppLockConfig(
    val isEnabled: Boolean = false,
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    companion object {
        /**
         * Default timeout: 1 minute (60,000 milliseconds).
         */
        const val DEFAULT_TIMEOUT_MILLIS = 60 * 1000L
    }
}
