package ai.homa.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.FirebaseApp

class HomaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            // Firebase may not be configured in debug — non-fatal
        }

        // Create notification channels (Android 8+)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = getSystemService(NotificationManager::class.java)

        // General messages
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_GENERAL,
            "پیام‌های هما",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "پیام‌های مهم، یادآوری‌ها و اعلان‌های عمومی"
            enableVibration(true)
        })

        // Payment & subscription
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_PAYMENT,
            "پرداخت و اشتراک",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "تأیید پرداخت، فعال‌سازی اشتراک، اتمام اعتبار"
        })

        // Reminders & alarms
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_REMINDER,
            "یادآوری و هشدار",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "یادآوری‌ها، هشدارها و وظایف"
            enableVibration(true)
        })

        // Low priority (quota info)
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_LOW,
            "اطلاعیه‌ها",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "اطلاعیه‌های کم‌اهمیت"
        })

        // Support (tickets, bug reports)
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_SUPPORT,
            "پشتیبانی",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "پاسخ تیکت‌ها و گزارش‌های باگ"
            enableVibration(true)
        })

        // AI responses
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_AI,
            "پاسخ هوش مصنوعی",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "اطلاع‌رسانی آماده شدن پاسخ هوش مصنوعی"
        })

        // Updates (announcements, new features)
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_UPDATES,
            "به‌روزرسانی‌ها",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "اعلامیه‌ها، قابلیت‌های جدید و تغییرات مهم"
        })

        // Security alerts
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_SECURITY,
            "امنیت",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "هشدارهای امنیتی و ورود به حساب"
            enableVibration(true)
        })
    }

    companion object {
        const val CHANNEL_GENERAL = "homa_general"
        const val CHANNEL_PAYMENT = "homa_payment"
        const val CHANNEL_REMINDER = "homa_reminder"
        const val CHANNEL_LOW = "homa_low"
        const val CHANNEL_SUPPORT = "homa_support"
        const val CHANNEL_AI = "homa_ai"
        const val CHANNEL_UPDATES = "homa_updates"
        const val CHANNEL_SECURITY = "homa_security"

        lateinit var instance: HomaApplication
            private set
    }
}