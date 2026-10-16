package com.zfl.launcher.settings

/**
 * Flag shown next to each language, as an ISO 3166 region. English uses the United States flag:
 * the default resources are written in American English and a bare "en" means en-US on Android.
 * A language missing here gets a neutral globe instead of a flag.
 */
val FLAG_REGIONS: Map<String, String> = mapOf(
    "en" to "US",
    "ru" to "RU",
)

/** Language subtag of a BCP 47 tag: "pt-BR" → "pt". */
fun languageOf(tag: String): String = tag.substringBefore('-').substringBefore('_').lowercase()

/** Region whose flag stands for [tag], or `null` for the globe. */
fun flagRegion(tag: String): String? = FLAG_REGIONS[languageOf(tag)]

/** The supported language matching [tag]: an exact match first, then the same language. */
private fun match(tag: String, supported: List<String>): String? =
    supported.firstOrNull { it.equals(tag, ignoreCase = true) } ?: supported.firstOrNull { languageOf(it) == languageOf(tag) }

/**
 * The language ZFL shows when it follows the system: the first system language it supports,
 * otherwise English. [supported] comes from `locales_config.xml`; [systemTags] in preference order.
 */
fun defaultLanguage(supported: List<String>, systemTags: List<String>): String =
    systemTags.firstNotNullOfOrNull { match(it, supported) }
        ?: match("en", supported)
        ?: supported.first()

/** The language checked in the list: the one chosen for ZFL ([override]) or else [defaultLanguage]. */
fun selectedLanguage(supported: List<String>, override: String?, systemTags: List<String>): String =
    override?.let { match(it, supported) } ?: defaultLanguage(supported, systemTags)

/**
 * What to store when the user picks [chosen]: `null` (follow the system) when that is what the
 * system language gives anyway, so a later change of the system language still applies.
 */
fun languageToStore(chosen: String, supported: List<String>, systemTags: List<String>): String? =
    chosen.takeUnless { it == defaultLanguage(supported, systemTags) }
