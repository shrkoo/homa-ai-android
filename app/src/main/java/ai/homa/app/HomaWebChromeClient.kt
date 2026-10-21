package ai.homa.app

import android.app.Activity
import android.content.ClipData
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

class HomaWebChromeClient(
    private val activity: Activity,
    private val swipeRefresh: SwipeRefreshLayout,
    private val launchChooser: (Intent) -> Unit
) : WebChromeClient() {
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var capturedFile: File? = null
    private var capturedUri: Uri? = null

    override fun onProgressChanged(view: WebView?, progress: Int) {
        swipeRefresh.isRefreshing = progress < 100
    }

    override fun onShowFileChooser(view: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = callback
        capturedFile = null
        capturedUri = null
        val image = params?.acceptTypes?.any { it.startsWith("image/") } == true
        val content = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (image) "image/*" else "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params?.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
        }
        val camera = if (image) cameraIntent() else null
        val chooser = if (params?.isCaptureEnabled == true && camera != null) camera else Intent.createChooser(content, "انتخاب فایل").apply {
            if (camera != null) putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(camera))
        }
        return try {
            launchChooser(chooser)
            true
        } catch (error: Exception) {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
            false
        }
    }

    private fun cameraIntent(): Intent? = try {
        val directory = File(activity.cacheDir, "captures").apply { mkdirs() }
        val file = File.createTempFile("homa_", ".jpg", directory)
        val uri = FileProvider.getUriForFile(activity, activity.packageName + ".fileprovider", file)
        capturedFile = file
        capturedUri = uri
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            clipData = ClipData.newRawUri("photo", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    } catch (error: Exception) { null }

    fun handleFileChooserResult(resultCode: Int, data: Intent?) {
        val callback = filePathCallback ?: return
        filePathCallback = null
        val uris = mutableListOf<Uri>()
        if (resultCode == Activity.RESULT_OK) {
            data?.clipData?.let { clip -> for (index in 0 until clip.itemCount) uris.add(clip.getItemAt(index).uri) }
            data?.data?.let { if (!uris.contains(it)) uris.add(it) }
            if (uris.isEmpty() && capturedFile?.length()?.let { it > 0 } == true) capturedUri?.let { uris.add(it) }
        }
        callback.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
        if (uris.isEmpty()) capturedFile?.delete()
        capturedFile = null
        capturedUri = null
    }

    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        result?.confirm()
        return true
    }
}
