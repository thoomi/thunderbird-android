package net.thunderbird.feature.applock.impl.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withTimeout
import net.thunderbird.core.outcome.Outcome
import net.thunderbird.feature.applock.api.AppLockAuthenticator
import net.thunderbird.feature.applock.api.AppLockConfig
import net.thunderbird.feature.applock.api.AppLockEffect
import net.thunderbird.feature.applock.api.AppLockError
import net.thunderbird.feature.applock.api.AppLockResult
import net.thunderbird.feature.applock.api.AppLockState
import org.junit.Test

class DefaultAppLockCoordinatorTest {

    @Test
    fun `cold start requires auth when enabled`() = runTest {
        val coordinator = createCoordinator(
            config = AppLockConfig(isEnabled = true),
        )

        assertThat(coordinator.state.value).isEqualTo(AppLockState.Locked)
    }

    @Test
    fun `cold start does not require auth when enabled but unavailable`() = runTest {
        val coordinator = createCoordinator(
            config = AppLockConfig(isEnabled = true),
            biometricAvailable = false,
        )

        assertThat(coordinator.state.value).isEqualTo(AppLockState.Disabled)
    }

    @Test
    fun `onForegrounded does nothing when feature is disabled`() = runTest {
        val coordinator = createCoordinator(
            config = AppLockConfig(isEnabled = false),
        )

        coordinator.onForegrounded()

        assertThat(coordinator.state.value).isEqualTo(AppLockState.Disabled)
    }

    @Test
    fun `onForegrounded does nothing when auth is unavailable`() = runTest {
        val coordinator = createCoordinator(
            config = AppLockConfig(isEnabled = true),
            biometricAvailable = false,
        )

        coordinator.onForegrounded()

        assertThat(coordinator.state.value).isEqualTo(AppLockState.Disabled)
    }

    @Test
    fun `onForegrounded transitions to Unlocking and emits lock screen when locked`() = runTest {
        val coordinator = createCoordinator(
            config = AppLockConfig(isEnabled = true),
        )

        val effectDeferred = async { coordinator.effects.first() }
        runCurrent()
        coordinator.onForegrounded()

        assertThat(coordinator.state.value).isInstanceOf<AppLockState.Unlocking>()
        assertThat(withTimeout(1_000) { effectDeferred.await() })
            .isEqualTo(AppLockEffect.LaunchLockScreen)
    }

    @Test
    fun `onForegrounded re-prompts when previously failed`() = runTest {
        val coordinator = createCoordinator(
            config = AppLockConfig(isEnabled = true),
        )

        // First unlock attempt -> consume effect and fail auth
        val firstEffect = async { coordinator.effects.first() }
        runCurrent()
        coordinator.onForegrounded()
        assertThat(withTimeout(1_000) { firstEffect.await() })
            .isEqualTo(AppLockEffect.LaunchLockScreen)

        coordinator.authenticate(FakeAuthenticator.failure(AppLockError.Failed))
        assertThat(coordinator.state.value).isEqualTo(AppLockState.Failed(AppLockError.Failed))

        // Re-foreground should prompt again
        val effectDeferred = async { coordinator.effects.first() }
        runCurrent()
        coordinator.onForegrounded()

        assertThat(withTimeout(1_000) { effectDeferred.await() })
            .isEqualTo(AppLockEffect.LaunchLockScreen)
    }

    @Test
    fun `effects are delivered to multiple collectors`() = runTest {
        val coordinator = createCoordinator(
            config = AppLockConfig(isEnabled = true),
        )

        val firstCollector = async { coordinator.effects.first() }
        val secondCollector = async { coordinator.effects.first() }
        runCurrent()

        coordinator.onForegrounded()

        assertThat(withTimeout(1_000) { firstCollector.await() })
            .isEqualTo(AppLockEffect.LaunchLockScreen)
        assertThat(withTimeout(1_000) { secondCollector.await() })
            .isEqualTo(AppLockEffect.LaunchLockScreen)
    }

