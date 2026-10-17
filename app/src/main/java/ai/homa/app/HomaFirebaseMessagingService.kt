package ai.homa.app

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class HomaFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "هُما AI"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val channel = message.data["channel"] ?: HomaApplication.CHANNEL_GENERAL
        // Route for deep-link navigation when the user taps the notification.
        // The Worker sends this as "route" or "click_action" in the data payload.
        val route = message.data["route"] ?: message.data["click_action"] ?: ""

        // Show notification with deep-link tap action
        NotificationHelper.showNotification(this, title, body, channel, route)
    }

    override fun onNewToken(token: String) {
        // Delegate to FcmTokenManager — it stores the token locally and sends to the Worker
        // using the auth token received from the web bridge (if available).
        FcmTokenManager.onNewToken(this, token)
    }
}