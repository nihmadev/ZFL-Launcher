package com.zfl.launcher.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

/** What weather widgets show. */
data class WeatherState(
    /** Null until the user picks a place (or allows the current location). */
    val place: WeatherPlace? = null,
    val forecast: Forecast? = null,
    val fahrenheit: Boolean = false,
)

/**
 * Weather for the widgets, from Open-Meteo (free, no account or API key).
 *
 * This is the only code in the app that uses the network, and it only runs while a
 * weather widget is on a page (see [refreshIfStale]) or when the user searches a city.
 * The last forecast is cached, so widgets show something right away and offline.
 */
class WeatherRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("weather", Context.MODE_PRIVATE)
    private val refreshLock = Mutex()

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<WeatherState> = _state

    fun setFahrenheit(value: Boolean) {
        prefs.edit { putBoolean(KEY_FAHRENHEIT, value) }
        _state.value = _state.value.copy(fahrenheit = value)
    }

    /** Uses a searched place, or [WeatherPlace.current] to follow the device location. Clears the old forecast. */
    suspend fun setPlace(place: WeatherPlace) {
        prefs.edit {
            putString(KEY_PLACE_NAME, place.name)
            putFloat(KEY_PLACE_LAT, place.latitude.toFloat())
            putFloat(KEY_PLACE_LON, place.longitude.toFloat())
            putBoolean(KEY_PLACE_CURRENT, place.current)
            remove(KEY_FORECAST)
        }
        _state.value = _state.value.copy(place = place, forecast = null)
        refresh()
    }

    /** Fetches a new forecast when the cached one is older than [MAX_AGE_MS]. */
    suspend fun refreshIfStale() {
        val forecast = _state.value.forecast
        if (forecast == null || System.currentTimeMillis() - forecast.fetchedAt > MAX_AGE_MS) refresh()
    }

    /** Fetches a new forecast now. Failures (offline, no location) keep the cached one. */
    suspend fun refresh(): Unit = refreshLock.withLock {
        val stored = _state.value.place ?: return
        val place = if (stored.current) currentPlace() ?: stored else stored
        if (place.latitude.isNaN()) return
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=7"
        val json = download(url) ?: return
        val forecast = runCatching { parseForecast(json, System.currentTimeMillis()) }
            .onFailure { Log.w(TAG, "Unreadable forecast", it) }
            .getOrNull() ?: return
        prefs.edit {
            putString(KEY_FORECAST, json)
            putLong(KEY_FETCHED_AT, forecast.fetchedAt)
            if (stored.current) {
                putString(KEY_PLACE_NAME, place.name)
                putFloat(KEY_PLACE_LAT, place.latitude.toFloat())
                putFloat(KEY_PLACE_LON, place.longitude.toFloat())
            }
        }
        _state.value = _state.value.copy(place = place.copy(current = stored.current), forecast = forecast)
    }

    /** City search for the settings screen (Open-Meteo geocoding). Empty on errors. */
    suspend fun searchPlaces(query: String): List<WeatherPlace> {
        if (query.isBlank()) return emptyList()
        val language = Locale.getDefault().language
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=10&language=$language&name=" + Uri.encode(query.trim())
        val json = download(url) ?: return emptyList()
        return runCatching { parsePlaces(json) }.getOrDefault(emptyList())
    }

    fun hasLocationPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** The device's coarse location, named by the system geocoder when it has one. */
    @SuppressLint("MissingPermission")
    private suspend fun currentPlace(): WeatherPlace? {
        if (!hasLocationPermission()) return null
        val manager = appContext.getSystemService(LocationManager::class.java)
        val location = manager.getProviders(true)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?: freshLocation(manager)
            ?: return null
        return WeatherPlace(placeName(location), location.latitude, location.longitude, current = true)
    }

    @SuppressLint("MissingPermission")
    private suspend fun freshLocation(manager: LocationManager): Location? {
        val provider = LocationManager.NETWORK_PROVIDER.takeIf { manager.isProviderEnabled(it) } ?: return null
        return withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, appContext.mainExecutor) { continuation.resume(it) }
            }
        }
    }

    private suspend fun placeName(location: Location): String {
        val fallback = _state.value.place?.name.orEmpty()
        if (!Geocoder.isPresent()) return fallback
        fun name(address: Address?): String = address?.locality ?: address?.subAdminArea ?: address?.adminArea ?: fallback
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { continuation ->
                        Geocoder(appContext).getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) = continuation.resume(name(addresses.firstOrNull()))
                            override fun onError(errorMessage: String?) = continuation.resume(fallback)
                        })
                    }
                } else {
                    // Android 12 only has the blocking call; this already runs on the IO dispatcher.
                    @Suppress("DEPRECATION")
                    runCatching { Geocoder(appContext).getFromLocation(location.latitude, location.longitude, 1) }
                        .getOrNull()?.firstOrNull().let(::name)
                }
            } ?: fallback
        }
    }

    private suspend fun download(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = NETWORK_TIMEOUT_MS
            connection.readTimeout = NETWORK_TIMEOUT_MS
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${connection.responseCode}")
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }.onFailure { Log.w(TAG, "Weather request failed", it) }.getOrNull()
    }

    private fun readState(): WeatherState {
        val place = prefs.getString(KEY_PLACE_NAME, null)?.let { name ->
            WeatherPlace(
                name = name,
                latitude = prefs.getFloat(KEY_PLACE_LAT, Float.NaN).toDouble(),
                longitude = prefs.getFloat(KEY_PLACE_LON, Float.NaN).toDouble(),
                current = prefs.getBoolean(KEY_PLACE_CURRENT, false),
            )
        }
        val forecast = prefs.getString(KEY_FORECAST, null)?.let { json ->
            runCatching { parseForecast(json, prefs.getLong(KEY_FETCHED_AT, 0L)) }.getOrNull()
        }
        return WeatherState(place, forecast, prefs.getBoolean(KEY_FAHRENHEIT, false))
    }

    companion object {
        private const val TAG = "ZflWeather"
        private const val KEY_PLACE_NAME = "place_name"
        private const val KEY_PLACE_LAT = "place_lat"
        private const val KEY_PLACE_LON = "place_lon"
        private const val KEY_PLACE_CURRENT = "place_current"
        private const val KEY_FORECAST = "forecast_json"
        private const val KEY_FETCHED_AT = "fetched_at"
        private const val KEY_FAHRENHEIT = "fahrenheit"
        private const val MAX_AGE_MS = 30 * 60 * 1000L
        private const val NETWORK_TIMEOUT_MS = 15_000
        private const val LOCATION_TIMEOUT_MS = 10_000L

        @Volatile
        private var instance: WeatherRepository? = null

        fun get(context: Context): WeatherRepository =
            instance ?: synchronized(this) { instance ?: WeatherRepository(context).also { instance = it } }
    }
}
