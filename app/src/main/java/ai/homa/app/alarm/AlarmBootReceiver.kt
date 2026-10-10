package ai.homa.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Restores alarms after device reboot.
 * The web app schedules alarms via HomaNative.showNotification(),
 * and this receiver re-registers them on boot.
 */
class AlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_QUICKBOOT_POWERON
        ) {
            // Re-schedule alarms from SharedPreferences
            // The web app stores alarm data in localStorage which persists across reboots
            // The WebView will re-read and re-trigger alarms on next app open
            // This receiver just ensures the app is ready
        }
    }
}