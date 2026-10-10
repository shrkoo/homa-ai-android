package ai.homa.app

import android.app.Activity
import android.speech.tts.TextToSpeech
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.util.Locale

/**
 * HomaTTSBridge — native text-to-speech bridge for the Homa Android WebView.
 * Injected as JavascriptInterface "HomaTTS".
 *
 * Web-side contract (src/utils/nativeTTS.js):
 *   isAvailable() -> boolean
 *   getLanguages() -> String[]   (returned as JSON string; web side parses)
 *   speak(text, lang, rate, volume, id)
 *   stop()
 * Callback:
 *   window.__homaTtsResolve(id, success)
 */
class HomaTTSBridge(
    private val activity: Activity,
    private val webView: WebView
) {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val pendingTts = mutableListOf<TtsJob>()

    private data class TtsJob(val text: String, val lang: String, val rate: Double, val volume: Float, val id: String)

    @JavascriptInterface
    fun isAvailable(): Boolean {
        // Report ready only after the TTS engine has initialized successfully.
        // If not yet ready, kick off init so the next call succeeds.
        if (!ttsReady) initTTS()
        return ttsReady
    }

    @JavascriptInterface
    fun getLanguages(): String {
        return try {
            val langs = tts?.engines?.flatMap { it.supportedLanguages?.toList() ?: emptyList() }?.distinct() ?: emptyList()
            "[" + langs.joinToString(",") { "\"${it}\"" } + "]"
        } catch (e: Exception) { "[]" }
    }

    @JavascriptInterface
    fun speak(text: String, lang: String, rate: Double, volume: Double, id: String) {
        activity.runOnUiThread {
            val job = TtsJob(text, lang, rate, volume.toFloat(), id)
            if (!ttsReady || tts == null) {
                pendingTts.add(job)
                initTTS()
                return@runOnUiThread
            }
            speakJob(job)
        }
    }

    @JavascriptInterface
    fun stop() {
        activity.runOnUiThread {
            try { tts?.stop() } catch (e: Exception) {}
        }
    }

    private fun speakJob(job: TtsJob) {
        try {
            val tts = this.tts ?: run {
                eval("window.__homaTtsResolve && window.__homaTtsResolve('${job.id}', false);")
                return
            }
            val locale = parseLocale(job.lang)
            val result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fall back to device default locale if requested language unavailable
                tts.setLanguage(Locale.getDefault())
            }
            tts.setSpeechRate(job.rate.toFloat())
            tts.setPitch(1.0f)
            tts.speak(job.text, TextToSpeech.QUEUE_FLUSH, null, job.id)
            // Resolve success optimistically — onDone/onError will fire if the
            // engine supports UtteranceProgressListener; otherwise the web side
            // has its own safety timeout.
            eval("window.__homaTtsResolve && window.__homaTtsResolve('${job.id}', true);")
        } catch (e: Exception) {
            Log.e("HomaTTS", "speak failed", e)
            eval("window.__homaTtsResolve && window.__homaTtsResolve('${job.id}', false);")
        }
    }

    private fun initTTS() {
        if (tts != null) return
        tts = TextToSpeech(activity) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        utteranceId?.let { eval("window.__homaTtsResolve && window.__homaTtsResolve('$it', true);") }
                    }
                    override fun onError(utteranceId: String?) {
                        utteranceId?.let { eval("window.__homaTtsResolve && window.__homaTtsResolve('$it', false);") }
                    }
                })
                val jobs = pendingTts.toList()
                pendingTts.clear()
                jobs.forEach { speakJob(it) }
            } else {
                val jobs = pendingTts.toList()
                pendingTts.clear()
                jobs.forEach { eval("window.__homaTtsResolve && window.__homaTtsResolve('${it.id}', false);") }
            }
        }
    }

    private fun parseLocale(lang: String): Locale {
        return try {
            if (lang.contains('-')) Locale.forLanguageTag(lang) else Locale(lang)
        } catch (e: Exception) { Locale.getDefault() }
    }

    private fun eval(js: String) {
        try { webView.evaluateJavascript(js, null) } catch (e: Exception) {}
    }

    fun destroy() {
        try { tts?.stop(); tts?.shutdown() } catch (e: Exception) {}
        tts = null
        ttsReady = false
    }
}