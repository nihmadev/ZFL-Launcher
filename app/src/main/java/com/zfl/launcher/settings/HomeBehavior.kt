package com.zfl.launcher.settings

import java.util.Base64

/** An exact launcher activity and profile, independent of its display label. */
data class AppTarget(val component: String, val profile: String) {
    val key: String get() = "$component#$profile"
}

sealed interface HomeAction {
    data object None : HomeAction
    data object Search : HomeAction
    data object AppLibrary : HomeAction
    data object Settings : HomeAction
    /** Only offered for Repeat Home. */
    data object MainPage : HomeAction
    data class OpenApp(val target: AppTarget) : HomeAction
}

enum class HomeBinding { SwipeUp, SwipeDown, EmptyDoubleTap, RepeatHome }
enum class HomeReturn { Stay, MainPage }

data class HomeBehavior(
    val swipeUp: HomeAction = HomeAction.None,
    val swipeDown: HomeAction = HomeAction.Search,
    val emptyDoubleTap: HomeAction = HomeAction.None,
    val repeatHome: HomeAction = HomeAction.MainPage,
    val returnPolicy: HomeReturn = HomeReturn.Stay,
    /** Home ordinal, independent of Today. Missing pages fall back to the nearest ordinal. */
    val mainPage: Int = 0,
    val showToday: Boolean = true,
    val haptics: Boolean = true,
) {
    operator fun get(binding: HomeBinding): HomeAction = when (binding) {
        HomeBinding.SwipeUp -> swipeUp
        HomeBinding.SwipeDown -> swipeDown
        HomeBinding.EmptyDoubleTap -> emptyDoubleTap
        HomeBinding.RepeatHome -> repeatHome
    }

    fun assign(binding: HomeBinding, action: HomeAction): HomeBehavior = when (binding) {
        HomeBinding.SwipeUp -> copy(swipeUp = action)
        HomeBinding.SwipeDown -> copy(swipeDown = action)
        HomeBinding.EmptyDoubleTap -> copy(emptyDoubleTap = action)
        HomeBinding.RepeatHome -> copy(repeatHome = action)
    }

    fun sanitized(): HomeBehavior = copy(
        mainPage = mainPage.coerceAtLeast(0),
        swipeUp = swipeUp.takeUnless { it == HomeAction.MainPage } ?: HomeAction.None,
        swipeDown = swipeDown.takeUnless { it == HomeAction.MainPage } ?: HomeAction.None,
        emptyDoubleTap = emptyDoubleTap.takeUnless { it == HomeAction.MainPage } ?: HomeAction.None,
    )
}

/** Versioned, bounded data only; never interpreted as an Intent or executable command. */
object HomeActionCodec {
    fun encode(action: HomeAction): String = when (action) {
        HomeAction.None -> "none"
        HomeAction.Search -> "search"
        HomeAction.AppLibrary -> "library"
        HomeAction.Settings -> "settings"
        HomeAction.MainPage -> "main"
        is HomeAction.OpenApp -> "app1:${part(action.target.component)}:${part(action.target.profile)}"
    }

    fun decode(value: String?): HomeAction = when (value) {
        "search" -> HomeAction.Search
        "library" -> HomeAction.AppLibrary
        "settings" -> HomeAction.Settings
        "main" -> HomeAction.MainPage
        else -> runCatching {
            val parts = value?.takeIf { it.length <= 4096 }?.split(':') ?: return HomeAction.None
            if (parts.size != 3 || parts[0] != "app1") return HomeAction.None
            val component = unpart(parts[1])
            val profile = unpart(parts[2])
            if ('/' !in component || profile.toIntOrNull() == null) return HomeAction.None
            HomeAction.OpenApp(AppTarget(component, profile))
        }.getOrDefault(HomeAction.None)
    }

    private fun part(value: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun unpart(value: String): String = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
}

/** A present new key wins even when its value is unknown. Used by the one-time migration. */
fun migratedSwipe(stored: String?, present: Boolean, old: SearchSwipe, up: Boolean): HomeAction =
    if (present) HomeActionCodec.decode(stored) else when {
        up && old == SearchSwipe.Up -> HomeAction.Search
        !up && old == SearchSwipe.Down -> HomeAction.Search
        else -> HomeAction.None
    }

/** Exact identity only: never fall back to a similarly named app or another profile. */
fun <T> resolveHomeTarget(target: AppTarget, apps: List<T>, unavailableUsers: Set<String>, key: (T) -> String): T? =
    if (target.profile in unavailableUsers) null else apps.firstOrNull { key(it) == target.key }
