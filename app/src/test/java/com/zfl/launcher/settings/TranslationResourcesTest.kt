package com.zfl.launcher.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Resource contracts that compilation alone does not enforce. */
class TranslationResourcesTest {
    private val res = File("src/main/res")
    private val languages = listOf("pl", "uk", "be", "es", "fr", "it", "de", "b+zh+Hans", "ja", "pt", "kk", "tr", "sr")
    private val placeholder = Regex("%(?:[0-9]+\\$)?[ds]")

    @Test
    fun addedTranslationsCoverEveryResourceAndPreserveArguments() {
        val source = resources("values")
        languages.forEach { language ->
            val translated = resources("values-$language")
            assertEquals(language, source.keys, translated.keys)
            source.forEach { (name, original) ->
                val target = translated.getValue(name)
                assertEquals("$language/$name", original.tagName, target.tagName)
                val expected = arguments(if (original.tagName == "plurals") {
                    original.getElementsByTagName("item").item(0).textContent
                } else original.textContent)
                val values = if (target.tagName == "plurals") {
                    val items = target.getElementsByTagName("item")
                    List(items.length) { items.item(it).textContent }
                } else listOf(target.textContent)
                values.forEach { text ->
                    assertTrue("$language/$name is empty", text.isNotBlank())
                    assertEquals("$language/$name arguments", expected, arguments(text))
                }
            }
        }
    }

    @Test
    fun countTranslationsIncludeLanguageSpecificPluralForms() {
        languages.forEach { language ->
            val plural = resources("values-$language").getValue("import_remove_others_count")
            val items = plural.getElementsByTagName("item")
            val quantities = List(items.length) { (items.item(it) as Element).getAttribute("quantity") }.toSet()
            val required = when (language) {
                "pl", "uk", "be" -> setOf("one", "few", "many", "other")
                "b+zh+Hans", "ja" -> setOf("other")
                "de", "kk", "tr" -> setOf("one", "other")
                "sr" -> setOf("one", "few", "other")
                else -> setOf("one", "many", "other")
            }
            assertEquals(language, required, quantities)
        }
    }

    private fun arguments(text: String): List<String> = placeholder.findAll(text).map { it.value }.sorted().toList()

    private fun resources(directory: String): Map<String, Element> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(res, "$directory/strings.xml")).documentElement
        val elements = (0 until root.childNodes.length).mapNotNull { root.childNodes.item(it) as? Element }
            .filter { it.getAttribute("translatable") != "false" }
        assertEquals("Duplicate resource in $directory", elements.size, elements.map { it.getAttribute("name") }.toSet().size)
        return elements.associateBy { it.getAttribute("name") }
    }
}
