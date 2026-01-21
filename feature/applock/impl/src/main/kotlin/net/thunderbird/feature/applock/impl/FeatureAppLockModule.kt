package net.thunderbird.feature.applock.impl

import androidx.biometric.BiometricManager
import net.thunderbird.feature.applock.api.AppLockCoordinator
import net.thunderbird.feature.applock.impl.data.AppLockConfigStore
import net.thunderbird.feature.applock.impl.domain.AppLockTimeoutCalculator
import net.thunderbird.feature.applock.impl.domain.DefaultAppLockCoordinator
import net.thunderbird.feature.applock.impl.domain.DefaultAppLockPolicy
import net.thunderbird.feature.applock.impl.domain.DefaultBiometricAvailabilityChecker
import net.thunderbird.feature.applock.impl.ui.AppLockAuthenticatorFactory
import net.thunderbird.feature.applock.impl.ui.AppVisibilityObserver
import net.thunderbird.feature.applock.impl.ui.DefaultAppLockAuthenticatorFactory
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin DI module for the app lock feature.
 *
 * Public API:
 * - [AppLockCoordinator] - Main entry point for app lock functionality
 */
val featureAppLockModule: Module = module {
    // Internal components
    single {
        AppLockConfigStore(
            context = androidContext(),
        )
    }

    single {
        AppLockTimeoutCalculator()
    }

    single {
        DefaultAppLockPolicy(
            timeoutCalculator = get(),
        )
    }

    single {
        DefaultBiometricAvailabilityChecker(
            biometricManager = BiometricManager.from(androidApplication()),
        )
    }

    single {
        DefaultAppLockCoordinator(
            configRepository = get<AppLockConfigStore>(),
            availability = get<DefaultBiometricAvailabilityChecker>(),
            policy = get<DefaultAppLockPolicy>(),
        )
    }

    // Public API - only this is exposed for injection by other modules
    single<AppLockCoordinator> { get<DefaultAppLockCoordinator>() }

    // Internal UI components
    single<AppLockAuthenticatorFactory> {
        DefaultAppLockAuthenticatorFactory()
    }

    single(createdAtStart = true) {
        AppVisibilityObserver(
            application = androidApplication(),
            coordinatorProvider = { get() },
        ).apply { register() }
    }
}
