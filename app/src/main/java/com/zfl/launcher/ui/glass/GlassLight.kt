package com.zfl.launcher.ui.glass

import android.animation.ValueAnimator
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import com.zfl.launcher.ui.theme.LiquidGlassTokens
import kotlin.math.sqrt

/**
 * Direction of the light falling on Liquid glass: [angle] in radians from the top of the
 * screen, positive towards the right. Glass nodes read it in the draw phase, so a change
 * redraws them without recomposing anything.
 */
class GlassLight {
    var angle: Float by mutableFloatStateOf(0f)
        internal set
}

/**
 * Moves [light] with the sideways tilt of the phone, so the highlight on Liquid glass floats
 * like a reflection. Uses the game rotation vector (gyroscope + accelerometer, no magnetometer),
 * else gravity, else the raw accelerometer: none of them needs a permission.
 *
 * The owner calls [update] whenever the screen's visibility or the glass settings change;
 * stopping returns the light to the top. Samples arrive on the main thread at
 * [LiquidGlassTokens.TiltSamplingUs]; [TiltLightFilter] smooths them and drops changes too
 * small to see, so a phone at rest causes no redraws.
 */
class GlassTiltSensor(context: Context, private val light: GlassLight) {
    private val sensors: SensorManager? = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensors?.let {
        it.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: it.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: it.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    private val filter = TiltLightFilter()
    private val rotation = FloatArray(9)
    private var listening = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (filter.update(upX(event))) light.angle = filter.angle
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    /**
     * Listens while [visible] (the home screen is started) with the effective [mode] Liquid and
     * [tiltEnabled]; animations disabled system-wide keep the light static on top.
     */
    fun update(mode: GlassMode, tiltEnabled: Boolean, visible: Boolean) {
        val listen = shouldTrackTilt(mode, tiltEnabled, visible, ValueAnimator.areAnimatorsEnabled(), sensor != null)
        if (listen == listening) return
        listening = listen
        if (listen) {
            val ok = sensors!!.registerListener(listener, sensor, LiquidGlassTokens.TiltSamplingUs, Handler(Looper.getMainLooper()))
            if (!ok) {
                Log.w(GLASS_TAG, "Tilt sensor ${sensor?.name} refused the listener")
                listening = false
            }
        } else {
            sensors?.unregisterListener(listener)
            filter.reset()
            light.angle = 0f
        }
    }

    /** x component of the world's "up" in screen coordinates: the sideways tilt. */
    private fun upX(event: SensorEvent): Float = when (event.sensor.type) {
        Sensor.TYPE_GAME_ROTATION_VECTOR -> {
            SensorManager.getRotationMatrixFromVector(rotation, event.values)
            // The third row of the device-to-world rotation is world Z (up) in device axes.
            rotation[6]
        }
        else -> {
            // Gravity and acceleration at rest point up in device axes; normalise to a unit vector.
            val v = event.values
            val len = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
            if (len > 0f) v[0] / len else 0f
        }
    }
}
