package com.zfl.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** reconcile: new installs, Library Only, hidden apps and paused profiles. */
class NewAppPlacementTest {
    private val libraryOnly = NewAppPlacement.LibraryOnly
    private val folder = FolderItem("folder-1", "Tools", listOf("c#0", "d#0"))

    /** A layout as the launcher saves it after its first reconcile. */
    private fun settled(vararg installed: String, newApps: NewAppPlacement = NewAppPlacement.HomeAndLibrary): LayoutData =
        LayoutData(pages = listOf(listOf(AppItem("a#0"), folder)), dock = listOf(AppItem("b#0")))
            .reconcile(installed.toList(), newApps = newApps)

    private val base = arrayOf("a#0", "b#0", "c#0", "d#0")

    @Test
    fun migratedLayoutTreatsNoInstalledAppAsNew() {
        // Saved before `known` existed: an app missing from it was placed by the old version too.
        val old = LayoutData(pages = listOf(listOf(AppItem("a#0"))), dock = emptyList(), removed = setOf("r#0"))
        assertNull(old.known)
        val next = old.reconcile(listOf("a#0", "x#0", "r#0"), newApps = libraryOnly)
        assertEquals(setOf("a#0", "x#0"), next.allAppKeys())
        assertEquals(setOf("r#0"), next.removed)
        assertEquals(setOf("a#0", "x#0", "r#0"), next.known)
    }

    @Test
    fun freshInstallPlacesEverythingWhateverThePolicy() {
        val first = LayoutData(pages = listOf(listOf(AppItem("a#0"), AppItem("b#0"))), dock = emptyList())
        val next = first.reconcile(listOf("a#0", "b#0"), newApps = libraryOnly)
        assertEquals(setOf("a#0", "b#0"), next.allAppKeys())
        assertTrue(next.removed.isEmpty())
    }

    @Test
    fun newInstallGoesHomeByDefault() {
        val next = settled(*base).reconcile((base + "new#0").toList())
        assertEquals(AppItem("new#0"), next.pages.last().last())
        assertEquals(folder, next.pages.first()[1])
    }

    @Test
    fun newInstallStaysInLibraryWithLibraryOnly() {
        val layout = settled(*base)
        val next = layout.reconcile((base + "new#0").toList(), newApps = libraryOnly)
        assertFalse("new#0" in next.allAppKeys())
        assertTrue("new#0" in next.removed)
        assertEquals(layout.pages, next.pages)
        assertEquals(layout.dock, next.dock)
        // Nothing changes on the next start either.
        assertEquals(next, next.reconcile((base + "new#0").toList(), newApps = libraryOnly))
    }

    @Test
    fun addToHomeAlwaysPlacesALibraryOnlyApp() {
        val next = settled(*base).reconcile((base + "new#0").toList(), newApps = libraryOnly).addToHome("new#0")
        assertTrue("new#0" in next.allAppKeys())
        assertFalse("new#0" in next.removed)
        assertEquals(next, next.reconcile((base + "new#0").toList(), newApps = libraryOnly))
    }

    @Test
    fun switchingPolicyBackDoesNotPourLibraryAppsOntoHome() {
        val libraryApps = settled(*base).reconcile((base + "new#0").toList(), newApps = libraryOnly)
        val back = libraryApps.reconcile((base + "new#0").toList())
        assertFalse("new#0" in back.allAppKeys())
        // And the next install follows the new policy.
        assertTrue("later#0" in back.reconcile((base + "new#0" + "later#0").toList()).allAppKeys())
    }

    @Test
    fun switchingToLibraryOnlyKeepsTheHomeScreen() {
        val layout = settled(*base)
        val next = layout.reconcile(base.toList(), newApps = libraryOnly)
        assertEquals(layout, next)
    }

    @Test
    fun updateIsNotANewInstall() {
        val layout = settled(*base).removeFromHome("a#0")
        // An update reports the same keys again (only the icon version changes).
        assertEquals(layout, layout.reconcile(base.toList(), newApps = libraryOnly))
        assertEquals(layout, layout.reconcile(base.toList()))
    }

