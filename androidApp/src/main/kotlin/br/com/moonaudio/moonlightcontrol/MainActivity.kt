package br.com.moonaudio.moonlightcontrol

import android.os.Bundle
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import br.com.moonaudio.moonlightcontrol.ui.MoonLightApp
import br.com.moonaudio.moonlightcontrol.ui.MoonLightTheme

/** The only Activity: hosts the native screens and the WebView console (BRIEF §2). */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoonLightTheme {
                MoonLightApp()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // The login cookie must survive the app being killed (BRIEF §4).
        CookieManager.getInstance().flush()
    }
}
