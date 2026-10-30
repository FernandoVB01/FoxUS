package com.foxus.app.intervention

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.foxus.app.MainActivity
import com.foxus.app.R
import com.foxus.app.data.remote.GenerateMissionResponseDto
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Manages FOXUS proactive Duolingo-style interventions:
 * 1. High-importance Heads-Up Notifications with bullet preview and direct action intents.
 * 2. Optional, strictly opt-in floating overlay card (`SYSTEM_ALERT_WINDOW`) with swipe gesture
 *    for users who explicitly authorize urgent project interruptions outside the app.
 * 3. Scheduled `WorkManager` periodic checks + 5-second live demo trigger.
 */
class FoxInterventionManager(private val appContext: Context) {

    companion object {
        const val CHANNEL_ID = "foxus_smart_pressure_v1"
        const val NOTIFICATION_ID = 4092
        const val EXTRA_INTERRUPT_ACTION = "foxus_interrupt_action"
        const val EXTRA_TASK_ID = "foxus_interrupt_task_id"
        const val EXTRA_HOOK_MODE = "foxus_interrupt_hook_mode"
        const val ACTION_OPEN_PRESSURE = "open_pressure_modal"
        const val ACTION_START_NOW = "start_3min_now"

        private const val PERIODIC_WORK_NAME = "foxus_periodic_smart_pressure"
        private const val DEMO_5S_WORK_NAME = "foxus_demo_5s_interrupt"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeOverlayView: View? = null

    init {
        ensureNotificationChannel()
    }

    fun ensureNotificationChannel() {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "FOXUS · Modo Presión Inteligente",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Intervenciones estilo Duolingo cuando tienes entregas urgentes o pediste presión."
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(appContext)

    fun createOverlayPermissionIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${appContext.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun syncPeriodicPressureSchedule(enabled: Boolean) {
        val wm = WorkManager.getInstance(appContext)
        if (!enabled) {
            wm.cancelUniqueWork(PERIODIC_WORK_NAME)
            return
        }
        val req = PeriodicWorkRequestBuilder<FoxPressureWorker>(15, TimeUnit.MINUTES)
            .setInputData(workDataOf("demo_force" to false))
            .build()
        wm.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }

    fun scheduleLiveTestInterruptIn5Seconds(taskId: String?) {
        val req = OneTimeWorkRequestBuilder<FoxPressureWorker>()
            .setInitialDelay(5, TimeUnit.SECONDS)
            .setInputData(
                workDataOf(
                    "demo_force" to true,
                    "task_id" to (taskId ?: "")
                )
            )
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            DEMO_5S_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            req
        )
    }

    @SuppressLint("MissingPermission")
    fun postHeadsUpIntervention(
        mission: GenerateMissionResponseDto,
        reasonShort: String,
        allowExperimentalOverlay: Boolean
    ) {
        val bullets = if (mission.microtask.steps.isNotEmpty()) {
            mission.microtask.steps.take(3).joinToString("\n") { "• $it" }
        } else {
            "• ${mission.microtask.title}\n• Meta: ${mission.microtask.successCriteria}"
        }

        val openIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_INTERRUPT_ACTION, ACTION_OPEN_PRESSURE)
            putExtra(EXTRA_TASK_ID, mission.taskId)
            putExtra(EXTRA_HOOK_MODE, mission.hook.hookType)
        }
        val openPending = PendingIntent.getActivity(
            appContext,
            101,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val startNowIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_INTERRUPT_ACTION, ACTION_START_NOW)
            putExtra(EXTRA_TASK_ID, mission.taskId)
            putExtra(EXTRA_HOOK_MODE, mission.hook.hookType)
        }
        val startPending = PendingIntent.getActivity(
            appContext,
            102,
            startNowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (hasNotificationPermission()) {
            val notif = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_foxus)
                .setContentTitle("🦊 ${mission.hook.visualModeName}: ${mission.microtask.title}")
                .setContentText(mission.hook.message)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("${mission.hook.message}\n$bullets\n$reasonShort")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(openPending)
                .addAction(0, "🎮 Minijuego 30s", openPending)
                .addAction(0, "⚡ Iniciar reto", startPending)
                .build()

            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notif)
        }

        if (allowExperimentalOverlay && canDrawOverlays()) {
            mainHandler.post {
                showNonBlockingSwipeOverlay(mission, openPending)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showNonBlockingSwipeOverlay(
        mission: GenerateMissionResponseDto,
        startPending: PendingIntent
    ) {
        dismissOverlay()
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = appContext.resources.displayMetrics.density

        val cardBg = GradientDrawable().apply {
            cornerRadius = 22f * density
            setColor(Color.parseColor("#093B4A"))
            setStroke((2f * density).toInt(), Color.parseColor("#F58220"))
        }

        val container = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg
            val pad = (16 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val titleView = TextView(appContext).apply {
            text = "🦊 FOXUS · ${mission.microtask.title}"
            setTextColor(Color.parseColor("#F8B62D"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }

        val bulletText = mission.microtask.steps.firstOrNull() ?: mission.hook.message
        val bodyView = TextView(appContext).apply {
            text = "• $bulletText\n🎮 Juega 30s 'Atrapa a FOX' para activar tu enfoque (o desliza izq. para cerrar)"
            setTextColor(Color.parseColor("#F5EBD7"))
            textSize = 13f
            setPadding(0, (6 * density).toInt(), 0, (10 * density).toInt())
        }

        val actionsRow = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val dismissBtn = TextView(appContext).apply {
            text = "Posponer ✕"
            setTextColor(Color.parseColor("#B8CBD0"))
            textSize = 13f
            setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
            setOnClickListener { dismissOverlay() }
        }

        val startBtnBg = GradientDrawable().apply {
            cornerRadius = 12f * density
            setColor(Color.parseColor("#F58220"))
        }
        val startBtn = TextView(appContext).apply {
            text = "🎮 Minijuego 30s"
            background = startBtnBg
            setTextColor(Color.parseColor("#0B1720"))
            typeface = Typeface.DEFAULT_BOLD
            textSize = 13f
            setPadding((14 * density).toInt(), (8 * density).toInt(), (14 * density).toInt(), (8 * density).toInt())
            setOnClickListener {
                dismissOverlay()
                runCatching { startPending.send() }
            }
        }

        actionsRow.addView(dismissBtn)
        actionsRow.addView(startBtn)
        container.addView(titleView)
        container.addView(bodyView)
        container.addView(actionsRow)

        val params = WindowManager.LayoutParams(
            (appContext.resources.displayMetrics.widthPixels * 0.92).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (48 * density).toInt()
        }

        var downRawX = 0f
        container.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    v.translationX = dx
                    v.alpha = (1f - (abs(dx) / (280f * density))).coerceIn(0.35f, 1f)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val dx = event.rawX - downRawX
                    val threshold = 95f * density
                    if (dx > threshold) {
                        dismissOverlay()
                        runCatching { startPending.send() }
                    } else if (dx < -threshold) {
                        dismissOverlay()
                    } else {
                        v.animate().translationX(0f).alpha(1f).setDuration(180).start()
                    }
                    true
                }
                else -> false
            }
        }

        runCatching {
            wm.addView(container, params)
            activeOverlayView = container
        }
    }

    fun dismissOverlay() {
        val view = activeOverlayView ?: return
        activeOverlayView = null
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        runCatching { wm.removeView(view) }
    }
}
