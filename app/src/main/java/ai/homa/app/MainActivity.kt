package ai.homa.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.Manifest
import android.content.pm.PackageManager

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var offlineView: LinearLayout
    private lateinit var webInterface: HomaWebInterface
    private lateinit var webViewClient: HomaWebViewClient
    private lateinit var webChromeClient: HomaWebChromeClient
    private var voiceBridge: HomaVoiceBridge? = null
    private var ttsBridge: HomaTTSBridge? = null

    // File picker launcher — handles both file and image selection
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        webInterface.onFilePicked(uri)
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        webInterface.onImagePicked(uri)
    }

    // POST_NOTIFICATIONS permission launcher (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Regardless of result, attempt FCM token registration — the token is still
        // useful for silent data pushes even if the user declined visible notifications.
        FcmTokenManager.fetchAndRegisterToken(this)
    }

    // File chooser launched from the WebView (<input type="file">). The result MUST return
    // through a registered launcher, otherwise the page never receives the picked file and
    // the gallery / camera / document buttons appear to do nothing.
    private val webFileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        webChromeClient.handleFileChooserResult(result.resultCode, result.data)
    }

    private fun launchFileChooser(content: Intent, camera: Intent?) {
        val chooser = if (camera != null) {
            Intent.createChooser(content, "انتخاب فایل").apply {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(camera))
            }
        } else {
            content
        }
        webFileChooserLauncher.launch(chooser)
    }


    private fun onFileChooserRequested(content: Intent, camera: Intent?) = launchFileChooser(content, camera)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge with safe area handling
        setupEdgeToEdge()

        // Root layout
        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Swipe refresh wrapping the WebView
        swipeRefresh = SwipeRefreshLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setOnRefreshListener {
                webView.reload()
            }
        }

        // WebView
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            id = View.generateViewId()
        }
        swipeRefresh.addView(webView)
        root.addView(swipeRefresh)

        // Offline view (shown when network is unavailable)
        offlineView = createOfflineView()
        root.addView(offlineView)

        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)

        // Configure WebView
        configureWebView()

        // Handle back press with WebView history
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Handle deep link from intent
        handleDeepLink(intent)

        // Load the Homa web app
        loadHomaApp()

        // Request POST_NOTIFICATIONS permission on Android 13+ (only once, on first launch)
        requestNotificationPermissionIfNeeded()
    }

    /**
     * Requests POST_NOTIFICATIONS permission on Android 13+ (API 33+).
     * Only asks once — if the user grants or denies, the system remembers.
     * Called after WebView loads so the user sees the app first.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                // Delay slightly so it doesn't interrupt the initial page load
                webView.postDelayed({
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }, 2000)
            } else {
                // Already granted — register FCM token
                FcmTokenManager.fetchAndRegisterToken(this)
            }
        } else {
            // Android 12 and below — no runtime permission needed, register directly
            FcmTokenManager.fetchAndRegisterToken(this)
        }
    }

    private fun setupEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            // Security: disable mixed content
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // User agent — identify as Homa Android app
            userAgentString = "$userAgentString HomaAI/Android/$appVersion"
            // Zoom
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            // Text
            standardFontFamily = "Vazirmatn"
            textZoom = 100
        }

        // Cookie persistence — ensures session/auth tokens survive across
        // app restarts and activity recreation. Base44 SDK uses localStorage
        // for the primary auth token, but some session cookies may still need
        // persistence for stable behaviour across WebView reloads.
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        // Web interface (JavaScript bridge)
        webInterface = HomaWebInterface(this, webView, filePickerLauncher, imagePickerLauncher)
        webView.addJavascriptInterface(webInterface, "HomaNative")

        // Voice bridges — native STT (HomaVoice) and TTS (HomaTTS) for the WebView.
        // Lets the web app use Android's SpeechRecognizer and TextToSpeech so voice
        // input/output works inside the app where the web Speech API is unavailable.
        voiceBridge = HomaVoiceBridge(this, webView)
        ttsBridge = HomaTTSBridge(this, webView)
        webView.addJavascriptInterface(voiceBridge!!, "HomaVoice")
        webView.addJavascriptInterface(ttsBridge!!, "HomaTTS")

        // WebView client — URL validation, navigation control
        webViewClient = HomaWebViewClient(this)
        webView.webViewClient = webViewClient

        // Chrome client — handles file upload dialogs, JS alerts, progress.
        // The chooser is launched through webFileChooserLauncher so the picked file
        // is delivered back to the page.
        webChromeClient = HomaWebChromeClient(this, swipeRefresh) { content, camera ->
            onFileChooserRequested(content, camera)
        }
        webView.webChromeClient = webChromeClient
    }

    private fun loadHomaApp() {
        val url = getStartUrl()
        webView.loadUrl(url)
    }

    private fun getStartUrl(): String {
        // Check for deep link path
        val data = intent?.data
        if (data != null) {
            val path = when (data.scheme) {
                "homa" -> data.host + (data.path ?: "")
                "https" -> data.path?.removePrefix("/") ?: ""
                else -> null
            }
            if (path != null && path.isNotEmpty()) {
                return "${BuildConfig.WEB_APP_URL}/$path"
            }
        }
        return BuildConfig.WEB_APP_URL
    }

    private fun handleDeepLink(intent: Intent?) {
        intent?.data?.let { uri ->
            // Security: validate that deep link does not target admin routes.
            // Check both host and path — e.g. "homa://admin-panel/users" has
            // host="admin-panel" and path="/users", so we must check the full route.
            val fullRoute = (uri.host ?: "") + (uri.path ?: "")
            if (fullRoute.contains("/admin") || fullRoute.contains("admin-panel") || fullRoute.contains("/admin")) {
                // Block deep links to admin — require in-app authorization
                intent.data = null
            }
        }
    }

    private fun createOfflineView(): LinearLayout {
        return LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            gravity = android.view.Gravity.CENTER
            setPadding(48, 48, 48, 48)

            val textView = TextView(this@MainActivity).apply {
                text = "اتصال اینترنت برقرار نیست.\nلطفاً اتصال خود را بررسی و دوباره تلاش کنید."
                textSize = 16f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 32, 0, 32)
            }
            addView(textView)

            val retryButton = android.widget.Button(this@MainActivity).apply {
                text = "تلاش مجدد"
                setOnClickListener {
                    offlineView.visibility = View.GONE
                    swipeRefresh.visibility = View.VISIBLE
                    webView.reload()
                }
            }
            addView(retryButton)
        }
    }

    fun showOffline(show: Boolean) {
        runOnUiThread {
            offlineView.visibility = if (show) View.VISIBLE else View.GONE
            swipeRefresh.visibility = if (show) View.GONE else View.VISIBLE
            if (show) swipeRefresh.isRefreshing = false
        }
    }

    fun stopSwipeRefresh() {
        swipeRefresh.isRefreshing = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
        val url = getStartUrl()
        webView.loadUrl(url)
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        // Persist cookies to disk so they survive activity destruction
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        // Refresh FCM token on resume — sends to Worker if token changed or not yet sent
        FcmTokenManager.fetchAndRegisterToken(this)
    }

    override fun onDestroy() {
        voiceBridge?.destroy()
        ttsBridge?.destroy()
        voiceBridge = null
        ttsBridge = null
        webView.apply {
            stopLoading()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }

    private val appVersion: String
        get() = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
}