package com.zfl.launcher.settings

/**
 * Whether the first-run setup still has to be shown.
 *
 * [storedDone] is the saved flag, or `null` when this install never stored one. Without it, an
 * install that already has saved settings or a saved Home Screen layout was set up by a version
 * before the onboarding existed: that user has finished setting up and must not see it.
 * The answer is stored right away, so a layout saved later (e.g. when the user picked ZFL as
 * the home app before opening it) can't change it.
 */
fun isOnboardingNeeded(storedDone: Boolean?, hasSavedSettings: Boolean, hasSavedLayout: Boolean): Boolean =
    if (storedDone != null) !storedDone else !(hasSavedSettings || hasSavedLayout)
