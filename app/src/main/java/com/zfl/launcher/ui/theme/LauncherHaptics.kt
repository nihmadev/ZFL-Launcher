package com.zfl.launcher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.zfl.launcher.settings.LocalSettingsState

/** Keeps the platform's system preference checks; adds the live launcher preference. */
@Composable
fun launcherHaptics(): HapticFeedback {
    val platform = LocalHapticFeedback.current
    val enabled by rememberUpdatedState(LocalSettingsState.current.homeBehavior.haptics)
    return remember(platform) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (enabled) platform.performHapticFeedback(hapticFeedbackType)
            }
        }
    }
}
