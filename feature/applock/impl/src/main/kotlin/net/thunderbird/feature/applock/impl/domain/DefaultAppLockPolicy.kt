package net.thunderbird.feature.applock.impl.domain

import net.thunderbird.core.outcome.Outcome
import net.thunderbird.feature.applock.api.AppLockError
import net.thunderbird.feature.applock.api.AppLockState

/**
 * Default policy for app lock state transitions.
 *
 * Policy summary:
 * - Foreground resumes do not force auth unless the app was backgrounded and the timeout elapsed.
 * - Screen-off is not treated as a distinct lock trigger.
 * - Auth cancellation transitions to Failed; the UI layer closes the app via effect.
 */
internal class DefaultAppLockPolicy(
    private val timeoutCalculator: AppLockTimeoutCalculator,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : AppLockPolicy {

    override fun reduce(state: AppLockState, event: AppLockEvent): AppLockState {
        return when (event) {
            is AppLockEvent.SettingsChanged -> {
                if (!event.config.isEnabled || !event.biometricAvailable) {
                    AppLockState.Disabled
                } else {
                    AppLockState.Locked
                }
            }
            AppLockEvent.AppBackgrounded -> {
                when (state) {
                    is AppLockState.Unlocked -> state.copy(lastHiddenAtMillis = clock())
                    else -> state
                }
            }
            is AppLockEvent.AppForegrounded -> {
                if (!event.config.isEnabled || !event.biometricAvailable) {
                    AppLockState.Disabled
                } else {
                    when (state) {
                        AppLockState.Disabled -> AppLockState.Locked
                        AppLockState.Locked -> state
                        is AppLockState.Unlocking -> state
                        is AppLockState.Failed -> state
                        is AppLockState.Unlocked -> {
                            val lastHiddenAtMillis = state.lastHiddenAtMillis
                                ?: return state

                            val timeoutExceeded = timeoutCalculator.isTimeoutExceeded(
                                lastUiHiddenMillis = lastHiddenAtMillis,
                                timeoutMillis = event.config.timeoutMillis,
                            )
                            if (timeoutExceeded) {
                                AppLockState.Locked
                            } else {
                                state.copy(lastHiddenAtMillis = null)
                            }
                        }
                    }
                }
            }
            is AppLockEvent.UnlockRequested -> {
                when (state) {
                    AppLockState.Disabled -> state
                    AppLockState.Locked,
                    is AppLockState.Failed,
                    -> AppLockState.Unlocking(event.attemptId)
                    is AppLockState.Unlocking -> state
                    is AppLockState.Unlocked -> state
                }
            }
            is AppLockEvent.AuthResult -> {
                if (state is AppLockState.Unlocking && state.attemptId == event.attemptId) {
                    val result = event.result
                    when (result) {
                        is Outcome.Success -> AppLockState.Unlocked(lastHiddenAtMillis = null)
                        is Outcome.Failure -> {
                            // System interruptions (rotation, backgrounding) should be transparent
                            // to the user - just go back to Locked without showing an error
                            if (result.error is AppLockError.Interrupted) {
                                AppLockState.Locked
                            } else {
                                AppLockState.Failed(result.error)
                            }
                        }
                    }
                } else {
                    state
                }
            }
        }
    }
}
