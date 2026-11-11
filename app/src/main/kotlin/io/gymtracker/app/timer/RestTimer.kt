package io.gymtracker.app.timer

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Estado único del temporizador de descanso, compartido por la UI y el servicio en primer plano.
 * Se persiste en hora de pared para sobrevivir a la muerte del proceso; el servicio lo
 * reconstruye al reiniciarse.
 */
@Singleton
class RestTimer @Inject constructor(@param:ApplicationContext private val context: Context) {

    data class Running(val endAtElapsedMs: Long, val totalSeconds: Int) {
        fun remainingMs(now: Long = SystemClock.elapsedRealtime()): Long = (endAtElapsedMs - now).coerceAtLeast(0)
    }

    private val prefs = context.getSharedPreferences("rest_timer", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Running?> = _state

    var defaultSeconds: Int
        get() = prefs.getInt(KEY_DEFAULT, 90)
        set(value) = prefs.edit { putInt(KEY_DEFAULT, value.coerceIn(10, 600)) }

    fun start(seconds: Int = defaultSeconds) {
        set(Running(SystemClock.elapsedRealtime() + seconds * 1000L, seconds))
        RestTimerService.start(context)
    }

    fun add(seconds: Int) {
        val current = _state.value ?: return
        val base = maxOf(current.endAtElapsedMs, SystemClock.elapsedRealtime())
        set(Running(base + seconds * 1000L, current.totalSeconds + seconds))
    }

    /** Lo detiene sin avisar (el usuario decidió seguir). */
    fun skip() = set(null)

    /** Lo llama el servicio cuando la cuenta llegó a cero y ya avisó. */
    internal fun markFinished() = set(null)

    private fun set(running: Running?) {
        _state.value = running
        prefs.edit {
            if (running == null) {
                remove(KEY_END_WALL); remove(KEY_TOTAL)
            } else {
                putLong(KEY_END_WALL, System.currentTimeMillis() + running.remainingMs())
                putInt(KEY_TOTAL, running.totalSeconds)
            }
        }
    }

    private fun load(): Running? {
        val endWall = prefs.getLong(KEY_END_WALL, 0L)
        if (endWall <= System.currentTimeMillis()) return null
        return Running(SystemClock.elapsedRealtime() + (endWall - System.currentTimeMillis()), prefs.getInt(KEY_TOTAL, 90))
    }

    private companion object {
        const val KEY_END_WALL = "end_wall_ms"
        const val KEY_TOTAL = "total_seconds"
        const val KEY_DEFAULT = "default_seconds"
    }
}
