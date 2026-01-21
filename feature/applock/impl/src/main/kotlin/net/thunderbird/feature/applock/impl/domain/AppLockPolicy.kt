package net.thunderbird.feature.applock.impl.domain

import net.thunderbird.feature.applock.api.AppLockState

/**
 * Pure policy interface that reduces app lock state transitions.
 */
internal fun interface AppLockPolicy {
    /**
     * Reduce a state transition given the current state and event.
     */
    fun reduce(state: AppLockState, event: AppLockEvent): AppLockState
}
