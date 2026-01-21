package net.thunderbird.feature.applock.impl.ui

import androidx.fragment.app.FragmentActivity
import net.thunderbird.feature.applock.api.AppLockAuthenticator

/**
 * Factory for creating authenticators for the app lock flow.
 */
internal interface AppLockAuthenticatorFactory {
    fun create(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
    ): AppLockAuthenticator
}
