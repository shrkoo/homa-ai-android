package ai.homa.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.app.ActivityCompat

/**
 * HomaVoiceBridge — native speech-to-text bridge for the Homa Android WebView.
 * Injected as JavascriptInterface "HomaVoice".
 *
 * Web-side contract (src/utils/nativeVoice.js):
 *   isAvailable() -> boolean
 *   startListening(lang)   // "fa-IR" | "en-US" | "ku-IQ"
 *   stopListening()
 * Callbacks into WebView:
 *   window.__homaVoiceCb.onResult(text, isFinal)
 *   window.__homaVoiceCb.onError(code)
 *   window.__homaVoiceCb.onEnd()
 */
class HomaVoiceBridge(
    private val activity: Activity,
    private val webView: WebView
) {
    private var speechRecognizer: SpeechRecognizer? = null

    fun ensureMicPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val granted = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) return true
        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), 707)
        return false
    }

    @JavascriptInterface
    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(activity)
    }

    @JavascriptInterface
    fun startListening(lang: String) {
        activity.runOnUiThread {
            if (!ensureMicPermission()) {
                eval("window.__homaVoiceCb && window.__homaVoiceCb.onError('not-allowed');")
                return@runOnUiThread
            }
            try {
                stopListeningInternal()
                val sr = SpeechRecognizer.createSpeechRecognizer(activity)
                sr.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsFloat: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        val code = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "no-speech"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no-speech"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "not-allowed"
                            SpeechRecognizer.ERROR_AUDIO -> "audio-capture"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "busy"
                            SpeechRecognizer.ERROR_NETWORK -> "network"
                            else -> "unknown"
                        }
                        eval("window.__homaVoiceCb && window.__homaVoiceCb.onError('$code');")
                        eval("window.__homaVoiceCb && window.__homaVoiceCb.onEnd();")
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotEmpty()) {
                            eval("window.__homaVoiceCb && window.__homaVoiceCb.onResult(${jsStr(text)}, true);")
                        }
                        eval("window.__homaVoiceCb && window.__homaVoiceCb.onEnd();")
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotEmpty()) {
                            eval("window.__homaVoiceCb && window.__homaVoiceCb.onResult(${jsStr(text)}, false);")
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                sr.startListening(intent)
                speechRecognizer = sr
            } catch (e: Exception) {
                Log.e("HomaVoice", "startListening failed", e)
                eval("window.__homaVoiceCb && window.__homaVoiceCb.onError('unknown');")
            }
        }
    }

    @JavascriptInterface
    fun stopListening() {
        activity.runOnUiThread { stopListeningInternal() }
    }

    private fun stopListeningInternal() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {}
        speechRecognizer = null
    }

    private fun eval(js: String) {
        try { webView.evaluateJavascript(js, null) } catch (e: Exception) {}
    }

    private fun jsStr(s: String): String {
        val escaped = s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")
        return "'$escaped'"
    }

    fun destroy() {
        stopListeningInternal()
    }
}