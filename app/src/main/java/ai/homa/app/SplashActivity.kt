package ai.homa.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition { false }

        // Immediately navigate to main — splash is handled by system.
        // Carry over the deep link URI (from notification tap or app link)
        // so MainActivity can route the WebView to the correct page.
        val intent = Intent(this, MainActivity::class.java)
        intent.data = getIntent().data
        intent.flags = Intent.FLAG_ACTIVITY_NO_HISTORY
        startActivity(intent)
        finish()
    }
}