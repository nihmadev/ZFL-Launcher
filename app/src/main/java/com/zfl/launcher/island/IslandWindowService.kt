package com.zfl.launcher.island

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service whose only job is to own the Island window: an accessibility overlay is
 * the one window type above the status bar, so the capsule around the camera can be touched.
 * It subscribes to no events, cannot read window content and performs no actions.
 */
class IslandWindowService : AccessibilityService() {
    override fun onServiceConnected(): Unit {
        super.onServiceConnected()
        IslandAccess.windowService(this, this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?): Unit = Unit

    override fun onInterrupt(): Unit = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        IslandAccess.windowService(this, null)
        return super.onUnbind(intent)
    }

    override fun onDestroy(): Unit {
        IslandAccess.windowService(this, null)
        super.onDestroy()
    }
}
