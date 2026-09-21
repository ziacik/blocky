package com.ziacik.blocky

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.ziacik.blocky.data.wolt.WoltCookieJar
import com.ziacik.blocky.data.wolt.WoltSessionStore
import com.ziacik.blocky.data.wolt.WoltSyncScheduler
import com.ziacik.blocky.data.wolt.WoltSyncService
import com.ziacik.blocky.data.wolt.WoltWebNavigation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WoltLoginActivity : ComponentActivity() {
	private lateinit var webView: WebView

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		webView = WebView(this).apply {
			settings.javaScriptEnabled = true
			settings.domStorageEnabled = true
			webViewClient = object : WebViewClient() {
				override fun shouldOverrideUrlLoading(
					view: WebView,
					request: WebResourceRequest,
				): Boolean {
					val url = request.url.toString()
					if (!WoltWebNavigation.shouldKeepInsideWebView(url)) {
						return false
					}

					WoltWebNavigation.browserFallbackUrl(url)?.let(view::loadUrl)
					return true
				}
			}
		}
		val cookieManager = CookieManager.getInstance().apply {
			setAcceptCookie(true)
			setAcceptThirdPartyCookies(webView, true)
		}
		val connectButton = Button(this).apply {
			text = "Pripojiť Wolt"
		}
		val layout = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			addView(
				webView,
				LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					0,
					1f,
				),
			)
			addView(
				connectButton,
				LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT,
				),
			)
		}
		setContentView(layout)

		connectButton.setOnClickListener {
			cookieManager.flush()
			val cookies = WoltCookieJar.mergeCookieHeaders(
				listOfNotNull(
					cookieManager.getCookie("https://wolt.com"),
					cookieManager.getCookie("https://consumer-api.wolt.com"),
					cookieManager.getCookie("https://authentication.wolt.com"),
				)
			)
			if (!cookies.contains("__wtoken=")) {
				Toast.makeText(this, "Najprv sa vo Wolte prihlás.", Toast.LENGTH_SHORT).show()
				return@setOnClickListener
			}

			WoltSessionStore(this).saveCookies(cookies)
			connectButton.isEnabled = false
			connectButton.text = "Načítavam nákupy…"

			lifecycleScope.launch {
				val result = withContext(Dispatchers.IO) {
					runCatching { WoltSyncService(applicationContext).sync() }
				}
				result.onSuccess { imported ->
					WoltSyncScheduler.schedulePeriodic(applicationContext)
					Toast.makeText(
						this@WoltLoginActivity,
						"Wolt pripojený. Načítaných objednávok: $imported",
						Toast.LENGTH_LONG,
					).show()
					finish()
				}.onFailure { error ->
					connectButton.isEnabled = true
					connectButton.text = "Skúsiť znova"
					Toast.makeText(
						this@WoltLoginActivity,
						error.message ?: "Wolt sa nepodarilo synchronizovať.",
						Toast.LENGTH_LONG,
					).show()
				}
			}
		}

		webView.loadUrl("https://wolt.com")
	}

	override fun onDestroy() {
		webView.destroy()
		super.onDestroy()
	}
}
