package net.thunderbird.feature.applock.impl.domain

import net.thunderbird.feature.applock.api.AppLockConfig
import net.thunderbird.feature.applock.api.AppLockResult

/**
 * Events that drive the app lock state machine.
 */
internal sealed interface AppLockEvent {
    /**
     * App entered foreground, evaluate whether auth is required.
     */
    data class AppForegrounded(
        val config: AppLockConfig,
        val biometricAvailable: Boolean,
    ) : AppLockEvent

    /**
     * App went to background.
     */
    data object AppBackgrounded : AppLockEvent

    /**
     * App lock settings changed.
     */
    data class SettingsChanged(
        val config: AppLockConfig,
        val biometricAvailable: Boolean,
    ) : AppLockEvent

    /**
     * Unlock flow was requested (prompt launch).
     */
    data class UnlockRequested(val attemptId: Long) : AppLockEvent

    /**
     * Authentication completed.
     */
    data class AuthResult(
        val attemptId: Long,
        val result: AppLockResult,
    ) : AppLockEvent
}
