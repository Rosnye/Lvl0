package io.gymtracker.app.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.gymtracker.app.MainActivity
import io.gymtracker.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Servicio en primer plano que mantiene vivo el descanso aunque la app esté en segundo plano
 * o la pantalla apagada. La notificación usa el cronómetro del sistema, así la cuenta regresiva
 * se ve en la barra sin que la app la redibuje cada segundo. Al llegar a cero publica un aviso
 * con sonido y vibración y se detiene.
 */
@AndroidEntryPoint
class RestTimerService : Service() {

    @Inject lateinit var timer: RestTimer

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    /** La notificación usa el idioma elegido en la app. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(io.gymtracker.app.settings.LanguageStore.wrap(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ADD -> timer.add(ADD_SECONDS)
            ACTION_SKIP -> timer.skip()
        }
        val running = timer.state.value
        if (running == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildRunning(running),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (job == null) job = scope.launch { track() }
        return START_STICKY
    }

    private suspend fun track() {
        timer.state.collectLatest { running ->
            if (running == null) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@collectLatest
            }
            notifySafely(NOTIFICATION_ID, buildRunning(running))
            delay(running.remainingMs())
            notifySafely(DONE_NOTIFICATION_ID, buildDone())
            timer.markFinished()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildRunning(running: RestTimer.Running): android.app.Notification =
        NotificationCompat.Builder(this, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(getString(R.string.timer_notification_title))
            .setContentText(getString(R.string.timer_notification_text, running.totalSeconds))
            .setWhen(System.currentTimeMillis() + running.remainingMs())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
            .addAction(0, getString(R.string.timer_add_30), actionIntent(ACTION_ADD, 1))
            .addAction(0, getString(R.string.timer_skip), actionIntent(ACTION_SKIP, 2))
            .build()

    private fun buildDone(): android.app.Notification =
        NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(getString(R.string.timer_done_title))
            .setContentText(getString(R.string.timer_done_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent())
            .build()

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun actionIntent(action: String, code: Int): PendingIntent {
        val intent = Intent(this, RestTimerService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, code, intent, flags)
        } else {
            PendingIntent.getService(this, code, intent, flags)
        }
    }

    private fun notifySafely(id: Int, notification: android.app.Notification) {
        runCatching { NotificationManagerCompat.from(this).notify(id, notification) }
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RUNNING, getString(R.string.channel_timer_running), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.channel_timer_running_desc)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, getString(R.string.channel_timer_done), NotificationManager.IMPORTANCE_HIGH).apply {
                description = getString(R.string.channel_timer_done_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
            },
        )
    }

    companion object {
        const val CHANNEL_RUNNING = "rest_timer_running"
        const val CHANNEL_DONE = "rest_timer_done"
        const val NOTIFICATION_ID = 1001
        const val DONE_NOTIFICATION_ID = 1002
        const val ACTION_ADD = "io.gymtracker.app.timer.ADD"
        const val ACTION_SKIP = "io.gymtracker.app.timer.SKIP"
        const val ADD_SECONDS = 30

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, RestTimerService::class.java))
        }
    }
}
