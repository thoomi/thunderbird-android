package net.thunderbird.feature.applock.impl.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import net.thunderbird.core.outcome.Outcome
import net.thunderbird.feature.applock.api.AppLockConfig
import net.thunderbird.feature.applock.api.AppLockError
import net.thunderbird.feature.applock.api.AppLockState
import org.junit.Test

class DefaultAppLockPolicyTest {

    @Test
    fun `settings disabled transitions to Disabled`() {
        val policy = createPolicy()

        val state = policy.reduce(
            AppLockState.Locked,
            AppLockEvent.SettingsChanged(
                config = AppLockConfig(isEnabled = false),
                biometricAvailable = true,
            ),
        )

        assertThat(state).isEqualTo(AppLockState.Disabled)
    }

    @Test
    fun `settings enabled transitions to Locked`() {
        val policy = createPolicy()

        val state = policy.reduce(
            AppLockState.Disabled,
            AppLockEvent.SettingsChanged(
                config = AppLockConfig(isEnabled = true),
                biometricAvailable = true,
            ),
        )

        assertThat(state).isEqualTo(AppLockState.Locked)
    }

    @Test
    fun `foregrounded when enabled transitions to Locked`() {
        val policy = createPolicy()

        val state = policy.reduce(
            AppLockState.Disabled,
            AppLockEvent.AppForegrounded(
                config = AppLockConfig(isEnabled = true),
                biometricAvailable = true,
            ),
        )

        assertThat(state).isEqualTo(AppLockState.Locked)
    }

    @Test
    fun `foreground before timeout stays Unlocked`() {
        var now = 1_000L
        val policy = createPolicy(clock = { now })
        val unlocked = AppLockState.Unlocked(lastHiddenAtMillis = 1_000L)

        now = 1_000L + 30_000L
        val state = policy.reduce(
            unlocked,
            AppLockEvent.AppForegrounded(
                config = AppLockConfig(isEnabled = true, timeoutMillis = 60_000L),
                biometricAvailable = true,
            ),
        )

        assertThat(state).isEqualTo(AppLockState.Unlocked(lastHiddenAtMillis = null))
    }

    @Test
    fun `foreground after timeout transitions to Locked`() {
        var now = 1_000L
        val policy = createPolicy(clock = { now })
        val unlocked = AppLockState.Unlocked(lastHiddenAtMillis = 1_000L)

        now = 1_000L + 60_001L
        val state = policy.reduce(
            unlocked,
            AppLockEvent.AppForegrounded(
                config = AppLockConfig(isEnabled = true, timeoutMillis = 60_000L),
                biometricAvailable = true,
            ),
        )

        assertThat(state).isEqualTo(AppLockState.Locked)
    }

    @Test
    fun `auth result with stale attempt is ignored`() {
        val policy = createPolicy()
        val unlocking = AppLockState.Unlocking(attemptId = 2L)

        val state = policy.reduce(
            unlocking,
            AppLockEvent.AuthResult(
                attemptId = 1L,
                result = Outcome.Success(Unit),
            ),
        )

        assertThat(state).isEqualTo(unlocking)
    }

    @Test
    fun `auth success transitions to Unlocked`() {
        val policy = createPolicy()

        val state = policy.reduce(
            AppLockState.Unlocking(attemptId = 1L),
            AppLockEvent.AuthResult(
                attemptId = 1L,
                result = Outcome.Success(Unit),
            ),
        )

        assertThat(state).isInstanceOf<AppLockState.Unlocked>()
    }

    @Test
    fun `auth failure transitions to Failed with error`() {
        val policy = createPolicy()

        val state = policy.reduce(
            AppLockState.Unlocking(attemptId = 1L),
            AppLockEvent.AuthResult(
                attemptId = 1L,
                result = Outcome.Failure(AppLockError.Failed),
            ),
        )

        assertThat(state).isEqualTo(AppLockState.Failed(AppLockError.Failed))
    }

    @Test
    fun `auth interrupted transitions to Locked without error`() {
        val policy = createPolicy()

        val state = policy.reduce(
            AppLockState.Unlocking(attemptId = 1L),
            AppLockEvent.AuthResult(
                attemptId = 1L,
                result = Outcome.Failure(AppLockError.Interrupted),
            ),
        )

        // System interruptions should not show an error to the user
        assertThat(state).isEqualTo(AppLockState.Locked)
    }

    @Test
    fun `auth canceled transitions to Failed with canceled error`() {
        val policy = createPolicy()

        val state = policy.reduce(
            AppLockState.Unlocking(attemptId = 1L),
            AppLockEvent.AuthResult(
                attemptId = 1L,
                result = Outcome.Failure(AppLockError.Canceled),
            ),
        )

        assertThat(state).isEqualTo(AppLockState.Failed(AppLockError.Canceled))
    }

    @Test
    fun `backgrounding records timestamp`() {
        var now = 1_000L
        val policy = createPolicy(clock = { now })
        val unlocked = AppLockState.Unlocked(lastHiddenAtMillis = null)

        now = 2_000L
        val state = policy.reduce(unlocked, AppLockEvent.AppBackgrounded)

        assertThat(state).isEqualTo(AppLockState.Unlocked(lastHiddenAtMillis = 2_000L))
    }

    @Test
    fun `backgrounding while Locked does not change state`() {
        val policy = createPolicy()

        val state = policy.reduce(AppLockState.Locked, AppLockEvent.AppBackgrounded)

        assertThat(state).isEqualTo(AppLockState.Locked)
    }

    @Test
    fun `foreground without prior background stays Unlocked`() {
        val policy = createPolicy()
        val unlocked = AppLockState.Unlocked(lastHiddenAtMillis = null) // Never went to background

        val state = policy.reduce(
            unlocked,
            AppLockEvent.AppForegrounded(
                config = AppLockConfig(isEnabled = true, timeoutMillis = 0L), // Immediate timeout
                biometricAvailable = true,
            ),
        )

        // Should stay Unlocked since there was no background event
        assertThat(state).isEqualTo(unlocked)
    }

    private fun createPolicy(
        clock: () -> Long = { 0L },
    ): DefaultAppLockPolicy {
        val timeoutCalculator = AppLockTimeoutCalculator(clock = clock)
        return DefaultAppLockPolicy(
            timeoutCalculator = timeoutCalculator,
            clock = clock,
        )
    }
}
