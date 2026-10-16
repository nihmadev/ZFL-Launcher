package com.zfl.launcher.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.zfl.launcher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val GPL_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
private const val HEADING_MAX_LENGTH = 60

/**
 * Full text of the GPL-3.0, bundled in the APK (res/raw/gpl_3_0.txt, a copy of the repo's
 * LICENSE) so it works offline. The launcher has no INTERNET permission; the "gnu.org"
 * row just hands the link to the browser.
 */
@Composable
fun LicenseScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val paragraphs: List<String> by produceState(emptyList()) {
        value = withContext(Dispatchers.IO) {
            resources.openRawResource(R.raw.gpl_3_0).bufferedReader().use { it.readText() }
        }
            // Paragraphs are separated by blank lines; inside one, lines are just wrapped.
            .split(Regex("\n\\s*\n"))
            .map { paragraph -> paragraph.lines().joinToString(" ") { it.trim() } }
            .filter { it.isNotBlank() }
    }

    SettingsPage(title = stringResource(R.string.row_license), onBack = onBack) {
        item {
            SettingsCard {
                Text(
                    stringResource(R.string.license_summary),
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(16.dp),
                )
                NavRow(Icons.Rounded.Public, stringResource(R.string.license_open_website), onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, GPL_URL.toUri())) }
                })
            }
            Spacer(Modifier.height(20.dp))
        }
        items(paragraphs) { paragraph ->
            // Short lines are headings ("Preamble", "0. Definitions.", ...).
            val isHeading = paragraph.length < HEADING_MAX_LENGTH
            Text(
                paragraph,
                color = if (isHeading) Color.White else Color.White.copy(alpha = 0.75f),
                fontSize = if (isHeading) 15.sp else 13.sp,
                fontWeight = if (isHeading) FontWeight.SemiBold else FontWeight.Normal,
                lineHeight = 19.sp,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = if (isHeading) 14.dp else 6.dp, bottom = 6.dp),
            )
        }
    }
}
