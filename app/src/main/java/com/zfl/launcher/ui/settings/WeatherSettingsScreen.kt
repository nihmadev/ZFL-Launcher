package com.zfl.launcher.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zfl.launcher.ui.theme.LocalAccent
import com.zfl.launcher.R
import com.zfl.launcher.data.WeatherPlace
import com.zfl.launcher.data.WeatherRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Search requests wait for the user to stop typing for this long. */
private const val SEARCH_DEBOUNCE_MS = 400L

/**
 * Weather widget settings: temperature unit and the place the forecast is for, either the
 * device location (coarse, asked on first use) or a city found by name.
 */
@Composable
fun WeatherSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val weather = remember { WeatherRepository.get(context) }
    val state by weather.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WeatherPlace>>(emptyList()) }

    val useCurrentLocation: () -> Unit = { scope.launch { weather.setPlace(WeatherPlace("", Double.NaN, Double.NaN, current = true)) } }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) useCurrentLocation()
    }

    LaunchedEffect(query) {
        delay(SEARCH_DEBOUNCE_MS)
        results = weather.searchPlaces(query)
    }

    SettingsPage(title = stringResource(R.string.widget_weather), onBack = onBack) {
        item {
            SegmentedControl(
                options = listOf("°C", "°F"),
                selectedIndex = if (state.fahrenheit) 1 else 0,
                onSelect = { weather.setFahrenheit(it == 1) },
            )
        }
        item { SectionHeader(stringResource(R.string.weather_location)) }
        item {
            SettingsCard {
                SettingsRow(onClick = {
                    if (weather.hasLocationPermission()) useCurrentLocation() else askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }) {
                    Icon(Icons.Rounded.NearMe, null, tint = LocalAccent.current.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    RowTitle(stringResource(R.string.weather_current_location), modifier = Modifier.weight(1f))
                    if (state.place?.current == true) CheckMark()
                }
                // The chosen city, shown as the selected option.
                state.place?.takeIf { !it.current }?.let { place ->
                    SettingsRow(onClick = null) {
                        RowTitle(place.name, modifier = Modifier.weight(1f))
                        CheckMark()
                    }
                }
            }
        }
        item { SectionHeader(stringResource(R.string.weather_search_city)) }
        item {
            SettingsCard {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, tint = SettingsStyle.SecondaryText, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text(stringResource(R.string.weather_search_hint), color = SettingsStyle.SecondaryText, fontSize = 16.sp)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                            cursorBrush = SolidColor(LocalAccent.current.primary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                results.forEach { place ->
                    SettingsRow(onClick = {
                        query = ""
                        scope.launch { weather.setPlace(place) }
                    }) { RowTitle(place.name, modifier = Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CheckMark() {
    Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
}
