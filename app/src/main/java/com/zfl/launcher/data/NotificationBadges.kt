package com.zfl.launcher.data

import android.app.Notification

/**
 * What badge counting needs from one posted notification. No title, text or other content:
 * only who posted it, how many items it stands for and how the system classifies it.
 */
data class BadgeNotification(
    val key: String,
    val packageName: String,
    /** [profileId] of the user that posted it, so personal and work apps stay apart. */
    val profile: String,
    /** [Notification.number]; 0 when the app didn't set it. */
    val number: Int = 0,
    /** [Notification.flags]. */
    val flags: Int = 0,
    /** A media player notification (MediaStyle or a media session). */
    val media: Boolean = false,
    /** [android.service.notification.NotificationListenerService.Ranking.canShowBadge]: the channel allows badges. */
    val canShowBadge: Boolean = true,
    /** [android.service.notification.StatusBarNotification.getGroupKey]. */
    val groupKey: String? = null,
)

/** Badge key of an app: package + profile, the same pair [AppInfo.badgeKey] gives every launcher activity of it. */
fun badgeKey(packageName: String, profile: String): String = "$packageName#$profile"

/**
 * Badge counts per [badgeKey]. A notification counts [Notification.number] when set, 1 otherwise.
 * Not counted: [ownPackage]'s notifications, channels without badges, ongoing and foreground
 * service notifications, media players, and a group summary whose group has other notifications
 * (the summary only stands for them; alone, the shade shows it as a notification of its own).
 * The same key is counted once.
 */
fun countBadges(notifications: Collection<BadgeNotification>, ownPackage: String): Map<String, Int> {
    val unique = LinkedHashMap<String, BadgeNotification>(notifications.size)
    notifications.forEach { unique[it.key] = it }
    val groupsWithMembers = unique.values
        .filter { it.groupKey != null && it.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
        .mapTo(HashSet()) { it.groupKey }
    val counts = HashMap<String, Int>()
    for (notification in unique.values) {
        if (!notification.badged(ownPackage, groupsWithMembers)) continue
        val amount = if (notification.number > 0) notification.number.coerceAtMost(MAX_NOTIFICATION_NUMBER) else 1
        counts.merge(badgeKey(notification.packageName, notification.profile), amount) { a, b ->
            (a + b).coerceAtMost(MAX_BADGE_COUNT)
        }
    }
    return counts
}

private fun BadgeNotification.badged(ownPackage: String, groupsWithMembers: Set<String?>): Boolean = when {
    packageName == ownPackage || !canShowBadge || media -> false
    flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0 -> false
    flags and Notification.FLAG_GROUP_SUMMARY != 0 -> groupKey !in groupsWithMembers
    else -> true
}

/** A folder's badge: the sum over its apps, each package + profile once (two activities of one app don't double it). */
fun badgeSum(keys: Iterable<String>, counts: Map<String, Int>): Int {
    if (counts.isEmpty()) return 0
    var sum = 0
    keys.toSet().forEach { key -> sum = (sum + (counts[key] ?: 0)).coerceAtMost(MAX_BADGE_COUNT) }
    return sum
}

/** What the badge shows: the count up to [MAX_SHOWN_COUNT], then "99+". */
fun badgeText(count: Int): String = if (count > MAX_SHOWN_COUNT) "$MAX_SHOWN_COUNT+" else count.toString()

const val MAX_SHOWN_COUNT: Int = 99
/** Caps one notification's number and a total, so a bogus [Notification.number] can't overflow a sum. */
private const val MAX_NOTIFICATION_NUMBER = 999
private const val MAX_BADGE_COUNT = 99_999
