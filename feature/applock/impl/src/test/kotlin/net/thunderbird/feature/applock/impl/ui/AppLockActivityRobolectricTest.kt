package net.thunderbird.feature.applock.impl.ui

import android.app.Application
import android.content.res.Configuration
import android.os.Looper
import androidx.fragment.app.FragmentActivity
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import net.thunderbird.feature.applock.api.AppLockConfig
import net.thunderbird.feature.applock.api.AppLockCoordinator
import net.thunderbird.feature.applock.api.AppLockAuthenticator
import net.thunderbird.feature.applock.api.AppLockError
import net.thunderbird.feature.applock.api.AppLockState
import net.thunderbird.feature.applock.impl.domain.AppLockAvailability
import net.thunderbird.feature.applock.impl.domain.AppLockConfigRepository
import net.thunderbird.feature.applock.impl.domain.AppLockTimeoutCalculator
import net.thunderbird.feature.applock.impl.domain.DefaultAppLockCoordinator
import net.thunderbird.feature.applock.impl.domain.DefaultAppLockPolicy
import net.thunderbird.feature.applock.impl.domain.FakeAuthenticator
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppLockActivityRobolectricTest {

    private lateinit var coordinator: DefaultAppLockCoordinator

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()

        val configRepository = FakeAppLockConfigRepository(AppLockConfig(isEnabled = true))
        val timeoutCalculator = AppLockTimeoutCalculator()
        val policy = DefaultAppLockPolicy(timeoutCalculator = timeoutCalculator)

        coordinator = DefaultAppLockCoordinator(
            configRepository = configRepository,
            availability = FakeAppLockAvailability(),
            policy = policy,
        )

        startKoin {
            androidContext(application)
            modules(
                module {
                    single { coordinator }
                    single<AppLockCoordinator> { coordinator }
                    single<AppLockAuthenticatorFactory> { FakeAppLockAuthenticatorFactory() }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `activity finishes when unlocked`() {
        // Simulate the real flow: onForegrounded transitions Locked -> Unlocking
        coordinator.onForegrounded()
        assertThat(coordinator.state.value).isInstanceOf<AppLockState.Unlocking>()

        val controller = Robolectric.buildActivity(AppLockActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()

        // Authentication should have succeeded, so activity should finish
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `authenticate is called on activity start when in Unlocking state`() {
        // State starts as Locked because config is enabled
        assertThat(coordinator.state.value).isEqualTo(AppLockState.Locked)

        // Simulate the real flow: onForegrounded transitions Locked -> Unlocking
        coordinator.onForegrounded()
        assertThat(coordinator.state.value).isInstanceOf<AppLockState.Unlocking>()

        Robolectric.buildActivity(AppLockActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        // After authentication, state should be Unlocked
        assertThat(coordinator.state.value).isInstanceOf<AppLockState.Unlocked>()
    }

    @Test
    fun `rotation during unlock only triggers one prompt`() {
        assertThat(coordinator.state.value).isEqualTo(AppLockState.Locked)

        // Simulate the real flow: onForegrounded transitions Locked -> Unlocking
        coordinator.onForegrounded()

        val controller = Robolectric.buildActivity(AppLockActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        // Authentication completed
        assertThat(coordinator.state.value).isInstanceOf<AppLockState.Unlocked>()

        // Simulate rotation - this recreates the activity
        val newConfig = Configuration(controller.get().resources.configuration)
        newConfig.orientation = Configuration.ORIENTATION_LANDSCAPE
        controller.configurationChange(newConfig)
        shadowOf(Looper.getMainLooper()).idle()

        // Should still be Unlocked - no re-auth triggered
        assertThat(coordinator.state.value).isInstanceOf<AppLockState.Unlocked>()
    }

    @Test
    fun `activity exits when user cancels authentication`() {
        val cancelAuthenticatorFactory = object : AppLockAuthenticatorFactory {
            override fun create(
                activity: FragmentActivity,
                title: String,
                subtitle: String,
            ): AppLockAuthenticator = FakeAuthenticator.failure(AppLockError.Canceled)
        }

        stopKoin()
        startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(
                module {
                    single { coordinator }
                    single<AppLockCoordinator> { coordinator }
                    single<AppLockAuthenticatorFactory> { cancelAuthenticatorFactory }
                },
            )
        }

        // Simulate the real flow: onForegrounded transitions Locked -> Unlocking
        coordinator.onForegrounded()

        val controller = Robolectric.buildActivity(AppLockActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        val activity = controller.get()
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `activity does not exit when authentication is interrupted`() {
        val interruptedAuthenticatorFactory = object : AppLockAuthenticatorFactory {
            override fun create(
                activity: FragmentActivity,
                title: String,
                subtitle: String,
            ): AppLockAuthenticator = FakeAuthenticator.failure(AppLockError.Interrupted)
        }

        stopKoin()
        startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(
                module {
                    single { coordinator }
                    single<AppLockCoordinator> { coordinator }
                    single<AppLockAuthenticatorFactory> { interruptedAuthenticatorFactory }
                },
            )
        }

        // Simulate the real flow: onForegrounded transitions Locked -> Unlocking
        coordinator.onForegrounded()

        val controller = Robolectric.buildActivity(AppLockActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()

        // After Interrupted, the policy returns to Locked (ready for retry)
        // The activity should NOT exit (unlike Canceled)
        val activity = controller.get()
        assertThat(activity.isFinishing).isFalse()
    }

    private class FakeAppLockConfigRepository(
        private var config: AppLockConfig,
    ) : AppLockConfigRepository {
        override fun getConfig(): AppLockConfig = config

        override fun setConfig(config: AppLockConfig) {
            this.config = config
        }
    }

    private class FakeAppLockAvailability : AppLockAvailability {
        override fun isAuthenticationAvailable(): Boolean = true
    }

    private class FakeAppLockAuthenticatorFactory : AppLockAuthenticatorFactory {
        override fun create(
            activity: FragmentActivity,
            title: String,
            subtitle: String,
        ): AppLockAuthenticator {
            return FakeAuthenticator.success()
        }
    }
}
