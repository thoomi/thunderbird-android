package net.thunderbird.feature.applock.impl.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import app.k9mail.core.ui.compose.designsystem.atom.Surface
import app.k9mail.core.ui.compose.theme2.thunderbird.ThunderbirdTheme2
import net.thunderbird.core.outcome.Outcome
import net.thunderbird.feature.applock.api.AppLockEffect
import net.thunderbird.feature.applock.api.AppLockError
import net.thunderbird.feature.applock.api.AppLockCoordinator
import net.thunderbird.feature.applock.api.AppLockState
import net.thunderbird.feature.applock.api.isUnlocked
import net.thunderbird.feature.applock.impl.R
import org.koin.android.ext.android.inject

/**
 * Activity that handles app-level authentication using biometrics or device credentials.
 *
 * This activity is launched by [AppVisibilityObserver] when the coordinator emits
 * [AppLockEffect.LaunchLockScreen]. The coordinator will have already transitioned
 * to [AppLockState.Unlocking] state. The UI observes this state and calls
 * [AppLockCoordinator.authenticate] to trigger the biometric prompt.
 *
 * - Pressing back closes the entire app (finishAffinity)
 * - User cancellation closes the app (via ExitApp effect)
 * - Successful authentication finishes this activity
 */
class AppLockActivity : FragmentActivity() {

    private val coordinator: AppLockCoordinator by inject()
    private val authenticatorFactory: AppLockAuthenticatorFactory by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishAffinity()
                }
            },
        )

        setContent {
            val state by coordinator.state.collectAsState()

            val title = stringResource(R.string.applock_prompt_title)
            val subtitle = stringResource(R.string.applock_prompt_subtitle)

            val authenticator = remember(title, subtitle, authenticatorFactory) {
                authenticatorFactory.create(
                    activity = this@AppLockActivity,
                    title = title,
                    subtitle = subtitle,
                )
            }

            // Finish when unlocked
            LaunchedEffect(state) {
                if (state.isUnlocked()) {
                    finish()
                }
            }

            // Authenticate when coordinator enters Unlocking state
            // Key on attemptId so the effect isn't cancelled when state changes to Unlocked/Failed
            val attemptId = (state as? AppLockState.Unlocking)?.attemptId
            LaunchedEffect(attemptId) {
                if (attemptId == null) return@LaunchedEffect
                val result = coordinator.authenticate(authenticator)
                if (result is Outcome.Failure && result.error is AppLockError.Canceled) {
                    finishAffinity()
                }
            }

            // Observe effects
            LaunchedEffect(Unit) {
                coordinator.effects.collect { effect ->
                    when (effect) {
                        AppLockEffect.ExitApp -> finishAffinity()
                        AppLockEffect.LaunchLockScreen -> {
                            // Already on lock screen, ignore
                        }
                    }
                }
            }

            ThunderbirdTheme2 {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppLockContent(
                        state = state,
                        onRetry = {
                            coordinator.retry()
                        },
                        onDisableAppLock = {
                            val config = coordinator.config
                            coordinator.onSettingsChanged(config.copy(isEnabled = false))
                        },
                    )
                }
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, AppLockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }
}
