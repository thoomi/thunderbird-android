package net.thunderbird.feature.applock.api

/**
 * One-shot effects that the UI must handle.
 */
sealed interface AppLockEffect {
    /**
     * Launch the lock screen to authenticate.
     */
    data object LaunchLockScreen : AppLockEffect

    /**
     * Exit the app (e.g., user canceled authentication).
     */
    data object ExitApp : AppLockEffect
}
