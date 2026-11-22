package com.thinkhab

import android.app.Activity
import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Environment
import android.os.StatFs
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var fileCb: ValueCallback<Array<Uri>>? = null

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
            webChromeClient = object : WebChromeClient() {
                // выбор аватарки с устройства
                override fun onShowFileChooser(
                    w: WebView?, cb: ValueCallback<Array<Uri>>?, p: FileChooserParams?
                ): Boolean {
                    fileCb?.onReceiveValue(null)
                    fileCb = cb
                    val i = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                    }
                    return try {
                        startActivityForResult(Intent.createChooser(i, "Выберите изображение"), 7)
                        true
                    } catch (e: Exception) {
                        fileCb = null
                        false
                    }
                }
            }
            loadUrl("file:///android_asset/launcher.html")
        }
        setContentView(web)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 7) {
            val uri = data?.data
            fileCb?.onReceiveValue(if (resultCode == RESULT_OK && uri != null) arrayOf(uri) else null)
            fileCb = null
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
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

        @JavascriptInterface fun charging(): Boolean {
            val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val s = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            return s == BatteryManager.BATTERY_STATUS_CHARGING || s == BatteryManager.BATTERY_STATUS_FULL
        }

        @JavascriptInterface fun getVolume(): Int {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            return if (max > 0) am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max else 0
        }

        @JavascriptInterface fun setVolume(p: Int) {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, p.coerceIn(0, 100) * max / 100, 0)
        }

        @JavascriptInterface fun getBrightness(): Int {
            val b = window.attributes.screenBrightness
            if (b >= 0f) return (b * 100).toInt()
            return try {
                Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) * 100 / 255
            } catch (e: Exception) { 50 }
        }

        @JavascriptInterface fun setBrightness(p: Int) {
            runOnUiThread {
                val a = window.attributes
                a.screenBrightness = p.coerceIn(2, 100) / 100f
                window.attributes = a
            }
        }

        // ---- режим восстановления: сведения об устройстве ----
        @JavascriptInterface fun deviceInfo(): String {
            val o = JSONObject()
            o.put("brand", Build.MANUFACTURER)
            o.put("model", Build.MODEL)
            o.put("android", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            val soc = if (Build.VERSION.SDK_INT >= 31 && Build.SOC_MODEL.isNotBlank() && Build.SOC_MODEL != "unknown")
                "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE
            o.put("soc", soc)
            val cores = Runtime.getRuntime().availableProcessors()
            o.put("cores", cores)
            o.put("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "")
            var mhz = 0L
            for (i in 0 until cores) {
                try {
                    val f = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toLong() / 1000
                    if (f > mhz) mhz = f
                } catch (e: Exception) { }
            }
            o.put("mhz", mhz)
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            o.put("ramTotal", mi.totalMem / 1048576)
            o.put("ramFree", mi.availMem / 1048576)
            try {
                val sf = StatFs(Environment.getDataDirectory().path)
                o.put("stTotal", sf.totalBytes / 1e9)
                o.put("stFree", sf.availableBytes / 1e9)
            } catch (e: Exception) { }
            val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (i != null) {
                val lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scl = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (lvl >= 0 && scl > 0) o.put("level", lvl * 100 / scl)
                val t = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                if (t != Int.MIN_VALUE) o.put("temp", t / 10.0)
                val v = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                if (v > 0) o.put("voltage", v / 1000.0)
                o.put("plugged", i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0))
            }
            cpuTemp()?.let { o.put("cpuTemp", it) }
            chargerVolt()?.let { o.put("chgV", it) }
            try {
                val pi = packageManager.getPackageInfo(packageName, 0)
                o.put("ver", pi.versionName ?: "1.0")
                o.put("built", SimpleDateFormat("MM/dd/yyyy", Locale.US).format(Date(pi.lastUpdateTime)))
            } catch (e: Exception) { }
            return o.toString()
        }

        private fun cpuTemp(): Double? {
            try {
                val zones = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") } ?: return null
                for (z in zones.sortedBy { it.name }) {
                    val type = try { File(z, "type").readText().trim().lowercase() } catch (e: Exception) { "" }
                    if (!(type.contains("cpu") || type.contains("tsens") || type.contains("soc") || type.contains("therm"))) continue
                    val raw = try { File(z, "temp").readText().trim().toDouble() } catch (e: Exception) { continue }
                    val c = if (raw > 1000) raw / 1000 else raw
                    if (c in 10.0..120.0) return c
                }
            } catch (e: Exception) { }
            return null
        }

        private fun chargerVolt(): Double? {
            for (n in listOf("usb", "ac", "wireless")) {
                try {
                    val uv = File("/sys/class/power_supply/$n/voltage_now").readText().trim().toDouble()
                    if (uv > 0) return uv / 1_000_000.0
                } catch (e: Exception) { }
            }
            return null
        }

        @Suppress("DEPRECATION")
        @JavascriptInterface fun getRadios(): String {
            val o = JSONObject()
            o.put("wifi", try { (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager).isWifiEnabled } catch (e: Exception) { false })
            o.put("bt", try { BluetoothAdapter.getDefaultAdapter()?.isEnabled == true } catch (e: Exception) { false })
            o.put("air", try { Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1 } catch (e: Exception) { false })
            return o.toString()
        }

        // Android не разрешает приложениям напрямую переключать эти модули,
        // поэтому открывается системная панель (на старых версиях Wi-Fi переключается сразу)
        @Suppress("DEPRECATION")
        @JavascriptInterface fun toggleRadio(which: String) {
            runOnUiThread {
                try {
                    val i = when (which) {
                        "wifi" -> {
                            if (Build.VERSION.SDK_INT < 29) {
                                val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                                wm.isWifiEnabled = !wm.isWifiEnabled
                                null
                            } else Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                        }
                        "bt" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                        else -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                    }
                    if (i != null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) { }
            }
        }

        @Suppress("DEPRECATION")
        @JavascriptInterface fun openBrowser() {
            runOnUiThread {
                startActivity(Intent(this@MainActivity, BrowserActivity::class.java))
                overridePendingTransition(R.anim.br_in, R.anim.br_hold)
            }
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
