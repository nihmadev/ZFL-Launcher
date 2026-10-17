package com.zfl.launcher

/** Resume is not a Home press: permission screens and Settings resume without a command. */
class HomeIntentRouter {
    private var appWasLaunched: Boolean = false
    private var returningHome: Boolean = false
    private var stopped: Boolean = false

    fun stop() { stopped = true }

    fun appLaunched() { appWasLaunched = true }

    /** True only for a repeated Home while the launcher owns window focus. */
    fun intent(isHome: Boolean, focused: Boolean, broughtToFront: Boolean): Boolean {
        if (!isHome) return false
        if (focused && !broughtToFront) return true
        returningHome = true
        return false
    }

    /** A single navigation event after an app launch or an actual background Home Intent. */
    fun resume(): Boolean {
        val returned = (appWasLaunched && stopped) || returningHome
        if (stopped || returningHome) appWasLaunched = false
        stopped = false
        returningHome = false
        return returned
    }
}
