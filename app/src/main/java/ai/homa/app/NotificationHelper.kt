package ai.homa.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    private var nextId = 1000

    /**
     * Show a local notification.
     *
     * @param route Optional Homa route (e.g. "chat/abc123", "pricing").
     *              When the user taps the notification, the app opens to that page.
     *              If null/empty, tapping opens the app at its default page.
     */
    fun showNotification(
        context: Context,
        title: String,
        body: String,
        channel: String = HomaApplication.CHANNEL_GENERAL,
        route: String? = null
    ) {
        // Check POST_NOTIFICATIONS permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        // Build the tap intent — opens SplashActivity with a homa:// deep link.
        // Strip leading "/" so the route becomes the host (e.g. "chat/abc123"),
        // not a triple-slash path (e.g. "/chat/abc123" → "homa:///chat/abc123").
        val cleanRoute = route?.removePrefix("/")
        val deepLinkUri = if (!cleanRoute.isNullOrEmpty()) {
            Uri.parse("homa://$cleanRoute")
        } else {
            Uri.parse("homa://")
        }
        val tapIntent = Intent(context, SplashActivity::class.java).apply {
            data = deepLinkUri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentIntent = PendingIntent.getActivity(
            context, nextId, tapIntent, pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(nextId++, notification)
        } catch (e: SecurityException) {
            // Permission not granted — silently skip
        }
    }

    fun vibrate(context: Context, durationMs: Long = 200) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.let {
                it.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            @Suppress("DEPRECATION")
            vibrator?.vibrate(durationMs)
        }
    }

    fun cancelAll(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancelAll()
    }
}