    @Test
    fun `onForegrounded transitions to Unlocking when timeout exceeded since background`() = runTest {
        var now = 100_000L
        val coordinator = createCoordinator(
            config = AppLockConfig(isEnabled = true, timeoutMillis = 60_000L),
            clock = { now },
        )

        // Get into Unlocking state and authenticate successfully
        coordinator.onForegrounded()
        coordinator.authenticate(FakeAuthenticator.success())
        assertThat(coordinator.state.value).isInstanceOf<AppLockState.Unlocked>()

        // Go to background and advance time past timeout
        coordinator.onBackgrounded()
        now += 120_000L

        val effectDeferred = async { coordinator.effects.first() }
        runCurrent()
        coordinator.onForegrounded()

        assertThat(coordinator.state.value).isInstanceOf<AppLockState.Unlocking>()
        assertThat(withTimeout(1_000) { effectDeferred.await() })
            .isEqualTo(AppLockEffect.LaunchLockScreen)
    }

    @Test
    fun `onForegrounded stays Unlocked when timeout not exceeded since background`() = runTest {
        var now = 100_000L
        val coordinator = createCoordinator(
            config = AppLockConfig(isEnabled = true, timeoutMillis = 60_000L),
            clock = { now },
        )

        // Get into Unlocking state and authenticate successfully
        coordinator.onForegrounded()
        coordinator.authenticate(FakeAuthenticator.success())
        assertThat(coordinator.state.value).isInstanceOf<AppLockState.Unlocked>()

        // Go to background but don't advance time past timeout
        coordinator.onBackgrounded()
        now += 30_000L

        coordinator.onForegrounded()

        assertThat(coordinator.state.value).isInstanceOf<AppLockState.Unlocked>()
    }

    @Test
    fun `onForegrounded transitions to Unlocking immediately when timeout is zero`() = runTest {
        var now = 100_000L
        val coordinator = createCoordinator(
            config = AppLockConfig(isEnabled = true, timeoutMillis = 0L),
            clock = { now },
        )

        // Get into Unlocking state and authenticate successfully
        coordinator.onForegrounded()
        coordinator.authenticate(FakeAuthenticator.success())
        assertThat(coordinator.state.value).isInstanceOf<AppLockState.Unlocked>()

        // Go to background and advance time minimally
        coordinator.onBackgrounded()
        now += 1L

        val effectDeferred = async { coordinator.effects.first() }
        runCurrent()
        coordinator.onForegrounded()

        assertThat(coordinator.state.value).isInstanceOf<AppLockState.Unlocking>()
        assertThat(withTimeout(1_000) { effectDeferred.await() })
            .isEqualTo(AppLockEffect.LaunchLockScreen)
    }

    @Test
    fun `onSettingsChanged transitions to Unlocking when enabled in foreground`() = runTest {
        val coordinator = createCoordinator(
            config = AppLockConfig(isEnabled = false),
        )

        coordinator.onForegrounded()

        val effectDeferred = async { coordinator.effects.first() }
        runCurrent()
        coordinator.onSettingsChanged(AppLockConfig(isEnabled = true))

        assertThat(coordinator.state.value).isInstanceOf<AppLockState.Unlocking>()
        assertThat(withTimeout(1_000) { effectDeferred.await() })
            .isEqualTo(AppLockEffect.LaunchLockScreen)
    }

    @Test
    fun `authenticate updates state on success`() = runTest {
        val coordinator = createCoordinator(
            config = AppLockConfig(isEnabled = true),
        )

        // Get into Unlocking state via onForegrounded
        coordinator.onForegrounded()
        assertThat(coordinator.state.value).isInstanceOf<AppLockState.Unlocking>()

        val result = coordinator.authenticate(FakeAuthenticator.success())

        assertThat(result).isEqualTo(Outcome.Success(Unit))
        assertThat(coordinator.state.value).isInstanceOf<AppLockState.Unlocked>()
    }

    @Test
    fun `authenticate updates state on failure`() = runTest {
        val coordinator = createCoordinator(
            config = AppLockConfig(isEnabled = true),
        )

        // Get into Unlocking state via onForegrounded
        coordinator.onForegrounded()
        assertThat(coordinator.state.value).isInstanceOf<AppLockState.Unlocking>()

        val result = coordinator.authenticate(FakeAuthenticator.failure(AppLockError.Failed))

        assertThat(result).isEqualTo(Outcome.Failure(AppLockError.Failed))
        assertThat(coordinator.state.value).isEqualTo(AppLockState.Failed(AppLockError.Failed))
    }

