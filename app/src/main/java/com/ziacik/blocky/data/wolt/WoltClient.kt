package com.ziacik.blocky.data.wolt

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class WoltClient(
	private val sessionStore: WoltSessionStore,
) {
	fun fetchOrders(limit: Int = 200): String {
		val cookies = sessionStore.cookies() ?: throw WoltAuthException("Wolt nie je pripojený.")
		val connection = (URL("$ORDERS_ENDPOINT?limit=$limit").openConnection() as HttpURLConnection).apply {
			requestMethod = "GET"
			connectTimeout = 15_000
			readTimeout = 20_000
			setRequestProperty("Accept", "application/json")
			setRequestProperty("Cookie", cookies)
			setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Blocky/0.1")
		}

		try {
			val status = connection.responseCode
			val rotatedCookies = connection.headerFields.entries
				.filter { (name, _) -> name?.equals("Set-Cookie", ignoreCase = true) == true }
				.flatMap { (_, values) -> values.orEmpty() }
			if (rotatedCookies.isNotEmpty()) {
				sessionStore.saveCookies(WoltCookieJar.mergeSetCookieHeaders(cookies, rotatedCookies))
			}

			val stream = if (status in 200..299) connection.inputStream else connection.errorStream
			val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

			if (status == HttpURLConnection.HTTP_UNAUTHORIZED || status == HttpURLConnection.HTTP_FORBIDDEN) {
				sessionStore.clearCookies()
				throw WoltAuthException("Wolt prihlásenie vypršalo. Pripoj Wolt znova.")
			}
			if (status !in 200..299) {
				val suffix = body.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()
				throw IOException("Wolt API vrátilo HTTP $status$suffix")
			}
			return body
		} finally {
			connection.disconnect()
		}
	}

	private companion object {
		const val ORDERS_ENDPOINT = "https://consumer-api.wolt.com/order-xp/web/v1/pages/orders"
	}
}

class WoltAuthException(message: String) : IOException(message)
