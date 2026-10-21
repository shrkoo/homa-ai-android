package ai.homa.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object MobileNotificationHttp {
    fun request(token: String, path: String, body: JSONObject? = null): JSONObject {
        val connection = URL(BuildConfig.WEB_APP_URL.trimEnd('/') + "/api/resilient/payments/" + path)
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = if (body == null) "GET" else "POST"
            connection.connectTimeout = 12000
            connection.readTimeout = 15000
            connection.setRequestProperty("X-User-Token", token)
            if (body != null) {
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            if (connection.responseCode !in 200..299) throw java.io.IOException("notification_http_" + connection.responseCode)
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }
}