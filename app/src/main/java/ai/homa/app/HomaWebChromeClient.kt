package ai.homa.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.core.content.FileProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.io.File

/**
 * Chrome client for the WebView. The important job here is delivering the result of
 * <input type="file"> pickers back to the page: the chooser is launched through a
 * registered ActivityResultLauncher owned by MainActivity, and its result is handed
 * back via [handleFileChooserResult]. Launching it any other way leaves the page
 * waiting forever — the picker opens, the user chooses a file, and nothing arrives.
 */
class HomaWebChromeClient(
    private val activity: Activity,
    private val swipeRefresh: SwipeRefreshLayout,
    private val launchChooser: (content: Intent, camera: Intent?) -> Unit
) : WebChromeClient() {

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraOutputUri: Uri? = null

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        if (newProgress < 100) {
            swipeRefresh.isRefreshing = true
        } else {
            swipeRefresh.isRefreshing = false
        }
    }

    override fun onShowFileChooser(
        webView: WebView?,
        callback: ValueCallback<Array<Uri>>?,
        params: FileChooserParams?
    ): Boolean {
        // A page that opens a second chooser while one is pending must not leak the first.
        filePathCallback?.onReceiveValue(null)
        filePathCallback = callback
        cameraOutputUri = null

        val accepted = params?.acceptTypes ?: arrayOf("*/*")
        // Empty accept list means "anything", which includes photos.
        val wantsImage = accepted.isEmpty() || accepted.any { it.contains("image") || it == "*/*" }

        val content: Intent = try {
            params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
        } catch (e: Exception) {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
        }
        if (content.type == null) content.type = "*/*"

        // Offer the camera alongside the gallery for any image pick — receipts are usually
        // photographed, and without this the camera button in the app does nothing.
        val camera = if (wantsImage) createCameraIntent() else null

        return try {
            launchChooser(content, camera)
            true
        } catch (e: ActivityNotFoundException) {
            filePathCallback = null
            cameraOutputUri = null
            false
        }
    }

    /**
     * Camera capture intent writing into the app's cache through the FileProvider.
     * Returns null when no camera app can be addressed, so the chooser still opens.
     */
    private fun createCameraIntent(): Intent? = try {
        val dir = File(activity.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "homa_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        cameraOutputUri = uri
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } catch (e: Exception) {
        null
    }

    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        // Show JS alerts as Android dialogs
        result?.confirm()
        return true
    }

    fun handleFileChooserResult(resultCode: Int, data: Intent?) {
        val callback = filePathCallback ?: return
        filePathCallback = null
        val captured = cameraOutputUri
        cameraOutputUri = null

        if (resultCode == Activity.RESULT_OK) {
            val uris = mutableListOf<Uri>()
            data?.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
            }
            data?.data?.let { uris.add(it) }
            // A camera capture returns no data URI — the photo is at the output we provided.
            if (uris.isEmpty() && captured != null) uris.add(captured)
            callback.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
        } else {
            callback.onReceiveValue(null)
        }
    }
}