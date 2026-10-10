package ai.homa.app

/**
 * Build configuration constants.
 * These are set in app/build.gradle.kts via buildConfigField.
 */
object BuildConfig {
    const val WEB_APP_URL: String = "https://capable-aria-chat-flow.base44.app"
    const val WORKER_API_URL: String = "https://homa-ai-core.shahramalidazeh6203.workers.dev"

    // Market flavor: "googleplay", "myket", or "bazaar"
    // This is overwritten by the generated BuildConfig from Gradle.
    val MARKET: String = try {
        ai.homa.app.BuildConfig::class.java.getDeclaredField("MARKET").get(null) as String
    } catch (e: Exception) {
        "googleplay"
    }
}