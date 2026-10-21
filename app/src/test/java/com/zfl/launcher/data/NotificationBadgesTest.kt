package com.zfl.launcher.data

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationBadgesTest {
    private fun note(
        key: String,
        packageName: String = "org.telegram",
        profile: String = "0",
        number: Int = 0,
        flags: Int = 0,
        media: Boolean = false,
        canShowBadge: Boolean = true,
        groupKey: String? = null,
    ) = BadgeNotification(key, packageName, profile, number, flags, media, canShowBadge, groupKey)

    private val telegram = badgeKey("org.telegram", "0")

    @Test
    fun countsOnePerNotification() {
        val counts = countBadges(listOf(note("a"), note("b")), OWN)
        assertEquals(mapOf(telegram to 2), counts)
    }

    @Test
    fun numberReplacesOne() {
        val counts = countBadges(listOf(note("a", number = 5), note("b")), OWN)
        assertEquals(6, counts[telegram])
    }

    @Test
    fun summaryWithMembersIsNotCounted() {
        val counts = countBadges(listOf(
            note("summary", flags = Notification.FLAG_GROUP_SUMMARY, groupKey = "g"),
            note("chat1", groupKey = "g"),
            note("chat2", groupKey = "g"),
        ), OWN)
        assertEquals(2, counts[telegram])
    }

    @Test
    fun loneSummaryCountsLikeTheShadeShowsIt() {
        val counts = countBadges(listOf(note("summary", flags = Notification.FLAG_GROUP_SUMMARY, groupKey = "g")), OWN)
        assertEquals(1, counts[telegram])
    }

    @Test
    fun summaryOfOngoingMembersIsNotCounted() {
        val counts = countBadges(listOf(
            note("summary", flags = Notification.FLAG_GROUP_SUMMARY, groupKey = "g"),
            note("upload", flags = Notification.FLAG_ONGOING_EVENT, groupKey = "g"),
        ), OWN)
        assertTrue(counts.isEmpty())
    }

    @Test
    fun ongoingForegroundAndMediaAreNotCounted() {
        val counts = countBadges(listOf(
            note("ongoing", flags = Notification.FLAG_ONGOING_EVENT),
            note("service", flags = Notification.FLAG_FOREGROUND_SERVICE),
            note("player", media = true),
        ), OWN)
        assertTrue(counts.isEmpty())
    }

    @Test
    fun channelWithoutBadgesIsNotCounted() {
        val counts = countBadges(listOf(note("a", canShowBadge = false), note("b")), OWN)
        assertEquals(1, counts[telegram])
    }

    @Test
    fun ownNotificationsAreNotCounted() {
        assertTrue(countBadges(listOf(note("a", packageName = OWN)), OWN).isEmpty())
    }

    @Test
    fun sameKeyCountsOnceWithItsLatestState() {
        val counts = countBadges(listOf(note("a", number = 3), note("a", number = 4)), OWN)
        assertEquals(4, counts[telegram])
    }

    @Test
    fun profilesStayApart() {
        val counts = countBadges(listOf(note("a", profile = "0"), note("b", profile = "10"), note("c", profile = "10")), OWN)
        assertEquals(1, counts[telegram])
        assertEquals(2, counts[badgeKey("org.telegram", "10")])
    }

    @Test
    fun folderSumsEachAppOnce() {
        val counts = mapOf(telegram to 3, badgeKey("com.mail", "0") to 2, badgeKey("org.telegram", "10") to 7)
        // Two launcher activities of Telegram share one count; the work profile app is not in the folder.
        assertEquals(5, badgeSum(listOf(telegram, telegram, badgeKey("com.mail", "0"), badgeKey("com.none", "0")), counts))
        assertEquals(0, badgeSum(listOf(telegram), emptyMap()))
    }

    @Test
    fun hugeNumbersDoNotOverflow() {
        val counts = countBadges((1..10).map { note("n$it", number = Int.MAX_VALUE) }, OWN)
        assertEquals(9_990, counts[telegram])
        assertEquals("99+", badgeText(counts.getValue(telegram)))
    }

    @Test
    fun textOverflowsAfterNinetyNine() {
        assertEquals("1", badgeText(1))
        assertEquals("99", badgeText(99))
        assertEquals("99+", badgeText(100))
    }

    private companion object {
        const val OWN = "com.zfl.launcher"
    }
}
