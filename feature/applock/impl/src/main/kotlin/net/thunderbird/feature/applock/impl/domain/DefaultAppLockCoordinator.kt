package net.thunderbird.feature.applock.impl.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import net.thunderbird.core.outcome.Outcome
import net.thunderbird.feature.applock.api.AppLockConfig
import net.thunderbird.feature.applock.api.AppLockCoordinator
import net.thunderbird.feature.applock.api.AppLockEffect
import net.thunderbird.feature.applock.api.AppLockError
import net.thunderbird.feature.applock.api.AppLockAuthenticator
import net.thunderbird.feature.applock.api.AppLockResult
import net.thunderbird.feature.applock.api.AppLockState

/**
 * Coordinates app lock flow: settings, availability, state, and authentication.
 *
 * State is managed in-memory and not persisted. Process death always requires
 * re-authentication when app lock is enabled. The timeout only applies to
 * background-to-foreground transitions within the same process.
 */
internal class DefaultAppLockCoordinator(
    private val configRepository: AppLockConfigRepository,
    private val availability: AppLockAvailability,
    private val policy: AppLockPolicy,
) : AppLockCoordinator {

    private val _state = MutableStateFlow<AppLockState>(AppLockState.Disabled)
    override val state: StateFlow<AppLockState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<AppLockEffect>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    override val effects: Flow<AppLockEffect> = _effects

    private var nextAttemptId: Long = 0L
    private var isInForeground: Boolean = false

    override val config: AppLockConfig
        get() = configRepository.getConfig()

    override val isAuthenticationAvailable: Boolean
        get() = availability.isAuthenticationAvailable()

    init {
        // Initialize state based on current config
        val currentConfig = configRepository.getConfig()
        val biometricAvailable = availability.isAuthenticationAvailable()
        applyEvent(AppLockEvent.SettingsChanged(currentConfig, biometricAvailable))
    }

    override fun onForegrounded() {
        isInForeground = true
        val currentConfig = configRepository.getConfig()
        val biometricAvailable = availability.isAuthenticationAvailable()

        // Auto-disable if biometric became unavailable
        if (currentConfig.isEnabled && !biometricAvailable) {
            configRepository.setConfig(currentConfig.copy(isEnabled = false))
        }

        applyEvent(
            AppLockEvent.AppForegrounded(
                config = configRepository.getConfig(),
                biometricAvailable = biometricAvailable,
            ),
        )

        // Coordinator decides to unlock - transition Locked/Failed → Unlocking
        if (_state.value == AppLockState.Locked || _state.value is AppLockState.Failed) {
            applyEvent(AppLockEvent.UnlockRequested(nextAttemptId++))
        }

        // Emit effect if now in Unlocking
        if (_state.value is AppLockState.Unlocking) {
            _effects.tryEmit(AppLockEffect.LaunchLockScreen)
        }
    }

    override fun onBackgrounded() {
        isInForeground = false
        applyEvent(AppLockEvent.AppBackgrounded)
    }

    override fun onSettingsChanged(config: AppLockConfig) {
        configRepository.setConfig(config)
        applyEvent(
            AppLockEvent.SettingsChanged(
                config = config,
                biometricAvailable = availability.isAuthenticationAvailable(),
            ),
        )

        // Coordinator decides to unlock - transition Locked/Failed → Unlocking
        if (isInForeground && (_state.value == AppLockState.Locked || _state.value is AppLockState.Failed)) {
            applyEvent(AppLockEvent.UnlockRequested(nextAttemptId++))
        }

        // Emit effect if now in Unlocking
        if (isInForeground && _state.value is AppLockState.Unlocking) {
            _effects.tryEmit(AppLockEffect.LaunchLockScreen)
        }
    }

    /**
     * Authenticate using the provided authenticator.
     *
     * Called by UI when observing [AppLockState.Unlocking] state. The coordinator
     * has already transitioned to Unlocking and assigned an attemptId.
     *
     * @return The authentication result.
     */
    @Suppress("TooGenericExceptionCaught")
    override suspend fun authenticate(authenticator: AppLockAuthenticator): AppLockResult {
        val unlocking = _state.value as? AppLockState.Unlocking
            ?: return Outcome.Failure(AppLockError.UnableToStart("Not in Unlocking state"))

        val result = try {
            authenticator.authenticate()
        } catch (e: Exception) {
            Outcome.Failure(AppLockError.UnableToStart(e.message ?: "Unknown error"))
        }

        onAuthResult(unlocking.attemptId, result)

        // Emit ExitApp effect if user canceled
        if (result is Outcome.Failure && result.error is AppLockError.Canceled) {
            _effects.tryEmit(AppLockEffect.ExitApp)
        }

        return result
    }

    /**
     * Retry authentication after a failure.
     *
     * Transitions from [AppLockState.Failed] to [AppLockState.Unlocking].
     */
    override fun retry() {
        if (_state.value is AppLockState.Failed) {
            applyEvent(AppLockEvent.UnlockRequested(nextAttemptId++))
        }
    }

    private fun onAuthResult(attemptId: Long, result: AppLockResult) {
        applyEvent(AppLockEvent.AuthResult(attemptId = attemptId, result = result))

        // If auth was interrupted while we're in foreground, immediately re-request unlock
        // to avoid getting stuck in Locked state without a prompt.
        if (isInForeground && result is Outcome.Failure && result.error is AppLockError.Interrupted) {
            applyEvent(AppLockEvent.UnlockRequested(nextAttemptId++))
            if (_state.value is AppLockState.Unlocking) {
                _effects.tryEmit(AppLockEffect.LaunchLockScreen)
            }
        }
    }

    private fun applyEvent(event: AppLockEvent) {
        _state.value = policy.reduce(_state.value, event)
    }
}
