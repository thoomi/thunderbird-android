package net.thunderbird.feature.applock.impl.ui

import androidx.fragment.app.FragmentActivity
import net.thunderbird.feature.applock.api.AppLockAuthenticator
import net.thunderbird.feature.applock.impl.domain.BiometricAuthenticator

/**
 * Default factory that creates [BiometricAuthenticator] instances.
 */
internal class DefaultAppLockAuthenticatorFactory : AppLockAuthenticatorFactory {
    override fun create(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
    ): AppLockAuthenticator {
        return BiometricAuthenticator(
            activity = activity,
            title = title,
            subtitle = subtitle,
        )
    }
}
