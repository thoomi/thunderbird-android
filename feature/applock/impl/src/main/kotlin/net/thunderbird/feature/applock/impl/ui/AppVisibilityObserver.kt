package net.thunderbird.feature.applock.impl.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.thunderbird.feature.applock.api.AppLockCoordinator
import net.thunderbird.feature.applock.api.AppLockEffect

/**
 * Observes app-level visibility via [ProcessLifecycleOwner] and manages app lock flow.
 *
 * - ON_START: Notify coordinator of foreground
 * - ON_STOP: Notify coordinator of background
 * - Observes effects to launch lock screen when needed
 */
internal class AppVisibilityObserver(
    private val application: Application,
    private val coordinatorProvider: () -> AppLockCoordinator,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var effectsObserved = false

    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    private fun ensureEffectsObserved() {
        if (effectsObserved) return
        effectsObserved = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            coordinatorProvider().effects.collect { effect ->
                when (effect) {
                    AppLockEffect.LaunchLockScreen -> launchLockScreen()
                    AppLockEffect.ExitApp -> {
                        // ExitApp is handled by AppLockActivity
                    }
                }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        ensureEffectsObserved()
        coordinatorProvider().onForegrounded()
    }

    override fun onStop(owner: LifecycleOwner) {
        coordinatorProvider().onBackgrounded()
    }

    private fun launchLockScreen() {
        val intent = AppLockActivity.createIntent(application).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        application.startActivity(intent)
    }
}
