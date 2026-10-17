package com.zfl.launcher.settings

import com.zfl.launcher.ui.onboarding.FLAG_DRAWABLES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LanguageChoiceTest {
    private val supported = listOf("en", "ru", "pl", "uk", "be", "es", "fr", "it", "de", "zh-Hans", "ja", "pt", "kk", "tr", "sr")

    @Test
    fun defaultFollowsFirstSupportedSystemLanguage() {
        assertEquals("ru", defaultLanguage(supported, listOf("ru-RU", "en-US")))
        assertEquals("ru", defaultLanguage(supported, listOf("ko-KR", "ru-RU")))
        assertEquals("en", defaultLanguage(supported, listOf("en-GB")))
    }

    @Test
    fun unsupportedSystemLanguageFallsBackToEnglish() {
        assertEquals("en", defaultLanguage(supported, listOf("ko-KR", "ar-EG")))
        assertEquals("en", defaultLanguage(supported, emptyList()))
    }

    @Test
    fun overrideIsSelectedOverSystem() {
        assertEquals("en", selectedLanguage(supported, override = "en", systemTags = listOf("ru-RU")))
        assertEquals("ru", selectedLanguage(supported, override = null, systemTags = listOf("ru-RU")))
        // A stored language that is no longer offered falls back to the system one.
        assertEquals("ru", selectedLanguage(supported, override = "ko", systemTags = listOf("ru-RU")))
    }

    @Test
    fun choosingTheSystemLanguageFollowsTheSystem() {
        assertNull(languageToStore("ru", supported, listOf("ru-RU")))
        assertEquals("en", languageToStore("en", supported, listOf("ru-RU")))
        assertNull(languageToStore("en", supported, listOf("ko-KR")))
    }

    @Test
    fun flagsComeFromTheTableWithGlobeFallback() {
        assertEquals("US", flagRegion("en"))
        assertEquals("RU", flagRegion("ru-RU"))
        assertNull(flagRegion("ko"))
        assertTrue(FLAG_DRAWABLES.keys.containsAll(FLAG_REGIONS.values))
    }

    @Test
    fun addedLanguagesMatchRegionalTagsAndKeepExplicitChoices() {
        val regions = mapOf("pl" to "PL", "uk" to "UA", "be" to "BY", "es" to "ES", "fr" to "FR", "it" to "IT",
            "de" to "DE", "zh-Hans" to "CN", "ja" to "JP", "pt" to "PT",
            "kk" to "KZ", "tr" to "TR", "sr" to "RS")
        regions.forEach { (language, region) ->
            val tag = "$language-$region"
            assertEquals(language, defaultLanguage(supported, listOf("ko-KR", tag)))
            assertEquals(language, selectedLanguage(supported, tag, listOf("en-US")))
            assertEquals(language, languageToStore(language, supported, listOf("en-US")))
            assertNull(languageToStore(language, supported, listOf(tag)))
            assertEquals(region, flagRegion(tag))
        }
        assertEquals("es", defaultLanguage(supported, listOf("es-MX")))
        assertEquals("fr", defaultLanguage(supported, listOf("fr-CA")))
        assertEquals("zh-Hans", defaultLanguage(supported, listOf("zh-CN")))
        assertEquals("zh-Hans", defaultLanguage(supported, listOf("zh-SG")))
        assertEquals("pt", defaultLanguage(supported, listOf("pt-BR")))
        assertEquals("sr", defaultLanguage(supported, listOf("sr-Cyrl-RS")))
    }

    /** Adding values-xx without a locales_config entry (or the reverse) would hide or break a language. */
    @Test
    fun localesConfigMatchesTranslations() {
        val res = File("src/main/res")
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(res, "xml/locales_config.xml"))
        val nodes = doc.getElementsByTagName("locale")
        val configured = List(nodes.length) {
            nodes.item(it).attributes.getNamedItemNS("http://schemas.android.com/apk/res/android", "name").nodeValue
        }.toSet()
        val translated = res.listFiles().orEmpty()
            .filter { it.name.startsWith("values-") && File(it, "strings.xml").exists() }
            .map { qualifierToTag(it.name.removePrefix("values-")) }
            .toSet()
        assertEquals(translated + "en", configured)
        assertEquals(supported.toSet(), configured)
    }

    /** "ru" → "ru", "pt-rBR" → "pt-BR", "b+sr+Latn" → "sr-Latn". */
    private fun qualifierToTag(qualifier: String): String =
        if (qualifier.startsWith("b+")) qualifier.removePrefix("b+").replace('+', '-') else qualifier.replace("-r", "-")
}
