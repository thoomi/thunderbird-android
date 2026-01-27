package net.thunderbird.feature.applock.impl.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import net.thunderbird.core.outcome.Outcome
import net.thunderbird.feature.applock.api.AppLockAuthenticator
import net.thunderbird.feature.applock.api.AppLockConfig
import net.thunderbird.feature.applock.api.AppLockCoordinator
import net.thunderbird.feature.applock.api.AppLockEffect
import net.thunderbird.feature.applock.api.AppLockError
import net.thunderbird.feature.applock.api.AppLockResult
import net.thunderbird.feature.applock.api.AppLockState

/**
 * Fake implementation of [AppLockCoordinator] for testing.
 */
internal class FakeAppLockCoordinator(
    private var authResult: AppLockResult = Outcome.Success(Unit),
    override var isEnabled: Boolean = false,
) : AppLockCoordinator {

    private val _state = MutableStateFlow<AppLockState>(AppLockState.Disabled)
    override val state: StateFlow<AppLockState> = _state.asStateFlow()

    private val _effects = Channel<AppLockEffect>(Channel.BUFFERED)
    override val effects: Flow<AppLockEffect> = _effects.receiveAsFlow()

    private var _config = AppLockConfig()
    override val config: AppLockConfig
        get() = _config

    override val isAuthenticationAvailable: Boolean = true

    var onForegroundedCallCount = 0
        private set

    var authenticateCallCount = 0
        private set

    var lastSettings: AppLockConfig? = null
        private set

    private var authDeferred: CompletableDeferred<AppLockResult>? = null
    private var nextAttemptId = 0L

    /**
     * Makes [authenticate] suspend until [completeAuthenticate] is called.
     */
    fun suspendOnAuthenticate() {
        authDeferred = CompletableDeferred()
    }

    fun completeAuthenticate(result: AppLockResult) {
        authDeferred?.complete(result)
    }

    override fun onForegrounded() {
        onForegroundedCallCount++
        if (_state.value == AppLockState.Locked) {
            // Coordinator-driven: transition to Unlocking before emitting effect
            _state.value = AppLockState.Unlocking(attemptId = nextAttemptId++)
            _effects.trySend(AppLockEffect.LaunchLockScreen)
        }
    }

    override fun onBackgrounded() = Unit

    override fun onSettingsChanged(config: AppLockConfig) {
        lastSettings = config
        _config = config
    }

    override suspend fun authenticate(authenticator: AppLockAuthenticator): AppLockResult {
        authenticateCallCount++
        val unlocking = _state.value as? AppLockState.Unlocking
            ?: return Outcome.Failure(AppLockError.UnableToStart("Not in Unlocking state"))

        val result = authDeferred?.await() ?: authResult
        _state.value = when (result) {
            is Outcome.Success -> AppLockState.Unlocked()
            is Outcome.Failure -> AppLockState.Failed(result.error)
        }
        return result
    }

    override fun retry() {
        if (_state.value is AppLockState.Failed) {
            _state.value = AppLockState.Unlocking(attemptId = nextAttemptId++)
        }
    }

    fun setAuthResult(result: AppLockResult) {
        authResult = result
    }

    fun setState(state: AppLockState) {
        _state.value = state
    }

    companion object {
        fun alwaysSucceeds(): FakeAppLockCoordinator = FakeAppLockCoordinator()

        fun alwaysFails(error: AppLockError = AppLockError.Failed): FakeAppLockCoordinator =
            FakeAppLockCoordinator(authResult = Outcome.Failure(error))
    }
}