    @Test
    fun removedStaysRemovedAndReinstallIsNew() {
        val layout = settled(*base).removeFromHome("a#0")
        assertFalse("a#0" in layout.reconcile(base.toList()).allAppKeys())
        // Uninstalled: forgotten, so installing it again counts as a new install.
        val gone = layout.reconcile(listOf("b#0", "c#0", "d#0"))
        assertFalse("a#0" in gone.removed || "a#0" in gone.known.orEmpty())
        assertTrue("a#0" in gone.reconcile(base.toList()).allAppKeys())
        assertFalse("a#0" in gone.reconcile(base.toList(), newApps = libraryOnly).allAppKeys())
    }

    @Test
    fun unhiddenAppReturnsHomeButIsNotNew() {
        val hidden = settled(*base).reconcile(listOf("b#0", "c#0", "d#0"), hidden = setOf("a#0"), newApps = libraryOnly)
        assertFalse("a#0" in hidden.allAppKeys())
        assertTrue("a#0" in hidden.known.orEmpty())
        assertFalse("a#0" in hidden.removed)
        // Shown again: back on the Home Screen as before hiding, even with Library Only.
        assertTrue("a#0" in hidden.reconcile(base.toList(), newApps = libraryOnly).allAppKeys())
        // A library-only app that gets hidden and shown again stays in the library.
        val libraryApp = settled(*base).reconcile((base + "new#0").toList(), newApps = libraryOnly)
        val rehidden = libraryApp.reconcile(base.toList(), hidden = setOf("new#0"))
        assertTrue("new#0" in rehidden.removed)
        assertFalse("new#0" in rehidden.reconcile((base + "new#0").toList()).allAppKeys())
    }

    @Test
    fun pausedProfileKeepsItsAppsAndReturnWithoutReinstall() {
        val work = arrayOf("mail#10", "docs#10")
        val layout = settled(*base, *work).removeFromHome("docs#10")
        assertEquals(layout.allAppKeys() + "mail#10", layout.allAppKeys())
        // Paused and listing nothing: its apps keep their place and the removed mark.
        val paused = layout.reconcile(base.toList(), unavailableUsers = setOf("10"), newApps = libraryOnly)
        assertEquals(layout, paused)
        // Back: nothing new, nothing moved.
        assertEquals(layout, paused.reconcile((base + work).toList(), newApps = libraryOnly))
        // A profile that is really gone (deleted) is cleaned up.
        val deleted = layout.reconcile(base.toList())
        assertFalse(deleted.allAppKeys().any { it.endsWith("#10") } || deleted.removed.any { it.endsWith("#10") })
    }

    @Test
    fun knownSurvivesProcessRestart() {
        val layout = settled(*base).reconcile((base + "new#0").toList(), newApps = libraryOnly)
        val restored = layoutFromJson(layout.toJson(), GridSpec.Default)!!
        assertEquals(layout, restored)
        assertEquals(restored, restored.reconcile((base + "new#0").toList(), newApps = libraryOnly))
        // An older file without "known" reads as null, which the next reconcile fills in.
        val old = org.json.JSONObject(layout.toJson()).apply { remove("known") }.toString()
        assertNull(layoutFromJson(old, GridSpec.Default)!!.known)
    }

    @Test
    fun foldersAndRemovedSurviveLibraryOnlyInstalls() {
        val layout = settled(*base)
        val next = layout.reconcile((base + "x#0" + "y#0").toList(), newApps = libraryOnly)
        assertEquals(layout.allAppKeys(), next.allAppKeys())
        assertEquals(folder, next.findFolder("folder-1"))
        assertEquals(setOf("x#0", "y#0"), next.removed)
    }

    @Test
    fun userOfReadsTheProfileSuffix() {
        assertEquals("10", userOf("com.a/.Main#10"))
        assertEquals("", userOf("legacy"))
    }
}
