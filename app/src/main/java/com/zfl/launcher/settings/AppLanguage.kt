package com.zfl.launcher.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import com.zfl.launcher.R
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

/**
 * ZFL's own UI language (per-app language), shared by the home screen and the settings app.
 *
 * Android 13+ has a per-app language: it is set through [LocaleManager], the same setting the
 * system "App languages" screen edits, and Android recreates every activity with it.
 * Android 12 has no such API, so the choice is stored here and applied by [wrap] in
 * `attachBaseContext` of both activities. One mechanism per Android version, never both.
 */
object AppLanguage {
    private const val PREFS = "app_language"
    private const val KEY_TAG = "tag"

    private val hasPlatformApi: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Language tags listed in `res/xml/locales_config.xml`, in file order (the single source of truth). */
    fun supported(context: Context): List<String> {
        val parser = context.resources.getXml(R.xml.locales_config)
        return try {
            buildList {
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                        parser.getAttributeValue(ANDROID_NS, "name")?.let(::add)
                    }
                }
            }
        } finally {
            parser.close()
        }
    }

    /** The language chosen for ZFL, or `null` when it follows the system. */
    fun override(context: Context): String? =
        if (hasPlatformApi) {
            context.getSystemService(LocaleManager::class.java).applicationLocales
                .takeUnless { it.isEmpty }?.get(0)?.toLanguageTag()
        } else {
            legacyTag(context)
        }

    /** The device languages, in the user's order of preference. */
    fun systemTags(context: Context): List<String> {
        val locales = if (hasPlatformApi) {
            context.getSystemService(LocaleManager::class.java).systemLocales
        } else {
            Resources.getSystem().configuration.locales
        }
        return List(locales.size()) { locales[it].toLanguageTag() }
    }

    /** Applies [tag] (`null` = follow the system); the activities are recreated with it. */
    fun set(activity: Activity, tag: String?) {
        if (hasPlatformApi) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(commit = true) {
                if (tag == null) remove(KEY_TAG) else putString(KEY_TAG, tag)
            }
            activity.recreate()
        }
    }

    /** Android 12: the context an activity is created with, in the stored language. */
    fun wrap(base: Context): Context {
        if (hasPlatformApi) return base
        val locale = legacyTag(base)?.let(Locale::forLanguageTag) ?: Resources.getSystem().configuration.locales[0]
        // Formatters that use the default locale (dates in widgets) follow the UI language too.
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(config)
    }

    /**
     * Android 12: whether [activity] was created before the language changed in the other
     * activity (Android 13+ recreates activities itself).
     */
    fun isStale(activity: Activity): Boolean {
        if (hasPlatformApi) return false
        val wanted = legacyTag(activity) ?: Resources.getSystem().configuration.locales[0].toLanguageTag()
        return languageOf(wanted) != languageOf(activity.resources.configuration.locales[0].toLanguageTag())
    }

    private fun legacyTag(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAG, null)

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
}
