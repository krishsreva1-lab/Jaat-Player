package com.krish.jaatplayer.ui.component

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs

/** Lets the Search screen claim the phone tilt so the bottom bar's puck stays still while it is open. */
object NavTiltGate {
    @Volatile
    var blockedBySearch: Boolean = false
}

private const val FILTER_ALPHA = 0.18f          // low-pass smoothing of the raw sensor
private const val STEP_THRESHOLD = 3.4f         // ~20 degrees of tilt (m/s^2 of gravity on the x axis)
private const val REARM_THRESHOLD = 1.5f        // phone must come back near level before the next step
private const val MIN_STEP_INTERVAL_MS = 300L

/**
 * Tilt-to-switch-tab, smoothed and one step at a time.
 *
 * - Samples at game rate through ONE listener for the whole time it is enabled (the old code used
 *   the slow UI rate and re-registered the listener after every tab change).
 * - Low-pass filtered, so a shaky hand or a sensor spike doesn't trigger a step.
 * - Moves one tab per tilt and only re-arms once the phone is level again, so the puck can stop on
 *   the middle tab instead of racing across all of them.
 */
@Composable
fun TiltTabStepper(
    enabled: Boolean,
    currentIndex: () -> Int,
    tabsCount: Int,
    blocked: () -> Boolean = { false },
    onStep: (Int) -> Unit,
) {
    val context = LocalContext.current
    val currentIndexState = rememberUpdatedState(currentIndex)
    val blockedState = rememberUpdatedState(blocked)
    val onStepState = rememberUpdatedState(onStep)

    DisposableEffect(enabled, tabsCount) {
        if (!enabled || tabsCount < 2) return@DisposableEffect onDispose {}
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensorManager == null || sensor == null) return@DisposableEffect onDispose {}

        var filtered = 0f
        var armed = false // stays disarmed until the phone has been level once, so opening the screen while tilted can't fire
        var lastStepAt = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                filtered += FILTER_ALPHA * (-event.values[0] - filtered)
                val magnitude = abs(filtered)
                if (!armed) {
                    if (magnitude < REARM_THRESHOLD) armed = true
                    return
                }
                if (magnitude < STEP_THRESHOLD) return
                val now = SystemClock.elapsedRealtime()
                if (now - lastStepAt < MIN_STEP_INTERVAL_MS) return
                armed = false
                if (blockedState.value()) return
                val index = currentIndexState.value()
                val target = (if (filtered > 0f) index + 1 else index - 1).coerceIn(0, tabsCount - 1)
                if (target != index) {
                    lastStepAt = now
                    onStepState.value(target)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensorManager.unregisterListener(listener) }
    }
}
