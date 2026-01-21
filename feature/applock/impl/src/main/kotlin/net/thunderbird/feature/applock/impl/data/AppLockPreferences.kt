package net.thunderbird.feature.applock.impl.data

import net.thunderbird.feature.applock.api.AppLockConfig

/**
 * Preference keys and defaults for authentication settings.
 */
internal object AppLockPreferences {
    const val PREFS_FILE_NAME = "authentication_preferences"

    const val KEY_ENABLED = "authentication_enabled"
    const val KEY_TIMEOUT_MILLIS = "authentication_timeout_millis"

    const val DEFAULT_ENABLED = false
    const val DEFAULT_TIMEOUT_MILLIS = AppLockConfig.DEFAULT_TIMEOUT_MILLIS
}
