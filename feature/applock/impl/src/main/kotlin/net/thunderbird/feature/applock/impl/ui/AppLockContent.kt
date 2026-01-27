package net.thunderbird.feature.applock.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilled
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextHeadlineSmall
import app.k9mail.core.ui.compose.designsystem.molecule.ErrorView
import app.k9mail.core.ui.compose.designsystem.molecule.LoadingView
import app.k9mail.core.ui.compose.theme2.MainTheme
import net.thunderbird.core.ui.compose.designsystem.atom.icon.Icon
import net.thunderbird.core.ui.compose.designsystem.atom.icon.Icons
import net.thunderbird.feature.applock.api.AppLockError
import net.thunderbird.feature.applock.api.AppLockState
import net.thunderbird.feature.applock.impl.R

/**
 * Content composable for the authentication screen.
 *
 * Displays the appropriate UI based on the current coordinator state.
 * With coordinator-driven auth, the UI should primarily see [AppLockState.Unlocking]
 * (while biometric prompt is showing) or [AppLockState.Failed] (after auth failure).
 */
@Composable
internal fun AppLockContent(
    state: AppLockState,
    onRetry: () -> Unit,
    onDisableAppLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is AppLockState.Unlocking -> {
                LoadingView(
                    message = stringResource(R.string.applock_loading_message),
                )
            }
            AppLockState.Locked -> {
                // Brief transitional state - coordinator should quickly move to Unlocking
                LoadingView(
                    message = stringResource(R.string.applock_loading_message),
                )
            }
            is AppLockState.Failed -> {
                val error = state.error
                if (error.requiresDisableAction()) {
                    RequirementsView(
                        message = error.toErrorMessage(),
                        onDisableAppLock = onDisableAppLock,
                    )
                } else {
                    ErrorView(
                        title = stringResource(R.string.applock_error_title),
                        message = error.toErrorMessage(),
                        onRetry = if (error.allowsRetry()) onRetry else null,
                    )
                }
            }
            AppLockState.Disabled,
            is AppLockState.Unlocked,
            -> {
                // These states shouldn't be visible in the lock screen
                // Activity should finish when these states are reached
                LoadingView(
                    message = stringResource(R.string.applock_loading_message),
                )
            }
        }
    }
}

@Composable
private fun RequirementsView(
    message: String,
    onDisableAppLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(MainTheme.spacings.triple),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            tint = MainTheme.colors.error,
        )

        Spacer(modifier = Modifier.height(MainTheme.spacings.double))

        TextHeadlineSmall(
            text = stringResource(R.string.applock_requirements_title),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(MainTheme.spacings.double))

        TextBodyMedium(
            text = message,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(MainTheme.spacings.double))

        TextBodyMedium(
            text = stringResource(R.string.applock_requirements_hint),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MainTheme.colors.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(MainTheme.spacings.triple))

        ButtonFilled(
            text = stringResource(R.string.applock_button_disable),
            onClick = onDisableAppLock,
        )
    }
}

@Composable
private fun AppLockError.toErrorMessage(): String {
    return when (this) {
        AppLockError.NotAvailable -> stringResource(R.string.applock_error_not_available)
        AppLockError.NotEnrolled -> stringResource(R.string.applock_error_not_enrolled)
        AppLockError.Failed -> stringResource(R.string.applock_error_failed)
        AppLockError.Canceled -> stringResource(R.string.applock_error_canceled)
        AppLockError.Interrupted -> stringResource(R.string.applock_error_failed)
        is AppLockError.Lockout -> {
            if (durationSeconds == Int.MAX_VALUE) {
                stringResource(R.string.applock_error_lockout_permanent)
            } else if (durationSeconds <= 0) {
                stringResource(R.string.applock_error_lockout_unknown)
            } else {
                stringResource(R.string.applock_error_lockout, durationSeconds)
            }
        }
        is AppLockError.UnableToStart -> stringResource(R.string.applock_error_unable_to_start, message)
    }
}

private fun AppLockError.allowsRetry(): Boolean {
    return when (this) {
        AppLockError.Failed,
        AppLockError.Interrupted,
        is AppLockError.UnableToStart,
        -> true
        AppLockError.NotAvailable,
        AppLockError.NotEnrolled,
        AppLockError.Canceled,
        is AppLockError.Lockout,
        -> false
    }
}

private fun AppLockError.requiresDisableAction(): Boolean {
    return this is AppLockError.NotAvailable || this is AppLockError.NotEnrolled
}
