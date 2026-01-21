package net.thunderbird.feature.applock.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Coordinates app lock flow and orchestration.
 *
 * This is the main public API for the app lock feature. Other modules should
 * only interact with app lock through this interface.
 */
interface AppLockCoordinator {
    /**
     * Observable app lock state for UI rendering.
     */
    val state: StateFlow<AppLockState>

    /**
     * One-shot effects that the UI must handle.
     */
    val effects: Flow<AppLockEffect>

    /**
     * Current app lock configuration.
     */
    val config: AppLockConfig

    /**
     * Whether app lock is currently enabled in settings.
     */
    val isEnabled: Boolean
        get() = config.isEnabled

    /**
     * Whether authentication (biometric or device credential) is available on this device.
     */
    val isAuthenticationAvailable: Boolean

    /**
     * Notify that the app came to foreground.
     */
    fun onForegrounded()

    /**
     * Notify that the app went to background.
     */
    fun onBackgrounded()

    /**
     * Update app lock configuration.
     */
    fun onSettingsChanged(config: AppLockConfig)

    /**
     * Authenticate using the provided authenticator.
     */
    suspend fun authenticate(authenticator: AppLockAuthenticator): AppLockResult

    /**
     * Retry authentication after a failure.
     */
    fun retry()
}