    @Test
    fun `authenticate returns error when not in Unlocking state`() = runTest {
        val coordinator = createCoordinator(
            config = AppLockConfig(isEnabled = true),
        )

        // Don't call onForegrounded - state is Locked, not Unlocking
        assertThat(coordinator.state.value).isEqualTo(AppLockState.Locked)

        val result = coordinator.authenticate(FakeAuthenticator.success())

        assertThat(result).isEqualTo(Outcome.Failure(AppLockError.UnableToStart("Not in Unlocking state")))
    }

    @Test
    fun `isEnabled returns true when feature is enabled`() = runTest {
        val coordinator = createCoordinator(
            config = AppLockConfig(isEnabled = true),
        )

        assertThat(coordinator.isEnabled).isTrue()
    }

    @Test
    fun `isEnabled returns false when feature is disabled`() = runTest {
        val coordinator = createCoordinator(
            config = AppLockConfig(isEnabled = false),
        )

        assertThat(coordinator.isEnabled).isFalse()
    }

    @Test
    fun `onSettingsChanged disables lock when isEnabled set to false`() = runTest {
        val coordinator = createCoordinator(
            config = AppLockConfig(isEnabled = true),
        )
        assertThat(coordinator.state.value).isEqualTo(AppLockState.Locked)

        coordinator.onSettingsChanged(AppLockConfig(isEnabled = false))

        assertThat(coordinator.state.value).isEqualTo(AppLockState.Disabled)
    }

    @Test
    fun `retry transitions from Failed to Unlocking`() = runTest {
        val coordinator = createCoordinator(
            config = AppLockConfig(isEnabled = true),
        )

        // Get into Failed state
        coordinator.onForegrounded()
        coordinator.authenticate(FakeAuthenticator.failure(AppLockError.Failed))
        assertThat(coordinator.state.value).isEqualTo(AppLockState.Failed(AppLockError.Failed))

        coordinator.retry()

        assertThat(coordinator.state.value).isInstanceOf<AppLockState.Unlocking>()
    }

    @Test
    fun `retry does nothing when not in Failed state`() = runTest {
        val coordinator = createCoordinator(
            config = AppLockConfig(isEnabled = true),
        )

        // State is Locked, not Failed
        assertThat(coordinator.state.value).isEqualTo(AppLockState.Locked)

        coordinator.retry()

        // State should still be Locked
        assertThat(coordinator.state.value).isEqualTo(AppLockState.Locked)
    }

    @Test
    fun `retry after failure allows successful authentication`() = runTest {
        val coordinator = createCoordinator(
            config = AppLockConfig(isEnabled = true),
        )

        // Get into Failed state
        coordinator.onForegrounded()
        coordinator.authenticate(FakeAuthenticator.failure(AppLockError.Failed))
        assertThat(coordinator.state.value).isEqualTo(AppLockState.Failed(AppLockError.Failed))

        // Retry and authenticate successfully
        coordinator.retry()
        assertThat(coordinator.state.value).isInstanceOf<AppLockState.Unlocking>()

        val result = coordinator.authenticate(FakeAuthenticator.success())

        assertThat(result).isEqualTo(Outcome.Success(Unit))
        assertThat(coordinator.state.value).isInstanceOf<AppLockState.Unlocked>()
    }

    private fun createCoordinator(
        config: AppLockConfig,
        biometricAvailable: Boolean = true,
        clock: () -> Long = { System.currentTimeMillis() },
    ): DefaultAppLockCoordinator {
        val timeoutCalculator = AppLockTimeoutCalculator(clock = clock)
        val policy = DefaultAppLockPolicy(
            timeoutCalculator = timeoutCalculator,
            clock = clock,
        )
        val configRepository = InMemoryAppLockConfigRepository(config)
        val availability = FakeAppLockAvailability(available = biometricAvailable)

        return DefaultAppLockCoordinator(
            configRepository = configRepository,
            availability = availability,
            policy = policy,
        )
    }

    private class InMemoryAppLockConfigRepository(
        private var config: AppLockConfig,
    ) : AppLockConfigRepository {
        override fun getConfig(): AppLockConfig = config

        override fun setConfig(config: AppLockConfig) {
            this.config = config
        }
    }

    private class FakeAppLockAvailability(
        private val available: Boolean,
    ) : AppLockAvailability {
        override fun isAuthenticationAvailable(): Boolean = available
    }
}
