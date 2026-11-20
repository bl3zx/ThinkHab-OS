package com.thinkhab

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class MainActivity : Activity() {
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        web = WebView(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false  // звук загрузки играет сразу
            addJavascriptInterface(Bridge(), "Android")
            webViewClient = WebViewClient()
            loadUrl("file:///android_asset/launcher.html")
        }
        setContentView(web)
    }

    override fun onResume() {
        super.onResume()
        hideBars()
        if (::web.isInitialized) web.evaluateJavascript("window.onResume&&onResume()", null)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        web.evaluateJavascript("window.onBack&&onBack()", null)
    }

    @Suppress("DEPRECATION")
    private fun hideBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    inner class Bridge {
        @JavascriptInterface fun getGames(): String = list(true)
        @JavascriptInterface fun getApps(): String = list(false)

        @JavascriptInterface fun launchApp(pkg: String) {
            packageManager.getLaunchIntentForPackage(pkg)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(it)
            }
        }

        @JavascriptInterface fun openPlayGames() {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://store/apps/category/GAME"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/games"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        @JavascriptInterface fun battery(): Int {
            val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
            return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }

        @JavascriptInterface fun exit() {
            runOnUiThread { finishAndRemoveTask() }
        }
    }

    @Suppress("DEPRECATION")
    private fun list(games: Boolean): String {
        val pm = packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val items = pm.queryIntentActivities(q, 0)
            .filter { it.activityInfo.packageName != packageName }
            .filter {
                val ai = it.activityInfo.applicationInfo
                val isGame = (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0 ||
                        (Build.VERSION.SDK_INT >= 26 && ai.category == ApplicationInfo.CATEGORY_GAME)
                isGame == games
            }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
        val arr = JSONArray()
        for (ri in items) {
            arr.put(JSONObject()
                .put("p", ri.activityInfo.packageName)
                .put("n", ri.loadLabel(pm).toString())
                .put("ic", icon(ri.loadIcon(pm))))
        }
        return arr.toString()
    }

    private fun icon(d: Drawable): String {
        val s = 128
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, s, s)
        d.draw(Canvas(bmp))
        val o = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 90, o)
        return Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP)
    }
}
