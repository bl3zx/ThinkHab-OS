package com.thinkhab

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.net.URLEncoder

// Встроенный браузер: настоящая веб-страница на движке Chromium (WebView) в собственном окне приложения
class BrowserActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var omni: EditText
    private lateinit var prog: ProgressBar
    private val home = "https://www.google.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFFF1F3F4.toInt())
            setPadding(px(8), px(4), px(8), px(4))
        }
        fun btn(t: String, f: () -> Unit) = TextView(this).apply {
            text = t
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(0xFF5F6368.toInt())
            layoutParams = LinearLayout.LayoutParams(px(40), px(40))
            setOnClickListener { f() }
        }

        omni = EditText(this).apply {
            setSingleLine(true)
            textSize = 14f
            hint = "Search or type URL"
            setTextColor(0xFF202124.toInt())
            setHintTextColor(0xFF70757A.toInt())
            setPadding(px(16), 0, px(16), 0)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = px(20).toFloat()
            }
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            layoutParams = LinearLayout.LayoutParams(0, px(38), 1f).apply { setMargins(px(4), 0, px(4), 0) }
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                    go(text.toString()); true
                } else false
            }
        }

        prog = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(3))
        }

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?): Boolean {
                    val u = r?.url ?: return false
                    if (u.scheme == "http" || u.scheme == "https") return false
                    try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (e: Exception) { }
                    return true
                }
                override fun onPageStarted(v: WebView?, url: String?, f: Bitmap?) {
                    if (url != null && !omni.hasFocus()) omni.setText(url)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(v: WebView?, p: Int) {
                    prog.progress = p
                    prog.visibility = if (p < 100) View.VISIBLE else View.GONE
                }
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        bar.addView(btn("‹") { if (web.canGoBack()) web.goBack() })
        bar.addView(btn("›") { if (web.canGoForward()) web.goForward() })
        bar.addView(btn("↻") { web.reload() })
        bar.addView(omni)
        bar.addView(btn("⌂") { web.loadUrl(home) })
        bar.addView(btn("✕") { finish() })
        root.addView(bar)
        root.addView(prog)
        root.addView(web)
        setContentView(root)
        web.loadUrl(home)
    }

    override fun onResume() {
        super.onResume()
        hideBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    @Suppress("DEPRECATION")
    private fun hideBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    private fun go(q: String) {
        val t = q.trim()
        if (t.isEmpty()) return
        val url = when {
            Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(t) -> t
            !t.contains(' ') && t.contains('.') -> "https://$t"
            else -> "https://www.google.com/search?q=" + URLEncoder.encode(t, "UTF-8")
        }
        web.loadUrl(url)
        omni.clearFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(omni.windowToken, 0)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else finish()
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.br_hold, R.anim.br_out)
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
