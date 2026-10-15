package com.zfl.launcher

import android.os.Bundle
import com.zfl.launcher.data.WidgetTarget

/**
 * Another app's widget on its way to the home screen: its allocated id, provider and where
 * it goes. Saved in the activity state because binding and configuring leave the launcher.
 */
data class PendingAppWidget(val appWidgetId: Int, val provider: String, val target: WidgetTarget) {
    fun save(out: Bundle) {
        out.putInt(KEY_ID, appWidgetId)
        out.putString(KEY_PROVIDER, provider)
        out.putInt(KEY_PAGE, (target as? WidgetTarget.Page)?.page ?: TODAY)
    }

    companion object {
        private const val KEY_ID = "pending_widget_id"
        private const val KEY_PROVIDER = "pending_widget_provider"
        private const val KEY_PAGE = "pending_widget_page"
        private const val TODAY = -1

        fun restore(state: Bundle): PendingAppWidget? {
            val provider = state.getString(KEY_PROVIDER) ?: return null
            val page = state.getInt(KEY_PAGE, TODAY)
            return PendingAppWidget(state.getInt(KEY_ID), provider, if (page == TODAY) WidgetTarget.Today else WidgetTarget.Page(page))
        }
    }
}
