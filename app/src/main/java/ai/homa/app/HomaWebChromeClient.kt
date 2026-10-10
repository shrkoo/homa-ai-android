package ai.homa.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class HomaWebChromeClient(
    private val activity: Activity,
    private val swipeRefresh: SwipeRefreshLayout
) : WebChromeClient() {

    // File upload callback for <input type="file">
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

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
        filePathCallback?.onReceiveValue(null)
        filePathCallback = callback

        // Determine accepted types
        val acceptTypes = params?.acceptTypes ?: arrayOf("*/*")
        val isImage = acceptTypes.any { it.contains("image") }

        try {
            val intent = if (isImage) {
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params?.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
                }
            } else {
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    // Accept all supported types: PDF, TXT, DOC, DOCX, XLS, XLSX, CSV, PPT, PPTX, images
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                        "application/pdf",
                        "text/plain",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/vnd.ms-excel",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "text/csv",
                        "application/vnd.ms-powerpoint",
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                        "image/*"
                    ))
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params?.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
                }
            }

            activity.startActivityForResult(Intent.createChooser(intent, "انتخاب فایل"), FILE_CHOOSER_REQUEST)
        } catch (e: ActivityNotFoundException) {
            filePathCallback = null
            return false
        }
        return true
    }

    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        // Show JS alerts as Android dialogs
        result?.confirm()
        return true
    }

    fun handleFileChooserResult(resultCode: Int, data: Intent?) {
        val callback = filePathCallback ?: return
        filePathCallback = null

        if (resultCode == Activity.RESULT_OK) {
            val uris = mutableListOf<Uri>()
            val clipData = data?.clipData
            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    uris.add(clipData.getItemAt(i).uri)
                }
            } else {
                data?.data?.let { uris.add(it) }
            }
            callback.onReceiveValue(uris.toTypedArray())
        } else {
            callback.onReceiveValue(null)
        }
    }

    companion object {
        const val FILE_CHOOSER_REQUEST = 1001
    }
}